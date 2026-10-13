package com.watchworld.domain

import com.watchworld.model.Agent
import com.watchworld.model.AgentSkill
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.ImageContextMode
import com.watchworld.model.KnowledgeItem
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.McpTransportType
import com.watchworld.model.MessageVersion
import com.watchworld.model.RegexRule
import com.watchworld.model.RegexScope
import com.watchworld.model.Role
import com.watchworld.model.SessionSummary
import com.watchworld.model.TimelineBranch
import com.watchworld.model.WorldDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T08 验收：ContextBuilder 对照 `api.ts sendChatMessage` 拼装段与 `App.tsx buildSystemInstruction`。
 * 期望字符串全部按 TS 源码手写（不从 Kotlin 常量回读），这样常量被改动时测试会失败。
 */
class ContextBuilderTest {

    private val agent = Agent(id = "agent-1", name = "顾问", systemPrompt = "AGENT_PROMPT")

    private fun user(id: String, content: String, floor: Int? = null, timelineId: String? = null,
                     codeTag: String? = null, isCommon: Boolean? = null, imageIds: List<String>? = null,
                     timestamp: Long = 0L) =
        ChatMessage(id = id, role = Role.USER, content = content, floorNumber = floor, timelineId = timelineId,
            codeTag = codeTag, isCommon = isCommon, imageIds = imageIds, timestamp = timestamp)

    private fun assistant(id: String, content: String, floor: Int? = null, timelineId: String? = null,
                          codeTag: String? = null, isCommon: Boolean? = null) =
        ChatMessage(id = id, role = Role.ASSISTANT, versions = listOf(MessageVersion(content = content)),
            floorNumber = floor, timelineId = timelineId, codeTag = codeTag, isCommon = isCommon)

    private fun connectedMcp(id: String, name: String, vararg tools: String, description: String? = null) =
        McpServerConfig(id = id, name = name, type = McpTransportType.STREAMABLE_HTTP,
            status = McpServerStatus.CONNECTED, tools = tools.map { McpTool(name = it) }, description = description)

    // ---- 验收 1：pushedCommonFloors 同正则 ----

    @Test
    fun pushedCommonFloorsUsesSameRegex() {
        assertEquals("\\[来自\\s+#(\\d+)\\]", PUSHED_COMMON_FLOOR_REGEX.pattern)
        val tail = "缓存 [来自 #3] 与 [来自   #12] 但不是 [来自#5] 也不是 [来自 #x]"
        assertEquals(setOf(3, 12), ContextBuilder.pushedCommonFloors(tail))
    }

    // ---- 验收 2：isCommonMessage 三重判定 ----

    @Test
    fun isCommonMessageHasThreeIndependentConditions() {
        assertTrue(ContextBuilder.isCommonMessage(user("a", "x", isCommon = true)))
        assertTrue(ContextBuilder.isCommonMessage(user("b", "x", codeTag = "通用")))
        assertTrue(ContextBuilder.isCommonMessage(user("c", "x", timelineId = "timeline-main")))
        assertFalse(ContextBuilder.isCommonMessage(user("d", "x", isCommon = false, codeTag = "A1", timelineId = "t-a1")))
    }

    // ---- T20：隐藏的分析报告不进入模型上下文 ----

    @Test
    fun hiddenAnalysisMessagesAreDroppedFromModelContext() {
        val hidden = ChatMessage(
            id = "an1", role = Role.ASSISTANT, floorNumber = 1, isAnalysis = true,
            analysisVisibility = AnalysisVisibility.HIDDEN,
            versions = listOf(MessageVersion(content = "隐藏报告")),
        )
        val visible = ChatMessage(
            id = "an2", role = Role.ASSISTANT, floorNumber = 2, isAnalysis = true,
            analysisVisibility = AnalysisVisibility.VISIBLE,
            versions = listOf(MessageVersion(content = "可见报告")),
        )
        val legacy = ChatMessage(
            id = "an3", role = Role.ASSISTANT, floorNumber = 3, isAnalysis = true,
            versions = listOf(MessageVersion(content = "无可见性字段")),
        )
        val result = ContextBuilder.build(
            ContextRequest(agent = agent, messages = listOf(hidden, visible, legacy)),
        )
        assertEquals(listOf("可见报告", "无可见性字段"), result.messages.map { it.content })
    }

