import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import vm from 'node:vm';

const require = createRequire(import.meta.url);
const ts = require(path.resolve('web/node_modules/typescript'));

function loadTsModule(relPath) {
  const fullPath = path.resolve(relPath);
  const code = fs.readFileSync(fullPath, 'utf8');
  const js = ts.transpileModule(code, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
  }).outputText;
  const mod = { exports: {} };
  vm.runInNewContext(
    js,
    {
      module: mod,
      exports: mod.exports,
      require,
      process,
      console,
      fetch: globalThis.fetch,
      AbortController,
      setTimeout,
      clearTimeout,
    },
    { filename: path.basename(relPath) }
  );
  return mod.exports;
}

const { sanitizeRedirectPath } = loadTsModule('web/src/lib/security.ts');
const { parseVoiceTranscriptWithSmartFallback } = loadTsModule('web/src/lib/voice-parser.ts');

// 1. Open Redirect sanitizer regression tests
assert.equal(sanitizeRedirectPath('/'), '/');
assert.equal(sanitizeRedirectPath('/transactions?filter=all'), '/transactions?filter=all');
assert.equal(sanitizeRedirectPath('//evil.com'), '/');
assert.equal(sanitizeRedirectPath('/\\evil.com'), '/');
assert.equal(sanitizeRedirectPath('https://evil.com/phish'), '/');
assert.equal(sanitizeRedirectPath('javascript:alert(1)'), '/');
assert.equal(sanitizeRedirectPath(''), '/');
assert.equal(sanitizeRedirectPath(null), '/');

// 2. Smart parser skips unauthenticated Edge Function calls when accessToken is absent
const cats = [
  { id: 'cat-food', user_id: null, name: 'Food & Dining', type: 'expense', icon: 'Utensils', color: '#EF4444', is_default: true },
];
const parsed = await parseVoiceTranscriptWithSmartFallback('spent 486 on swiggy dinner', cats, new Date(), null);
assert.equal(parsed.type, 'expense');
assert.equal(parsed.amount, 486);
assert.equal(parsed.usedSmartParser, false);

// 3. Static verification: no NEXT_PUBLIC_ secret leaks & no dangerouslySetInnerHTML
const webSrcFiles = [];
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === '__tests__') continue;
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p);
    else if (/\.(ts|tsx|js|mjs)$/.test(entry.name)) webSrcFiles.push(p);
  }
}
walk(path.resolve('web/src'));
for (const file of webSrcFiles) {
  const txt = fs.readFileSync(file, 'utf8');
  assert.equal(/dangerouslySetInnerHTML/.test(txt), false, `Found dangerouslySetInnerHTML in ${file}`);
  assert.equal(/NEXT_PUBLIC_.*(SECRET|SERVICE_ROLE|GEMINI|PASSWORD)/i.test(txt), false, `Sensitive NEXT_PUBLIC var in ${file}`);
}

console.log('ALL WEB SECURITY REGRESSION TESTS PASSED!');
