#!/usr/bin/env node
/**
 * v5 存档样本脱敏器 —— 对应 `docs/BASELINE_COLLECTION.md` §D.3。
 *
 * 从应用「设置 → 备份与同步 → 导出全部数据」拿到 JSON 后，**先过一遍本脚本再提交**。
 *
 * 用法：
 *   node fixtures/tools/redact-archive.mjs <输入.json> [输出.json]
 *   # 省略输出时，原地覆盖（会先备份为 <输入>.bak）
 *
 * 规则（§D.3）：
 *   - **只替换非空值**。空字符串保持为空 —— 「字段存在但值为空」是 T06 必须测到的兼容路径，
 *     把 '' 填成占位符反而会破坏它。
 *   - 字段名照旧保留，只换值。
 *   - 覆盖：`apiKey`、`password`、`authToken`、`accessToken`（含嵌套与数组内元素）。
 *   - 结束后**再扫一遍**，还有残留就退出码 1。
 */
import { readFileSync, writeFileSync, copyFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * 要点：脱敏要分两类，别只想着「密钥」。
 *
 * - `SECRET_KEYS`：**凭据**。泄露 = 别人能冒用你的账号。
 * - `IDENTIFIER_KEYS`：**身份**。不是凭据，但同样是个人标识，进仓库就洗不掉了。
 *   ⚠️ 2026-10-07 补：`username` 曾漏在外面，于是真实导出里的**邮箱地址**（WebDAV 用户名）
 *   安然通过了残留扫描 —— 扫描器只找 `sk-` / `password` / `apiKey`，压根没往邮箱上看。
 *   教训与 `plotSummary` 那次同一类：**「已核对过」必须逐字段成立，不能按字段名的直觉归类。**
 */
const SECRET_KEYS = new Set(['apiKey', 'password', 'authToken', 'accessToken', 'secret', 'token']);
const IDENTIFIER_KEYS = new Set(['username']);
const PLACEHOLDER = 'REDACTED_TEST_KEY';
const IDENTIFIER_PLACEHOLDER = 'REDACTED_TEST_USER';

const [inputArg, outputArg] = process.argv.slice(2);
if (!inputArg) {
  console.error('用法：node fixtures/tools/redact-archive.mjs <输入.json> [输出.json]');
  process.exit(1);
}

const input = resolve(process.cwd(), inputArg);
const output = outputArg ? resolve(process.cwd(), outputArg) : input;

let parsed;
try {
  parsed = JSON.parse(readFileSync(input, 'utf8'));
} catch (error) {
  console.error(`❌ 无法解析 ${input}：`, error.message);
  process.exit(1);
}

let redactedCount = 0;
let identifierCount = 0;

/** 递归脱敏；返回新的值（不原地改原对象，便于对比）。 */
function redact(node) {
  if (Array.isArray(node)) return node.map(redact);
  if (node && typeof node === 'object') {
    const out = {};
    for (const [key, value] of Object.entries(node)) {
      if (SECRET_KEYS.has(key) && typeof value === 'string' && value.trim() !== '') {
        out[key] = PLACEHOLDER;
        redactedCount++;
      } else if (IDENTIFIER_KEYS.has(key) && typeof value === 'string' && value.trim() !== '') {
        out[key] = IDENTIFIER_PLACEHOLDER;
        identifierCount++;
      } else {
        out[key] = redact(value);
      }
    }
    return out;
  }
  return node;
}

const cleaned = redact(parsed);
const serialized = JSON.stringify(cleaned, null, 2);

// ---- 残留扫描（§D.3 的检查命令等价物）----
const residuals = [];
const patterns = [
  [/sk-[A-Za-z0-9_-]{8,}/g, 'OpenAI 风格密钥 (sk-…)'],
  [/sk-ant-[A-Za-z0-9_-]{8,}/g, 'Anthropic 密钥 (sk-ant-…)'],
  [/AIza[0-9A-Za-z_-]{10,}/g, 'Google API Key (AIza…)'],
  [/Bearer\s+[A-Za-z0-9._-]{8,}/g, 'Bearer 令牌'],
  [/"password"\s*:\s*"(?!REDACTED_TEST_KEY)[^"]{6,}"/g, '非占位符 password'],
  [/"apiKey"\s*:\s*"(?!REDACTED_TEST_KEY)[^"]{8,}"/g, '非占位符 apiKey'],
  // 身份类：新增于 2026-10-07，起因是真实导出里的邮箱一路通过了旧扫描器
  [/"username"\s*:\s*"(?!REDACTED_TEST_USER)[^"]{4,}"/g, '非占位符 username'],
  [/[\w.+-]+@[\w-]+\.[A-Za-z]{2,}/g, '邮箱地址'],
];
for (const [re, label] of patterns) {
  const hits = serialized.match(re);
  if (hits) residuals.push(`${label} × ${hits.length}`);
}

if (residuals.length > 0) {
  console.error('❌ 仍有疑似密钥残留，未写入：');
  for (const r of residuals) console.error(`   - ${r}`);
  console.error('   若属误报（如测试用的假 Key），请手动确认后改用占位符。');
  process.exit(1);
}

if (output === input) {
  copyFileSync(input, `${input}.bak`);
  writeFileSync(input, serialized, 'utf8');
  console.log(`✅ 已原地脱敏（原文件备份为 ${input}.bak）`);
} else {
  writeFileSync(output, serialized, 'utf8');
  console.log(`✅ 已写入 ${output}`);
}

console.log(`   替换字段值 ${redactedCount} 处、身份字段 ${identifierCount} 处；残留扫描通过。`);
console.log(`   version=${String(cleaned.version)}  sessions=${Array.isArray(cleaned.sessions) ? cleaned.sessions.length : 'n/a'}`);
console.log('   下一步（第 2 步是人工，别跳）：');
console.log('     1) node fixtures/tools/scrub-archive-messages.mjs <in> <out>');
console.log('        （对话派生文本：消息正文 + summaries[].content + timelines[].plotSummary，已自动化）');
console.log('     2) 人工过一眼「个人端点」—— webdavConfig.url / mcpServers[].url 不在任何自动脱敏范围内');
