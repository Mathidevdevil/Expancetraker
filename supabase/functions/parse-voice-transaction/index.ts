// Optional Supabase Edge Function: parse-voice-transaction
// Holds GEMINI_API_KEY securely in Supabase Secrets (`supabase secrets set GEMINI_API_KEY=...`)
// Returns strict JSON: { type, amount, category, date, paymentMethod, note }

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
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) {
      return new Response(
        JSON.stringify({ error: "GEMINI_API_KEY not configured" }),
        { status: 501, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { transcript, categories, referenceDate } = await req.json();
    if (!transcript || typeof transcript !== "string") {
      return new Response(
        JSON.stringify({ error: "Missing transcript" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const categoryList = Array.isArray(categories) ? categories : [];
    const today = typeof referenceDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(referenceDate)
      ? referenceDate
      : new Date().toISOString().split("T")[0];

    const prompt = `Extract a financial transaction from the spoken transcript below (English or Tamil-transliterated English, INR currency).
Reference today's date: ${today}.
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
        JSON.stringify({ error: "Gemini API call failed" }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const geminiData = await geminiRes.json();
    const text = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text;
    const raw = JSON.parse(text);

    const validated = {
      type: ALLOWED_TYPES.has(raw?.type) ? raw.type : null,
      amount: typeof raw?.amount === "number" && raw.amount > 0 ? raw.amount : null,
      category:
        typeof raw?.category === "string" && categoryList.includes(raw.category)
          ? raw.category
          : null,
      date: typeof raw?.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(raw.date) ? raw.date : today,
      paymentMethod: ALLOWED_PAYMENT_METHODS.has(raw?.paymentMethod) ? raw.paymentMethod : "upi",
      note: typeof raw?.note === "string" ? raw.note.trim().slice(0, 80) : "",
    };

    return new Response(JSON.stringify({ parsed: validated }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(
      JSON.stringify({ error: err instanceof Error ? err.message : "Parse error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
