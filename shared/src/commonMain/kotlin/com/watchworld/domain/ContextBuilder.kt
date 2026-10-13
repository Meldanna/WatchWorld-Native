package com.watchworld.domain

import com.watchworld.model.Agent
import com.watchworld.model.AgentSkill
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.ImageContextMode
import com.watchworld.model.KnowledgeItem
import com.watchworld.model.McpServerConfig
import com.watchworld.model.RegexRule
import com.watchworld.model.Role
import com.watchworld.model.TimelineBranch
import com.watchworld.model.WorldDocument

/*
 * 上下文拼装（T08）。规格书：
 *  - `src/lib/api.ts` `sendChatMessage` 的拼装段（:154-373，发请求之前的全部纯逻辑）；
 *  - `src/App.tsx` `buildSystemInstruction`:417、`dropSummarizedFloors`:466，
 *    以及 :801-854 的「本窗口生效的知识库 / 世界观 / MCP / Skill / 正则」选择逻辑。
 *
 * ⚠️ 1:1 直译，**执行顺序、字符串字面量、正则一律不改**（MIGRATION_CONSTRAINTS §4）。
 * ⚠️ system 段落的实际顺序以 **api.ts 源码**为准：
 *    人格/窗口指令 → 世界观正典 → MCP → Skill → 知识库 → 联网结果 → 楼号约定 → extraSystemTail → 时间线树。
 *    （CONSTRAINTS §4.1 / PLAN §6.6 写的是「正典 → 知识库 → MCP → Skill」，与源码不符；源码是规格书。）
 * ⚠️ 联网搜索（T11）与图片读取（T21）不在本文件：联网结果由调用方以 [ContextRequest.webSearchContext]
 *    传入，图片只保留 `imageIds` 引用。
 */

/** api.ts:184 —— 「推入通用」缓存里的来源楼层标记。⚠️ 必须是同一正则。 */
val PUSHED_COMMON_FLOOR_REGEX: Regex = Regex("\\[来自\\s+#(\\d+)\\]")

const val WORLD_CONTEXT_HEADING: String = "\n\n# 【世界观·正典设定】:\n"
const val KNOWLEDGE_CONTEXT_HEADING: String = "\n\n# 【外部参考资料·知识库】:\n"
const val MCP_CONTEXT_HEADING: String = "\n\n# 【已验证可用的 MCP 服务能力】:\n"
const val FLOOR_REFERENCE_PROTOCOL: String =
    "# 【楼号引用约定】\n历史消息中的 #数字是稳定楼号；用户引用“第 N 楼”时，请以 #N 对应消息为准。"

/** `sendChatMessage` 拼装段的入参（对应 `SendChatParams` 里与拼装有关的字段，默认值照抄 TS）。 */
data class ContextRequest(
    val agent: Agent,
    val messages: List<ChatMessage>,
    val systemInstruction: String? = null,
    val aiContextVisibility: AiContextVisibilityFilter = AiContextVisibilityFilter.ALL,
    val aiContextCustomLimit: Int? = 3,
    val connectedKnowledge: List<KnowledgeItem> = emptyList(),
    val activeWorldDocuments: List<WorldDocument> = emptyList(),
    val activeSkills: List<AgentSkill> = emptyList(),
    val activeMcpServers: List<McpServerConfig> = emptyList(),
    val regexRules: List<RegexRule> = emptyList(),
    val extraSystemTail: String = "",
    /**
     * 时间线树提示词的**覆盖**入口（T17）。
     *
     * 默认 `null` = 由 ContextBuilder 内部调用 [buildTimelineTreePromptContext] 生成；
     * 显式给值则直接使用（用于测试或窗口级定制）。
     * ⚠️ 无论哪种来源，活动分支不可见 / 未开启时间线记忆时都会被置空。
     */
    val timelineContextPrompt: String? = null,
    val allTimelines: List<TimelineBranch> = emptyList(),
    val activeTimelineId: String? = null,
    val timelineMemoryEnabled: Boolean = false,
    val imageContextMode: ImageContextMode = ImageContextMode.ONCE,
    /** 本轮联网/Skill 目录搜索得到的段落（T11 产出）；空串 = 本轮未联网。 */
    val webSearchContext: String = "",
)

/** 发给 Provider 的一条历史消息（api.ts:342 的 `{ role, content, imageIds }`）。 */
data class ContextMessage(
    val role: Role,
    val content: String,
    val imageIds: List<String>? = null,
)

