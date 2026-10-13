package com.watchworld.model

import kotlinx.serialization.Serializable

/**
 * `src/types.ts:43 Agent`（智能体/顾问）。
 *
 * ⚠️ 字段名与 `AgentModal`、`defaultData.ts` 的 `DEFAULT_AGENTS` 逐字对应；
 *    legacy fixture 里的 `agent-omni` 只写了 id/name/avatar/description/systemPrompt/temperature/topP/isDefault，
 *    因此 `avatar`/`description`/`systemPrompt` 必须有默认值（storage.ts `getAgents` 会给老条目补 `tags`）。
 */
@Serializable
data class Agent(
    val id: String,
    val name: String = "",
    /** emoji 或图片标识，TS 是 string。 */
    val avatar: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val topP: Double? = null,
    /** 历史消息上下文上限。 */
    val contextMessageLimit: Int? = null,
    val isDefault: Boolean? = null,
    val tags: List<String>? = null,
    val isBuiltin: Boolean? = null,
    val model: String? = null,
    /** 该 Agent 的长期记忆（手动编辑，会注入系统提示词）。 */
    val memory: String? = null,
    /** 此 Agent 绑定的 MCP 服务 ID。 */
    val mcpServerIds: List<String>? = null,
    /** 此 Agent 绑定的 Skill ID。 */
    val skillIds: List<String>? = null,
    /**
     * 文件读写权限；readwrite 允许 Agent 主动写入记忆（types.ts:62-63）。
     * ⚠️ 没有默认值：未知取值会抛错而不是静默降级成「无权限」——
     * 权限字段猜错的代价比报错大。
     */
    val fileAccess: FileAccess? = null,
)

/** `src/types.ts:67 PromptPreset`（提示词预设）。 */
@Serializable
data class PromptPreset(
    val id: String,
    val title: String,
    val category: String,
    val content: String,
    val description: String? = null,
    val tags: List<String>? = null,
)
