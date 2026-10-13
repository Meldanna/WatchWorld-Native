package com.watchworld.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ImageContextMode
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import com.watchworld.model.TagDisplayMode
import com.watchworld.model.TimelineDescriptionTagRequest
import kotlinx.serialization.Serializable

/*
 * 核心表（PLAN §5.2 / MIGRATION_CONSTRAINTS §7 第 5 条）。
 *
 * **只有 `messages` / `timelines` / `summaries` 允许从 `sessions` 拆出去**，其余 ~40 个
 * `ChatSession` 字段一律进 [SessionEntity.configJson]。约束条款的原话是「配置类字段拆表会让
 * 迁移成本与出错率暴涨」，而这三张拆表有实打实的理由：
 *
 * - `messages`：重度会话几千条。塞进 configJson 意味着**每次保存会话都要序列化整个消息数组**，
 *   在 Compose 的 `StateFlow` 更新里就是可见卡顿，SQLite 单行体积也会失控；
 * - `timelines` / `summaries`：T17 要做时间线树、T20 要做总结列表，都要按会话单独查询/排序。
 */

/**
 * 会话行 —— 只放「列表页/侧边栏要用的标量」，其余全部进 [configJson]。
 *
 * 拆出来的这几个字段都是有明确用途的：`updatedAt` 给同步引擎判断增量、`pinned`/`title`/`groupId`
 * 给侧边栏排序分组、`agentId`/`providerId`/`model` 给列表页显示。
 */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["updatedAt"], orders = [Index.Order.DESC])],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    /** 会话列表在存档数组里的下标。SQLite 不保证行序，而侧边栏/导出的顺序由它决定。 */
    val sortIndex: Int,
    val title: String,
    val groupId: String,
    val agentId: String,
    val providerId: String?,
    val model: String?,
    val pinned: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
    /** [SessionConfigBlob] 的 JSON。见该类的说明。 */
    val configJson: SessionConfigBlob,
)

/**
 * `ChatSession` 里**除了** [SessionEntity] 已经铺平的那 9 个字段之外的全部字段。
 *
 * ### 为什么 `timelines` / `summaries` 的「null 还是空数组」要单独记两个布尔
 *
 * 这两个字段在 TS 里是 `TimelineBranch[] | undefined`，且 `normalizeChatSession` **不碰它们** ——
 * 也就是说 `undefined` 会原样留到导出。而它们的内容落在**行**上（0 行），
 * 光看行数分不出「null」和「空数组」，模型层 `null != emptyList()` 会让往返测试挂掉。
 * 因此在 blob 里显式记一态。
 *
 * `messages` 不需要这个标记：它在 TS 里就是 `ChatMessage[]`（非可选），只有空数组一种「空」。
 * `timelineDescriptionTagRequests` 也不需要：`normalizeChatSession` 第 ③ 条已把它拍成非 null。
 */
@Serializable
data class SessionConfigBlob(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val maxTokens: Int? = null,
    val toolCallStepLimit: Int? = null,
    val systemPromptOverride: String? = null,
    val systemPromptFixed: String? = null,
    val systemPromptInjectedCommon: String? = null,
    val isCommonPushed: Boolean? = null,
    val tagDisplayMode: TagDisplayMode? = null,
    val aiContextVisibility: AiContextVisibilityFilter = AiContextVisibilityFilter.ALL,
    val aiContextCustomLimit: Int? = null,
    val uiRenderLimit: Int = 0,
    val connectedKnowledgeIds: List<String> = emptyList(),
    val connectedWorldDocIds: List<String>? = null,
    val regexRuleIds: List<String>? = null,
    val timelineMemoryEnabled: Boolean? = null,
    val activeTimelineId: String? = null,
    val timelineDescriptionTagRequests: List<TimelineDescriptionTagRequest>? = null,
    val summaryPrompt: String? = null,
    val summaryModel: String? = null,
    val lastSummarizedFloor: Int? = null,
    val imageContextMode: ImageContextMode? = null,
    val webSearchEnabled: Boolean? = null,
    val streamingEnabled: Boolean? = null,
    /**
     * 窗口级 AI 头像文件名（原生新增字段）。
     *
     * 放在 blob 里而不是新开一列：`configJson` 本来就是整个 [SessionConfigBlob] 的 JSON，
     * 加字段**不需要**动 Room schema，老行的 JSON 缺这一项时按 `null` 读。
     */
    val aiAvatarFileName: String? = null,
    /** 窗口级用户头像文件名（原生新增字段，用户 2026-10-12 补充：窗口里也要能设用户头像）。 */
    val userAvatarFileName: String? = null,
    /** `ChatSession.timelines` 是否为 `null`（不是空数组）。 */
    val timelinesNull: Boolean = false,
    /** `ChatSession.summaries` 是否为 `null`（不是空数组）。 */
    val summariesNull: Boolean = false,
)

