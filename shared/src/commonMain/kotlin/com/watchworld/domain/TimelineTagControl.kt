package com.watchworld.domain

import com.watchworld.model.ChatMessage
import com.watchworld.model.TimelineBranch
import com.watchworld.model.TimelineDescriptionTagRequest
import com.watchworld.model.TimelineDescriptionTagRequestStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * `src/lib/timelineDescriptionTagControl.ts`（216 行）1:1 直译（T17）。
 *
 * 模型无权直接改「描述标签」：它只能在回复里附一段私有控制标记
 * `<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{...}-->`，由应用解析成**待审批申请**，
 * 用户点「同意」后才真正写入分支（并同步该分支下所有消息的 `descriptionTag` 缓存）。
 *
 * ⚠️ 控制标记常量逐字照抄（`MIGRATION_CONSTRAINTS §4.3` 的精神：字符串是协议的一部分）。
 * ⚠️ 未闭合的控制标记会**吞掉其后全部内容**（可能正在流式传输中，剩余部分是私有载荷）。
 * ⚠️ 解析是**容错**的：JSON 非法 / 字段数不对 / 缺字段 / 空串 → 不产生申请，但控制标记仍然隐藏。
 */

/** `timelineDescriptionTagControl.ts:7`。 */
const val TIMELINE_DESCRIPTION_TAG_CONTROL_OPEN: String = "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:"

/** `timelineDescriptionTagControl.ts:9`。 */
const val TIMELINE_DESCRIPTION_TAG_CONTROL_CLOSE: String = "-->"

/** `timelineDescriptionTagControl.ts:10`。 */
const val TIMELINE_DESCRIPTION_TAG_MAX_LENGTH: Int = 120

/** `timelineDescriptionTagControl.ts:11`。 */
const val TIMELINE_DESCRIPTION_TAG_REASON_MAX_LENGTH: Int = 240

/** `timelineDescriptionTagControl.ts:13`。 */
data class TimelineDescriptionTagProposal(
    val proposedDescriptionTag: String,
    val reason: String,
)

/** `timelineDescriptionTagControl.ts:18`。 */
data class TimelineDescriptionTagParseResult(
    val visibleReply: String,
    val proposal: TimelineDescriptionTagProposal? = null,
)

/** `timelineDescriptionTagControl.ts:23`：控制字符（C0 除 `\t\n\r` + DEL）。 */
private val CONTROL_CHARACTER_PATTERN: Regex =
    Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

/**
 * `timelineDescriptionTagControl.ts:24`：完整控制块，`([\s\S]*?)` 非贪婪跨行。
 * ⚠️ Kotlin 的 `.` 默认不匹配换行；这里用 `[\s\S]` 等价写法，与 JS 一致。
 */
private val COMPLETE_CONTROL_PATTERN: Regex =
    Regex("<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:([\\s\\S]*?)-->")

/**
 * `timelineDescriptionTagControl.ts:27 sanitizeText`：
 * 去掉控制字符 → `trim` → 截断到 `maxLength` → 再 `trim`；结果为空则 `null`。
 */
internal fun sanitizeTimelineTagText(value: String?, maxLength: Int): String? {
    if (value == null) return null
    val sanitized = value
        .replace(CONTROL_CHARACTER_PATTERN, "")
        .trim()
        .take(maxLength)
        .trim()
    return sanitized.ifEmpty { null }
}

/**
 * `timelineDescriptionTagControl.ts:44 stripTimelineDescriptionTagControl`：
 * 先删掉全部**完整**控制块，再把**未闭合**的开标记及其后全部内容截掉，最后 `trimEnd()`。
 *
 * ⚠️ 未闭合的标记必须吞掉其后所有内容：那可能是**正在流式传输**的私有载荷片段，
 *    若只删标记本身，半截 JSON 会漏到用户可见正文里。
 */
fun stripTimelineDescriptionTagControl(rawReply: String): String {
    val withoutCompleteControls = rawReply.replace(COMPLETE_CONTROL_PATTERN, "")
    val incompleteStart = withoutCompleteControls.indexOf(TIMELINE_DESCRIPTION_TAG_CONTROL_OPEN)
    val kept = if (incompleteStart >= 0) {
        withoutCompleteControls.substring(0, incompleteStart)
    } else {
        withoutCompleteControls
    }
    return kept.trimEnd()
}

