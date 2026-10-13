package com.watchworld.model

import kotlinx.serialization.Serializable

/**
 * `src/types.ts:77 MessageVersion` — 一条消息的一个版本（滚动重答产生多个，用户可切换）。
 *
 * ⚠️ **正文存放位置按角色不对称**（`fixtures/sse/archive-v5-legacy.json` 实测）：
 *    - assistant：`versions` 长度 1，正文在 `versions[0].content`，带 `latencyMs`；
 *    - user：`versions` 是**空数组**，正文在 [ChatMessage.content]，且**没有** `latencyMs`。
 *    → `versions` 必须允许空数组；取值前先看角色，别写 `versions.first()`。
 */
@Serializable
data class MessageVersion(
    val content: String = "",
    val timestamp: Long = 0L,
    val model: String? = null,
    val providerName: String? = null,
    val latencyMs: Long? = null,
    val tokens: Int? = null,
    /** 思维链原文（模型 reasoning/thinking 内容），**仅用于展示，不进入后续上下文**。 */
    val reasoning: String? = null,
)

/**
 * `src/types.ts:121 ChatMessage`（19 个字段，与 TS 一一对应）。
 *
 * 字段可空性按 TS 的 `?` 逐条翻译：TS 可选 = Kotlin `null` 默认值。
 * 带 `?` 的字段在 `explicitNulls = false` 下不会被写回存档（与 TS 的 `undefined` 等价）。
 */
@Serializable
data class ChatMessage(
    val id: String,
    /** ⚠️ 有默认值：未知角色靠 `coerceInputValues` 退回 [Role.USER]，不抛错。 */
    val role: Role = Role.USER,
    val versions: List<MessageVersion> = emptyList(),
    val currentVersionIndex: Int = 0,
    /**
     * 顶层正文。
     * ⚠️ **不是**「版本数组的副本」：user 消息正文只在这里，其 `versions` 为空数组。
     */
    val content: String = "",
    val timestamp: Long = 0L,
    val latencyMs: Long? = null,
    val tokens: Int? = null,
    /** 关联的时间线 ID。 */
    val timelineId: String? = null,
    /** 缓存的时间线标签显示。 */
    val timelineTag: String? = null,
    /** 楼号系统：每条消息强制标注递增楼号 [#1] [#2] (PRD 4.2)。 */
    val floorNumber: Int? = null,
    /** 归属分支编号标签（如 A12、通用）。⚠️ 名字看着别扭，是存档字段，不许改。 */
    val codeTag: String? = null,
    /** 归属分支描述标签（如 学园线·结识好友）。⚠️ 同上，不许改。 */
    val descriptionTag: String? = null,
    /** 是否标记为通用信息 (PRD 4.5)。⚠️ 不许改名为 isShared。 */
    val isCommon: Boolean? = null,
    /** 是否为心理动力学角色关系分析报告。 */
    val isAnalysis: Boolean? = null,
    /** 分析报告的可见性。 */
    val analysisVisibility: AnalysisVisibility? = null,
    /** 附加图片的 id（图片本体存文件系统，这里只存引用）。 */
    val imageIds: List<String>? = null,
    /** 所属分析分支。 */
    val analysisBranchCode: String? = null,
)