    @Test
    fun hiddenAnalysisDoesNotConsumeLatestCustomAssistantBudget() {
        val messages = listOf(
            user("u1", "问题"),
            ChatMessage(
                id = "hidden", role = Role.ASSISTANT, floorNumber = 2, isAnalysis = true,
                analysisVisibility = AnalysisVisibility.HIDDEN,
                versions = listOf(MessageVersion(content = "隐藏报告")),
            ),
            assistant("a3", "回答", floor = 3),
        )
        val result = ContextBuilder.build(
            ContextRequest(
                agent = agent, messages = messages,
                aiContextVisibility = AiContextVisibilityFilter.LATEST_CUSTOM,
                aiContextCustomLimit = 1,
            ),
        )
        assertEquals(listOf("问题", "回答"), result.messages.map { it.content })
    }

    @Test
    fun pushedCommonFloorsOnlyDropMarkedCommonMessagesAndSelfHealWhenUnpushed() {
        val messages = listOf(
            user("m1", "common-by-flag", floor = 1, isCommon = true),
            user("m2", "common-by-tag", floor = 2, codeTag = "通用"),
            user("m3", "common-by-main", floor = 3, timelineId = "timeline-main"),
            user("m4", "branch-marked", floor = 4, timelineId = "t-a1", codeTag = "A1"),
            user("m5", "common-not-marked", floor = 5, isCommon = true),
        )
        val tail = "[来自 #1] [来自 #2] [来自 #3] [来自 #4]"
        val pushed = ContextBuilder.build(ContextRequest(agent = agent, messages = messages, extraSystemTail = tail))
        assertEquals(listOf("branch-marked", "common-not-marked"), pushed.messages.map { it.content })

        // 撤回推入：tail 不再含标记 → 全部恢复
        val unpushed = ContextBuilder.build(ContextRequest(agent = agent, messages = messages, extraSystemTail = ""))
        assertEquals(5, unpushed.messages.size)
    }

    // ---- 验收 3：system 段落标题逐字一致 + 顺序 ----

    @Test
    fun systemHeadingsAreVerbatim() {
        assertEquals("\n\n# 【世界观·正典设定】:\n", WORLD_CONTEXT_HEADING)
        assertEquals("\n\n# 【外部参考资料·知识库】:\n", KNOWLEDGE_CONTEXT_HEADING)
        assertEquals("\n\n# 【已验证可用的 MCP 服务能力】:\n", MCP_CONTEXT_HEADING)
    }

    @Test
    fun systemSectionsFollowApiTsOrder() {
        val mcp = connectedMcp("mcp-1", "Andunya", "read_file", "list_files", description = "仓库工具")
        val skill = AgentSkill(id = "s1", name = "写作", description = "  帮写  ", toolsNeeded = listOf("read_file"))
        val result = ContextBuilder.build(ContextRequest(
            agent = agent,
            messages = listOf(user("u1", "  你好  ", floor = 1)),
            systemInstruction = "HEAD",
            activeWorldDocuments = listOf(WorldDocument(id = "w1", title = "世界", content = "W")),
            connectedKnowledge = listOf(KnowledgeItem(id = "k1", title = "知识", content = "K", enabled = true)),
            activeMcpServers = listOf(mcp),
            activeSkills = listOf(skill),
            webSearchContext = "WEB",
            extraSystemTail = "TAIL",
        ))
        val expected = listOf(
            "HEAD",
            "\n\n# 【世界观·正典设定】:\n### 世界\nW",
            "\n\n# 【已验证可用的 MCP 服务能力】:\n- 服务: Andunya (STREAMABLE_HTTP) | 工具集: [read_file, list_files] | 描述: 仓库工具",
            listOf(
                "# 【可用专业 Skill 能力清单】",
                "以下是本 Agent 已绑定的专业 Skill，属于「可调用的能力」，本轮尚未加载正文。",
                "当用户请求明显属于某个 Skill 的用途时，先调用 load_skill 工具并传入该 Skill 的 name 取得完整执行指引，再据此作答。",
                "若请求与任何 Skill 的用途都无关，则不要调用，按常规直接回答。",
                "若当前环境无法调用工具，则不要假装已加载该 Skill，直接依据本清单与自身能力尽力回答，并说明这一点。",
                "- 写作：帮写",
            ).joinToString("\n"),
            "\n\n# 【外部参考资料·知识库】:\n### 知识\nK",
            "WEB",
            "# 【楼号引用约定】\n历史消息中的 #数字是稳定楼号；用户引用“第 N 楼”时，请以 #N 对应消息为准。",
            "TAIL",
        ).joinToString("\n\n")
        assertEquals(expected, result.systemInstruction)
        assertEquals("你好", result.latestUserQuery)
        assertEquals(listOf("mcp-1"), result.availableMcpServers.map { it.id })
        assertEquals(listOf("s1"), result.availableSkills.map { it.id })
    }

