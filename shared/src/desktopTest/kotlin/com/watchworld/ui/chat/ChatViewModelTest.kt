package com.watchworld.ui.chat

import com.sun.net.httpserver.HttpServer
import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.model.Agent
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.ChatSession
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.Role
import com.watchworld.net.ChatStreamClient
import com.watchworld.net.HttpClientFactory
import com.watchworld.net.McpClient
import com.watchworld.net.WebSearchClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T14 验收：`ChatViewModel` 的正式请求链路
 * （`ContextBuilder.build → ChatRequestAssembler → runToolChat`）+ 落库与停止语义。
 *
 * 上游是进程内假 SSE 服务：**不碰真网络**，但走的是与生产完全相同的
 * `preparePost().execute{}` + 逐行读 + `runToolChat` 路径（不是 mock 客户端）。
 */
class ChatViewModelTest {

    private companion object {
        var clock = 1_800_000_000_000L
        fun tick(): Long {
            clock += 1_000
            return clock
        }

        /** 一轮完整正文：内容帧 + finish_reason + `[DONE]`。 */
        fun textStream(text: String): String = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"$text\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
            append("data: [DONE]\n\n")
        }

        /** 只有一帧正文、没有结束标记：收到它之后流会一直挂着。 */
        fun hangingStream(text: String): String =
            "data: {\"choices\":[{\"delta\":{\"content\":\"$text\"}}]}\n\n"
    }

    /**
     * 假上游。`status = 500` 时直接回错误响应（测失败路径）；
     * `hang` 非空时吐完负载就挂住（测「停止」）。
     */
    private class FakeSse(
        private val payload: String,
        private val status: Int = 200,
        private val hang: CountDownLatch? = null,
    ) {
        val bodies = mutableListOf<String>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/v1/chat/completions") { exchange ->
                bodies += exchange.requestBody.readBytes().decodeToString()
                if (status != 200) {
                    val body = "{\"error\":\"upstream boom\"}".toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(status, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                    return@createContext
                }
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                    hang?.await(30, TimeUnit.SECONDS)
                }
            }
            server.executor = null
            server.start()
        }

        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/v1"

        fun stop() {
            hang?.countDown()
            server.stop(0)
        }
    }

    private class Fixture(val db: WwDatabase, val upstream: FakeSse) {
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

        /** 写一份指向**本次上游**的 provider + 一个 Agent。 */
        suspend fun seed(mcpServerIds: List<String>? = null) {
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
            config.setAgents(
                listOf(
                    Agent(
                        id = "agent-omni",
                        name = "顾问",
                        systemPrompt = "你是顾问",
                        mcpServerIds = mcpServerIds,
                    ),
                ),
            )
        }

        suspend fun newSession(): ChatSession = sessions.create(
            ChatSession(
                id = "session-test",
                title = "新对话",
                agentId = "agent-omni",
                providerId = "provider-openai",
                model = "gpt-4o-mini",
                createdAt = tick(),
                updatedAt = tick(),
            ),
        )

        fun close() {
            client.close()
            http.close()
        }
    }

    private fun <T> withFixture(upstream: FakeSse, block: suspend (Fixture) -> T): T {
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

    // ------------------------------------------------------------------ 正常一轮

    @Test
    fun sendPersistsUserAndAssistantMessagesAndStreamsIntoTheBubble() =
        withFixture(FakeSse(textStream("今天晴。"))) { fixture ->
            fixture.seed()
            val session = fixture.newSession()
            fixture.vm.open(session)

            fixture.vm.send("你好")

            val stored = assertNotNull(fixture.sessions.byId(session.id))
            assertEquals(2, stored.messages.size)
            assertEquals(Role.USER, stored.messages[0].role)
            assertEquals("你好", stored.messages[0].content)
            assertEquals(1, stored.messages[0].floorNumber)
            assertEquals(Role.ASSISTANT, stored.messages[1].role)
            assertEquals("今天晴。", stored.messages[1].versions.single().content)
            assertEquals(2, stored.messages[1].floorNumber)
            // 首条用户消息顺带把标题从「新对话」改掉（与 Web 版 `text.slice(0, 20)` 一致）。
            assertEquals("你好", stored.title)
            // 状态回流：UI 拿到的就是落库后那条；流式标记已清。
            assertEquals(stored, fixture.vm.state.value.session)
            assertEquals(null, fixture.vm.state.value.streamingMessageId)
            // 请求体里带上了 system（人格）与刚发的那条用户消息。
            val request = fixture.upstream.bodies.single()
            assertTrue(request.contains("你是顾问"), request)
            assertTrue(request.contains("你好"), request)
        }

    @Test
    fun longFirstMessageIsTruncatedToTwentyCharsPlusEllipsis() =
        withFixture(FakeSse(textStream("好"))) { fixture ->
            fixture.seed()
            val session = fixture.newSession()
            fixture.vm.open(session)

            val long = "这是一条很长的第一条用户消息用来验证标题截断行为"
            fixture.vm.send(long)

            val stored = assertNotNull(fixture.sessions.byId(session.id))
            // 与 Web 版一致：`text.slice(0, 20) + (text.length > 20 ? '...' : '')`。
            assertEquals(long.take(20) + "...", stored.title)
        }

    @Test
    fun sessionsWithoutMcpToolsSendNoToolsFieldAndKeepStreamingOn() =
        withFixture(FakeSse(textStream("好"))) { fixture ->
            fixture.seed()
            val session = fixture.newSession()
            fixture.vm.open(session)

            fixture.vm.send("在吗")

            val request = fixture.upstream.bodies.single()
            assertTrue(!request.contains("\"tools\""), "没有可用 MCP 时不该带 tools：$request")
            assertTrue(request.contains("\"stream\":true"), request)
            // 「null 就是不传这个键」：未设置的采样参数不出现在请求体里。
            assertTrue(!request.contains("frequency_penalty"), request)
            assertTrue(!request.contains("presence_penalty"), request)
        }

    @Test
    fun mcpBoundToAgentIsRegisteredAndInjectedIntoSystem() =
        withFixture(FakeSse(textStream("好"))) { fixture ->
            fixture.seed(mcpServerIds = listOf("weather-mcp"))
            fixture.config.setMcpServers(
                listOf(
                    McpServerConfig(
                        id = "weather-mcp",
                        name = "天气服务",
                        url = "https://example.invalid/mcp",
                        status = McpServerStatus.CONNECTED,
                        tools = listOf(
                            McpTool(
                                name = "get_weather",
                                description = "查天气",
                                parametersSchema = """{"type":"object","properties":{}}""",
                            ),
                        ),
                    ),
                ),
            )
            val session = fixture.newSession()
            fixture.vm.open(session)

            fixture.vm.send("北京天气")

            val request = fixture.upstream.bodies.single()
            // 工具注册与注入 system 的清单必须出自同一份过滤结果（CONSTRAINTS §3.3）。
            assertTrue(request.contains("get_weather"), request)
            assertTrue(request.contains("已验证可用的 MCP 服务能力"), request)
        }

    @Test
    fun emptyAssistantRoundWithoutFinishMarkerIsReportedByTheToolLoopNote() =
        withFixture(FakeSse("data: [DONE]\n\n")) { fixture ->
            fixture.seed()
            val session = fixture.newSession()
            fixture.vm.open(session)

            fixture.vm.send("在吗")

            val assistant = assertNotNull(fixture.sessions.byId(session.id)).messages.last()
            assertTrue(
                assistant.versions.single().content
                    .contains("[响应意外中断：未收到完成标记，未执行本轮工具]"),
                assistant.versions.single().content,
            )
        }

    // ------------------------------------------------------------------ 失败与停止

    @Test
    fun upstreamFailureIsPersistedAsAMessageWithErrorNote() =
        withFixture(FakeSse("", status = 500)) { fixture ->
            fixture.seed()
            val session = fixture.newSession()
            fixture.vm.open(session)

            fixture.vm.send("在吗")

            val assistant = assertNotNull(fixture.sessions.byId(session.id)).messages.last()
            val content = assistant.versions.single().content
            // `runToolChat` 把上游错误包成 note 文案「[请求失败：{原始错误}]」（逐字来自 nativeToolChat.ts）。
            // ⚠️ 上游错误**不**走 ChatViewModel 的 catch（工具循环吞掉了它），
            //    因此 state.error 保持 null —— 用户从正文里的 note 得知失败原因。
            assertTrue(content.startsWith("[请求失败："), content)
            assertTrue(content.contains("upstream boom"), content)
            assertEquals(null, fixture.vm.state.value.error)
            assertEquals(null, fixture.vm.state.value.streamingMessageId)
        }

    @Test
    fun missingProviderSurfacesInStateErrorInsteadOfTheMessageBody() =
        withFixture(FakeSse(textStream("好"))) { fixture ->
            // 不 seed：库里没有 Provider → ChatViewModel 自己抛，走 catch 分支。
            val session = fixture.newSession()
            fixture.vm.open(session)

            fixture.vm.send("在吗")

            val error = assertNotNull(fixture.vm.state.value.error)
            assertTrue(error.contains("没有可用的 Provider"), error)
            val assistant = assertNotNull(fixture.sessions.byId(session.id)).messages.last()
            assertTrue(
                assistant.versions.single().content.contains("[请求失败]"),
                assistant.versions.single().content,
            )
            // 失败也要落库，且不再显示流式中。
            assertEquals(null, fixture.vm.state.value.streamingMessageId)
        }

    @Test
    fun stoppingKeepsPartialTextAndMarksTheMessageStopped() {
        val hang = CountDownLatch(1)
        val upstream = FakeSse(hangingStream("已经写了一半"), hang = hang)
        val db = WwDatabase.openInMemory()
        val fixture = Fixture(db, upstream)
        try {
            runBlocking {
                fixture.seed()
                val session = fixture.newSession()
                fixture.vm.open(session)

                val job: Job = launch { fixture.vm.send("讲个长故事") }
                fixture.vm.bindJob(job)
                // 等到正文真的推给了 UI 再停（不是靠 sleep）。
                withTimeout(10_000) {
                    while (assistantContent(fixture, session.id).isNullOrEmpty()) yield()
                }
                fixture.vm.stop()
                job.join()

                val stored = assertNotNull(fixture.sessions.byId(session.id))
                val content = stored.messages.last().versions.single().content
                assertTrue(content.contains("已经写了一半"), "停止后必须保留已输出正文：$content")
                assertTrue(content.endsWith("[已停止生成]"), content)
                // 关键：取消分支里的落库是在 NonCancellable 里做的，所以这里能读到。
                assertEquals(null, fixture.vm.state.value.streamingMessageId)
            }
        } finally {
            fixture.close()
            upstream.stop()
            db.close()
        }
    }

    private fun assistantContent(fixture: Fixture, sessionId: String): String? =
        fixture.vm.state.value.session
            ?.takeIf { it.id == sessionId }
            ?.messages?.lastOrNull()
            ?.versions?.firstOrNull()
            ?.content
}
