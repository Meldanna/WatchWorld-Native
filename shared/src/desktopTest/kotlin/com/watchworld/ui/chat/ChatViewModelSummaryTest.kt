package com.watchworld.ui.chat

import com.sun.net.httpserver.HttpServer
import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.domain.ROLE_ANALYSIS_SYSTEM_INSTRUCTION
import com.watchworld.model.Agent
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.net.ChatStreamClient
import com.watchworld.net.HttpClientFactory
import com.watchworld.net.McpClient
import com.watchworld.net.WebSearchClient
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T20 验收：`ChatViewModel` 的前文总结与角色关系分析（一次非流式请求）。
 *
 * 上游是进程内假 OpenAI 兼容服务：返回**非流式** JSON（`stream = false` 路径）。
 */
class ChatViewModelSummaryTest {

    private companion object {
        var clock = 1_800_000_000_000L
        fun tick(): Long {
            clock += 1_000
            return clock
        }
    }

    /** 非流式假上游：`choices[0].message.content`。 */
    private class FakeChat(private val reply: String, private val status: Int = 200) {
        val bodies = mutableListOf<String>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/v1/chat/completions") { exchange ->
                bodies += exchange.requestBody.readBytes().decodeToString()
                val body = if (status != 200) {
                    "{\"error\":\"upstream boom\"}".toByteArray()
                } else {
                    """{"choices":[{"message":{"content":"$reply"},"finish_reason":"stop"}]}""".toByteArray()
                }
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.executor = null
            server.start()
        }

        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/v1"