    @Test
    fun emptySystemInstructionFallsBackToAgentPromptAndUnavailableMcpIsDropped() {
        val disconnected = connectedMcp("mcp-x", "X", "t").copy(status = McpServerStatus.DISCONNECTED)
        val noTools = connectedMcp("mcp-y", "Y")
        val skill = AgentSkill(id = "s", name = "需要工具", toolsNeeded = listOf("t"))
        val result = ContextBuilder.build(ContextRequest(
            agent = agent, messages = listOf(user("u", "hi")), systemInstruction = "",
            activeMcpServers = listOf(disconnected, noTools), activeSkills = listOf(skill),
        ))
        assertEquals("AGENT_PROMPT", result.systemInstruction)
        assertTrue(result.availableMcpServers.isEmpty())
        assertTrue(result.availableSkills.isEmpty())
    }

    @Test
    fun mcpWithoutDescriptionUsesPlaceholderAndSseUppercased() {
        val sse = connectedMcp("m", "S", "a").copy(type = McpTransportType.SSE)
        val result = ContextBuilder.build(ContextRequest(agent = agent, messages = emptyList(), systemInstruction = "H",
            activeMcpServers = listOf(sse)))
        assertTrue(result.systemInstruction.endsWith("- 服务: S (SSE) | 工具集: [a] | 描述: 无"))
    }

    // ---- 可见性过滤 ----

    @Test
    fun visibilityFiltersMatchApiTs() {
        val msgs = listOf(user("u1", "1"), assistant("a1", "A1"), user("u2", "2"), assistant("a2", "A2"),
            user("u3", "3"), assistant("a3", "A3"))
        assertEquals(6, ContextBuilder.filterByVisibility(msgs, AiContextVisibilityFilter.ALL, 3).size)
        assertEquals(listOf("u1", "u2", "u3"),
            ContextBuilder.filterByVisibility(msgs, AiContextVisibilityFilter.HIDE_ALL, 3).map { it.id })
        assertEquals(listOf("u1", "u2", "a2", "u3", "a3"),
            ContextBuilder.filterByVisibility(msgs, AiContextVisibilityFilter.LATEST_CUSTOM, 2).map { it.id })
        // null → 默认 3；0 → JS slice(-0) = 全部
        assertEquals(6, ContextBuilder.filterByVisibility(msgs, AiContextVisibilityFilter.LATEST_CUSTOM, null).size)
        assertEquals(6, ContextBuilder.filterByVisibility(msgs, AiContextVisibilityFilter.LATEST_CUSTOM, 0).size)
    }

    // ---- 消息格式化：正文取值、正则、时间线标签、图片 ----

