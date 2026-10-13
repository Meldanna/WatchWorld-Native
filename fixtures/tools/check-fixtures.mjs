#!/usr/bin/env node
/**
 * fixtures 自校验器 —— 把 `docs/BASELINE_COLLECTION.md` §A.3 / §D.5 的判据变成可执行检查。
 *
 * 用法：
 *   node fixtures/tools/check-fixtures.mjs              # 检查 fixtures/sse 下全部
 *   node fixtures/tools/check-fixtures.mjs a.txt b.json # 只查指定文件
 *
 * 退出码：0 = 全过；1 = 有 FAIL（可接进 CI 或采集脚本）。
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, basename, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const sseDir = resolve(here, '../sse');

/**
 * 判定 Provider：**内容嗅探优先，文件名兜底**。
 *
 * 早期版本只看文件名前缀，结果换个命名（如 `openrouter-stream.txt`）就误报 FAIL。
 * 这里以报文内容为准（三家的事件形状在 `MIGRATION_CONSTRAINTS.md` §3.4 里互不重叠），
 * 文件名只用来消歧（比如 OpenAI 兼容与 Claude 都可能出现 "delta"）。
 */
const OPENAI_COMPATIBLE_NAMES = ['openai', 'deepseek', 'openrouter', 'groq', 'moonshot', 'qwen', 'ollama', 'siliconflow', 'relay', 'zhongzhuan'];

/** 只看文件名能推断出哪家（推不出返回 null）。用于与内容比对。 */
function nameProvider(name) {
  const lower = name.toLowerCase();
  if (lower.includes('claude') || lower.includes('anthropic')) return 'claude';
  if (lower.includes('gemini') || lower.includes('google')) return 'gemini';
  if (OPENAI_COMPATIBLE_NAMES.some((n) => lower.includes(n))) return 'openai';
  return null;
}

function detectProvider(name, text) {
  // 内容优先：三家的事件形状互不重叠（MIGRATION_CONSTRAINTS §3.4）
  if (text.includes('content_block_delta') || text.includes('content_block_start')) return 'claude';
  if (text.includes('"candidates"')) return 'gemini';
  if (text.includes('"choices"')) return 'openai';
  return nameProvider(name) ?? 'unknown';
}

/** 从原始 SSE 文本里抽出所有「文本增量」，用于验证不同行确实是增量。 */
function extractTextDeltas(provider, lines) {
  const deltas = [];
  for (const line of lines) {
    if (!line.startsWith('data:')) continue;
    const payload = line.slice(5).replace(/^ /, '').trim();
    if (!payload || payload === '[DONE]') continue;
    let obj;
    try {
      obj = JSON.parse(payload);
    } catch {
      continue;
    }
    if (provider === 'openai') {
      const delta = obj?.choices?.[0]?.delta;
      if (typeof delta?.content === 'string') deltas.push(delta.content);
      if (typeof delta?.reasoning_content === 'string') deltas.push(delta.reasoning_content);
      for (const toolCall of delta?.tool_calls ?? []) {
        if (typeof toolCall?.function?.name === 'string') deltas.push(toolCall.function.name);
        if (typeof toolCall?.function?.arguments === 'string') deltas.push(toolCall.function.arguments);
      }
    } else if (provider === 'claude') {
      const delta = obj?.delta;
      if (obj?.type === 'content_block_delta') {
        if (typeof delta?.text === 'string') deltas.push(delta.text);
        if (typeof delta?.thinking === 'string') deltas.push(delta.thinking);
      }
    } else if (provider === 'gemini') {
      const parts = obj?.candidates?.[0]?.content?.parts ?? [];
      for (const part of parts) {
        if (typeof part?.text === 'string') deltas.push(part.text);
      }
    }
  }
  return deltas;
}