/**
 * `timelineDescriptionTagControl.ts:61 parseTimelineDescriptionTagControl`：
 * 从回复里解析私有申请协议。
 *
 * 规则（对照 TS 逐条）：
 *   - 每个完整控制块都被替换成空串（**控制标记永远不进可见正文**）；
 *   - 载荷必须是 JSON 对象、恰好 2 个键、且同时含 `descriptionTag` 与 `reason`；
 *   - 两个值都要能过 [sanitizeTimelineTagText]（非空），否则丢弃；
 *   - **最多只接受 1 个**：`validProposals.length === 1` 才给 `proposal`，0 个或多于 1 个都是 `null`。
 */
fun parseTimelineDescriptionTagControl(rawReply: String): TimelineDescriptionTagParseResult {
    val validProposals = mutableListOf<TimelineDescriptionTagProposal>()

    val withoutCompleteControls = COMPLETE_CONTROL_PATTERN.replace(rawReply) { match ->
        val payload = match.groupValues[1]
        try {
            val element = LENIENT_JSON.parseToJsonElement(payload)
            val obj = element as? JsonObject
            if (obj != null && obj.size == 2 &&
                obj.containsKey("descriptionTag") && obj.containsKey("reason")
            ) {
                val descriptionTag = sanitizeTimelineTagText(
                    (obj["descriptionTag"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                    TIMELINE_DESCRIPTION_TAG_MAX_LENGTH,
                )
                val reason = sanitizeTimelineTagText(
                    (obj["reason"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                    TIMELINE_DESCRIPTION_TAG_REASON_MAX_LENGTH,
                )
                if (descriptionTag != null && reason != null) {
                    validProposals += TimelineDescriptionTagProposal(descriptionTag, reason)
                }
            }
        } catch (_: Exception) {
            // Malformed JSON is intentionally ignored. The private control remains hidden.
        }
        ""
    }

    return TimelineDescriptionTagParseResult(
        visibleReply = stripTimelineDescriptionTagControl(withoutCompleteControls),
        proposal = validProposals.singleOrNull(),
    )
}

/** 解析控制标记载荷用的宽松 JSON（不抛未知键、不要求引号语义之外的宽松处理）。 */
private val LENIENT_JSON = Json { isLenient = true }

/**
 * `timelineDescriptionTagControl.ts:118 createTimelineDescriptionTagRequest`：
 * 把解析出的 [proposal] 变成一条**待审批**申请（不改动任何时间线/消息）。
 *
 * 返回 `null`（= TS 的 `undefined`）的全部情形：
 *   - 时间线记忆未开启 / 无申请 / `sourceMessageId` 为空；
 *   - `targetTimelineId` 找不到**可见**分支；
 *   - 净化后两个字段有空缺；
 *   - 净化后的新标签**与分支当前标签相同**（没有变化就不需要申请）。
 */
fun createTimelineDescriptionTagRequest(
    targetTimelineId: String,
    sourceMessageId: String,
    proposal: TimelineDescriptionTagProposal?,
    timelines: List<TimelineBranch>,
    timelineMemoryEnabled: Boolean,
    requestId: String? = null,
    now: Long = 0L,
): TimelineDescriptionTagRequest? {
    if (!timelineMemoryEnabled || proposal == null || sourceMessageId.isEmpty()) return null

    val targetBranch = timelines.firstOrNull {
        it.id == targetTimelineId && it.visible != false
    } ?: return null

    val proposedDescriptionTag = sanitizeTimelineTagText(
        proposal.proposedDescriptionTag, TIMELINE_DESCRIPTION_TAG_MAX_LENGTH,
    ) ?: return null
    val reason = sanitizeTimelineTagText(
        proposal.reason, TIMELINE_DESCRIPTION_TAG_REASON_MAX_LENGTH,
    ) ?: return null

    val oldDescriptionTag = targetBranch.descriptionTag.orEmpty()
    if (proposedDescriptionTag == oldDescriptionTag.trim()) return null

    return TimelineDescriptionTagRequest(
        id = requestId?.ifEmpty { null } ?: "timeline-description-tag-$now-$sourceMessageId",
        targetTimelineId = targetTimelineId,
        oldDescriptionTag = oldDescriptionTag,
        proposedDescriptionTag = proposedDescriptionTag,
        reason = reason,
        sourceMessageId = sourceMessageId,
        createdAt = now,
        status = TimelineDescriptionTagRequestStatus.PENDING,
    )
}

/** `timelineDescriptionTagControl.ts:160 ResolveTimelineDescriptionTagRequestResult`。 */
data class ResolveTimelineDescriptionTagRequestResult(
    val requests: List<TimelineDescriptionTagRequest>,
    val timelines: List<TimelineBranch>,
    val messages: List<ChatMessage>,
    val resolved: Boolean,
)

/**
 * `timelineDescriptionTagControl.ts:168 approveTimelineDescriptionTagRequest`：
 * 批准一条待处理申请，并**同步该分支下所有消息的 `descriptionTag` 缓存**。
 *
 * ⚠️ 时间线记忆关闭时**直接原样返回且 `resolved = false`**（连状态都不改）。
 * ⚠️ 只认 `status == pending` 的申请；`visible === false` 的目标分支拒批。
 */
fun approveTimelineDescriptionTagRequest(
    requests: List<TimelineDescriptionTagRequest>,
    timelines: List<TimelineBranch>,
    messages: List<ChatMessage>,
    requestId: String,
    timelineMemoryEnabled: Boolean,
    now: Long = 0L,
): ResolveTimelineDescriptionTagRequestResult {
    if (!timelineMemoryEnabled) {
        return ResolveTimelineDescriptionTagRequestResult(requests, timelines, messages, resolved = false)
    }

    val request = requests.firstOrNull {
        it.id == requestId && it.status == TimelineDescriptionTagRequestStatus.PENDING
    } ?: return ResolveTimelineDescriptionTagRequestResult(requests, timelines, messages, resolved = false)

    if (timelines.none { it.id == request.targetTimelineId && it.visible != false }) {
        return ResolveTimelineDescriptionTagRequestResult(requests, timelines, messages, resolved = false)
    }

    val nextRequests = requests.map { item ->
        if (item.id != requestId) item
        else item.copy(status = TimelineDescriptionTagRequestStatus.APPROVED, resolvedAt = now)
    }
    val nextTimelines = timelines.map { timeline ->
        if (timeline.id != request.targetTimelineId) timeline
        else timeline.copy(descriptionTag = request.proposedDescriptionTag, updatedAt = now)
    }
    val nextMessages = messages.map { message ->
        if (message.timelineId != request.targetTimelineId) message
        else message.copy(descriptionTag = request.proposedDescriptionTag)
    }

    return ResolveTimelineDescriptionTagRequestResult(
        requests = nextRequests,
        timelines = nextTimelines,
        messages = nextMessages,
        resolved = true,
    )
}

/**
 * `timelineDescriptionTagControl.ts:220 rejectTimelineDescriptionTagRequest`：
 * 拒绝一条待处理申请，**不动时间线与消息**。
 *
 * ⚠️ 与 approve 不同，reject **不看** `timelineMemoryEnabled`，也不校验目标分支可见性
 *   （TS 源码如此；拒绝一条申请不需要分支仍然存在）。
 */
fun rejectTimelineDescriptionTagRequest(
    requests: List<TimelineDescriptionTagRequest>,
    timelines: List<TimelineBranch>,
    messages: List<ChatMessage>,
    requestId: String,
    now: Long = 0L,
): ResolveTimelineDescriptionTagRequestResult {
    val request = requests.firstOrNull {
        it.id == requestId && it.status == TimelineDescriptionTagRequestStatus.PENDING
    } ?: return ResolveTimelineDescriptionTagRequestResult(requests, timelines, messages, resolved = false)

    return ResolveTimelineDescriptionTagRequestResult(
        requests = requests.map { item ->
            if (item.id != requestId) item
            else item.copy(status = TimelineDescriptionTagRequestStatus.REJECTED, resolvedAt = now)
        },
        timelines = timelines,
        messages = messages,
        resolved = true,
    )
}

