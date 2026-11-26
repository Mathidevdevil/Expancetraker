import type { Category, PaymentMethod, TransactionType } from '@/types/database';

export type VoiceLanguage = 'en-IN' | 'ta-IN';

export type VoiceErrorKind =
  | 'permission-denied'
  | 'no-speech'
  | 'network'
  | 'unsupported'
  | 'unknown';

export interface ParsedVoiceTransaction {
  type: TransactionType | null;
  amount: number | null;
  categoryId: string | null;
  categoryName: string | null;
  date: string; // YYYY-MM-DD
  paymentMethod: PaymentMethod;
  note: string;
  usedSmartParser?: boolean;
}

const EXPENSE_VERBS = new Set([
  'spent', 'spend', 'paid', 'pay', 'bought', 'buy', 'purchased', 'purchase',
  'gave', 'give', 'charged', 'ordered', 'order', 'booked', 'book',
  'recharged', 'recharge', 'bill', 'billed', 'expense',
  'selavu', 'kuduthen', 'koduthen', 'vaanginen', 'vanginen', 'potten',
  'katnen', 'kattinen', 'anupinen',
]);

const INCOME_VERBS = new Set([
  'got', 'received', 'receive', 'earned', 'earn', 'salary', 'credited',
  'credit', 'deposited', 'deposit', 'income', 'bonus', 'refund', 'refunded',
  'dividend', 'interest', 'cashback',
  'vanthuchu', 'vandhuchu', 'vandhadhu', 'kidaichuthu', 'kedaichadhu',
  'sambalam', 'varavu',
]);

const CATEGORY_KEYWORDS: Record<string, string[]> = {
  'Food & Dining': [
    'swiggy', 'zomato', 'dinner', 'lunch', 'breakfast', 'coffee', 'tea', 'chai',
    'snacks', 'snack', 'biryani', 'dosa', 'idli', 'tiffin', 'restaurant',
    'cafe', 'bakery', 'pizza', 'burger', 'food', 'dining', 'meal', 'eat',
    'sapadu', 'saapadu', 'hotel',
  ],
  'Transportation': [
    'metro', 'uber', 'ola', 'rapido', 'auto', 'rickshaw', 'bus', 'train',
    'petrol', 'diesel', 'fuel', 'cab', 'taxi', 'parking', 'toll', 'commute',
    'fastag', 'bmtc', 'cmrl',
  ],
  'Bills & Utilities': [
    'electricity', 'eb', 'power bill', 'current bill', 'recharge', 'mobile bill',
    'jio', 'airtel', 'vodafone', 'vi', 'bsnl', 'wifi', 'internet', 'broadband',
    'fiber', 'gas', 'cylinder', 'water bill', 'dth', 'rent', 'maintenance', 'bill',
  ],
  'Groceries': [
    'grocery', 'groceries', 'milk', 'vegetables', 'veggies', 'fruits',
    'supermarket', 'bigbasket', 'blinkit', 'zepto', 'instamart', 'dmart',
    'reliance smart', 'kirana', 'maligai', 'rice', 'curd',
  ],
  'Shopping': [
    'amazon', 'flipkart', 'myntra', 'ajio', 'meesho', 'clothes', 'dress',
    'shirt', 'jeans', 'shoes', 'shopping', 'mall', 'watch', 'bag', 'electronics',
  ],
  'Entertainment': [
    'movie', 'cinema', 'bookmyshow', 'pvr', 'inox', 'netflix', 'prime',
    'hotstar', 'spotify', 'concert', 'game', 'gaming', 'outing',
  ],
  'Healthcare': [
    'hospital', 'pharmacy', 'medical', 'medicine', 'medicines', 'tablets',
    'doctor', 'apollo', 'medplus', 'clinic', 'checkup', 'scan', 'dental',
  ],
  'Travel': [
    'flight', 'indigo', 'air india', 'oyo', 'resort', 'vacation', 'trip',
    'lodge', 'tour', 'makemytrip', 'irctc', 'stay',
  ],
  'Education': [
    'tuition', 'books', 'book', 'course', 'udemy', 'coursera', 'exam',
    'fees', 'college', 'school', 'stationery', 'notebook',
  ],
  'Salary': ['salary', 'paycheck', 'wages', 'sambalam', 'stipend'],
  'Freelance': ['freelance', 'client', 'consulting', 'project', 'invoice', 'gig'],
  'Investment': ['dividend', 'interest', 'mutual fund', 'sip', 'stock', 'shares', 'returns'],
  'Gift / Bonus': ['gift', 'bonus', 'cashback', 'prize', 'reward', 'diwali', 'pongal'],
};