function checkSseFile(path) {
  const name = basename(path);
  const checks = [];
  const text = readFileSync(path, 'utf8');
  const lines = text.split(/\r?\n/);
  const dataLines = lines.filter((l) => l.startsWith('data:'));
  const provider = detectProvider(name, text);

  if (provider === 'unknown') {
    return {
      name,
      checks: [['识别 Provider', false, '内容与文件名都没认出是哪家（期望 choices / content_block_delta / candidates）']],
    };
  }

  // ★ 防「中转站毒化」：中转站常把 Claude/Gemini 的响应也归一化成 OpenAI 形状。
  //   若文件名声称是 claude/gemini，内容却是 openai 形状，直接判 FAIL ——
  //   否则会拿它写出错误的适配器，而流式 bug 极难排查。
  const hint = nameProvider(name);
  const consistent = !hint || hint === provider;
  checks.push([
    '文件名与内容一致',
    consistent,
    consistent
      ? (hint ? `均为 ${hint}` : '文件名无提示，按内容判定')
      : `文件名像 ${hint}，内容是 ${provider} 形状 —— 多半是中转站归一化了，不能当 ${hint} 的 fixture`,
  ]);

  // A.3-1 多行 data:
  checks.push(['多行 data:', dataLines.length >= 3, `data: 行数 = ${dataLines.length}`]);

  // A.3-2 结束标志
  const lastData = dataLines.at(-1)?.slice(5).replace(/^ /, '').trim() ?? '';
  const hasDone = lastData === '[DONE]';
  const hasMessageStop = text.includes('message_stop');
  const hasFinishReason = text.includes('finish_reason') || text.includes('finishReason');
  const endOk =
    provider === 'openai' ? hasDone || hasFinishReason
    : provider === 'claude' ? hasMessageStop
    : provider === 'gemini' ? hasFinishReason
    : hasDone || hasMessageStop || hasFinishReason;
  checks.push([
    '结束标志',
    endOk,
    provider === 'openai'
      ? `[DONE]=${hasDone} finish_reason=${hasFinishReason}`
      : provider === 'claude'
        ? `message_stop=${hasMessageStop}`
        : `finishReason=${hasFinishReason}`,
  ]);

  // A.3-3 关键字段
  const keyOk =
    provider === 'openai' ? text.includes('"delta"')
    : provider === 'claude' ? text.includes('content_block_delta')
    : provider === 'gemini' ? text.includes('candidates')
    : false;
  checks.push(['关键字段', keyOk, provider]);

  // A.3-4 不同行确实是增量（最重要的一条）
  const deltas = extractTextDeltas(provider, lines);
  const joined = deltas.join('');
  const distinct = new Set(deltas.filter((d) => d.length > 0));
  const incrementsOk = joined.length > 0 && deltas.length >= 3 && distinct.size >= 2 &&
    !deltas.some((d) => d === joined); // 不能"一帧就是全文"
  checks.push([
    '内容是增量',
    incrementsOk,
    `帧数=${deltas.length} 去重=${distinct.size} 拼接长度=${joined.length}`,
  ]);

  if (provider === 'openai' && /tool[-_]?calls?/i.test(name)) {
    const argumentDeltas = [];
    for (const line of dataLines) {
      const payload = line.slice(5).replace(/^ /, '').trim();
      if (!payload || payload === '[DONE]') continue;
      try {
        const obj = JSON.parse(payload);
        for (const toolCall of obj?.choices?.[0]?.delta?.tool_calls ?? []) {
          if (typeof toolCall?.function?.arguments === 'string') {
            argumentDeltas.push(toolCall.function.arguments);
          }
        }
      } catch {
        // Invalid frames are reported by the general SSE checks above.
      }
    }
    checks.push([
      '工具调用参数分片',
      argumentDeltas.length >= 2 && new Set(argumentDeltas).size >= 2,
      `参数帧=${argumentDeltas.length} 去重=${new Set(argumentDeltas).size}`,
    ]);
  }

  return { name, checks };
}

