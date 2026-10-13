#!/usr/bin/env node
/**
 * 把存档里的**个人端点**换成中性占位符。
 *
 * 为什么需要第三个脚本：脱敏的分工是
 *   - `redact-archive.mjs`  → **凭据与身份**（apiKey / password / authToken / username）
 *   - `scrub-archive-messages.mjs` → **对话派生文本**（消息正文 / 总结 / 梗概）
 *   - 本脚本                → **个人端点**（URL 里的主机名与账号 slug）
 *
 * 三者互不重叠。URL 之所以单独处理：它既不是凭据（换了 host 也不能冒用），
 * 也不是对话内容，但 `https://github.windlife.site/mcp/andunya` 这类地址等于把
 * **你的域名 + 你的账号名**写进仓库 —— 2026-10-07 实测，它一路通过了前两个脚本。
 *
 * 用法：
 *   node fixtures/tools/neutralize-endpoints.mjs <输入.json> <输出.json>
 *
 * 做与不做：
 *   - **做** `webdavConfig.url`（云端存储账号端点）与 `mcpServers[].url`（个人 MCP 服务）。
 *     MCP 端点常在路径里带账号 slug（`/mcp/andunya`），所以**最后一段**一并换掉。
 *   - **保留 URL 形状**：协议、路径段数、查询串都不变 —— fixture 仍要能测「URL 能否解析、
 *     路径拼接对不对」。localhost / 127.0.0.1 原样保留（本地地址不含个人身份）。
 *   - **不做** `providers[].baseUrl`：那是服务端点而非账号标识，且 Provider 配置
 *     （T04/T15）正需要真实形状的 baseUrl（`/v1` 结尾与否会影响请求拼接）。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const HOST = 'REDACTED_TEST_HOST';
const USER = 'REDACTED_TEST_USER';

const [inputArg, outputArg] = process.argv.slice(2);
if (!inputArg || !outputArg) {
  console.error('用法：node fixtures/tools/neutralize-endpoints.mjs <输入.json> <输出.json>');
  process.exit(1);
}

const input = resolve(process.cwd(), inputArg);
const output = resolve(process.cwd(), outputArg);

let data;
try {
  data = JSON.parse(readFileSync(input, 'utf8'));
} catch (error) {
  console.error(`❌ 无法解析 ${input}：`, error.message);
  process.exit(1);
}

const changed = [];
const isLocal = (host) => /^(localhost|127\.0\.0\.1|\[?::1\]?)$/.test(host);

/** 只换 host，保留 pathname / search（形状留给测试）。 */
function maskHost(url) {
  if (typeof url !== 'string' || url === '') return url;
  try {
    const parsed = new URL(url);
    if (isLocal(parsed.hostname)) return url;
    changed.push(`${parsed.hostname} → ${HOST}`);
    return `${parsed.protocol}//${HOST}${parsed.pathname}${parsed.search}`;
  } catch {
    return url; // 不是合法 URL 就不动（宁可不改，也别改坏）
  }
}

if (data.webdavConfig) {
  data.webdavConfig.url = maskHost(data.webdavConfig.url);
}

for (const server of Array.isArray(data.mcpServers) ? data.mcpServers : []) {
  server.url = maskHost(server.url);
  // `…/mcp/andunya` → `…/mcp/REDACTED_TEST_USER`；只动最后一段，服务路径（/mcp）留着
  if (typeof server.url === 'string' && server.url.includes(`//${HOST}/`)) {
    const masked = server.url.replace(/\/([^/?#]+)(?=$|[?#])/, (whole, last) => {
      if (last === '') return whole;
      const isServicePath = /^(mcp|dav|sse|api|v\d+)$/i.test(last);
      return isServicePath ? whole : `/${USER}`;
    });
    if (masked !== server.url) {
      changed.push(`MCP 路径段 → ${USER}`);
      server.url = masked;
    }
  }
}

writeFileSync(output, JSON.stringify(data, null, 2) + '\n', 'utf8');

if (changed.length === 0) {
  console.log('✅ 已写入（没有发现需要改写的个人端点）');
} else {
  console.log(`✅ 已写入 ${output}`);
  console.log(`   改写 ${changed.length} 处：`);
  for (const c of changed) console.log(`     - ${c}`);
}
console.log('   提醒：providers[].baseUrl 是**故意**不动的；若某个 baseUrl 也是你的私人端点，请手工处理。');