const NUMBER_UNITS: Record<string, number> = {
  zero: 0, one: 1, a: 1, two: 2, three: 3, four: 4,
  five: 5, six: 6, seven: 7, eight: 8, nine: 9,
  ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14,
  fifteen: 15, sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19,
  onnu: 1, oru: 1, rendu: 2, moonu: 3, naalu: 4, anju: 5,
  aaru: 6, ezhu: 7, ettu: 8, ombathu: 9, pathu: 10,
  irunooru: 200, munnooru: 300, naanooru: 400, anjunooru: 500, arunooru: 600,
};

const NUMBER_TENS: Record<string, number> = {
  twenty: 20, thirty: 30, forty: 40, fifty: 50,
  sixty: 60, seventy: 70, eighty: 80, ninety: 90,
};

const NUMBER_MULTIPLIERS: Record<string, number> = {
  hundred: 100,
  nooru: 100,
  thousand: 1000,
  aayiram: 1000,
  lakh: 100000,
  lakhs: 100000,
  lac: 100000,
  lacs: 100000,
  latcham: 100000,
};

const MONTHS_MAP: Record<string, number> = {
  jan: 0, january: 0, feb: 1, february: 1, mar: 2, march: 2,
  apr: 3, april: 3, may: 4, jun: 5, june: 5, jul: 6, july: 6,
  aug: 7, august: 7, sep: 8, sept: 8, september: 8, oct: 9, october: 9,
  nov: 10, november: 10, dec: 11, december: 11,
};

const ALLOWED_PAYMENT_METHODS = new Set<PaymentMethod>([
  'cash', 'upi', 'card', 'bank_transfer', 'net_banking',
]);

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function formatLocalIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function formatInrAmount(amount: number | null): string {
  if (amount === null || !Number.isFinite(amount)) return '';
  const hasFraction = Math.abs(amount % 1) > 0.001;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: hasFraction ? 2 : 0,
    maximumFractionDigits: 2,
  }).format(amount);
}

function extractDateAndStrip(lower: string, refDate: Date): { date: string; cleaned: string } {
  const cal = new Date(refDate.getTime());
  let cleaned = lower;

  const daysAgoMatch = lower.match(/\b(\d+)\s+days\s+ago\b/);
  if (lower.includes('day before yesterday') || lower.includes('munthanethu')) {
    cal.setDate(cal.getDate() - 2);
    cleaned = cleaned.replace('day before yesterday', ' ').replace('munthanethu', ' ');
  } else if (daysAgoMatch) {
    const n = parseInt(daysAgoMatch[1], 10) || 1;
    cal.setDate(cal.getDate() - n);
    cleaned = cleaned.replace(daysAgoMatch[0], ' ');
  } else if (/\b(yesterday|nethu|netru)\b/.test(lower)) {
    cal.setDate(cal.getDate() - 1);
    cleaned = cleaned.replace(/\b(yesterday|nethu|netru)\b/g, ' ');
  } else if (/\b(today|iniku|innaiku)\b/.test(lower)) {
    cleaned = cleaned.replace(/\b(today|iniku|innaiku)\b/g, ' ');
  } else {
    const dmMatch = lower.match(
      /\b(?:on\s+(?:the\s+)?)?(\d{1,2})(?:st|nd|rd|th)?\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\b/
    );
    if (dmMatch) {
      const day = parseInt(dmMatch[1], 10);
      const monthIdx = MONTHS_MAP[dmMatch[2]];
      if (day >= 1 && day <= 31 && monthIdx !== undefined) {
        cal.setMonth(monthIdx, 1);
        const maxDay = new Date(cal.getFullYear(), monthIdx + 1, 0).getDate();
        cal.setDate(Math.min(day, maxDay));
        cleaned = cleaned.replace(dmMatch[0], ' ');
      }
    } else {
      const ordMatch = lower.match(/\b(?:on\s+(?:the\s+)?)?(\d{1,2})(?:st|nd|rd|th)\b/);
      if (ordMatch) {
        const day = parseInt(ordMatch[1], 10);
        if (day >= 1 && day <= 31) {
          if (day > cal.getDate()) {
            cal.setMonth(cal.getMonth() - 1);
          }
          const maxDay = new Date(cal.getFullYear(), cal.getMonth() + 1, 0).getDate();
          cal.setDate(Math.min(day, maxDay));
          cleaned = cleaned.replace(ordMatch[0], ' ');
        }
      }
    }
  }

  return { date: formatLocalIsoDate(cal), cleaned };
}