function checkArchive(path, name) {
  const checks = [];
  const raw = readFileSync(path, 'utf8');
  let data;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    return { name, checks: [['可解析 JSON', false, String(e)]] };
  }

  checks.push(['version === 5', data.version === 5, `version=${String(data.version)}`]);
  checks.push(['无明文密钥残留', !/sk-[A-Za-z0-9]|AIza[0-9A-Za-z_-]|Bearer [A-Za-z0-9]/.test(raw), '']);
  const sessions = Array.isArray(data.sessions) ? data.sessions : [];
  checks.push(['有 sessions', sessions.length >= 1, `sessions=${sessions.length}`]);
  const withMessages = sessions.filter((s) => Array.isArray(s?.messages) && s.messages.length > 0).length;
  const withTimelines = sessions.filter((s) => Array.isArray(s?.timelines) && s.timelines.length > 0).length;
  // minimal（新装）本就是 0 条消息；对 *minimal* 不做硬性要求，只报告事实。
  const isMinimal = name.includes('minimal');
  checks.push([
    '有带 timelines 的 session',
    withTimelines >= 1,
    `withTimelines=${withTimelines}`,
  ]);
  checks.push([
    isMinimal ? '有消息的 session（minimal 不要求）' : '有消息的 session',
    isMinimal ? true : withMessages >= 1,
    `withMessages=${withMessages}`,
  ]);
  // 字段必须存在（哪怕是空值）——MIGRATION_CONSTRAINTS §1.1
  const required = ['providers', 'groups', 'agents', 'prompts', 'regexRules', 'knowledgeBase',
    'theme', 'uiMode', 'mcpServers', 'skills', 'displaySettings', 'webdavConfig',
    'worldDocuments', 'worldGroups', 'sessions', 'appPreferences'];
  const missing = required.filter((k) => !(k in data));
  // legacy 样本**故意**是旧形状（缺 worldGroups / appPreferences 等）。
  // 对它来说"缺字段"正是采集目的，只报告不算失败。
  const isLegacy = name.includes('legacy');
  checks.push([
    isLegacy ? '顶层字段（legacy 允许缺，仅报告）' : '顶层字段齐全',
    isLegacy ? true : missing.length === 0,
    missing.length ? `缺:${missing.join(',')}` : '齐全',
  ]);
  return { name, checks };
}

// ---- 收集目标文件 ----
const args = process.argv.slice(2);
let targets;
if (args.length > 0) {
  targets = args.map((a) => resolve(process.cwd(), a));
} else {
  if (!existsSync(sseDir)) {
    console.error(`❌ 找不到目录：${sseDir}`);
    process.exit(1);
  }
  targets = readdirSync(sseDir)
    .filter((f) => f.endsWith('.txt') || f.endsWith('.json'))
    .map((f) => join(sseDir, f));
}

if (targets.length === 0) {
  console.log('ℹ️  fixtures/sse 下还没有采到任何 .txt / .json —— 先跑采集脚本。');
  process.exit(0);
}

// ---- 执行 ----
let failed = 0;
const results = [];
for (const path of targets) {
  if (!existsSync(path)) {
    console.error(`❌ 文件不存在：${path}`);
    failed++;
    continue;
  }
  const name = basename(path);
  const result = name.endsWith('.json') ? checkArchive(path, name) : checkSseFile(path);
  results.push(result);
  const bad = result.checks.filter(([, ok]) => !ok);
  if (bad.length > 0) failed++;
}

console.log('\n=== fixtures 自校验 ===\n');
for (const { name, checks } of results) {
  const bad = checks.filter(([, ok]) => !ok);
  console.log(`${bad.length === 0 ? '✅ PASS' : '❌ FAIL'}  ${name}`);
  for (const [label, ok, detail] of checks) {
    console.log(`     ${ok ? '✓' : '✗'} ${label}${detail ? `  （${detail}）` : ''}`);
  }
}
console.log(
  `\n共 ${results.length} 个文件，${results.length - failed} 通过，${failed} 失败。` +
    (failed > 0 ? '\n处理建议见 docs/BASELINE_COLLECTION.md §A.3 的「踩坑特征」。' : ''),
);
process.exit(failed > 0 ? 1 : 0);
