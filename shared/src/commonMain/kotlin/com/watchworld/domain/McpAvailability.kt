package com.watchworld.domain

import com.watchworld.model.AgentSkill
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus

/*
 * `src/lib/mcpAvailability.ts`（1:1 直译）。
 *
 * ⚠️ 这里决定「哪些 MCP / Skill 会被声明给模型」，ContextBuilder 的 MCP 能力清单与
 *    T09 的工具注册都必须用同一套判定，否则 system 里写的工具和真正注册的工具对不上。
 */

/**
 * 内置的「按需加载 Skill 正文」工具名（`mcpAvailability.ts:8`）。
 * ⚠️ 值必须是 `load_skill`，与注入 system 的 Skill 清单文案一致（MIGRATION_CONSTRAINTS §6）。
 */
const val LOAD_SKILL_TOOL_NAME: String = "load_skill"

/** `mcpAvailability.ts:11`：只有真实测试成功且发现了工具的 MCP，才允许向模型声明为可用能力。 */
fun isMcpAvailableToModel(server: McpServerConfig): Boolean =
    server.status == McpServerStatus.CONNECTED && server.tools.isNotEmpty()

/** `mcpAvailability.ts:15`：可用 MCP 的全部工具名（空名被过滤，对应 TS 的 `.filter(Boolean)`）。 */
fun getAvailableMcpToolNames(servers: List<McpServerConfig>): Set<String> =
    servers
        .filter(::isMcpAvailableToModel)
        .flatMap { server -> server.tools.map { it.name } }
        .filter { it.isNotEmpty() }
        .toSet()

/** `mcpAvailability.ts:24`：Skill 声明依赖、但当前不可用的工具名。 */
fun getMissingSkillTools(skill: AgentSkill, availableToolNames: Set<String>): List<String> =
    (skill.toolsNeeded ?: emptyList()).filter { it !in availableToolNames }

/** `mcpAvailability.ts:31`：依赖工具全部满足才可用（无依赖即可用）。 */
fun isSkillAvailableToModel(skill: AgentSkill, availableToolNames: Set<String>): Boolean =
    getMissingSkillTools(skill, availableToolNames).isEmpty()

/**
 * `mcpAvailability.ts:46`：把 Skill 渲染成「能力清单」注入 system。
 * 只注入名称与用途，正文由模型通过 [LOAD_SKILL_TOOL_NAME] 按需拉取（渐进式披露）。
 *
 * ⚠️ 文案是模型理解上下文的锚点，**逐字一致**，改标点也不行（MIGRATION_CONSTRAINTS §4.3）。
 */
fun formatSkillManifest(skills: List<AgentSkill>): String {
    if (skills.isEmpty()) return ""
    val items = skills.map { skill ->
        val desc = skill.description.trim().ifEmpty { "无描述" }
        "- ${skill.name}：$desc"
    }
    return (
        listOf(
            "# 【可用专业 Skill 能力清单】",
            "以下是本 Agent 已绑定的专业 Skill，属于「可调用的能力」，本轮尚未加载正文。",
            "当用户请求明显属于某个 Skill 的用途时，先调用 $LOAD_SKILL_TOOL_NAME 工具并传入该 Skill 的 name 取得完整执行指引，再据此作答。",
            "若请求与任何 Skill 的用途都无关，则不要调用，按常规直接回答。",
            "若当前环境无法调用工具，则不要假装已加载该 Skill，直接依据本清单与自身能力尽力回答，并说明这一点。",
        ) + items
    ).joinToString("\n")
}
