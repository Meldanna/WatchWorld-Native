package com.watchworld.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.watchworld.model.ApiProviderType
import com.watchworld.model.FileAccess
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.McpTransportType
import com.watchworld.model.RegexScope

/*
 * 配置类表（PLAN §5.1）。这些实体「读多写少、条目数少」，因此**一表一实体、字段尽量铺平**，
 * 只有 TS 里本来就是 `T[]` / `Record<string,string>` 的字段才落成 `*Json` 列。
 *
 * ### 每张表都有 `sortIndex`，这不是冗余
 *
 * Web 版把每个集合整个 `JSON.stringify` 进一个 localStorage 键，**数组顺序原样保留**，
 * 而顺序是有语义的：`DEFAULT_AGENTS` 的新增内置顾问要排在最前、Provider 下拉的分组顺序
 * 由数组顺序决定、世界书分组树靠数组顺序稳定渲染。SQLite **不保证** `SELECT *` 的行序，
 * 所以顺序必须显式落在列上。少了它，存档往返会出现「字段都对、顺序变了」这种最难查的差异。
 *
 * ### 与 Web 版 localStorage 键的对应
 *
 * 原生版**不用** localStorage，所以 `omnichat_` / `guanjie_` 那些前缀在这里不承载数据。
 * 但「哪个表对应哪个键」是必须查得到的（T19 WebDAV、从浏览器搬迁数据都要），
 * 完整对照表在 `data/StorageKeys.kt` 的 KDoc 里，**只放那一处**。
 */

/** `provider` 配置。JSON 列：`modelsJson` / `customHeadersJson`（PLAN §5.1）。 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val type: ApiProviderType,
    val baseUrl: String?,
    /** ⚠️ 明文存的密钥。加密存储归 PLAN §5.6 / 后续任务，本任务不做。 */
    val apiKey: String?,
    val defaultModel: String,
    val modelsJson: List<String>,
    val isCustom: Boolean?,
    val enabled: Boolean?,
    val isSystemDefault: Boolean?,
    val customHeadersJson: Map<String, String>?,
    val defaultTemperature: Double?,
    val defaultTopP: Double?,
    val defaultTopK: Int?,
    val defaultFrequencyPenalty: Double?,
    val defaultPresencePenalty: Double?,
    val defaultMaxTokens: Int?,
)

/** `agent` 配置。JSON 列：`tagsJson` / `mcpServerIdsJson` / `skillIdsJson`。 */
@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val avatar: String,
    val description: String,
    val systemPrompt: String,
    val temperature: Double,
    val topP: Double?,
    val contextMessageLimit: Int?,
    val isDefault: Boolean?,
    val tagsJson: List<String>?,
    val isBuiltin: Boolean?,
    val model: String?,
    val memory: String?,
    val mcpServerIdsJson: List<String>?,
    val skillIdsJson: List<String>?,
    val fileAccess: FileAccess?,
)

/** `prompt` 预设。JSON 列：`tagsJson`。 */
@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val title: String,
    val category: String,
    val content: String,
    val description: String?,
    val tagsJson: List<String>?,
)

/** 正则规则。 */
@Entity(tableName = "regexRules")
data class RegexRuleEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val pattern: String,
    val flags: String,
    val replacement: String,
    val scope: RegexScope,
    val enabled: Boolean,
    val description: String?,
    val group: String?,
)

/** 知识库条目。JSON 列：`tagsJson`。 */
@Entity(tableName = "knowledgeBase")
data class KnowledgeEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val title: String,
    val content: String,
    val tagsJson: List<String>,
    val enabled: Boolean,
    val updatedAt: Long,
)

/**
 * MCP 服务配置。JSON 列：`headersJson` / `toolsJson`。
 *
 * ⚠️ `tools`（由 `tools/list` 自动发现的缓存）是**入档字段**，不是可随手丢弃的运行期缓存：
 * Web 版 `exportAllData` 原样把它带出去。
 */
@Entity(tableName = "mcpServers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val type: McpTransportType,
    val url: String?,
    val authToken: String?,
    val headersJson: Map<String, String>?,
    val status: McpServerStatus,
    val toolsJson: List<McpTool>,
    val description: String?,
    val lastTestedAt: Long?,
    val lastLatencyMs: Long?,
    val lastError: String?,
    val serverName: String?,
    val serverVersion: String?,
)

/** Skill。JSON 列：`triggerKeywordsJson` / `toolsNeededJson`。 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val icon: String,
    val description: String,
    val triggerKeywordsJson: List<String>,
    val systemInstructionInjection: String,
    /** 与 [systemInstructionInjection] 同义的另一个字段，两个都要原样保留（`model/Content.kt` 有说明）。 */
    val systemInstruction: String?,
    val toolsNeededJson: List<String>?,
    val isBuiltin: Boolean?,
)

/** 世界观分组（可任意嵌套，靠 `parentId` 建树）。 */
@Entity(tableName = "worldGroups")
data class WorldGroupEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val parentId: String?,
)

/** 世界观正典文档。JSON 列：`tagsJson`。 */
@Entity(tableName = "worldDocuments")
data class WorldDocumentEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val title: String,
    val groupId: String?,
    val content: String,
    val enabled: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
    val tagsJson: List<String>,
    /**
     * 旧版 `group`（**组名字符串**）的落库列，只为老存档迁移服务（T18）。
     *
     * ⚠️ **不进存档**：`storage.ts:370` 的 `const { group: _legacy, ...rest }` 在**迁移那一刻**把它删掉，
     *    而迁移发生在**读**的时候 —— `ConfigRepository.worldDocuments()` 读完立刻解析
     *    （`domain/WorldLibrary.resolveWorldDocumentGroupIds`）。
     *  ⚠️ 所以**导入时它必须先落库**（Mapper 的 `toEntity` 原样带上），否则「导入 → 读」这段路
     *    中间就把它丢了，迁移永远看不到老 `group`（表现为导入老存档后文档全部未分组）。
     *    一旦迁移跑过，内存里的对象已没有 `legacyGroup`，再写回库时该列自然清空，老 `group` 不会复活。
     */
    val legacyGroup: String?,
)

/**
 * 用户 DIY 主题。
 *
 * ⚠️ **不在 v5 存档里**：Web 版把它存在独立键 `guanjie_custom_themes`，`exportAllData` 不带它。
 * 所以 [com.watchworld.data.repository.ArchiveStore] 导入/导出时都不要碰这张表。
 */
@Entity(tableName = "customThemes")
data class CustomThemeEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val m1: String,
    val m2: String,
    val createdAt: Long,
)

/**
 * 模型收藏。复合主键 `(providerId, modelName)`（PLAN §5.1）。
 *
 * `sortIndex` 记的是 Web 版数组里的位置；Web 版没有别的排序依据。
 */
@Entity(tableName = "modelFavorites", primaryKeys = ["providerId", "modelName"])
data class ModelFavoriteEntity(
    val providerId: String,
    val modelName: String,
    val sortIndex: Int,
    val addedAt: Long,
)

/**
 * 会话分组。
 *
 * ⚠️ `order` 是 SQL 关键字，这里**故意保留原名**：它是模型/存档字段名，改名才是事故。
 * Room 生成的 SQL 会给标识符加反引号所以能用，但手写 SQL 时必须自己加。
 */
@Entity(tableName = "groups")
data class ChatGroupEntity(
    @PrimaryKey val id: String,
    val sortIndex: Int,
    val name: String,
    val color: String,
    val icon: String?,
    val order: Int,
)
