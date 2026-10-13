package com.watchworld.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * 这块对应 `src/types.ts:207-354`：分组 / 正则 / 知识库 / MCP / Skill / 主题 / WebDAV / 世界书。
 * 都是「配置类」实体，T07 会一表一实体落库（PLAN §5.1）。
 */

/** `src/types.ts:207 ChatGroup`（会话分组）。 */
@Serializable
data class ChatGroup(
    val id: String,
    val name: String = "",
    val color: String = "",
    val icon: String? = null,
    val order: Int = 0,
)

/** `src/types.ts:216 RegexRule`（正则规则）。 */
@Serializable
data class RegexRule(
    val id: String,
    val name: String = "",
    val pattern: String = "",
    val flags: String = "",
    val replacement: String = "",
    /** ⚠️ 必须给默认值：未知 scope 靠 `coerceInputValues` 退回 [RegexScope.BOTH]，不抛错。 */
    val scope: RegexScope = RegexScope.BOTH,
    val enabled: Boolean = false,
    val description: String? = null,
    /** 所属分组名（纯库内的分类目录，可折叠）。留空视为「未分组」。 */
    val group: String? = null,
)

/** `src/types.ts:230 KnowledgeItem`（知识库条目）。 */
@Serializable
data class KnowledgeItem(
    val id: String,
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val enabled: Boolean = false,
    val updatedAt: Long = 0L,
)

/** `src/types.ts:240 McpTool`（由 `tools/list` 自动发现并缓存）。 */
@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    /** JSON schema 或描述文本。TS 是 string，**不是对象**。 */
    val parametersSchema: String? = null,
)

/** `src/types.ts:248 McpServerConfig`。 */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String = "",
    /** ⚠️ 默认值见 [McpTransportType]；老存档的 `custom_api` / `stdio` 由 normalize 层丢弃。 */
    val type: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val url: String? = null,
    val authToken: String? = null,
    val headers: Map<String, String>? = null,
    val status: McpServerStatus = McpServerStatus.DISCONNECTED,
    val tools: List<McpTool> = emptyList(),
    val description: String? = null,
    val lastTestedAt: Long? = null,
    val lastLatencyMs: Long? = null,
    val lastError: String? = null,
    val serverName: String? = null,
    val serverVersion: String? = null,
)

/** `src/types.ts:270 AgentSkill`。 */
@Serializable
data class AgentSkill(
    val id: String,
    val name: String = "",
    val icon: String = "",
    val description: String = "",
    val triggerKeywords: List<String> = emptyList(),
    /** 命中 Skill 时注入 system 的指导文本。 */
    val systemInstructionInjection: String = "",
    /** TS 注释写的就是「alias」，与上一字段同义；两个都要原样保留。 */
    val systemInstruction: String? = null,
    val toolsNeeded: List<String>? = null,
    val isBuiltin: Boolean? = null,
)

/**
 * `src/types.ts:303 CustomTheme` — 用户 DIY 主题，「两个模块各一个 HEX」。
 * 存键 `guanjie_custom_themes`，按 createdAt 倒序存放。
 */
@Serializable
data class CustomTheme(
    val id: String,
    val name: String = "",
    /** 模块 1 主色。 */
    val m1: String = "",
    /** 模块 2 辅助色。 */
    val m2: String = "",
    val createdAt: Long = 0L,
)

/**
 * `src/types.ts:317 WebDavConfig`。
 * ⚠️ `password` 是**应用密码**（坚果云要求），导出时被 `redactSecrets` 换成空串 `''` 而不是 `null`。
 */
@Serializable
data class WebDavConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val username: String = "",
    val password: String? = null,
    /** 兼容旧版全量备份文件路径，默认 `/guanjie_backup.json`。 */
    val syncPath: String? = null,
    /** 增量同步的根目录，默认 `/guanjie/`。 */
    val syncRootDir: String? = null,
    val autoSync: Boolean = false,
    /** 手动全量备份到 WebDAV 时，是否一并上传 API 密钥与 WebDAV 密码（默认否）。 */
    val includeApiConfig: Boolean? = null,
    val lastSyncTime: Long? = null,
    val syncStatus: WebDavSyncStatus? = null,
    val lastError: String? = null,
)

/**
 * `src/types.ts:336 WorldGroup`（世界观分组，可任意嵌套）。
 * 大组（`parentId` 为空）就是一个「世界观」，组内的子分组是它的目录。
 *
 * ⚠️ `parentId` 在 TS 里是 `string | null`（显式 null 与 undefined 都可能出现）。
 *    `explicitNulls = false` 会让 `null` 在**回写时**被省略，这两态在存档里本来就等价。
 */
@Serializable
data class WorldGroup(
    val id: String,
    val name: String = "",
    /** 上级分组 ID；留空表示顶层大组。 */
    val parentId: String? = null,
)

/** `src/types.ts:343 WorldDocument`（世界观正典文档）。 */
@Serializable
data class WorldDocument(
    val id: String,
    val title: String = "",
    /**
     * 所属分组 ID；留空视为「未分组」。
     * ⚠️ 旧数据里的 `group`（**组名字符串**）在读取时迁移到 `groupId`（`storage.ts:360-373`），
     *    迁移入口是 `domain/WorldLibrary.resolveWorldDocumentGroupIds`（T18）。
     */
    val groupId: String? = null,
    /**
     * 旧版字段 `group`（**组名字符串**）的原样保留处，仅用于 T18 的读时迁移。
     *
     * ⚠️ 这里**刻意保留原名** `group`：它不是新字段，而是 `storage.ts:360-373`
     *    `getWorldDocuments` 要迁移掉的那个键。改名会让 `ignoreUnknownKeys` 把它静默丢掉，
     *    「老数据 → groupId」这一步就永远不会发生（表现为用户升级后文档全部变成未分组）。
     *    迁移逻辑在 `domain/WorldLibrary.resolveWorldDocumentGroupIds`，**不要**在 UI 里读它。
     */
    @SerialName("group")
    val legacyGroup: String? = null,
    /** Markdown 文本。 */
    val content: String = "",
    /** 全局是否启用；窗口还要再单独勾选一层，两者都满足才会注入 system。 */
    val enabled: Boolean? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val tags: List<String> = emptyList(),
)
