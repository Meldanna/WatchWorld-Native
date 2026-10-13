package com.watchworld.data

import com.watchworld.data.migration.normalizeArchive
import com.watchworld.data.migration.normalizeChatSession
import com.watchworld.data.migration.toolCallStepLimitFrom
import com.watchworld.data.repository.ArchiveRepository
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ArchiveV5
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.ImageContextMode
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.McpTransportType
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import com.watchworld.model.SessionSummary
import com.watchworld.model.TagDisplayMode
import com.watchworld.model.TimelineBranch
import com.watchworld.model.TimelineDescriptionTagRequest
import com.watchworld.model.WebDavConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T06 验收：v5 存档「导入 → 内存 → 导出」的往返，以及 `normalizeChatSession` 的六条兼容逻辑。
 *
 * 样本：`fixtures/sse/archive-v5-{legacy,minimal}.json`（脱敏后的**真实**导出）。
 *
 * ⚠️ 断言里凡涉及「归一化之后长什么样」的，都是**照 Web 版行为**写的，不是照直觉：
 * `exportAllData` 走的是 `getSessions()`，所以 Web 版导出的存档本身就是归一化过的
 * （`webSearchEnabled` 已被拍成 `false`、`timelineDescriptionTagRequests` 已补成 `[]`）。
 */
class ArchiveRoundTripTest {

    /** 固定时间戳：MCP 补 `id` 时会用到，写死才能让断言可复现。 */
    private companion object {
        const val FIXED_NOW = 1_800_000_000_000L

        fun fixtureText(name: String): String {
            val start = File(System.getProperty("user.dir"))
            val file = generateSequence(start) { it.parentFile }
                .map { File(it, "fixtures/sse/$name") }
                .firstOrNull { it.isFile }
                ?: error("Fixture not found from ${start.absolutePath}: fixtures/sse/$name")
            return file.readText()
        }

        /** 比较 JSON 结构时用：忽略缩进与空格，避免断言被 prettyPrint 的排版绑死。 */
        fun compact(text: String): String = text.filterNot(Char::isWhitespace)
    }

    private fun parseFixture(name: String) =
        ArchiveRepository.parseArchive(fixtureText(name), now = FIXED_NOW)

    // ---------------------------------------------------------------- 六条兼容逻辑

    /**
     * 第 ②–⑥ 条：legacy 存档**一个字段都没有**，归一化后必须全部落到既定值上。
     *
     * 第 ① 条（删 `connectedSkillIds`）不在这里 —— 那条由「模型不声明该字段 + 解码忽略」
     * 实现，见 [legacyConnectedSkillIdsNeverSurvivesIntoTheModel]。
     */
    @Test
    fun legacySessionGetsAllCompatibilityRulesApplied() {
        val session = parseFixture("archive-v5-legacy.json").sessions.single()

        // ② 内置产品指南已迁出知识库。原值是 ["kb-quick-notes"]，必须被剔除干净。
        assertTrue(
            session.connectedKnowledgeIds.isEmpty(),
            "kb-quick-notes 必须被剔除，否则老用户会看到幽灵知识库条目",
        )

        // ③ 老存档没有这个字段 → 空队列（是 []，不是 null）
        val requests = assertNotNull(session.timelineDescriptionTagRequests, "第 ③ 条要求补成空队列而不是留 null")
        assertTrue(requests.isEmpty())

        // ④ 老存档没有这个字段 → null（继承全局）
        assertNull(session.toolCallStepLimit)

        // ⑤ 非严格 true → false（老存档没有这个字段，等价于未授权）
        assertEquals(false, session.webSearchEnabled)

        // ⑥ 三态：字段缺失 → null（= 视为流式），绝不能拍成 false
        assertNull(
            session.streamingEnabled,
            "老存档缺这个字段 → 必须保持 null；拍成 false 会让所有老窗口突然不流式",
        )
    }