    @Test
    fun assistantContentUsesCurrentVersionThenFallsBack() {
        val multi = ChatMessage(id = "a", role = Role.ASSISTANT, content = "TOP",
            versions = listOf(MessageVersion(content = "v0"), MessageVersion(content = "v1")), currentVersionIndex = 1)
        val outOfRange = multi.copy(currentVersionIndex = 9)
        val emptyVersion = multi.copy(versions = listOf(MessageVersion(content = "")), currentVersionIndex = 0)
        val noVersions = multi.copy(versions = emptyList())
        val r = ContextBuilder.build(ContextRequest(agent = agent, messages = listOf(multi, outOfRange, emptyVersion, noVersions)))
        assertEquals(listOf("v1", "v0", "TOP", "TOP"), r.messages.map { it.content })
    }

    @Test
    fun regexRulesApplyOnOutputScopeWithJsSemantics() {
        val rules = listOf(
            RegexRule(id = "r1", pattern = "<总结>.*?</总结>", flags = "", replacement = "", scope = RegexScope.OUTPUT, enabled = true),
            RegexRule(id = "r2", pattern = "(猫)", flags = "g", replacement = "[$1|$&|$$]", scope = RegexScope.BOTH, enabled = true),
            RegexRule(id = "r3", pattern = "x", flags = "g", replacement = "Y", scope = RegexScope.INPUT, enabled = true),
            RegexRule(id = "r4", pattern = "(", flags = "g", replacement = "", scope = RegexScope.BOTH, enabled = true),
            RegexRule(id = "r5", pattern = "a", flags = "i", replacement = "b", scope = RegexScope.BOTH, enabled = true),
            RegexRule(id = "r6", pattern = "猫", flags = "g", replacement = "狗", scope = RegexScope.BOTH, enabled = false),
        )
        val r = ContextBuilder.build(ContextRequest(agent = agent, regexRules = rules,
            messages = listOf(assistant("a", "<总结>s</总结>猫x猫AA"))))
        assertEquals("[猫|猫|$]x[猫|猫|$]bA", r.messages.single().content)
    }

    @Test
    fun timelineMemoryFormatsWithTagAndFloor() {
        val timelines = listOf(
            TimelineBranch(id = "timeline-main", name = "通用世界观", tag = "通用", codeTag = "通用", descriptionTag = "通用世界观"),
            TimelineBranch(id = "t-a1", name = "学园线", tag = "A1", codeTag = "A1", descriptionTag = "学园线", parentId = "timeline-main"),
        )
        val msgs = listOf(
            user("u1", "开场", floor = 1, timelineId = "timeline-main"),
            user("u2", "上学", floor = 2, timelineId = "t-a1"),
            ChatMessage(id = "u3", role = Role.USER, content = "自带标签", floorNumber = 3, codeTag = "B1", descriptionTag = "战役"),
        )
        val r = ContextBuilder.build(ContextRequest(agent = agent, messages = msgs, timelineMemoryEnabled = true,
            allTimelines = timelines, activeTimelineId = "timeline-main"))
        // 当前分支（main）排最后；u3 无 timelineId → 视为通用、深度 0
        assertEquals(listOf("[B1·战役] #3 自带标签", "[A1·学园线] #2 上学", "[通用·通用世界观] #1 开场"),
            r.messages.map { it.content })
    }

    @Test
    fun timelineMemoryKeepsRegexResultAndLeavesTagPrefixUntouched() {
        // 方案 A：时间线模式下正文先过正则再加前缀；前缀「学园线」不被规则 学园→Z 改写。
        val timelines = listOf(
            TimelineBranch(id = "timeline-main", name = "通用世界观", tag = "通用", codeTag = "通用", descriptionTag = "通用世界观"),
            TimelineBranch(id = "t-a1", name = "学园线", tag = "A1", codeTag = "A1", descriptionTag = "学园线", parentId = "timeline-main"),
        )
        val rules = listOf(
            RegexRule(id = "r1", pattern = "猫", flags = "g", replacement = "狗", scope = RegexScope.BOTH, enabled = true),
            RegexRule(id = "r2", pattern = "学园", flags = "g", replacement = "Z", scope = RegexScope.OUTPUT, enabled = true),
        )
        val r = ContextBuilder.build(ContextRequest(agent = agent, regexRules = rules, timelineMemoryEnabled = true,
            allTimelines = timelines, activeTimelineId = "t-a1",
            messages = listOf(user("u2", "猫在学园", floor = 2, timelineId = "t-a1"))))
        assertEquals("[A1·学园线] #2 狗在Z", r.messages.single().content)
    }