data class AssembledContext(
    val systemInstruction: String,
    val messages: List<ContextMessage>,
    /** 防御性过滤后真正可用的 MCP（T09 工具注册必须用同一份，与 system 清单一致）。 */
    val availableMcpServers: List<McpServerConfig>,
    val availableSkills: List<AgentSkill>,
    /** api.ts:283 —— 过滤后最新一条用户消息（trim 后），供 T11 判断是否联网。 */
    val latestUserQuery: String,
)

object ContextBuilder {

    fun build(request: ContextRequest): AssembledContext {
        // 0. 「仅自己可见」的角色关系分析报告不进入模型上下文（T20）——
        //    必须在可见性过滤**之前**剔除，否则它会占掉 latest_custom 的助手消息名额。
        val contextMessages = dropHiddenAnalysisMessages(request.messages)

        // 1. 按 aiContextVisibility 过滤历史
        var filtered = filterByVisibility(contextMessages, request.aiContextVisibility, request.aiContextCustomLimit)

        // 已「推入通用」的楼层正文已在 system 尾部缓存中，不再作为普通历史重复发送。
        // 撤回推入后 extraSystemTail 不再有标记，消息自动恢复 —— 不要「优化」这段。
        val pushedCommonFloors = pushedCommonFloors(request.extraSystemTail)
        if (pushedCommonFloors.isNotEmpty()) {
            filtered = filtered.filter { message ->
                val floor = message.floorNumber
                if (floor == null || floor == 0 || floor !in pushedCommonFloors) return@filter true
                !isCommonMessage(message)
            }
        }

        // 2. 消息重排中间件
        if (request.timelineMemoryEnabled && request.allTimelines.isNotEmpty()) {
            val currentBranch = request.allTimelines.firstOrNull { it.id == request.activeTimelineId }
                ?: request.allTimelines.first()
            filtered = rearrangeMessagesForAi(currentBranch, request.allTimelines, filtered)
        }

        // 3a. 世界观正典
        val worldContext = if (request.activeWorldDocuments.isNotEmpty()) {
            WORLD_CONTEXT_HEADING +
                request.activeWorldDocuments.joinToString("\n\n") { "### ${it.title}\n${it.content}" }
        } else ""

        // 3b. 知识库
        val knowledgeContext = if (request.connectedKnowledge.isNotEmpty()) {
            KNOWLEDGE_CONTEXT_HEADING +
                request.connectedKnowledge.joinToString("\n\n") { "### ${it.title}\n${it.content}" }
        } else ""

        // 4. MCP 能力清单（防御性再过滤一次；用原始工具名）
        val availableMcp = request.activeMcpServers.filter(::isMcpAvailableToModel)
        val availableToolNames = getAvailableMcpToolNames(availableMcp)
        val mcpContext = if (availableMcp.isNotEmpty()) {
            MCP_CONTEXT_HEADING + availableMcp.joinToString("\n") { m ->
                val toolNames = m.tools.map { it.name }.filter { it.isNotEmpty() }
                val toolsLabel = "[${toolNames.joinToString(", ")}]"
                val description = m.description?.ifEmpty { null } ?: "无"
                "- 服务: ${m.name} (${m.type.wireName().uppercase()}) | 工具集: $toolsLabel | 描述: $description"
            }
        } else ""

        // 5. Skill 清单（依赖全部满足才注入）
        val availableSkills = request.activeSkills.filter { isSkillAvailableToModel(it, availableToolNames) }
        val skillContext = if (availableSkills.isNotEmpty()) formatSkillManifest(availableSkills) else ""

        // 6. 合并 system。不可见的当前分支，其时间线提示词不得进入 system。
        //    ⚠️ T17：时间线树提示词现在由本层**内部构建**（`buildTimelineTreePromptContext`），
        //    不再依赖调用方传入。`timelineContextPrompt` 保留为可选的**覆盖**入口
        //    （测试与未来的窗口级定制需要「强制塞一段」时用），默认 `null` = 走内部构建。
        val activeTimeline = request.allTimelines.firstOrNull { it.id == request.activeTimelineId }
        val timelineContextPrompt = when {
            !request.timelineMemoryEnabled -> ""
            activeTimeline == null -> ""
            activeTimeline.visible == false -> ""
            request.timelineContextPrompt != null -> request.timelineContextPrompt
            else -> buildTimelineTreePromptContext(activeTimeline, request.allTimelines, filtered)
        }

        val hasFloorNumbers = filtered.any { it.floorNumber != null }
        val floorReferenceProtocol = if (hasFloorNumbers) FLOOR_REFERENCE_PROTOCOL else ""

        val latestUserQuery = filtered.lastOrNull { it.role == Role.USER }?.content?.trim().orEmpty()

        val fullSystemInstruction = listOf(
            request.systemInstruction?.ifEmpty { null } ?: request.agent.systemPrompt,
            worldContext,
            mcpContext,
            skillContext,
            knowledgeContext,
            request.webSearchContext,
            floorReferenceProtocol,
            request.extraSystemTail,
            timelineContextPrompt,
        ).filter { it.isNotEmpty() }.joinToString("\n\n")

        // 7. 消息格式化：正则（output 方向）→ 时间线开启时加「[标签] #楼号」前缀。
        //    ⚠️ 有意偏离 TS（方案 A）：TS 在时间线模式下重读原文、正则结果被覆盖；
        //    这里把**已过正则的正文**传给 formatMessageWithFloorAndTag，前缀本身不过正则。
        val textFormatted = filtered.map { m ->
            var content = messageContentForAi(m)
            if (request.regexRules.isNotEmpty()) {
                content = applyRegexRules(content, request.regexRules, RegexApplyScope.OUTPUT)
            }
            if (request.timelineMemoryEnabled) {
                content = formatMessageWithFloorAndTag(m, request.allTimelines, content)
            }
            ContextMessage(role = m.role, content = content, imageIds = m.imageIds)
        }

        // 7.1 图片：'once' 只让最后一条消息携带图片
        val lastIndex = textFormatted.lastIndex
        val withKeptImages = textFormatted.mapIndexed { idx, item ->
            if (request.imageContextMode == ImageContextMode.HISTORY || idx == lastIndex) item
            else item.copy(imageIds = null)
        }

        return AssembledContext(
            systemInstruction = fullSystemInstruction,
            messages = withKeptImages,
            availableMcpServers = availableMcp,
            availableSkills = availableSkills,
            latestUserQuery = latestUserQuery,
        )
    }

