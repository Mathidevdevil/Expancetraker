// Supabase Edge Function: parse-voice-transaction
// Holds GEMINI_API_KEY securely in Supabase Secrets (`supabase secrets set GEMINI_API_KEY=...`)
// Verifies the caller's Supabase user JWT and returns strict JSON:
// { type, amount, category, date, paymentMethod, note }

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const ALLOWED_TYPES = new Set(["expense", "income"]);
const ALLOWED_PAYMENT_METHODS = new Set([
  "cash",
  "upi",
  "card",
  "bank_transfer",
  "net_banking",
]);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

async function verifyCallerJwt(authHeader: string | null): Promise<string | null> {
  if (!authHeader || !authHeader.startsWith("Bearer ")) return null;
  const token = authHeader.slice(7).trim();
  if (!token || token.length > 4096) return null;

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !supabaseAnonKey) return null;

  const res = await fetch(`${supabaseUrl.replace(/\/$/, "")}/auth/v1/user`, {
    method: "GET",
    headers: {
      apikey: supabaseAnonKey,
      Authorization: `Bearer ${token}`,
    },
  });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user.id : null;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    // Require valid authenticated Supabase user JWT
    const userId = await verifyCallerJwt(req.headers.get("authorization"));
    if (!userId) {
      return new Response(
        JSON.stringify({ error: "Unauthorized" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) {
      return new Response(
        JSON.stringify({ error: "Smart parser unavailable" }),
        { status: 501, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const body = await req.json();
    const transcript = typeof body?.transcript === "string" ? body.transcript.trim() : "";
    if (!transcript || transcript.length > 500) {
      return new Response(
        JSON.stringify({ error: "Invalid transcript length" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const rawCategories = Array.isArray(body?.categories) ? body.categories : [];
    const categoryList: string[] = rawCategories
      .filter((c: unknown): c is string => typeof c === "string" && c.trim().length > 0 && c.length <= 80)
      .slice(0, 50);

    const ref = typeof body?.referenceDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.referenceDate)
      ? body.referenceDate
      : new Date().toISOString().split("T")[0];

    const prompt = `Extract a financial transaction from the spoken transcript below (English or Tamil-transliterated English, INR currency).
Reference today's date: ${ref}.
Existing user categories: ${JSON.stringify(categoryList)}.
Return STRICT JSON ONLY with keys:
{
  "type": "expense" | "income" | null,
  "amount": number | null,
  "category": string | null (must exactly match one of the existing categories above or null),
  "date": "YYYY-MM-DD",
  "paymentMethod": "cash" | "upi" | "card" | "net_banking" | "bank_transfer",
  "note": string
}
Transcript: ${JSON.stringify(transcript)}`;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2600);

    const geminiRes = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            responseMimeType: "application/json",
            temperature: 0.1,
          },
        }),
      }
    );
    clearTimeout(timeout);

    if (!geminiRes.ok) {
      return new Response(
        JSON.stringify({ error: "Upstream parser unavailable" }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const geminiData = await geminiRes.json();
    const text = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text;
    const raw = JSON.parse(text);

    const validated = {
      type: ALLOWED_TYPES.has(raw?.type) ? raw.type : null,
      amount: typeof raw?.amount === "number" && Number.isFinite(raw.amount) && raw.amount > 0 && raw.amount <= 100000000
        ? raw.amount
        : null,
      category:
        typeof raw?.category === "string" && categoryList.includes(raw.category)
          ? raw.category
          : null,
      date: typeof raw?.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(raw.date) ? raw.date : ref,
      paymentMethod: ALLOWED_PAYMENT_METHODS.has(raw?.paymentMethod) ? raw.paymentMethod : "upi",
      note: typeof raw?.note === "string" ? raw.note.trim().slice(0, 80) : "",
    };

    return new Response(JSON.stringify({ parsed: validated }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch {
    return new Response(
      JSON.stringify({ error: "Voice parse failed" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