        fun stop() = server.stop(0)
    }

    private class Fixture(val db: WwDatabase, val upstream: FakeChat) {
        val settings = SettingsRepository(db)
        val config = ConfigRepository(db)
        val sessions = SessionRepository(db) { tick() }
        private val http = HttpClientFactory.create()
        private val client = ChatStreamClient(http)
        val vm = ChatViewModel(
            sessionRepository = sessions,
            settingsRepository = settings,
            configRepository = config,
            chatClient = client,
            searchClient = WebSearchClient(http),
            mcpClient = McpClient(http),
            now = { tick() },
        )

        suspend fun seedProvider() {
            config.setProviders(
                listOf(
                    ApiProviderConfig(
                        id = "provider-openai",
                        name = "OpenAI",
                        type = ApiProviderType.OPENAI,
                        baseUrl = upstream.baseUrl,
                        apiKey = "sk-test",
                        defaultModel = "gpt-4o-mini",
                    ),
                ),
            )
            config.setAgents(listOf(Agent(id = "agent-omni", name = "顾问")))
        }

        fun close() {
            client.close()
            http.close()
        }
    }

    private fun <T> withFixture(upstream: FakeChat, block: suspend (Fixture) -> T): T {
        val db = WwDatabase.openInMemory()
        val fixture = Fixture(db, upstream)
        try {
            return runBlocking { block(fixture) }
        } finally {
            fixture.close()
            upstream.stop()
            db.close()
        }
    }

    private fun session(id: String = "session-test") = ChatSession(
        id = id,
        title = "新对话",
        agentId = "agent-omni",
        providerId = "provider-openai",
        model = "gpt-4o-mini",
        messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, content = "你好", floorNumber = 1, codeTag = "通用"),
            ChatMessage(
                id = "a2",
                role = Role.ASSISTANT,
                floorNumber = 2,
                codeTag = "通用",
                versions = listOf(com.watchworld.model.MessageVersion(content = "回复")),
            ),
        ),
    )

    // ------------------------------------------------------------------ 前文总结

    @Test
    fun generateSummaryAppendsSummaryAdvancesFloorAndSendsNonStreamRequest() =
        withFixture(FakeChat("结构化总结")) { fixture ->
            fixture.seedProvider()
            val stored = fixture.sessions.create(session())
            fixture.vm.open(stored)

            fixture.vm.generateSummary(null, null)

            val after = assertNotNull(fixture.sessions.byId(stored.id))
            assertEquals(1, after.summaries.orEmpty().size)
            val summary = after.summaries!!.single()
            assertEquals("结构化总结", summary.content)
            assertEquals(1, summary.fromFloor)
            assertEquals(2, summary.toFloor)
            assertEquals("gpt-4o-mini", summary.model)
            assertEquals(2, after.lastSummarizedFloor)
            assertEquals(false, fixture.vm.state.value.summarizing)
            assertNull(fixture.vm.state.value.summaryError)

            val request = fixture.upstream.bodies.single()
            assertContains(request, "\"stream\":false")
            // 默认提示词的片段（正文里的换行被 JSON 转义，不能整段比对）。
            assertContains(request, "不复述原文，只提炼结论与当前状态。")
            assertContains(request, "===== 待总结对话 =====")
            assertContains(request, "#1 [通用] 用户：你好")
            assertTrue(!request.contains("\"tools\""), "总结请求不该带工具：$request")
        }

    @Test
    fun generateSummaryOnlyCoversNewFloors() = withFixture(FakeChat("增量总结")) { fixture ->
        fixture.seedProvider()
        val base = session().copy(
            messages = listOf(
                ChatMessage(id = "u1", role = Role.USER, content = "一", floorNumber = 1),
                ChatMessage(id = "a2", role = Role.ASSISTANT, floorNumber = 2,
                    versions = listOf(com.watchworld.model.MessageVersion(content = "二"))),
                ChatMessage(id = "u3", role = Role.USER, content = "三", floorNumber = 3),
                ChatMessage(id = "a4", role = Role.ASSISTANT, floorNumber = 4,
                    versions = listOf(com.watchworld.model.MessageVersion(content = "四"))),
            ),
            lastSummarizedFloor = 2,
        )
        val stored = fixture.sessions.create(base)
        fixture.vm.open(stored)

        fixture.vm.generateSummary("自定义提示词", null)

        val after = fixture.sessions.byId(stored.id)!!
        val summary = after.summaries!!.single()
        assertEquals(3, summary.fromFloor)
        assertEquals(4, summary.toFloor)
        assertEquals(4, after.lastSummarizedFloor)
        val request = fixture.upstream.bodies.single()
        assertContains(request, "自定义提示词")
        assertTrue(!request.contains("#1 用户：一"), "已总结的楼层不该再发送：$request")
    }

    @Test
    fun generateSummaryReturnsEarlyWhenNothingNew() = withFixture(FakeChat("不该被调用")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session().copy(lastSummarizedFloor = 2))
        fixture.vm.open(stored)

        fixture.vm.generateSummary(null, null)

        assertTrue(fixture.upstream.bodies.isEmpty(), "没有新内容时不该发请求")
        assertTrue(fixture.sessions.byId(stored.id)!!.summaries.orEmpty().isEmpty())
    }

    @Test
    fun generateSummaryEmptyReplySetsErrorWithoutTouchingSession() = withFixture(FakeChat("")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)

        fixture.vm.generateSummary(null, null)

        val after = fixture.sessions.byId(stored.id)!!
        assertNull(after.lastSummarizedFloor)
        assertTrue(after.summaries.orEmpty().isEmpty())
        // 空回复被 [runToolChat] 写成 note，一次性调用把它转回异常 → 写进 summaryError。
        assertTrue(
            fixture.vm.state.value.summaryError.orEmpty().contains("模型未返回"),
            fixture.vm.state.value.summaryError.orEmpty(),
        )
        assertEquals(false, fixture.vm.state.value.summarizing)
    }

    @Test
    fun generateSummaryUpstreamFailureSetsErrorWithoutTouchingSession() =
        withFixture(FakeChat("", status = 500)) { fixture ->
            fixture.seedProvider()
            val stored = fixture.sessions.create(session())
            fixture.vm.open(stored)

            fixture.vm.generateSummary(null, null)

            assertNull(fixture.sessions.byId(stored.id)!!.lastSummarizedFloor)
            assertTrue(fixture.sessions.byId(stored.id)!!.summaries.orEmpty().isEmpty())
            assertTrue(fixture.vm.state.value.summaryError.orEmpty().contains("请求失败"))
            assertEquals(false, fixture.vm.state.value.summarizing)
        }

    @Test
    fun summaryConfigAndDeleteAndClear() = withFixture(FakeChat("总结")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)

        fixture.vm.saveSummaryConfig("我的提示词", "summary-model")
        val configured = fixture.sessions.byId(stored.id)!!
        assertEquals("我的提示词", configured.summaryPrompt)
        assertEquals("summary-model", configured.summaryModel)

        fixture.vm.generateSummary("我的提示词", "summary-model")
        val summaryId = fixture.sessions.byId(stored.id)!!.summaries!!.single().id
        fixture.vm.deleteSummary(summaryId)
        assertTrue(fixture.sessions.byId(stored.id)!!.summaries.orEmpty().isEmpty())

        fixture.vm.generateSummary("我的提示词", "summary-model")
        fixture.vm.clearSummaries()
        // 清空总结**不**回退 lastSummarizedFloor（与 Web 版一致）。
        val cleared = fixture.sessions.byId(stored.id)!!
        assertTrue(cleared.summaries.orEmpty().isEmpty())
        assertEquals(2, cleared.lastSummarizedFloor)
    }

    @Test
    fun hiddenAnalysisIsExcludedFromSummaryTranscript() = withFixture(FakeChat("总结结果")) { fixture ->
        fixture.seedProvider()
        val base = session().copy(
            messages = session().messages + ChatMessage(
                id = "an-hidden",
                role = Role.ASSISTANT,
                floorNumber = 3,
                isAnalysis = true,
                analysisVisibility = AnalysisVisibility.HIDDEN,
                versions = listOf(com.watchworld.model.MessageVersion(content = "HIDDEN_REPORT_XYZ")),
            ),
        )
        val stored = fixture.sessions.create(base)
        fixture.vm.open(stored)

        fixture.vm.generateSummary(null, null)

        val request = fixture.upstream.bodies.single()
        assertTrue(!request.contains("HIDDEN_REPORT_XYZ"), "隐藏的分析报告不该进入总结：$request")
        assertContains(request, "#1 [通用] 用户：你好")
        assertContains(request, "#2 [通用] AI：回复")
    }

    // ------------------------------------------------------------------ 角色关系分析

    @Test
    fun analysisInsertsReportMessageOnCurrentBranch() = withFixture(FakeChat("### 分析报告")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)

        fixture.vm.analyzeRoleRelationships()

        val after = fixture.sessions.byId(stored.id)!!
        val message = after.messages.last()
        assertEquals(true, message.isAnalysis)
        assertEquals(AnalysisVisibility.VISIBLE, message.analysisVisibility)
        assertEquals("分析", message.codeTag)
        assertEquals("角色心理博弈", message.descriptionTag)
        assertEquals(3, message.floorNumber)
        assertEquals("### 分析报告", message.versions.single().content)
        assertEquals(false, fixture.vm.state.value.analyzing)

        val request = fixture.upstream.bodies.single()
        assertContains(request, ROLE_ANALYSIS_SYSTEM_INSTRUCTION.take(20))
        assertContains(request, "【当前时间线分支】：通用 · 主线")
        assertTrue(!request.contains("\"tools\""), request)
    }

    @Test
    fun analysisWithoutProviderWritesFallbackReport() = withFixture(FakeChat("不应到达")) { fixture ->
        // 不 seed：没有 Provider → resolveProvider 抛错 → 走兜底报告。
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)

        fixture.vm.analyzeRoleRelationships()

        val message = fixture.sessions.byId(stored.id)!!.messages.last()
        assertEquals(true, message.isAnalysis)
        assertContains(message.versions.single().content, "分析功能暂不可用，请检查 API 配置。")
        assertEquals(false, fixture.vm.state.value.analyzing)
    }

    @Test
    fun hiddenAnalysisIsExcludedFromNextAnalysisContext() = withFixture(FakeChat("UNIQUE_REPORT_TEXT")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)
        fixture.vm.analyzeRoleRelationships()
        val id = fixture.sessions.byId(stored.id)!!.messages.last().id
        fixture.vm.toggleAnalysisVisibility(id)

        fixture.vm.analyzeRoleRelationships()

        assertEquals(2, fixture.upstream.bodies.size)
        assertTrue(
            !fixture.upstream.bodies[1].contains("UNIQUE_REPORT_TEXT"),
            "隐藏的分析报告不该进入下一次分析：${fixture.upstream.bodies[1]}",
        )
        // 会话里两条分析消息都还在（只是隐藏的那条不发）。
        assertEquals(2, fixture.sessions.byId(stored.id)!!.messages.count { it.isAnalysis == true })
    }

    @Test
    fun toggleAnalysisVisibilityFlipsToHiddenAndBack() = withFixture(FakeChat("报告")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)
        fixture.vm.analyzeRoleRelationships()
        val id = fixture.sessions.byId(stored.id)!!.messages.last().id

        fixture.vm.toggleAnalysisVisibility(id)
        assertEquals(
            AnalysisVisibility.HIDDEN,
            fixture.sessions.byId(stored.id)!!.messages.last().analysisVisibility,
        )
        fixture.vm.toggleAnalysisVisibility(id)
        assertEquals(
            AnalysisVisibility.VISIBLE,
            fixture.sessions.byId(stored.id)!!.messages.last().analysisVisibility,
        )
    }

    @Test
    fun archiveAnalysisAddsWorldDocumentInAnalysisGroup() = withFixture(FakeChat("报告正文")) { fixture ->
        fixture.seedProvider()
        val stored = fixture.sessions.create(session())
        fixture.vm.open(stored)
        fixture.vm.analyzeRoleRelationships()
        val id = fixture.sessions.byId(stored.id)!!.messages.last().id

        fixture.vm.archiveAnalysisToDocument(id)

        val doc = fixture.config.worldDocuments().single()
        assertEquals("wg-analysis", doc.groupId)
        assertEquals("报告正文", doc.content)
        assertContains(doc.title, "心理博弈分析·#3")
        assertEquals(listOf("角色分析", "分析"), doc.tags)
    }
}
