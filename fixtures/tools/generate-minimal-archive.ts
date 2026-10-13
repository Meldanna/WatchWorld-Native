/**
 * 生成 `fixtures/sse/archive-v5-minimal.json` —— 「全新安装未做任何配置」的 v5 存档导出。
 *
 * 对应 `docs/BASELINE_COLLECTION.md` §D（存档样本），用于 T06 的 v5 往返测试。
 *
 * ## 为什么是「用 React 版自己的代码路径生成」，而不是我照着源码重写
 * `Storage.exportAllData()` 的输出形状是**跨版本契约**（`MIGRATION_CONSTRAINTS.md` §1.1）。
 * 手写复刻一定会漂移；这里直接 import React 版的 `storage.ts` 并调用它，
 * 保证 fixture 与真实导出**同源**。`localStorage` 为空 → 各 getter 回落到 `DEFAULT_*`，
 * 这正是新装用户首次导出得到的内容。
 *
 * ## 运行方式（必须在 React 仓里跑：要用它的 tsx 与源码）
 * ```bash
 * cd E:/WatchWorld/WatchWorld
 * npx tsx E:/WatchWorld/WatchWorld-Native/fixtures/tools/generate-minimal-archive.ts
 * ```
 *
 * ## 已知事实（不要误判为 bug）
 * - `INITIAL_SESSION.messages` 是 `[]` → **这份 minimal 里没有"有消息的 session"**。
 *   这是新装导出的真实面貌；`BASELINE_COLLECTION.md` §D.5 那条判据对它不适用。
 * - `DEFAULT_PROVIDERS[*].apiKey` 与 `DEFAULT_WEBDAV_CONFIG.password` 本来就是 `''`，
 *   因此 minimal 天然不含明文密钥。
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, resolve } from 'node:path';

// ---- 1. 装 localStorage 假实现（必须在 import storage.ts 之前）----
// storage.ts 只在函数体内用 localStorage，模块体不碰，所以模块级 import 是安全的。
const memory = new Map<string, string>();
const localStorageMock = {
  getItem: (key: string): string | null => (memory.has(key) ? memory.get(key)! : null),
  setItem: (key: string, value: string): void => void memory.set(key, String(value)),
  removeItem: (key: string): void => void memory.delete(key),
  clear: (): void => memory.clear(),
  key: (index: number): string | null => Array.from(memory.keys())[index] ?? null,
  get length(): number {
    return memory.size;
  },
};
(globalThis as Record<string, unknown>).localStorage = localStorageMock;

const here = dirname(fileURLToPath(import.meta.url));
const storageModulePath = resolve(here, '../../../WatchWorld/src/lib/storage.ts');

const outPath = resolve(here, '../sse/archive-v5-minimal.json');

// ⚠️ 不能用顶层 await：本文件在 React 仓之外，tsx 会按 CJS 处理它，顶层 await 不被支持。
async function main(): Promise<void> {
  mkdirSync(dirname(outPath), { recursive: true });

  // ⚠️ Windows 上动态 import 必须用 file:// URL，传裸绝对路径会报 ERR_UNSUPPORTED_ESM_URL_SCHEME。
  const mod = (await import(pathToFileURL(storageModulePath).href)) as {
    Storage: { exportAllData(opts?: { redactSecrets?: boolean }): string };
  };

  // redactSecrets: true 是保险：即便将来 DEFAULT_* 里被人填了真 Key，导出也不会带出来。
  const json = mod.Storage.exportAllData({ redactSecrets: true });

  // 生成前自检：形状必须对，且不能带明文密钥。
  const parsed = JSON.parse(json) as Record<string, unknown>;
  if (parsed.version !== 5) throw new Error(`version 应为 5，实际 ${String(parsed.version)}`);
  for (const required of [
    'version', 'exportTime', 'providers', 'groups', 'agents', 'prompts', 'regexRules',
    'knowledgeBase', 'theme', 'uiMode', 'mcpServers', 'skills', 'displaySettings',
    'webdavConfig', 'worldDocuments', 'worldGroups', 'sessions', 'appPreferences',
  ]) {
    if (!(required in parsed)) throw new Error(`导出缺少必需顶层字段：${required}`);
  }
  if (/sk-[A-Za-z0-9]|AIza[0-9A-Za-z_-]|Bearer [A-Za-z0-9]/.test(json)) {
    throw new Error('导出疑似含明文密钥，已中止写入');
  }

  writeFileSync(outPath, json, 'utf8');
  console.log(`✅ 已写入 ${outPath}`);
  console.log(`   version=${String(parsed.version)}  sessions=${(parsed.sessions as unknown[]).length}`);
  console.log('   下一步：node fixtures/tools/check-fixtures.mjs 复核；rich 样本请自行从应用导出后用 redact-archive.mjs 脱敏。');
}

main().catch((error: unknown) => {
  console.error('❌ 生成失败：', error);
  console.error(
    `   若是 import 失败，多半是没在 React 仓里跑。请执行：\n` +
      `     cd E:/WatchWorld/WatchWorld && npx tsx ${fileURLToPath(import.meta.url)}`,
  );
  process.exit(1);
});
