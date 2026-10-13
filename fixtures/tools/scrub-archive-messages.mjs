#!/usr/bin/env node
/**
 * 抹掉存档里**从对话派生的文本**，保留全部字段与结构（含时间线树、楼层号、版本数组）。
 *
 * 与 `redact-archive.mjs` 的分工：
 *   - `redact-archive.mjs` 处理**凭据与身份**（apiKey / password / authToken / username）
 *   - 本脚本处理**对话内容**（隐私）
 * 两者可先后串联使用。
 *
 * 抹哪些（默认）：
 *   1. 消息正文（含 `versions[].content` / `reasoning`）
 *   2. `sessions[].summaries[].content` —— 前文总结，是对话的结构化摘要
 *   3. `sessions[].timelines[].plotSummary` —— 自动生成的「用户: … | 回复: …」日志
 *
 * 用法：
 *   node fixtures/tools/scrub-archive-messages.mjs <输入.json> <输出.json> [--include-text]
 *
 * 加 `--include-text` 还会抹掉其它自由文本
 * （会话标题、时间线描述、世界书正文、知识库正文、提示词/智能体指令）。
 *
 * 规则：
 *   - **空字符串不动** —— 「字段存在但值为空」是 T06 必须测的兼容路径。
 *   - 非空文本替换为 `[[REDACTED_MESSAGE_BODY len=<原长度>]]`，**保留长度线索**便于目测。
 *   - 所有结构字段（id / role / floorNumber / timelineId / codeTag / isCommon /
 *     currentVersionIndex / timestamp / tokens / versions 数组长度 / fromFloor / toFloor …）**一律不动**。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const args = process.argv.slice(2).filter((a) => a !== '--include-text');
const includeText = process.argv.includes('--include-text');
const [inputArg, outputArg] = args;

if (!inputArg || !outputArg) {
  console.error(
    '用法：node fixtures/tools/scrub-archive-messages.mjs <输入.json> <输出.json> [--include-text]',
  );
  process.exit(1);
}

const input = resolve(process.cwd(), inputArg);
const output = resolve(process.cwd(), outputArg);

/** 消息里承载正文的字段名（不同版本可能用不同名字）。 */
const MESSAGE_TEXT_KEYS = new Set([
  'content', 'reasoning', 'thinking', 'text', 'summary', 'error', 'toolResult', 'result',
]);

/** --include-text 时才抹的其它自由文本字段（顶层/嵌套均按名字匹配）。 */
const EXTRA_TEXT_KEYS = new Set([
  'title', 'description', 'plotSummary', 'systemPromptFixed', 'systemInstruction',
  'systemInstructionInjection', 'prompt', 'instruction', 'note', 'content',
]);

let data;
try {
  data = JSON.parse(readFileSync(input, 'utf8'));
} catch (error) {
  console.error(`❌ 无法解析 ${input}：`, error.message);
  process.exit(1);
}

const placeholder = (len) => `[[REDACTED_MESSAGE_BODY len=${len}]]`;
let scrubbed = 0;
let keptEmpty = 0;

function scrubText(value) {
  if (typeof value !== 'string') return value;
  if (value === '') {
    keptEmpty++; // 空值保留，测兼容路径
    return value;
  }
  scrubbed++;
  return placeholder(value.length);
}

for (const session of Array.isArray(data.sessions) ? data.sessions : []) {
  for (const message of Array.isArray(session.messages) ? session.messages : []) {
    // ① 消息正文（含 versions[].content）
    for (const key of Object.keys(message)) {
      if (MESSAGE_TEXT_KEYS.has(key)) message[key] = scrubText(message[key]);
    }
    for (const version of Array.isArray(message.versions) ? message.versions : []) {
      for (const key of Object.keys(version)) {
        if (MESSAGE_TEXT_KEYS.has(key)) version[key] = scrubText(version[key]);
      }
    }
  }

  // ② 会话级「派生自对话」的文本 —— 它们不是消息正文，但都是对话内容的转写/摘要。
  //
  //    ⚠️ 2026-10-07 补。起因：两份真实导出都带着**没被抹掉的** `timelines[].plotSummary`
  //    （应用自动生成的「用户: … | 回复: …」日志，含用户原话）；后来 02:10 那份又多了
  //    `summaries[].content` —— **一段 803 字的对话结构化总结**，把剧情线、人物背景、
  //    分支状态全写进去了。两者都比消息正文更浓缩，泄露的信息量只多不少。
  //
  //    教训（与 redact 脚本的 `username` 那次同源）：**别按「字段属于哪个对象」判断要不要抹，
  //    要按「这段文本是不是从对话派生的」判断。** 消息正文只是对话内容的一种存放位置。
  if (!includeText) {
    for (const summary of Array.isArray(session.summaries) ? session.summaries : []) {
      if ('content' in summary) summary.content = scrubText(summary.content);
    }
    for (const timeline of Array.isArray(session.timelines) ? session.timelines : []) {
      if ('plotSummary' in timeline) timeline.plotSummary = scrubText(timeline.plotSummary);
    }
  }
}

if (includeText) {
  /** 递归抹掉 EXTRA_TEXT_KEYS 里的自由文本（会跳过 sessions[].messages，已处理过）。 */
  const walk = (node, inMessages = false) => {
    if (Array.isArray(node)) {
      for (const item of node) walk(item, inMessages);
      return;
    }
    if (!node || typeof node !== 'object') return;
    for (const [key, value] of Object.entries(node)) {
      if (typeof value === 'string' && EXTRA_TEXT_KEYS.has(key)) {
        node[key] = scrubText(value);
      } else if (value && typeof value === 'object') {
        walk(value, inMessages || key === 'messages');
      }
    }
  };
  // 从顶层走，但把 messages 当作已处理（避免把 content 再抹一次导致计数重复）
  for (const [key, value] of Object.entries(data)) {
    if (key === 'sessions') {
      for (const session of Array.isArray(value) ? value : []) {
        for (const [sk, sv] of Object.entries(session)) {
          if (sk === 'messages') continue;
          if (typeof sv === 'string' && EXTRA_TEXT_KEYS.has(sk)) session[sk] = scrubText(sv);
          else if (sv && typeof sv === 'object') walk(sv);
        }
      }
    } else if (typeof value === 'string' && EXTRA_TEXT_KEYS.has(key)) {
      data[key] = scrubText(value);
    } else if (value && typeof value === 'object') {
      walk(value);
    }
  }
}

writeFileSync(output, JSON.stringify(data, null, 2), 'utf8');

console.log(`✅ 已写入 ${output}`);
console.log(`   抹掉文本 ${scrubbed} 处；保留空值 ${keptEmpty} 处${includeText ? '（含 --include-text 的额外字段）' : '（仅消息正文）'}`);
console.log('   结构字段（id / role / floorNumber / timelineId / codeTag / isCommon / versions 长度 …）全部未动。');
