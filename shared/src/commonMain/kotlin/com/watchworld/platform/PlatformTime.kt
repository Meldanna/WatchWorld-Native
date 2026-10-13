package com.watchworld.platform

/**
 * 当前 UTC 毫秒时间戳。
 *
 * 放在 `platform/` 的理由与 [platformEnv] 相同：**commonMain 没有系统时钟**。
 * `kotlin.time.Clock` 在 2.4.20 上仍带实验注解，为这一个调用点引实验 API 不划算，
 * 各平台 `actual` 一行就够（桌面 `System.currentTimeMillis()`）。
 *
 * 用途：v5 存档导出的 `exportTime`（`storage.ts:420` 的 `Date.now()`）。
 * **故意不提供默认参数之外的便利**：调用方显式传入时间戳时行为完全可复现。
 */
expect fun currentTimeMillis(): Long
