package com.watchworld.model

import kotlinx.serialization.Serializable

/**
 * `src/types.ts:357 ChatSession` —— 约 60 个字段的「上帝对象」。
 *
 * ### 为什么这里是一个扁平 data class，而不是 PLAN §4.3 的 SessionConfig + SessionEntity + 聚合对象
 *
 * PLAN §4.3 把会话拆成三块，那是**落库**（T07 Room）的形态。但 `ChatSession` 同时是
 * **v5 存档里的形状**（`exportAllData()` 直接序列化整个对象），T06 的往返测试要求
 * `v5 JSON → 内存 → v5 JSON` 关键字段一致。若在此处就拆成三块，T06 得先写一层
 * 重组/再拆分的转换，等于把「存档契约」和「存储实现」的映射提前到了模型层。
 *
 * 因此本类**保持与 `src/types.ts` 逐字段对齐的扁平形状**（`messages` / `timelines` /
 * `summaries` 内嵌），作为**存档契约的唯一权威**；T07 再引入 `SessionConfig` /
 * `SessionEntity` 与它们之间的映射（映射放在 `data/` 层，不动本类）。
 *
 * ### 不可动的约定
 * - **字段名一律不许改**（`codeTag` / `descriptionTag` / `floorNumber` / `isCommon` …）：
 *   每个字段都对应存档，改名即破兼容（MIGRATION_CONSTRAINTS §6）。
 * - **不声明** `connectedSkillIds`（遗留字段）：Kotlin 侧直接不声明，读老存档时由
 *   `ignoreUnknownKeys` 丢弃 —— 这正是 `normalizeChatSession` 第 ① 条要的效果。
 * - 带 `?` 的字段在 `explicitNulls = false` 下不会被写回存档，等价于 TS 的 `undefined`。
 */
@Serializable
data class ChatSession(
    val id: String,
    val title: String = "",
    val groupId: String = "",
    val agentId: String = "",
    val providerId: String? = null,
    val model: String? = null,
    // ↓ 窗口级 API 采样参数：仅覆盖本会话，不改变全局 Provider 配置。
    //   未设置(null) 时回退到当前 Agent 的对应值。
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val maxTokens: Int? = null,
    /**
     * 当前窗口连续工具调用轮数上限；`null` = 继承全局，`0` = 无限制。
     * ⚠️ 老存档可能是 `-1` / `NaN` / 字符串 → 必须按 `MIGRATION_CONSTRAINTS.md` §2 第 ④ 条
     *    从**原始 JSON 数值**归一化（`max(0, floor(n))`），不能只靠 `coerceInputValues`。
     */
    val toolCallStepLimit: Int? = null,
    val systemPromptOverride: String? = null,
    // ↓ 3.1 双框提示词区域
    /** 用户手写的固定提示词（始终展开）。 */
    val systemPromptFixed: String? = null,
    /** 注入的通用提示词（默认折叠，推入后走系统提示词缓存）。 */
    val systemPromptInjectedCommon: String? = null,
    /** 「推入通用」按钮开关状态。 */
    val isCommonPushed: Boolean? = null,
    /** 标签显示模式：文字版（学园线·结识好友）或字母版（A12）。 */
    val tagDisplayMode: TagDisplayMode? = null,
    /** 控制 AI 自己在上下文里能看到多少条历史。 */
    val aiContextVisibility: AiContextVisibilityFilter = AiContextVisibilityFilter.ALL,
    /** 当 [aiContextVisibility] 为 `latest_custom` 时，保留最新 N 条助手消息（默认 3）。 */
    val aiContextCustomLimit: Int? = null,
    /** 控制在 UI 里渲染多少条消息（`0` = 全部）。⚠️ 默认值是 `0`，不是 `null`。 */
    val uiRenderLimit: Int = 0,
    /** 本窗口启用的知识库条目 ID。 */
    val connectedKnowledgeIds: List<String> = emptyList(),
    /** 本窗口启用的世界观正典文档 ID（与全局 enabled 取交集后注入 system）。 */
    val connectedWorldDocIds: List<String>? = null,
    /** 本窗口启用的正则规则 ID（与全局规则池取交集，实现按窗口独立生效）。 */
    val regexRuleIds: List<String>? = null,
    /**
     * 本会话的消息。
     * ⚠️ **不拆出去**：v5 存档里它就是嵌在 session 内的（见类注释）。
     *    legacy fixture 实测：user 消息 `versions` 为空数组、正文在 `content`。
     */
    val messages: List<ChatMessage> = emptyList(),
    // ↓ 记忆整理·时间线分支树
    /** 是否开启时间线记忆整理。 */
    val timelineMemoryEnabled: Boolean? = null,
    /** 当前激活选中的时间线分支 ID。 */
    val activeTimelineId: String? = null,
    /** 会话内的所有时间线分支。 */
    val timelines: List<TimelineBranch>? = null,
    /**
     * 模型提出的描述标签变更申请，必须由用户批准后才写入分支。
     * ⚠️ 归一时非法值要变成**空队列**（不是 `null`）—— MIGRATION_CONSTRAINTS §2 第 ③ 条。
     */
    val timelineDescriptionTagRequests: List<TimelineDescriptionTagRequest>? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pinned: Boolean? = null,
    // ↓ 4.6 前文总结：按顺序推入系统提示词，不重复总结已处理过的楼层
    /** 总结用的提示词（可配置，留空用默认）。 */
    val summaryPrompt: String? = null,
    /** 总结用的模型（留空跟随本窗口当前模型）。 */
    val summaryModel: String? = null,
    /** 已生成的总结，按时间顺序。 */
    val summaries: List<SessionSummary>? = null,
    /** 已总结到的最大楼号，用于避免重复总结。 */
    val lastSummarizedFloor: Int? = null,
    /**
     * 图片是否留在后续上下文：
     * `once`（默认）= 只在发送它的那一轮带给模型，之后不再重复计费；
     * `history`   = 图片随消息保留在历史里，模型后续仍能看到。
     */
    val imageContextMode: ImageContextMode? = null,
    /** 当前窗口是否允许模型使用内置联网能力。仅表示授权开关，不代表每轮都会联网。 */
    val webSearchEnabled: Boolean? = null,
    /**
     * 流式输出：本窗口独立取值，新建窗口时由偏好里的默认值初始化。
     * ⚠️ **三态，不许合并**：`null`（老存档）= 视为流式；`false` = 明确关闭。
     *    `normalizeChatSession` 第 ⑥ 条：非 boolean → `null`。
     *    把 `null` 归一化成 `false` 会让所有老存档突然变成非流式（gotchas「预置」有记）。
     */
    val streamingEnabled: Boolean? = null,
    /** 窗口级 AI 头像文件名（覆盖 Agent 默认头像，存储在该窗口的 avatars/ 目录）。 */
    val aiAvatarFileName: String? = null,
    /** 窗口级用户头像文件名（覆盖全局用户头像，仅在本窗口生效）。 */
    val userAvatarFileName: String? = null,
)
