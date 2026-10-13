package com.watchworld.model

import kotlinx.serialization.Serializable

/** `src/types.ts:89 TimelineBranch`（记忆整理·时间线分支树的一个分支）。 */
@Serializable
data class TimelineBranch(
    val id: String,
    /** 时间线名称，如「主线」「五年后要塞战役」「IF线：大学时代」。 */
    val name: String = "",
    /** 标签简写，如「主线」「A1」「五年后」。 */
    val tag: String = "",
    /** 编号标签（用户快速输入用，如 A1、A12、B1）。 */
    val codeTag: String? = null,
    /** 描述标签（如 学园线、学园线·结识好友，由 · 分隔支持嵌套）。 */
    val descriptionTag: String? = null,
    /** 标签可见性开关：隐藏的分支消息不发送给 AI (PRD 4.6)。 */
    val visible: Boolean? = null,
    /** 标识色彩，如 "indigo" / "emerald" / "amber" / "rose" / "cyan" / "purple"。**是名字不是色值**。 */
    val color: String = "",
    /** 核心区分信息/世界观设定特征。 */
    val description: String = "",
    /** 随剧情发展的脉络发展记录。 */
    val plotSummary: String = "",
    /** 关键事件节点。 */
    val keyMilestones: List<String>? = null,
    /** 归属该时间线消息 ID。 */
    val messageIds: List<String> = emptyList(),
    /** 父级分支 ID（构建消息树）。 */
    val parentId: String? = null,
    /** 分支起始消息 ID。 */
    val forkMessageId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** `src/types.ts:109 TimelineDescriptionTagRequest`（模型提出的描述标签变更申请，必须用户批准才写入分支）。 */
@Serializable
data class TimelineDescriptionTagRequest(
    val id: String,
    val targetTimelineId: String = "",
    val oldDescriptionTag: String = "",
    val proposedDescriptionTag: String = "",
    val reason: String = "",
    val sourceMessageId: String = "",
    val createdAt: Long = 0L,
    /** ⚠️ 有默认值：未知状态靠 `coerceInputValues` 退回 [TimelineDescriptionTagRequestStatus.PENDING]。 */
    val status: TimelineDescriptionTagRequestStatus = TimelineDescriptionTagRequestStatus.PENDING,
    val resolvedAt: Long? = null,
)

/** `src/types.ts:423 SessionSummary`（一条前文总结记录）。 */
@Serializable
data class SessionSummary(
    val id: String,
    val content: String = "",
    val fromFloor: Int = 0,
    val toFloor: Int = 0,
    val model: String? = null,
    val createdAt: Long = 0L,
)
