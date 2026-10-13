package com.watchworld.domain

import com.watchworld.model.ChatMessage
import com.watchworld.model.Role

/**
 * Token 估算器（直译自 `src/lib/tokenEstimator.ts:9-30`）。
 *
 * 估算规则：
 * - 中文字符 / CJK 标点：~1.1 个 token
 * - 英文单词：~1.3 个 token
 * - 回退校验：确保不小于 `length / 4`
 *
 * 该函数在 [MessageBubble] 中用于当消息本身没有 `tokens` 字段时（例如用户消息、
 * 或者 API 响应没有返回 token 计数时）提供一个估算值，用于显示。
 */
fun estimateTokens(text: String?): Int {
    if (text.isNullOrEmpty()) return 0

    var tokens = 0

    // 统计 CJK 字符（含 CJK 标点）
    val cjkRegex = Regex("[\\u4e00-\\u9fa5\\u3000-\\u303f\\uff01-\\uffee]")
    val cjkCount = cjkRegex.findAll(text).count()
    tokens += kotlin.math.ceil(cjkCount * 1.1).toInt()

    // 非 CJK 文本按单词统计
    val nonCjk = text.replace(cjkRegex, " ")
    val words = nonCjk.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    tokens += kotlin.math.ceil(words.size * 1.3).toInt()

    // 回退校验：确保不小于 length / 4
    val minTokens = kotlin.math.ceil(text.length / 4.0).toInt()
    return maxOf(tokens, minTokens)
}

/**
 * 会话累计 Token（直译 `App.tsx:860 sessionTotalTokens`）。
 *
 * - user：取顶层 `tokens`（无则估算正文）；
 * - assistant：取**当前版本**的 `tokens`（无则估算该版本正文）；
 * - 其它：估算正文。
 *
 * ⚠️ 与 Web 一致：`tokens` 为 0 时视为「没有」而改用估算（JS 的 `||` 语义）。
 */
fun estimateSessionTokens(messages: List<ChatMessage>): Int = messages.sumOf { message ->
    when (message.role) {
        Role.USER -> message.tokens?.takeIf { it > 0 } ?: estimateTokens(message.content)
        Role.ASSISTANT -> {
            val version = message.versions.getOrNull(message.currentVersionIndex)
                ?: message.versions.firstOrNull()
            version?.tokens?.takeIf { it > 0 }
                ?: estimateTokens(version?.content ?: message.content)
        }
        else -> estimateTokens(message.content)
    }
}

/**
 * Token 数量的紧凑显示（直译 `tokenEstimator.ts:32 formatTokenCount`）：
 * ≥1M → `1.2M`，≥1k → `2.5k`，否则原样数字。
 */
fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${oneDecimal(tokens / 1_000_000.0)}M"
    tokens >= 1_000 -> "${oneDecimal(tokens / 1_000.0)}k"
    else -> tokens.toString()
}

/** `toFixed(1)`：保留 1 位小数（四舍五入；commonMain 无 `String.format`）。 */
private fun oneDecimal(value: Double): String {
    val scaled = kotlin.math.round(value * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}