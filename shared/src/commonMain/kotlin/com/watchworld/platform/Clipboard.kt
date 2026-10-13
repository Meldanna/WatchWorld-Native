package com.watchworld.platform

/**
 * 复制纯文本到系统剪贴板。
 *
 * 为什么不用 `LocalClipboardManager`：它在 Compose 里已废弃；替代品 `LocalClipboard` 要
 * 构造跨平台的 `ClipEntry`，各平台形态不一致。这里用一个最小平台封装，
 * 调用方（世界观详情的「复制全文」）只关心「把这段文字放进剪贴板」。
 */
expect fun copyTextToClipboard(text: String)