    @Test
    fun rearrangeHidesInvisibleBranchesAndOrdersByDepth() {
        val timelines = listOf(
            TimelineBranch(id = "timeline-main", codeTag = "通用"),
            TimelineBranch(id = "a1", codeTag = "A1", parentId = "timeline-main"),
            TimelineBranch(id = "a11", codeTag = "A11", parentId = "a1"),
            TimelineBranch(id = "b1", codeTag = "B1", parentId = "timeline-main", visible = false),
        )
        val msgs = listOf(
            user("deep", "d", floor = 1, timelineId = "a11"),
            user("hidden", "h", floor = 2, timelineId = "b1"),
            user("shallow", "s", floor = 5, timelineId = "a1"),
            user("main", "m", floor = 9, timelineId = "timeline-main"),
            user("current", "c", floor = 0, timelineId = "a1"),
        )
        val out = rearrangeMessagesForAi(timelines[1], timelines, msgs)
        assertEquals(listOf("main", "deep", "current", "shallow"), out.map { it.id })
    }

    @Test
    fun invisibleActiveTimelineSuppressesTimelinePrompt() {
        val timelines = listOf(TimelineBranch(id = "t", codeTag = "A1", visible = false))
        val base = ContextRequest(agent = agent, messages = emptyList(), systemInstruction = "H",
            timelineContextPrompt = "TREE", allTimelines = timelines, activeTimelineId = "t", timelineMemoryEnabled = true)
        assertEquals("H", ContextBuilder.build(base).systemInstruction)
        assertEquals("H\n\nTREE", ContextBuilder.build(base.copy(allTimelines = listOf(timelines[0].copy(visible = true)))).systemInstruction)
        assertEquals("H", ContextBuilder.build(base.copy(timelineMemoryEnabled = false)).systemInstruction)
    }

    /**
     * T17：不传 `timelineContextPrompt` 时，ContextBuilder 内部调用 `buildTimelineTreePromptContext`
     * 生成时间线树提示词（供 ChatViewModel 这类真实调用方使用）。
     */
    @Test
    fun timelinePromptIsBuiltInternallyWhenNotOverridden() {
        val active = TimelineBranch(id = "a1", name = "学园线", tag = "A1", codeTag = "A1",
            descriptionTag = "学园线", description = "校园", plotSummary = "已开学", color = "indigo",
            messageIds = emptyList())
        val built = ContextBuilder.build(ContextRequest(agent = agent, messages = emptyList(),
            systemInstruction = "H", allTimelines = listOf(active), activeTimelineId = "a1",
            timelineMemoryEnabled = true))
        assertTrue(built.systemInstruction.startsWith("H\n\n\n# 【时间线记忆树 · 分支重组上下文】\n"))
        assertTrue(built.systemInstruction.contains("- 当前激活分支：【A1 · 学园线】"))
        assertTrue(built.systemInstruction.contains("- 本时间线因果剧情沉淀：\n  已开学"))

        // 关闭时间线记忆 → 不注入
        assertEquals("H", ContextBuilder.build(ContextRequest(agent = agent, messages = emptyList(),
            systemInstruction = "H", allTimelines = listOf(active), activeTimelineId = "a1",
            timelineMemoryEnabled = false)).systemInstruction)
    }

    @Test
    fun imageOnceModeKeepsOnlyLastMessageImages() {
        val msgs = listOf(user("u1", "a", imageIds = listOf("img1")), user("u2", "b", imageIds = listOf("img2")))
        val once = ContextBuilder.build(ContextRequest(agent = agent, messages = msgs))
        assertNull(once.messages[0].imageIds)
        assertEquals(listOf("img2"), once.messages[1].imageIds)
        val history = ContextBuilder.build(ContextRequest(agent = agent, messages = msgs, imageContextMode = ImageContextMode.HISTORY))
        assertEquals(listOf("img1"), history.messages[0].imageIds)
    }

