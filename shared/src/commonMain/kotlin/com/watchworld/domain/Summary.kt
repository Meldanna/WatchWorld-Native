package com.watchworld.domain

import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.AppPreferences
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role

/**
 * 前文总结（T20）—— `App.tsx handleGenerateSummary`(:496) + `SummaryModal.tsx` 的纯逻辑部分。
 *
 * 语义（与 Web 版逐条一致）：
 * - 只总结 `lastSummarizedFloor` **之后**的楼层，因此不会重复总结；
 * - 结果按顺序追加到 `session.summaries`，由 `buildSystemInstruction` 拼进 system tail；
 * - `lastSummarizedFloor` 推进到 `maxFloor`，`dropSummarizedFloors`（T08）据此把已总结原文
 *   从送往模型的历史里剔除 —— 这是省 Token 的关键，**不要**只加总结却不丢原文。
 */

/** 总结请求的 system 提示词（`App.tsx:504` 逐字）。 */
const val SUMMARY_SYSTEM_INSTRUCTION: String =
    "你是一名严谨的剧情总结助手。只依据提供的对话内容做总结，不添加任何未出现的信息。"

/** `SummaryModal` 里的进度统计（`useMemo`）。 */
data class SummaryRange(
    /** 待总结的起始楼号 = `lastSummarizedFloor + 1`。 */
    val fromFloor: Int,
    /** 会话最大楼号。 */
    val maxFloor: Int,
    /** 待总结消息条数。 */
    val pendingCount: Int,
    /** 已总结、因此不再发送给 AI 的原文条数。 */
    val hiddenCount: Int,
)

/** `SummaryModal` 的 `useMemo`：当前待总结范围与「对 AI 隐藏」条数。 */
fun summaryRange(session: ChatSession): SummaryRange {
    // 整块只算**会发送给模型**的消息：被隐藏的分析报告既不进待总结范围，也不计楼号（T20）。
    val messages = dropHiddenAnalysisMessages(session.messages)
    val maxFloor = messages.maxOfOrNull { it.floorNumber ?: 0 } ?: 0
    val done = session.lastSummarizedFloor ?: 0
    val from = done + 1
    val pending = messages.count { (it.floorNumber ?: 0) >= from }
    val hidden = messages.count { it.floorNumber != null && it.floorNumber <= done }
    return SummaryRange(from, maxFloor, pending, hidden)
}

/**
 * `handleGenerateSummary` 选中的待总结消息：`floorNumber >= fromFloor`。
 * ⚠️ 「仅自己可见」的分析报告被剔除 —— 它若被总结，正文等于又经总结回到了上下文，
 *    与「不占AI上下文」的承诺相悰。
 */
fun unsummarizedMessages(session: ChatSession): List<ChatMessage> {
    val from = (session.lastSummarizedFloor ?: 0) + 1
    return dropHiddenAnalysisMessages(session.messages).filter { (it.floorNumber ?: 0) >= from }
}

/**
 * 把待总结消息拼成逐楼文本（`App.tsx:513-524`）。
 *
 * 格式：`#<楼号> [<codeTag>] <用户|AI>：<正文>`，条与条之间空行。
 * ⚠️ 正文取值链是 `versions[currentVersionIndex] → versions[0] → content`
 *    （与 `api.ts:328` 的 `||` 语义一致，不要只读 `versions.first()`）。
 */
fun buildSummaryTranscript(messages: List<ChatMessage>): String = messages.joinToString("\n\n") { message ->
    val who = if (message.role == Role.USER) "用户" else "AI"
    val text = if (message.role == Role.ASSISTANT) {
        // `versions[idx].content || versions[0].content || content`（JS 的 `||` 把空串当假）。
        message.versions.getOrNull(message.currentVersionIndex)?.content?.takeIf { it.isNotEmpty() }
            ?: message.versions.firstOrNull()?.content?.takeIf { it.isNotEmpty() }
            ?: message.content
    } else {
        message.content
    }
    val tag = if (!message.codeTag.isNullOrEmpty()) "[${message.codeTag}] " else ""
    "#${message.floorNumber ?: 0} $tag$who：$text"
}

/** 总结请求的 user 正文（`App.tsx:534`）。 */
fun buildSummaryRequest(prompt: String, transcript: String): String =
    "$prompt\n\n===== 待总结对话 =====\n$transcript"

/**
 * 总结所用模型（`App.tsx:527-531`）：
 * `opts.model || session.model || preferences.defaultModels.summary || provider.defaultModel || ''`。
 */
fun resolveSummaryModel(
    overrideModel: String?,
    session: ChatSession,
    preferences: AppPreferences,
    provider: ApiProviderConfig?,
): String = sequenceOf(
    overrideModel,
    session.model,
    preferences.defaultModels.summary,
    provider?.defaultModel,
).firstOrNull { !it.isNullOrBlank() }.orEmpty()