function extractAmount(textWithoutDate: string): number | null {
  const regex = /(?:₹|rs\.?|inr)?\s*(\d[\d,]*(?:\.\d+)?)\s*(k|thousand|aayiram|lakh|lakhs|lac|lacs|latcham)?\b/g;
  for (const match of textWithoutDate.matchAll(regex)) {
    const rawNum = parseFloat(match[1].replace(/,/g, ''));
    if (!Number.isFinite(rawNum) || rawNum <= 0) continue;
    const suffix = (match[2] || '').toLowerCase();
    let mult = 1;
    if (suffix === 'k' || suffix === 'thousand' || suffix === 'aayiram') mult = 1000;
    else if (['lakh', 'lakhs', 'lac', 'lacs', 'latcham'].includes(suffix)) mult = 100000;
    return rawNum * mult;
  }

  const normalized = textWithoutDate
    .replace(/one and a half thousand/g, '1500')
    .replace(/and a half thousand/g, '500')
    .replace(/-/g, ' ');

  const digitFallback = normalized.match(/\b(\d+(?:\.\d+)?)\b/);
  if (digitFallback) {
    return parseFloat(digitFallback[1]);
  }

  const tokens = normalized
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(Boolean);

  let total = 0;
  let current = 0;
  let found = false;

  for (const token of tokens) {
    if (token === 'and' && found) continue;
    if (token in NUMBER_UNITS) {
      current += NUMBER_UNITS[token];
      found = true;
    } else if (token in NUMBER_TENS) {
      current += NUMBER_TENS[token];
      found = true;
    } else if (token in NUMBER_MULTIPLIERS) {
      const mult = NUMBER_MULTIPLIERS[token];
      const base = current === 0 ? 1 : current;
      if (mult >= 1000) {
        total += base * mult;
        current = 0;
      } else {
        current = base * mult;
      }
      found = true;
    } else if (found) {
      break;
    }
  }

  const combined = total + current;
  return found && combined > 0 ? combined : null;
}

function detectExplicitType(lower: string): TransactionType | null {
  const sanitized = lower.replace(/\b(?:credit|debit)\s+card\b/g, ' card ');
  const words = sanitized
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(Boolean);

  const hasIncome = words.some((w) => INCOME_VERBS.has(w)) || sanitized.includes('money received');
  const hasExpense = words.some((w) => EXPENSE_VERBS.has(w));

  if (hasIncome && !hasExpense) return 'income';
  if (hasExpense && !hasIncome) return 'expense';
  if (hasIncome && hasExpense) {
    if (words.some((w) => w === 'salary' || w === 'credited' || w === 'sambalam' || w === 'received')) {
      return 'income';
    }
    return 'expense';
  }
  return null;
}

function extractPaymentMethod(lower: string): PaymentMethod {
  if (/\b(net\s*banking|netbanking|neft|imps|rtgs)\b/.test(lower)) return 'net_banking';
  if (/\b(bank\s*transfer)\b/.test(lower)) return 'bank_transfer';
  if (/\b(credit\s*card|debit\s*card|card|visa|mastercard|rupay|atm)\b/.test(lower)) return 'card';
  if (/\b(cash|kaasu)\b/.test(lower)) return 'cash';
  if (/\b(upi|gpay|g\s*pay|google\s*pay|phonepe|phone\s*pe|paytm|bhim)\b/.test(lower)) return 'upi';
  return 'upi';
}

function matchCategory(
  lower: string,
  preferredType: TransactionType | null,
  categories: Category[]
): Category | null {
  const scoped = preferredType ? categories.filter((c) => c.type === preferredType) : categories;
  const candidates = scoped.length > 0 ? scoped : categories;

  const customFirst = [...candidates].sort((a, b) => Number(a.is_default) - Number(b.is_default));
  for (const cat of customFirst) {
    const catLower = cat.name.trim().toLowerCase();
    if (catLower && new RegExp(`\\b${escapeRegExp(catLower)}\\b`).test(lower)) {
      return cat;
    }
  }

  let bestCat: Category | null = null;
  let bestScore = 0;

  for (const cat of candidates) {
    const kws = CATEGORY_KEYWORDS[cat.name];
    if (!kws) continue;
    let score = 0;
    for (const kw of kws) {
      if (new RegExp(`\\b${escapeRegExp(kw)}\\b`).test(lower)) {
        score += kw.includes(' ') ? 3 : 2;
      }
    }
    if (score > bestScore) {
      bestScore = score;
      bestCat = cat;
    }
  }

  if (bestCat) return bestCat;

  if (preferredType === 'expense') {
    return candidates.find((c) => c.id === 'cat-other-exp' || c.name.toLowerCase() === 'other expense') || null;
  }
  if (preferredType === 'income') {
    return candidates.find((c) => c.id === 'cat-other-inc' || c.name.toLowerCase() === 'other income') || null;
  }
  return null;
}