    /** 第 ① 条：遗留字段 `connectedSkillIds` 不得以任何形式活到模型或导出里。 */
    @Test
    fun legacyConnectedSkillIdsNeverSurvivesIntoTheModel() {
        val archive = parseFixture("archive-v5-legacy.json")

        // 原存档里确实有它（两个 skill id），否则这条测试测了个寂寞
        assertTrue(
            fixtureText("archive-v5-legacy.json").contains("connectedSkillIds"),
            "legacy fixture 应当含该遗留字段，否则本用例失去意义",
        )
        assertTrue(
            !compact(ArchiveRepository.exportArchive(archive, exportTime = 0L)).contains("connectedSkillIds"),
            "遗留字段不得被回写到导出里",
        )
    }

    /**
     * 第 ④ 条的判据逐项对照 `storage.ts:91-94`：
     * `typeof n === 'number' && Number.isFinite(n) ? Math.max(0, Math.floor(n)) : undefined`。
     */
    @Test
    fun toolCallStepLimitMatchesTheTypeScriptPredicate() {
        // typeof === 'number' 不成立
        assertNull(toolCallStepLimitFrom(null), "字段缺失 → 继承全局")
        assertNull(toolCallStepLimitFrom(kotlinx.serialization.json.JsonNull), "JSON null → 继承全局")
        assertNull(toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive("5")), "字符串不算数字")
        assertNull(toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(true)), "布尔不算数字")

        // Number.isFinite 不成立
        assertNull(toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(Double.NaN)))
        assertNull(toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(Double.POSITIVE_INFINITY)))

        // Math.max(0, Math.floor(n))
        assertEquals(0, toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(-1)), "老存档的 -1 必须归一到 0")
        assertEquals(0, toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(0)))
        assertEquals(2, toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(2.7)))
        assertEquals(7, toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(7)))

        // 越界：TS 的 number 没有上限，Kotlin 的 Int 有。钳住比溢出成负数安全。
        assertEquals(Int.MAX_VALUE, toolCallStepLimitFrom(kotlinx.serialization.json.JsonPrimitive(1e30)))
    }

    /**
     * 归一化必须**幂等**。这不是洁癖：`exportArchive` 会对已归一化的会话再跑一遍，
     * 若不幂等，合法的 `toolCallStepLimit = 5` 会在导出时被抹成 `null`。
     */
    @Test
    fun normalizeChatSessionIsIdempotent() {
        val original = ChatSession(id = "s1", toolCallStepLimit = 5, webSearchEnabled = true)

        val once = normalizeChatSession(original)
        val twice = normalizeChatSession(once)

        assertEquals(once, twice)
        assertEquals(5, twice.toolCallStepLimit, "重复归一化不得抹掉合法的轮数上限")
    }

    // ---------------------------------------------------------------- 容错（老存档的类型错配）

    /**
     * 老存档里这些字段可能是任意类型。TS 用 `Array.isArray` / `typeof` 兜，Kotlin 的解码器
     * **类型不符直接抛** —— 一个字段类型不对就整份存档导入失败。这条守住「不许抛」。
     */
    @Test
    fun malformedFieldTypesAreCoercedInsteadOfThrowing() {
        val archive = ArchiveRepository.parseArchive(
            """
            {
              "version": 5,
              "sessions": [{
                "id": "s1",
                "connectedSkillIds": ["skill-legacy"],
                "connectedKnowledgeIds": "not-an-array",
                "timelineDescriptionTagRequests": 42,
                "toolCallStepLimit": "7",
                "webSearchEnabled": "yes",
                "streamingEnabled": "maybe"
              }]
            }
            """.trimIndent(),
            now = FIXED_NOW,
        )

        val session = archive.sessions.single()
        assertTrue(session.connectedKnowledgeIds.isEmpty(), "非数组 → 空列表")
        assertTrue(assertNotNull(session.timelineDescriptionTagRequests).isEmpty(), "非数组 → 空队列")
        assertNull(session.toolCallStepLimit, "字符串形式的数字不算数字")
        assertEquals(false, session.webSearchEnabled, "非严格 true → false")
        assertNull(session.streamingEnabled, "非 boolean → null（视为流式）")
    }

    /** 合法但「需要归一」的数值要真的被归一，而不是被一并丢掉。 */
    @Test
    fun legalToolCallStepLimitValuesSurviveNormalization() {
        val archive = ArchiveRepository.parseArchive(
            """
            {
              "version": 5,
              "sessions": [
                {"id": "neg", "toolCallStepLimit": -1},
                {"id": "frac", "toolCallStepLimit": 2.7},
                {"id": "zero", "toolCallStepLimit": 0}
              ]
            }
            """.trimIndent(),
            now = FIXED_NOW,
        )

        val byId = archive.sessions.associateBy { it.id }
        assertEquals(0, byId.getValue("neg").toolCallStepLimit)
        assertEquals(2, byId.getValue("frac").toolCallStepLimit)
        assertEquals(0, byId.getValue("zero").toolCallStepLimit, "0 = 无限制，是合法值，不得变成 null")
    }

    // ---------------------------------------------------------------- 往返

    /**
     * 核心验收：`v5 JSON → 内存 → v5 JSON`，两份真实样本都要能**完整往返**。
     *
     * 比对的是**模型**而不是原始文本：`explicitNulls = false` / `encodeDefaults = false`
     * 本来就会改写形状（省略默认值、补上归一化后的 `false` / `[]`），逐字节比对没有意义，
     * 而且 Web 版自己也不是逐字节稳定的。
     */
    @Test
    fun archiveSurvivesFullRoundTrip() {
        for (name in listOf("archive-v5-legacy.json", "archive-v5-minimal.json", "archive-v5-rich.json")) {
            val first = parseFixture(name)
            val exported = ArchiveRepository.exportArchive(first, exportTime = first.exportTime)
            val second = ArchiveRepository.parseArchive(exported, now = FIXED_NOW)

            assertEquals(first, second, "$name 往返后模型应完全一致")
        }
    }

    /** 往返之后，消息正文「按角色不对称」的存放位置不得被"顺手修正"。 */
    @Test
    fun messageBodyAsymmetrySurvivesRoundTrip() {
        val first = parseFixture("archive-v5-legacy.json")
        val second = ArchiveRepository.parseArchive(
            ArchiveRepository.exportArchive(first, exportTime = first.exportTime),
            now = FIXED_NOW,
        )

        val session = second.sessions.single()
        assertEquals(5, session.messages.size)

        val user = session.messages.first { it.role == Role.USER }
        assertTrue(user.versions.isEmpty(), "user 消息的 versions 必须还是空数组")
        assertTrue(user.content.isNotBlank(), "user 消息正文必须还在顶层 content")

        val assistant = session.messages.first { it.role == Role.ASSISTANT }
        assertTrue(assistant.versions.first().content.isNotBlank(), "assistant 正文必须在 versions[0]")
    }

    /**
     * **真实富样本** `archive-v5-rich.json`（2026-10-06 的实际导出，脱敏后入库）。
     *
     * 它是三份样本里唯一有**真实历史**的：2 个会话 / 31 条消息 / 28 个版本，覆盖了 legacy 与 minimal
     * 都没有的一整批字段。这里断言的是**只有真实数据才会暴露**的几件事：
     *
     * 1. **滚动重答留下的多版本消息**（7 条）。最长的有 4 个版本、`currentVersionIndex = 3`；
     *    其中一条的 **`versions[0].content` 是空串**（首次生成没出正文）。
     *    → 「取 `versions[0]` 当正文」和「假设 `currentVersionIndex` 总是 0」两种写法都会错。
     * 2. **顶层的 `latencyMs`/`tokens` 镜像的是「当前版本」**，不是 `versions[0]`。
     *    legacy 的单版本消息里两者恰好相等，所以从那份样本里看不出来。
     * 3. **两个会话的 `streamingEnabled` 一个显式 `false`、一个显式 `true`** ——
     *    三态（`null`/`true`/`false`）里的两个非空值都有真实样本钉住。
     * 4. **思维链 `versions[].reasoning`**（9 个版本有）。
     */
    @Test
    fun richArchivePinsRealMultiVersionMessages() {
        val archive = parseFixture("archive-v5-rich.json")

        // 当前格式的完整顶层（legacy 只有 16 个字段）
        assertNotNull(archive.appPreferences)
        assertTrue(archive.worldGroups.isNotEmpty())
        assertEquals("mint", archive.theme, "主题是非默认 id")

        // ① 两个会话，窗口级开关一关一开 → 三态里的两个非空值都有真实样本
        assertEquals(2, archive.sessions.size)
        val streamingFlags = archive.sessions.map { it.streamingEnabled }.toSet()
        assertEquals(
            setOf(true, false),
            streamingFlags,
            "两个会话分别是显式 true / 显式 false；缺一个就说明样本没覆盖到那一条路径",
        )

        val session = archive.sessions.first { it.id == "session-welcome" }
        assertEquals(29, session.messages.size)
        assertEquals(TagDisplayMode.DESC, session.tagDisplayMode)

        // ② 多版本：7 条，最长 4 版
        val regenerated = session.messages.filter { it.versions.size > 1 }
        assertEquals(7, regenerated.size)
        assertEquals(4, regenerated.maxOf { it.versions.size })
        assertTrue(
            regenerated.any { it.currentVersionIndex == 3 },
            "当前版本可以停在最后一版（curIdx = 3 / 共 4 版）",
        )

        // ③ 有一条消息的第一版正文是空的 —— 不许假设 versions[0] 有内容
        assertEquals(
            1,
            regenerated.count { it.versions[0].content.isEmpty() },
            "真实数据里确实存在 versions[0].content 为空的滚动重答消息",
        )

        // ④ 顶层 latency/tokens 镜像当前版本，不是 versions[0]
        for (message in regenerated) {
            val current = message.versions[message.currentVersionIndex]
            assertEquals(current.latencyMs, message.latencyMs, "顶层 latencyMs 应等于当前版本的")
        }

        // ⑤ 思维链
        assertTrue(
            session.messages.sumOf { message -> message.versions.count { it.reasoning != null } } > 0,
            "真实样本里有带思维链的版本（storage 里 reasoning 只用于展示，不进后续上下文）",
        )

        // ⑥ 按角色不对称在富样本里同样成立
        assertTrue(
            session.messages.filter { it.role == Role.USER }.all { it.versions.isEmpty() },
            "user 消息的 versions 依旧是空数组",
        )

        // ⑦ 窗口级绑定与前文总结：这两项 legacy/minimal 都没有
        assertEquals(3, assertNotNull(session.connectedWorldDocIds).size)
        assertEquals(2, assertNotNull(session.regexRuleIds).size)
        assertEquals(1, assertNotNull(session.summaries).size)
        assertEquals(29, session.lastSummarizedFloor, "已总结到第 29 楼")
        assertEquals(1, assertNotNull(session.timelineDescriptionTagRequests).size, "留了一条待审批的标签变更")
    }

    /** 顶层结构的红线：单复数、版本号、以及「归一化会补出 false / []」这一 Web 版行为。 */
    @Test
    fun exportedArchiveKeepsTheV5TopLevelShape() {
        val text = compact(ArchiveRepository.exportArchive(parseFixture("archive-v5-legacy.json"), exportTime = 0L))

        assertTrue(text.contains("\"version\":5"), "版本号必须写 5")
        assertTrue(text.contains("\"theme\":\"emerald\""), "theme 是单数，值是主题 id 字符串")
        assertTrue(!text.contains("\"themes\""), "不得写成 themes")
        assertTrue(text.contains("\"worldDocuments\":"), "worldDocuments 是复数")
        assertTrue(text.contains("\"worldGroups\":"))
        assertTrue(!text.contains("\"worldDocument\":"), "不得写成单数 worldDocument")

        // 归一化的可见后果（与 Web 版一致，不是 bug）：老存档缺这两个字段，
        // 归一化后 webSearchEnabled 被拍成 false、待审批队列被补成空数组。
        assertTrue(text.contains("\"webSearchEnabled\":false"))
        assertTrue(text.contains("\"timelineDescriptionTagRequests\":[]"))
        // 三态红线：缺字段的流式开关不得在往返中被写成 false
        assertTrue(!text.contains("\"streamingEnabled\":false"))
    }

    /** `exportArchive` 无条件盖上版本号与导出时间（Web 版也是硬编码 `version: 5`）。 */
    @Test
    fun exportStampsVersionAndExportTime() {
        val text = ArchiveRepository.exportArchive(ArchiveV5(version = 4, exportTime = 111L), exportTime = 999L)
        val reparsed = ArchiveRepository.parseArchive(text, now = FIXED_NOW)

        assertEquals(5, reparsed.version, "version 不读旧值，无条件写 5")
        assertEquals(999L, reparsed.exportTime)
    }

    // ---------------------------------------------------------------- 密钥擦除

    /** `redactSecrets` 是**空字符串**，不是 `null`，也不是删掉这个键（`storage.ts:439-448`）。 */
    @Test
    fun redactSecretsBlanksTheKeysWithEmptyString() {
        val archive = ArchiveV5(
            providers = listOf(ApiProviderConfig(id = "p1", name = "中转站", apiKey = "sk-secret-value")),
            webdavConfig = WebDavConfig(url = "https://dav.example.com", password = "app-password"),
        )

        val redacted = ArchiveRepository.exportArchive(archive, redactSecrets = true, exportTime = 0L)
        assertTrue(!redacted.contains("sk-secret-value"), "密钥不得出现在远端备份里")
        assertTrue(!redacted.contains("app-password"))

        val compacted = compact(redacted)
        assertTrue(compacted.contains("\"apiKey\":\"\""), "必须是空串，不是 null、也不是整个键消失")
        assertTrue(compacted.contains("\"password\":\"\""))

        val reparsed = ArchiveRepository.parseArchive(redacted, now = FIXED_NOW)
        assertEquals("", reparsed.providers.single().apiKey)
        assertEquals("", reparsed.webdavConfig?.password)
    }

    /** 默认导出**不**擦密钥 —— 本地导出要能直接恢复完整配置。 */
    @Test
    fun defaultExportKeepsSecrets() {
        val archive = ArchiveV5(
            providers = listOf(ApiProviderConfig(id = "p1", name = "中转站", apiKey = "sk-secret-value")),
            webdavConfig = WebDavConfig(password = "app-password"),
        )

        val text = ArchiveRepository.exportArchive(archive, exportTime = 0L)

        assertEquals("sk-secret-value", ArchiveRepository.parseArchive(text, now = FIXED_NOW).providers.single().apiKey)
        assertTrue(text.contains("app-password"))
    }

    // ---------------------------------------------------------------- MCP 老配置形态迁移

    /**
     * 旧版 MCP 配置用的是 `endpoint` + `type: "custom_api"`。这一层**必须在 JSON 层做**：
     * 解码成模型时 `custom_api` 会被 `coerceInputValues` 悄悄落成默认的 `streamable_http`，
     * `endpoint` 更是直接丢失 —— 那两条 SSE 服务的地址就再也找不回来了。
     */
    @Test
    fun legacyMcpServersAreMigratedFromEndpointToUrl() {
        val servers = parseFixture("archive-v5-legacy.json").mcpServers

        assertEquals(2, servers.size, "custom_api / stdio 类型的老配置应被丢弃（Web 版同样是 return null）")
        assertTrue(servers.none { it.id == "mcp-database" }, "custom_api 那条必须消失")

        val filesystem = servers.first { it.id == "mcp-filesystem" }
        assertEquals(McpTransportType.SSE, filesystem.type, "type 是 sse 的要保留 sse")
        assertEquals("http://localhost:3001/sse", filesystem.url, "endpoint 必须迁移成 url，否则这条配置等于废了")
        assertEquals(McpServerStatus.CONNECTED, filesystem.status)
        assertEquals(2, filesystem.tools.size)

        val webSearch = servers.first { it.id == "mcp-web-search" }
        assertEquals("http://localhost:3003/sse", webSearch.url)
    }

    // ---------------------------------------------------------------- 真实样本覆盖不到的字段

    /**
     * 用**合成**样本把两份 fixture 覆盖不到的字段也钉一遍往返。
     *
     * `archive-v5-legacy.json` 是旧版真实导出，它没有：多版本消息（`currentVersionIndex > 0`）、
     * 前文总结、分析报告标记、窗口绑定的世界书/正则、图片引用、子分支（`parentId`）、
     * 非空的待审批队列、以及**显式**的 `streamingEnabled`。
     * `archive-v5-rich.json`（T06 原本的硬前置）仍未拿到，见任务文档「遗留问题 / 下一步」。
     *
     * ⚠️ 合成样本只能证明「序列化是对称的」，**不能替代**真实样本 —— 真实样本才能暴露
     * 「字段在 Web 版里其实长这样」这类认知错误。
     *
     * 断言的是 `parse(export(x)) == normalizeArchive(x)` 而非 `== x`：导出本来就会跑归一化，
     * 所以 `connectedKnowledgeIds` 里的 `kb-quick-notes` 会被滤掉，这是预期行为。
     */
    @Test
    fun fieldsMissingFromTheFixturesSurviveRoundTrip() {
        val session = ChatSession(
            id = "session-rich",
            title = "含全部冷门字段",
            groupId = "group-default",
            agentId = "agent-omni",
            providerId = "provider-claude",
            model = "claude-3-7-sonnet",
            temperature = 0.42,
            topP = 0.9,
            topK = 40,
            frequencyPenalty = -0.5,
            presencePenalty = 0.25,
            maxTokens = 4096,
            toolCallStepLimit = 7,
            systemPromptOverride = "覆盖",
            systemPromptFixed = "固定提示词",
            systemPromptInjectedCommon = "注入的通用提示词",
            isCommonPushed = true,
            tagDisplayMode = TagDisplayMode.CODE,
            aiContextVisibility = AiContextVisibilityFilter.LATEST_CUSTOM,
            aiContextCustomLimit = 9,
            uiRenderLimit = 20,
            connectedKnowledgeIds = listOf("kb-user", "kb-quick-notes"),
            connectedWorldDocIds = listOf("doc-1"),
            regexRuleIds = listOf("rule-1", "rule-2"),
            timelineMemoryEnabled = true,
            activeTimelineId = "timeline-sub",
            timelines = listOf(
                TimelineBranch(id = "timeline-main", name = "通用世界观", tag = "通用", codeTag = "通用"),
                TimelineBranch(id = "timeline-sub", name = "支线", tag = "A1", parentId = "timeline-main"),
            ),
            timelineDescriptionTagRequests = listOf(
                TimelineDescriptionTagRequest(
                    id = "req-1",
                    targetTimelineId = "timeline-sub",
                    oldDescriptionTag = "旧标签",
                    proposedDescriptionTag = "新标签",
                    reason = "剧情分歧",
                    sourceMessageId = "m-2",
                ),
            ),
            messages = listOf(
                ChatMessage(
                    id = "m-1",
                    role = Role.ASSISTANT,
                    floorNumber = 1,
                    codeTag = "通用",
                    descriptionTag = "通用世界观",
                    isCommon = true,
                    // 滚动重答留下的多版本，当前停在第 2 版
                    currentVersionIndex = 1,
                    versions = listOf(
                        MessageVersion(content = "第一版", timestamp = 1, model = "m", providerName = "p", latencyMs = 10, tokens = 3),
                        MessageVersion(
                            content = "第二版",
                            timestamp = 2,
                            model = "m",
                            providerName = "p",
                            latencyMs = 20,
                            tokens = 4,
                            reasoning = "思维链原文",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "m-2",
                    role = Role.ASSISTANT,
                    floorNumber = 2,
                    imageIds = listOf("img-1", "img-2"),
                    isAnalysis = true,
                    analysisVisibility = AnalysisVisibility.HIDDEN,
                    analysisBranchCode = "B1",
                    versions = listOf(MessageVersion(content = "分析报告", timestamp = 3)),
                ),
            ),
            summaries = listOf(
                SessionSummary(id = "sum-1", content = "前文总结", fromFloor = 1, toFloor = 2, model = "m"),
            ),
            lastSummarizedFloor = 2,
            imageContextMode = ImageContextMode.HISTORY,
            webSearchEnabled = true,
            // 显式的 false：三态里的「明确关闭」，必须与「缺字段」区分开
            streamingEnabled = false,
            pinned = true,
        )
        val archive = ArchiveV5(exportTime = 42L, sessions = listOf(session))

        val reparsed = ArchiveRepository.parseArchive(
            ArchiveRepository.exportArchive(archive, exportTime = 42L),
            now = FIXED_NOW,
        )

        assertEquals(normalizeArchive(archive), reparsed)
        assertEquals(
            listOf("kb-user"),
            reparsed.sessions.single().connectedKnowledgeIds,
            "内置指南 ID 应当被滤掉，用户自己选的条目必须留下",
        )
        assertEquals(false, reparsed.sessions.single().streamingEnabled, "显式 false 不得被当成「未设置」")
    }

    /** 受支持的 MCP 服务要能完整往返；瞬时状态 `testing` 按 `storage.ts:135` 落回 `disconnected`。 */
    @Test
    fun supportedMcpServerRoundTripsAndTransientStatusIsReset() {
        val archive = ArchiveV5(
            mcpServers = listOf(
                McpServerConfig(
                    id = "mcp-1",
                    name = "远程服务",
                    type = McpTransportType.STREAMABLE_HTTP,
                    url = "https://mcp.example.com/mcp",
                    authToken = "token-1",
                    headers = mapOf("X-Tenant" to "acme"),
                    status = McpServerStatus.TESTING,
                    tools = listOf(McpTool(name = "do_thing", description = "做事")),
                    description = "说明",
                    serverName = "example",
                    serverVersion = "1.2.3",
                    lastTestedAt = 100L,
                    lastLatencyMs = 30L,
                ),
            ),
        )

        val server = ArchiveRepository.parseArchive(
            ArchiveRepository.exportArchive(archive, exportTime = 0L),
            now = FIXED_NOW,
        ).mcpServers.single()

        assertEquals("https://mcp.example.com/mcp", server.url)
        assertEquals(McpTransportType.STREAMABLE_HTTP, server.type)
        assertEquals("token-1", server.authToken)
        assertEquals(mapOf("X-Tenant" to "acme"), server.headers, "自定义请求头不得丢，中转站全靠它")
        assertEquals("example", server.serverName)
        assertEquals(100L, server.lastTestedAt)
        assertEquals(listOf(McpTool(name = "do_thing", description = "做事")), server.tools)
        assertEquals(McpServerStatus.DISCONNECTED, server.status, "testing 是瞬时状态，读回来一律是 disconnected")
    }

    // ---------------------------------------------------------------- 坏输入

    /** 导入是用户点按钮触发的，坏存档必须返回 `null` 而不是把界面炸掉（对应 `importAllData` 的 `catch`）。 */
    @Test
    fun tryImportArchiveReturnsNullInsteadOfThrowing() {
        assertNull(ArchiveRepository.tryImportArchive("这不是 JSON"))
        assertNull(ArchiveRepository.tryImportArchive("[1, 2, 3]"), "顶层不是对象")
        assertNull(ArchiveRepository.tryImportArchive(""), "空串")
        assertNotNull(ArchiveRepository.tryImportArchive(fixtureText("archive-v5-minimal.json"), now = FIXED_NOW))
    }

    /** 缺字段的顶层面（`{"version":5}`）不是错误：`importAllData` 是逐项 `if (data.xxx)` 判断。 */
    @Test
    fun sparseArchiveIsAcceptedNotRejected() {
        val archive = assertNotNull(ArchiveRepository.tryImportArchive("""{"version": 5}""", now = FIXED_NOW))

        assertEquals(5, archive.version)
        assertTrue(archive.providers.isEmpty())
        assertTrue(archive.sessions.isEmpty())
        assertNull(archive.appPreferences)
    }
}