    /** api.ts:157-176。`latest_custom` 的 `slice(-limit)` 按 JS 语义（0 = 全部保留）。 */
    fun filterByVisibility(
        messages: List<ChatMessage>,
        visibility: AiContextVisibilityFilter,
        customLimit: Int?,
    ): List<ChatMessage> = when (visibility) {
        AiContextVisibilityFilter.ALL -> messages.toList()
        AiContextVisibilityFilter.HIDE_ALL -> messages.filter { it.role != Role.ASSISTANT }
        AiContextVisibilityFilter.LATEST_CUSTOM -> {
            val limit = customLimit ?: 3
            val assistantIndices = messages.indices.filter { messages[it].role == Role.ASSISTANT }
            val allowed = jsSliceNegative(assistantIndices, limit).toSet()
            messages.filterIndexed { idx, m -> m.role == Role.USER || idx in allowed }
        }
    }

    /** extraSystemTail 中所有 `[来自 #N]` 的楼号。 */
    fun pushedCommonFloors(extraSystemTail: String): Set<Int> =
        PUSHED_COMMON_FLOOR_REGEX.findAll(extraSystemTail)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()

    /** api.ts:192-195 三重判定，缺一不可。 */
    fun isCommonMessage(message: ChatMessage): Boolean =
        message.isCommon == true ||
            message.codeTag == COMMON_CODE_TAG ||
            message.timelineId == DEFAULT_MAIN_TIMELINE_ID
}

/** JS `array.slice(-n)`：n>0 取末 n 个；n==0 即 `slice(0)` 全部；n<0 即 `slice(|n|)` 去掉前 |n| 个。 */
private fun <T> jsSliceNegative(list: List<T>, n: Int): List<T> = when {
    n > 0 -> list.takeLast(n)
    n == 0 -> list
    else -> list.drop(-n)
}

/** `m.type.toUpperCase()` 用的是存档字面量（`streamable_http` / `sse`），不是 Kotlin 枚举名。 */
private fun com.watchworld.model.McpTransportType.wireName(): String = when (this) {
    com.watchworld.model.McpTransportType.STREAMABLE_HTTP -> "streamable_http"
    com.watchworld.model.McpTransportType.SSE -> "sse"
}

