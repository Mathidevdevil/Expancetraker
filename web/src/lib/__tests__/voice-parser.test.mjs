import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import vm from 'node:vm';

const require = createRequire(import.meta.url);
const ts = require(path.resolve('web/node_modules/typescript'));

const sourcePath = path.resolve('web/src/lib/voice-parser.ts');
const sourceCode = fs.readFileSync(sourcePath, 'utf8');
const transpiled = ts.transpileModule(sourceCode, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020,
    esModuleInterop: true,
  },
}).outputText;

const moduleObj = { exports: {} };
const sandbox = {
  module: moduleObj,
  exports: moduleObj.exports,
  require,
  process,
  console,
  fetch: globalThis.fetch,
  AbortController,
  setTimeout,
  clearTimeout,
};
vm.runInNewContext(transpiled, sandbox, { filename: 'voice-parser.js' });

const {
  parseVoiceTranscriptOffline,
  parseVoiceTranscriptWithSmartFallback,
  formatInrAmount,
  mapWebSpeechError,
} = moduleObj.exports;

const CATEGORIES = [
  { id: 'c_food', user_id: 'u1', name: 'Food & Dining', type: 'expense', icon: 'Utensils', color: '#FF6B6B', is_default: true },
  { id: 'c_trans', user_id: 'u1', name: 'Transportation', type: 'expense', icon: 'Car', color: '#4ECDC4', is_default: true },
  { id: 'c_bills', user_id: 'u1', name: 'Bills & Utilities', type: 'expense', icon: 'Receipt', color: '#45B7D1', is_default: true },
  { id: 'c_groc', user_id: 'u1', name: 'Groceries', type: 'expense', icon: 'ShoppingCart', color: '#96CEB4', is_default: true },
  { id: 'c_shop', user_id: 'u1', name: 'Shopping', type: 'expense', icon: 'ShoppingBag', color: '#D4AF37', is_default: true },
  { id: 'c_ent', user_id: 'u1', name: 'Entertainment', type: 'expense', icon: 'Film', color: '#9B59B6', is_default: true },
  { id: 'c_health', user_id: 'u1', name: 'Healthcare', type: 'expense', icon: 'HeartPulse', color: '#E74C3C', is_default: true },
  { id: 'c_travel', user_id: 'u1', name: 'Travel', type: 'expense', icon: 'Plane', color: '#3498DB', is_default: true },
  { id: 'c_edu', user_id: 'u1', name: 'Education', type: 'expense', icon: 'GraduationCap', color: '#F39C12', is_default: true },
  { id: 'c_sal', user_id: 'u1', name: 'Salary', type: 'income', icon: 'Wallet', color: '#2ECC71', is_default: true },
  { id: 'c_free', user_id: 'u1', name: 'Freelance', type: 'income', icon: 'Laptop', color: '#1ABC9C', is_default: true },
  { id: 'c_inv', user_id: 'u1', name: 'Investment', type: 'income', icon: 'TrendingUp', color: '#34495E', is_default: true },
  { id: 'c_gift', user_id: 'u1', name: 'Gift / Bonus', type: 'income', icon: 'Gift', color: '#E67E22', is_default: true },
];

// Reference date: 2026-11-26
const REF_DATE = new Date(2026, 10, 26, 12, 0, 0);