/**
 * 消息行。
 *
 * ⚠️ **`sortIndex` 不是可有可无的**：`ChatMessage` 没有任何能排序的字段（`timestamp` 会撞、
 * 老存档里还有时间戳相同的消息），而会话内的消息顺序是语义的一部分（楼号、上下文拼接、
 * `pushedCommonFloors` 过滤全靠它）。顺序只能由「写库时的数组下标」承载。
 *
 * ⚠️ 正文的存放位置**按角色不对称**：assistant 在 `versions[0].content`、user 在
 * 顶层 [content] 且 `versions` 为空数组（见 `model/Message.kt`）。本实体两处都存，不做合并。
 */
@Entity(
    tableName = "messages",
    // 与 timelines 同理：子表 id 只保证在会话内唯一，主键必须带上 sessionId。
    primaryKeys = ["sessionId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "sortIndex"])],
)
data class MessageEntity(
    val id: String,
    val sessionId: String,
    val sortIndex: Int,
    val role: Role,
    val versionsJson: List<MessageVersion>,
    val currentVersionIndex: Int,
    val content: String,
    val timestamp: Long,
    val latencyMs: Long?,
    val tokens: Int?,
    val timelineId: String?,
    val timelineTag: String?,
    val floorNumber: Int?,
    val codeTag: String?,
    val descriptionTag: String?,
    val isCommon: Boolean?,
    val isAnalysis: Boolean?,
    val analysisVisibility: AnalysisVisibility?,
    val imageIdsJson: List<String>?,
    val analysisBranchCode: String?,
)

/** 时间线分支行。JSON 列：`keyMilestonesJson` / `messageIdsJson`。
 *
 * ⚠️ 主键是 `(sessionId, id)` 而不是 `id`：时间线 id 只在会话内唯一（如每个会话都有
 * `timeline-main`），单列主键会让后写入的会话把前一个会话的同名分支 upsert 走。
 */
@Entity(
    tableName = "timelines",
    primaryKeys = ["sessionId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "sortIndex"])],
)
data class TimelineEntity(
    val id: String,
    val sessionId: String,
    /** 会话内 `timelines` 数组的下标；`TimelineBranch` 自身没有可排序字段。 */
    val sortIndex: Int,
    val name: String,
    val tag: String,
    val codeTag: String?,
    val descriptionTag: String?,
    val visible: Boolean?,
    val color: String,
    val description: String,
    val plotSummary: String,
    val keyMilestonesJson: List<String>?,
    val messageIdsJson: List<String>,
    val parentId: String?,
    val forkMessageId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 前文总结行。会话内按时间顺序存放，同样需要 [sortIndex]。 */
@Entity(
    tableName = "summaries",
    // 与 timelines 同理：子表 id 只保证在会话内唯一，主键必须带上 sessionId。
    primaryKeys = ["sessionId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "sortIndex"])],
)
data class SummaryEntity(
    val id: String,
    val sessionId: String,
    val sortIndex: Int,
    val content: String,
    val fromFloor: Int,
    val toFloor: Int,
    val model: String?,
    val createdAt: Long,
)

/**
 * 杂项键值表（PLAN §5.1 的 `kv`）。
 *
 * 承载 Web 版散落在 localStorage 上的那些**单体设置**：主题调色板、明暗模式、显示设置、
 * 当前会话/Provider、WebDAV 配置、应用偏好、每会话输入草稿。
 * 它们要么是小对象、要么是 `String`，铺表反而把「一个逻辑设置拆成多列」这件事复杂化。
 */
@Entity(tableName = "kv")
data class KvEntity(
    @PrimaryKey val key: String,
    val value: String,
)
