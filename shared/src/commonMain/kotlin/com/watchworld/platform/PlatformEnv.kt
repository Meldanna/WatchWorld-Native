package com.watchworld.platform

/**
 * 读取进程环境变量；不存在时返回 `null`。
 *
 * 放在 `platform/` 是因为 **commonMain 没有环境变量的概念**（Android 上根本没有），
 * 必须由各平台 `actual` 提供。当前只有 `desktopMain` 的实现。
 *
 * 用途（T03）：`HardcodedChatProvider` 从环境变量取 API Key，
 * **避免把密钥写进代码或提交进 git**。
 */
expect fun platformEnv(name: String): String?