    // ---- App.tsx 侧辅助 ----

    @Test
    fun buildSystemInstructionHeadAndTailAreVerbatim() {
        val a = agent.copy(systemPrompt = "  人格  ", memory = " 记忆 ")
        val session = ChatSession(id = "s", systemPromptFixed = "", systemPromptOverride = " 窗口 ",
            isCommonPushed = true, systemPromptInjectedCommon = "通用缓存",
            summaries = listOf(SessionSummary(id = "x", content = "S1", fromFloor = 1, toFloor = 4),
                SessionSummary(id = "y", content = "S2", fromFloor = 5, toFloor = 8)))
        val parts = buildSystemInstruction(session, a)
        assertEquals(
            "# 【角色人格设定】:\n人格\n\n# 【当前窗口指令（优先级高于角色人格，冲突时以此为准）】:\n窗口\n\n# 【Agent 长期记忆】:\n记忆",
            parts.head)
        assertEquals(
            "# 【通用世界观基石缓存】:\n通用缓存\n\n# 【前文总结（按时间顺序，越靠后越新）】:\n【第 1–4 楼】\nS1\n\n【第 5–8 楼】\nS2",
            parts.tail)
        assertEquals(SystemInstructionParts("", ""), buildSystemInstruction(ChatSession(id = "e"), null))
    }

    @Test
    fun dropSummarizedFloorsKeepsUnnumberedMessages() {
        val msgs = listOf(user("a", "1", floor = 1), user("b", "2", floor = 3), user("c", "none"))
        assertEquals(listOf("b", "c"), dropSummarizedFloors(msgs, ChatSession(id = "s", lastSummarizedFloor = 2)).map { it.id })
        assertEquals(3, dropSummarizedFloors(msgs, ChatSession(id = "s")).size)
    }

    @Test
    fun selectionHelpersMatchAppTsx() {
        val kb = listOf(KnowledgeItem(id = "k1", enabled = true), KnowledgeItem(id = "k2", enabled = false), KnowledgeItem(id = "k3", enabled = true))
        assertEquals(listOf("k1"), selectConnectedKnowledge(kb, ChatSession(id = "s", connectedKnowledgeIds = listOf("k1", "k2"))).map { it.id })

        val docs = listOf(WorldDocument(id = "w1"), WorldDocument(id = "w2"))
        assertEquals(2, selectActiveWorldDocuments(docs, ChatSession(id = "s")).size)
        assertEquals(listOf("w2"), selectActiveWorldDocuments(docs, ChatSession(id = "s", connectedWorldDocIds = listOf("w2"))).map { it.id })

        val rules = listOf(RegexRule(id = "r1", enabled = true), RegexRule(id = "r2", enabled = false), RegexRule(id = "r3", enabled = true))
        assertEquals(listOf("r1", "r3"), selectActiveRegexRules(rules, ChatSession(id = "s")).map { it.id })
        assertEquals(listOf("r3"), selectActiveRegexRules(rules, ChatSession(id = "s", regexRuleIds = listOf("r2", "r3"))).map { it.id })

        val servers = listOf(connectedMcp("m1", "A", "t1"), connectedMcp("m2", "B"), connectedMcp("m3", "C", "t3"))
        val bound = agent.copy(mcpServerIds = listOf("m1", "m2"), skillIds = listOf("s1", "s2"))
        val active = selectActiveMcpServers(servers, bound)
        assertEquals(listOf("m1"), active.map { it.id })
        assertTrue(selectActiveMcpServers(servers, agent).isEmpty())

        val skills = listOf(AgentSkill(id = "s1", toolsNeeded = listOf("t1")), AgentSkill(id = "s2", toolsNeeded = listOf("t3")),
            AgentSkill(id = "s3"))
        assertEquals(listOf("s1"), selectActiveSkills(skills, bound, getAvailableMcpToolNames(active)).map { it.id })
    }
}