function buildNote(raw: string): string {
  let note = raw
    .trim()
    .replace(/\b(i|spent|paid|bought|gave|got|received|earned|credited|yesterday|today|day before yesterday|nethu|iniku|selavu|kuduthen|vaanginen|potten|vanthuchu|kidaichuthu|rooba|rupees|inr|rs\.?|by|via|using|through|with|on|for|at|from|to|the|a|an|la|ku)\b/gi, ' ')
    .replace(/(?:₹|\b\d[\d,]*(?:\.\d+)?\s*(?:k|thousand|lakh|lakhs|lac)?\b)/gi, ' ')
    .replace(/\b(upi|gpay|google pay|phonepe|paytm|cash|card|credit card|debit card|net banking|netbanking|neft|imps|bank transfer)\b/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  if (!note) note = raw.trim();
  return (note.charAt(0).toUpperCase() + note.slice(1)).slice(0, 80);
}

/**
 * Step 1: Offline on-device rule parser.
 */
export function parseVoiceTranscriptOffline(
  transcript: string,
  categories: Category[],
  referenceDate: Date = new Date()
): ParsedVoiceTransaction {
  const raw = transcript.trim();
  const lower = raw.toLowerCase();

  const { date, cleaned } = extractDateAndStrip(lower, referenceDate);
  const amount = extractAmount(cleaned);
  const paymentMethod = extractPaymentMethod(lower);
  const explicitType = detectExplicitType(lower);
  const matchedCat = matchCategory(lower, explicitType, categories);

  const finalType: TransactionType | null = explicitType ?? matchedCat?.type ?? null;
  const alignedCat =
    matchedCat && finalType && matchedCat.type !== finalType
      ? matchCategory(lower, finalType, categories)
      : matchedCat;

  return {
    type: finalType,
    amount,
    categoryId: alignedCat?.id ?? null,
    categoryName: alignedCat?.name ?? null,
    date,
    paymentMethod,
    note: buildNote(raw),
    usedSmartParser: false,
  };
}

/**
 * Step 2: Optional Gemini smart parser via Supabase Edge Function (strict 3-second timeout).
 */
export async function parseVoiceTranscriptWithSmartFallback(
  transcript: string,
  categories: Category[],
  referenceDate: Date = new Date()
): Promise<ParsedVoiceTransaction> {
  const offline = parseVoiceTranscriptOffline(transcript, categories, referenceDate);
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

  if (!supabaseUrl || !supabaseKey || supabaseUrl.includes('placeholder') || !transcript.trim()) {
    return offline;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 3000);

  try {
    const res = await fetch(`${supabaseUrl.replace(/\/$/, '')}/functions/v1/parse-voice-transaction`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: supabaseKey,
        Authorization: `Bearer ${supabaseKey}`,
      },
      body: JSON.stringify({
        transcript,
        categories: categories.map((c) => c.name),
        referenceDate: offline.date,
      }),
      signal: controller.signal,
    });

    if (!res.ok) return offline;
    const json = await res.json();
    const payload = json?.parsed || json;
    if (!payload || typeof payload !== 'object') return offline;

    const smartType: TransactionType | null =
      payload.type === 'expense' || payload.type === 'income' ? payload.type : offline.type;
    const smartAmount =
      typeof payload.amount === 'number' && payload.amount > 0 ? payload.amount : offline.amount;

    const catName = typeof payload.category === 'string' ? payload.category.trim() : '';
    const smartCat = catName
      ? categories.find(
          (c) => c.name.toLowerCase() === catName.toLowerCase() && (!smartType || c.type === smartType)
        ) || categories.find((c) => c.name.toLowerCase() === catName.toLowerCase())
      : null;

    const smartDate =
      typeof payload.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(payload.date)
        ? payload.date
        : offline.date;

    const smartPayment: PaymentMethod = ALLOWED_PAYMENT_METHODS.has(payload.paymentMethod)
      ? payload.paymentMethod
      : offline.paymentMethod;

    const smartNote =
      typeof payload.note === 'string' && payload.note.trim() ? payload.note.trim() : offline.note;

    return {
      type: smartType,
      amount: smartAmount,
      categoryId: smartCat?.id ?? offline.categoryId,
      categoryName: smartCat?.name ?? offline.categoryName,
      date: smartDate,
      paymentMethod: smartPayment,
      note: smartNote,
      usedSmartParser: true,
    };
  } catch {
    return offline;
  } finally {
    clearTimeout(timer);
  }
}

export function mapWebSpeechError(code: string): { kind: VoiceErrorKind; message: string } {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
      return {
        kind: 'permission-denied',
        message: 'Microphone permission was denied. Allow microphone access or enter details manually.',
      };
    case 'no-speech':
      return {
        kind: 'no-speech',
        message: 'No speech detected. Try speaking again or enter the transaction manually.',
      };
    case 'network':
      return {
        kind: 'network',
        message: 'Speech recognition requires an internet connection in this browser.',
      };
    default:
      return {
        kind: 'unknown',
        message: 'Could not capture voice input. Please try again or enter manually.',
      };
  }
}