/* ---------------------------------------------------------------------------------------------
 * App.tsx 侧的拼装辅助（调用 ContextBuilder 之前要准备的入参）。
 * ------------------------------------------------------------------------------------------- */

/** `App.tsx:417 buildSystemInstruction` 的返回值：head 放 system 最前，tail 垫在最后。 */
data class SystemInstructionParts(val head: String, val tail: String)

/** `App.tsx:417 buildSystemInstruction`。⚠️ 段落标题逐字一致。 */
fun buildSystemInstruction(session: ChatSession, agent: Agent?): SystemInstructionParts {
    val head = mutableListOf<String>()
    val persona = agent?.systemPrompt?.trim().orEmpty()
    if (persona.isNotEmpty()) head += "# 【角色人格设定】:\n$persona"

    val windowPrompt = (session.systemPromptFixed?.ifEmpty { null } ?: session.systemPromptOverride.orEmpty()).trim()
    if (windowPrompt.isNotEmpty()) {
        head += "# 【当前窗口指令（优先级高于角色人格，冲突时以此为准）】:\n$windowPrompt"
    }

    val memory = agent?.memory?.trim().orEmpty()
    if (memory.isNotEmpty()) head += "# 【Agent 长期记忆】:\n$memory"

    val tail = mutableListOf<String>()
    val injectedCommon = session.systemPromptInjectedCommon
    if (session.isCommonPushed == true && !injectedCommon.isNullOrEmpty()) {
        tail += "# 【通用世界观基石缓存】:\n$injectedCommon"
    }

    val summaries = session.summaries.orEmpty()
    if (summaries.isNotEmpty()) {
        tail += "# 【前文总结（按时间顺序，越靠后越新）】:\n" +
            summaries.joinToString("\n\n") { "【第 ${it.fromFloor}–${it.toFloor} 楼】\n${it.content}" }
    }

    return SystemInstructionParts(head = head.joinToString("\n\n"), tail = tail.joinToString("\n\n"))
}

/** `App.tsx:466 dropSummarizedFloors`：已总结楼层不再发给 AI；没有楼号的消息保守保留。 */
fun dropSummarizedFloors(messages: List<ChatMessage>, session: ChatSession): List<ChatMessage> {
    val upTo = session.lastSummarizedFloor ?: 0
    if (upTo <= 0) return messages
    return messages.filter { m -> m.floorNumber?.let { it > upTo } ?: true }
}

/** `App.tsx:803`：窗口勾选 ∩ 全局 enabled。 */
fun selectConnectedKnowledge(knowledgeBase: List<KnowledgeItem>, session: ChatSession?): List<KnowledgeItem> {
    val ids = session?.connectedKnowledgeIds.orEmpty()
    return knowledgeBase.filter { it.enabled && it.id in ids }
}

/** `App.tsx:808`：窗口未保存过选择（`null`）时默认包含全部文档。 */
fun selectActiveWorldDocuments(worldDocuments: List<WorldDocument>, session: ChatSession?): List<WorldDocument> {
    val ids = session?.connectedWorldDocIds ?: return worldDocuments.toList()
    return worldDocuments.filter { it.id in ids }
}

/** `App.tsx:821`：只由当前 Agent 的绑定决定，且必须真实可用。 */
fun selectActiveMcpServers(mcpServers: List<McpServerConfig>, agent: Agent?): List<McpServerConfig> {
    val bound = agent?.mcpServerIds.orEmpty()
    if (bound.isEmpty()) return emptyList()
    return mcpServers.filter { it.id in bound && isMcpAvailableToModel(it) }
}

/** `App.tsx:836`：Agent.skillIds 绑定 ∩ 依赖工具全部可用。 */
fun selectActiveSkills(skills: List<AgentSkill>, agent: Agent?, availableToolNames: Set<String>): List<AgentSkill> {
    val ids = agent?.skillIds.orEmpty()
    return skills.filter { it.id in ids && isSkillAvailableToModel(it, availableToolNames) }
}

/** `App.tsx:849`：未配置过 regexRuleIds 的窗口沿用全部已启用规则。 */
fun selectActiveRegexRules(regexRules: List<RegexRule>, session: ChatSession?): List<RegexRule> {
    val enabled = regexRules.filter { it.enabled }
    val ids = session?.regexRuleIds ?: return enabled
    return enabled.filter { it.id in ids }
}