const CASES = [
  ['spent 486 on swiggy dinner yesterday by UPI', 'expense', 486, 'Food & Dining', '2026-11-25', 'upi'],
  ['got 12000 freelance payment', 'income', 12000, 'Freelance', '2026-11-26', 'upi'],
  ['paid 1840 electricity bill', 'expense', 1840, 'Bills & Utilities', '2026-11-26', 'upi'],
  ['metro recharge 300', 'expense', 300, 'Transportation', '2026-11-26', 'upi'],
  ['salary credited 65000', 'income', 65000, 'Salary', '2026-11-26', 'upi'],
  ['bought groceries 1,250 by card', 'expense', 1250, 'Groceries', '2026-11-26', 'card'],
  ['spent twelve hundred on lunch in cash', 'expense', 1200, 'Food & Dining', '2026-11-26', 'cash'],
  ['paid 1.5k for uber cab yesterday', 'expense', 1500, 'Transportation', '2026-11-25', 'upi'],
  ['bought amazon headphones 2499 via net banking', 'expense', 2499, 'Shopping', '2026-11-26', 'net_banking'],
  ['movie tickets 600 on 12th', 'expense', 600, 'Entertainment', '2026-11-12', 'upi'],
  ['apollo pharmacy medicines 780 today', 'expense', 780, 'Healthcare', '2026-11-26', 'upi'],
  ['received 5000 bonus', 'income', 5000, 'Gift / Bonus', '2026-11-26', 'upi'],
  ['earned 3500 dividend from mutual fund', 'income', 3500, 'Investment', '2026-11-26', 'upi'],
  ['paid 420 for petrol day before yesterday', 'expense', 420, 'Transportation', '2026-11-24', 'upi'],
  ['indigo flight ticket 4800 by credit card', 'expense', 4800, 'Travel', '2026-11-26', 'card'],
  ['ordered zomato biryani 360', 'expense', 360, 'Food & Dining', '2026-11-26', 'upi'],
  ['bought udemy course 499', 'expense', 499, 'Education', '2026-11-26', 'upi'],
  ['paid wifi broadband bill 999', 'expense', 999, 'Bills & Utilities', '2026-11-26', 'upi'],
  ['nethu swiggy sapadu 350 kuduthen', 'expense', 350, 'Food & Dining', '2026-11-25', 'upi'],
  ['iniku maligai 1200 vaanginen cash', 'expense', 1200, 'Groceries', '2026-11-26', 'cash'],
  ['sambalam 55000 vanthuchu bank transfer', 'income', 55000, 'Salary', '2026-11-26', 'bank_transfer'],
  ['petrol 500 potten gpay', 'expense', 500, 'Transportation', '2026-11-26', 'upi'],
  ['nethu current bill 1450 kattinen', 'expense', 1450, 'Bills & Utilities', '2026-11-25', 'upi'],
  ['oru aayiram rooba doctor selavu', 'expense', 1000, 'Healthcare', '2026-11-26', 'upi'],
  ['client project 8000 kidaichuthu', 'income', 8000, 'Freelance', '2026-11-26', 'upi'],
  ['anjunooru coffee snacks by phonepe', 'expense', 500, 'Food & Dining', '2026-11-26', 'upi'],
];

console.log('=== Web Voice Parser 26 Sample Sentences ===');
for (let i = 0; i < CASES.length; i++) {
  const [sentence, expectedType, expectedAmount, expectedCat, expectedDate, expectedPay] = CASES[i];
  const res = parseVoiceTranscriptOffline(sentence, CATEGORIES, REF_DATE);
  console.log(
    `[${String(i + 1).padStart(2, '0')}] "${sentence}" => ${JSON.stringify({
      type: res.type,
      amount: res.amount,
      category: res.categoryName,
      date: res.date,
      paymentMethod: res.paymentMethod,
    })}`
  );
  assert.equal(res.type, expectedType, `Type mismatch for: ${sentence}`);
  assert.equal(res.amount, expectedAmount, `Amount mismatch for: ${sentence}`);
  assert.equal(res.categoryName, expectedCat, `Category mismatch for: ${sentence}`);
  assert.equal(res.date, expectedDate, `Date mismatch for: ${sentence}`);
  assert.equal(res.paymentMethod, expectedPay, `Payment mismatch for: ${sentence}`);
}

// Missing amount / type should return null rather than guessing
const noAmount = parseVoiceTranscriptOffline('paid for swiggy dinner yesterday', CATEGORIES, REF_DATE);
assert.equal(noAmount.amount, null, 'Missing amount must remain null');
assert.equal(noAmount.type, 'expense');

const noType = parseVoiceTranscriptOffline('500 something miscellaneous', CATEGORIES, REF_DATE);
assert.equal(noType.amount, 500);
assert.equal(noType.type, null, 'Ambiguous type must remain null so Confirmation Card prompts user');

// INR grouping format
assert.equal(formatInrAmount(125000), '₹1,25,000');
assert.equal(formatInrAmount(486.5), '₹486.50');

// Speech errors
assert.equal(mapWebSpeechError('not-allowed').kind, 'permission-denied');
assert.equal(mapWebSpeechError('no-speech').kind, 'no-speech');
assert.equal(mapWebSpeechError('network').kind, 'network');

// Smart parser gracefully falls back to offline when Supabase URL is not configured
const smartRes = await parseVoiceTranscriptWithSmartFallback(
  'spent 486 on swiggy dinner yesterday by UPI',
  CATEGORIES,
  REF_DATE
);
assert.equal(smartRes.amount, 486);
assert.equal(smartRes.type, 'expense');

console.log('ALL WEB PARSER TESTS PASSED (26 sentences + missing fields + INR + error mappings).');
