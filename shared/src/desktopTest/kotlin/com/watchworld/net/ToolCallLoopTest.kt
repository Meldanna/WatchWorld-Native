package com.watchworld.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.watchworld.model.AgentSkill
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.WwJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **T09 工具轮次循环**（PLAN §8.1「工具循环单测：mock MCP，验证重名后缀、originalName 回填」）。
 *
 * 上游用 JDK 自带的 `com.sun.net.httpserver.HttpServer` 演一家 OpenAI 兼容 / Claude / Gemini 的
 * SSE 服务：按**收到的请求体**决定这一轮返回「工具调用」还是「最终正文」—— 同一个 handler 就能
 * 跑多轮。MCP 侧注入 mock [McpToolCaller]：T09 不碰真 MCP 传输（那是 T10）。
 *
 * ⚠️ 断言集中在**发出去的请求体**与**回来的正文**两个面上 —— 工具循环最容易错的地方
 * （注册名 / originalName / history 形状）全都只体现在这两个面。
 */
class ToolCallLoopTest {

    // -----------------------------------------------------------------------------------------
    // 验收 1：原始工具名优先
    // -----------------------------------------------------------------------------------------

    @Test
    fun uniqueToolKeepsOriginalNameAndMcpReceivesIt() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("查完了，今天晴。") else openAiToolCall("get_weather", """{"city":"北京"}""")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            // 注册名就是原始名，没有多余后缀。
            assertEquals(listOf("get_weather"), upstream.bodies.first().toolNames())
            // 发给 MCP 的也是原始名 + 对应服务。
            assertEquals(listOf("weather-mcp" to "get_weather"), calls.map { it.serverId to it.name })
            assertEquals("北京", calls.single().args["city"].jsonStr())
            assertTrue(result.text.contains("[工具调用第 1 步：get_weather]"), result.text)
            assertTrue(result.text.contains("查完了，今天晴。"), result.text)
        }
    }

    // -----------------------------------------------------------------------------------------
    // 验收 2 + 3：重名才加 _2/_3，调用时回填 originalName 与所属服务
    // -----------------------------------------------------------------------------------------

    @Test
    fun duplicateNamesAcrossServersGetSuffixesAndCallTheRightServer() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) {
                openAiText("都读完了")
            } else {
                // 一轮里同时调用「原始名」和「被加后缀的那个」。
                openAiToolCallFrames("read_file", """{"path":"a.txt"}""", id = "call-a", index = 0) +
                    openAiToolCallFrames("read_file_2", """{"path":"b.txt"}""", id = "call-b", index = 1) +
                    openAiToolCallEnd()
            }
        }
        runAgainst(
            upstream,
            servers = listOf(serverWith("read_file", "srv-a"), serverWith("read_file", "srv-b")),
            calls = calls,
        ) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            // 注册名：第一个保持原名，第二个让给 _2。
            assertEquals(listOf("read_file", "read_file_2"), upstream.bodies.first().toolNames())
            // 关键：真正发给 MCP 的是**原始名**，且各自落到正确的服务上。
            assertEquals(
                listOf("srv-a" to "read_file", "srv-b" to "read_file"),
                calls.map { it.serverId to it.name },
            )
            // 正文里的 note 用原始名（模型与用户都只认原始名）。
            assertTrue(result.text.contains("[工具调用第 1 步：read_file、read_file]"), result.text)
        }
    }

    // -----------------------------------------------------------------------------------------
    // 验收 4：toolCallStepLimit 0 = 无限
    // -----------------------------------------------------------------------------------------

    @Test
    fun zeroStepLimitKeepsLoopingUntilTheModelStopsAskingForTools() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            val round = body.assistantToolCallRounds()
            if (round < 3) openAiToolCall("get_weather", """{"city":"第${round + 1}次"}""") else openAiText("最终答案")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls, stepLimit = 0) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(3, calls.size, "0 = 不限步数，应一直循环到模型不再要工具")
            assertEquals(listOf("第1次", "第2次", "第3次"), calls.map { it.args["city"].jsonStr() })
            assertTrue(result.text.contains("最终答案"), result.text)
            assertFalse(result.text.contains("达到本窗口上限"), "0 步上限不该触发上限提示")
        }
    }

    @Test
    fun stepLimitStopsRightAfterThatManyToolRounds() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.assistantToolCallRounds() < 2) openAiToolCall("get_weather", """{"city":"x"}""") else openAiText("不该出现")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls, stepLimit = 2) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(2, calls.size)
            assertTrue(
                result.text.contains("[已完成 2 步工具调用，达到本窗口上限，已停止本条 AI 消息]"),
                "上限文案必须逐字一致：${result.text}",
            )
            assertFalse(result.text.contains("不该出现"), "达到上限后不应再请求下一轮")
        }
    }

    // -----------------------------------------------------------------------------------------
    // 内置 load_skill
    // -----------------------------------------------------------------------------------------

    @Test
    fun builtinLoadSkillIsInterceptedLocallyAndNeverReachesMcp() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("按 Skill 指引作答") else openAiToolCall("load_skill", """{"name":"写作规范"}""")
        }
        val skills = listOf(
            AgentSkill(id = "sk-1", name = "写作规范", description = "规范", systemInstruction = "正文：多用短句"),
        )
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls, skills = skills) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(0, calls.size, "load_skill 必须本地拦截，不能打到 MCP")
            val toolResult = upstream.bodies.last().toolResultText()
            assertTrue(toolResult.contains("正文：多用短句"), toolResult)
            assertTrue(toolResult.contains("写作规范"), toolResult)
            assertTrue(result.text.contains("[工具调用第 1 步：load_skill]"), result.text)
        }
    }

    @Test
    fun unknownSkillNameBecomesAToolErrorTheModelCanReactTo() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("换个说法") else openAiToolCall("load_skill", """{"name":"不存在的"}""")
        }
        val skills = listOf(AgentSkill(id = "sk-1", name = "写作规范"))
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls, skills = skills) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            val toolResult = upstream.bodies.last().toolResultText()
            assertTrue(toolResult.contains("未找到名为“不存在的”的 Skill"), toolResult)
            assertTrue(toolResult.contains("可用的 Skill 有：写作规范"), toolResult)
            assertTrue(result.text.contains("[工具返回错误，已记录到结果]"), result.text)
            assertTrue(result.text.contains("换个说法"), "错误不能中断整条消息：${result.text}")
        }
    }

    // -----------------------------------------------------------------------------------------
    // 错误路径
    // -----------------------------------------------------------------------------------------

    @Test
    fun invalidToolSchemaThrowsWithTheTsMessage() {
        val broken = McpServerConfig(
            id = "srv",
            name = "坏服务",
            status = McpServerStatus.CONNECTED,
            tools = listOf(McpTool(name = "broken", description = "", parametersSchema = "[1,2]")),
        )
        val error = assertFailsWith<ToolSchemaException> { registerTools(listOf(broken), emptyList()) }
        assertEquals("工具 broken 的参数 schema 无效", error.message)
    }

    @Test
    fun disconnectedServerIsNotRegisteredAtAll() {
        val offline = McpServerConfig(
            id = "srv",
            name = "离线",
            status = McpServerStatus.DISCONNECTED,
            tools = listOf(McpTool(name = "get_weather")),
        )
        assertEquals(emptyList(), registerTools(listOf(offline), emptyList()))
    }

    @Test
    fun unauthorizedToolCallIsRecordedAsAnErrorResult() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("抱歉") else openAiToolCall("drop_database", "{}")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertTrue(upstream.bodies.last().toolResultText().contains("模型请求了未授权的工具"))
            assertTrue(result.text.contains("[工具返回错误，已记录到结果]"), result.text)
        }
    }

    @Test
    fun toolArgumentsThatAreNotAJsonObjectAreRejected() = runBlocking {
        // 合法 JSON，但不是对象 → 「工具参数必须为 JSON 对象」。
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("好") else openAiToolCall("get_weather", """"北京"""")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            client.runToolChat(params, caller(calls))
            assertEquals(0, calls.size, "参数非法时不应真的调用 MCP")
            assertTrue(upstream.bodies.last().toolResultText().contains("工具参数必须为 JSON 对象"))
        }
    }

    // -----------------------------------------------------------------------------------------
    // finish_reason 白名单
    // -----------------------------------------------------------------------------------------

    @Test
    fun toolCallsFinishedWithStopInsteadOfToolCallsAreNotExecuted() = runBlocking {
        // 白名单内的 stop，但本轮却带了 tool_calls：TS（:467）「工具调用未正常结束，已停止」。
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { _ -> openAiToolCall("get_weather", """{"city":"北京"}""", reason = "stop") }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(0, calls.size, "finish_reason 与 tool_calls 不匹配时不得执行工具")
            assertTrue(result.text.contains("[工具调用未正常结束，已停止]"), result.text)
            assertEquals(1, upstream.bodies.size, "不应再发第二轮请求")
        }
    }

    @Test
    fun toolCallsWithFinishReasonOutsideTheWhitelistAreNotExecuted() = runBlocking {
        // length 不在白名单：TS（:459-463）先于「未正常结束」命中，文案是「模型停止：…」。
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { _ -> openAiToolCall("get_weather", """{"city":"北京"}""", reason = "length") }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(0, calls.size)
            assertTrue(result.text.contains("[模型停止：length；未执行本轮工具]"), result.text)
            assertEquals(1, upstream.bodies.size)
        }
    }

    @Test
    fun unknownFinishReasonLeavesTheRoundWithoutExecutingTools() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { _ -> openAiText("被截断了", reason = "content_filter") }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(0, calls.size)
            assertTrue(
                result.text.contains("[模型停止：content_filter；未执行本轮工具]"),
                "文案必须逐字一致：${result.text}",
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // 取消：正文保留，异常继续往外抛
    // -----------------------------------------------------------------------------------------

    @Test
    fun cancellationKeepsPartialTextAndPropagates() = runBlocking {
        // 上游吐一帧就卡住，由 withTimeout 触发取消。
        val release = java.util.concurrent.CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                // 只发一帧正文：不能带 finish_reason / [DONE]，否则这一轮正常结束就取消不到了。
                out.write(sse("""{"choices":[{"delta":{"content":"已经写了一半"}}]}""").toByteArray(Charsets.UTF_8))
                out.flush()
                // 挂住直到测试收尾放行；比 sleep 好在不会让整个用例白等 30 秒。
                release.await(30, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        server.executor = null
        server.start()

        val client = ChatStreamClient(HttpClientFactory.create())
        val calls = mutableListOf<McpCall>()
        val seen = mutableListOf<String>()
        try {
            val params = ToolChatParams(
                provider = ApiProviderConfig(
                    id = "p-1",
                    name = "测试 Provider",
                    type = ApiProviderType.OPENAI,
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1",
                    apiKey = "sk-test",
                    defaultModel = "test-model",
                ),
                model = "test-model",
                messages = listOf(ToolChatMessage(role = "user", content = "你好")),
                activeMcpServers = listOf(serverWith("get_weather")),
                listeners = ToolChatListeners(onChunk = { seen += it }),
            )
            assertFailsWith<CancellationException> {
                withTimeout(1_000) { client.runToolChat(params, caller(calls)) }
            }
            assertTrue(seen.isNotEmpty(), "取消前收到的正文应已推给 UI")
            assertTrue(seen.last().contains("已经写了一半"), seen.last())
        } finally {
            release.countDown()
            client.close()
            server.stop(0)
        }
    }

    // -----------------------------------------------------------------------------------------
    // history 形状：三家各自的回合结构
    // -----------------------------------------------------------------------------------------

    @Test
    fun secondOpenAiRoundCarriesAssistantToolCallsAndToolResult() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("最后说一句") else openAiToolCall("get_weather", """{"city":"武汉"}""")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val seen = mutableListOf<String>()
            val result = client.runToolChat(params.copy(listeners = ToolChatListeners(onChunk = { seen += it })), caller(calls))
            // 第一轮只有工具调用、没有正文：按 TS 原样补一条 note。
            assertTrue(result.text.startsWith("[工具调用第 1 步：get_weather]"), result.text)
            // ⚠️ 第二轮的正文与第一轮的 note 之间隔一个空行（就是这条前导 \n\n）。
            assertTrue(result.text.contains("]\n\n最后说一句"), "两轮之间应有空行：${result.text}")
            assertTrue(seen.last().endsWith("最后说一句"), seen.last())
        }
    }

    @Test
    fun reasoningAccumulatesAcrossToolRoundsSeparatedByBlankLine() = runBlocking {
        // TS pushReasoning：跨轮累积，用空行拼接；思维链只用于展示，不进 history。
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) {
                sse("""{"choices":[{"delta":{"reasoning_content":"第二轮思考"}}]}""") + openAiText("好")
            } else {
                sse("""{"choices":[{"delta":{"reasoning_content":"第一轮思考"}}]}""") +
                    openAiToolCall("get_weather", """{"city":"北京"}""")
            }
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val seen = mutableListOf<String>()
            client.runToolChat(
                params.copy(listeners = ToolChatListeners(onReasoningChunk = { seen += it })),
                caller(calls),
            )
            assertEquals(listOf("第一轮思考", "第一轮思考\n\n第二轮思考"), seen)
            // 思维链不得进入 history（TS：只有 assistant 的 reasoning_content 字段，且仅 OpenAI 回填）。
            assertFalse(upstream.bodies.last().jsonText().contains("第一轮思考"), "思维链不应回传给模型")
        }
    }

    @Test
    fun nonStreamingRequestGoesThroughTheSameLoop() = runBlocking {
        // stream=false：上游一次性返回完整 JSON，经 pseudoEvents 归一化后走同一套累积逻辑。
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) {
                """{"choices":[{"finish_reason":"stop","message":{"content":"非流式收工"}}]}"""
            } else {
                """{"choices":[{"finish_reason":"tool_calls","message":{"content":null,"tool_calls":[{"id":"call-9","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"北京\"}"}}]}}]}"""
            }
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            val result = client.runToolChat(params.copy(stream = false), caller(calls))
            assertEquals(listOf("weather-mcp" to "get_weather"), calls.map { it.serverId to it.name })
            assertEquals("北京", calls.single().args["city"].jsonStr())
            assertTrue(result.text.contains("非流式收工"), result.text)
            // 请求体里 stream 必须是 false。
            assertEquals("false", upstream.bodies.first()["stream"].jsonStr())
        }
    }

    @Test
    fun openAiAssistantToolCallsAreRebuiltWithTypeAndConcatenatedArguments() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/chat/completions") { body ->
            if (body.hasToolResult()) openAiText("好") else openAiToolCall("get_weather", """{"city":"北京"}""")
        }
        runAgainst(upstream, servers = listOf(serverWith("get_weather")), calls = calls) { client, params ->
            client.runToolChat(params, caller(calls))
            val assistant = upstream.bodies.last().getValue("messages").jsonArray
                .map { it.jsonObject }
                .first { it["role"].jsonStr() == "assistant" }
            val call = assistant.getValue("tool_calls").jsonArray.single().jsonObject
            assertEquals("call-1", call["id"].jsonStr())
            // ⚠️ 首帧带来的 type 必须原样回填，否则第二轮请求形状不完整。
            assertEquals("function", call["type"].jsonStr())
            assertEquals("get_weather", call.getValue("function").jsonObject["name"].jsonStr())
            assertEquals("""{"city":"北京"}""", call.getValue("function").jsonObject["arguments"].jsonStr())

            val toolMessage = upstream.bodies.last().getValue("messages").jsonArray
                .map { it.jsonObject }
                .first { it["role"].jsonStr() == "tool" }
            assertEquals("call-1", toolMessage["tool_call_id"].jsonStr())
            // tool result 的 content 是 JSON **字符串**（TS 的 JSON.stringify(result)）。
            val content = WwJson.parseToJsonElement(toolMessage["content"].jsonStr()!!).jsonObject
            assertEquals("晴", content["text"].jsonStr())
        }
    }

    @Test
    fun claudeRoundsUseToolUseBlocksAndToolResultBlocks() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1/messages") { body ->
            if (body.hasToolResult()) claudeText("收工") else claudeToolUse("get_weather", """{"city":"北京"}""")
        }
        runAgainst(
            upstream,
            servers = listOf(serverWith("get_weather")),
            calls = calls,
            providerType = ApiProviderType.CLAUDE,
        ) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(listOf("weather-mcp" to "get_weather"), calls.map { it.serverId to it.name })
            assertTrue(result.text.contains("收工"), result.text)

            val messages = upstream.bodies.last().getValue("messages").jsonArray.map { it.jsonObject }
            val assistant = messages.first { it["role"].jsonStr() == "assistant" }
            val block = assistant.getValue("content").jsonArray.first().jsonObject
            assertEquals("tool_use", block["type"].jsonStr())
            assertEquals("toolu_1", block["id"].jsonStr())
            assertEquals("北京", block.getValue("input").jsonObject["city"].jsonStr())

            val user = messages.last { it["role"].jsonStr() == "user" }
            val resultBlock = user.getValue("content").jsonArray.first().jsonObject
            assertEquals("tool_result", resultBlock["type"].jsonStr())
            assertEquals("toolu_1", resultBlock["tool_use_id"].jsonStr())
            assertEquals("false", resultBlock["is_error"].jsonStr())
        }
    }

    @Test
    fun geminiRoundsUseFunctionCallAndFunctionResponseParts() = runBlocking {
        val calls = mutableListOf<McpCall>()
        val upstream = FakeUpstream("/v1beta/models/test-model:streamGenerateContent") { body ->
            if (body.hasToolResult()) geminiText("收工") else geminiToolCall("get_weather", """{"city":"北京"}""")
        }
        runAgainst(
            upstream,
            servers = listOf(serverWith("get_weather")),
            calls = calls,
            providerType = ApiProviderType.GEMINI,
            baseUrlSuffix = "/v1beta",
        ) { client, params ->
            val result = client.runToolChat(params, caller(calls))
            assertEquals(listOf("weather-mcp" to "get_weather"), calls.map { it.serverId to it.name })
            assertTrue(result.text.contains("收工"), result.text)

            val contents = upstream.bodies.last().getValue("contents").jsonArray.map { it.jsonObject }
            val model = contents.first { it["role"].jsonStr() == "model" }
            val callPart = model.getValue("parts").jsonArray.first().jsonObject
                .getValue("functionCall").jsonObject
            assertEquals("get_weather", callPart["name"].jsonStr())

            val user = contents.last { it["role"].jsonStr() == "user" }
            val response = user.getValue("parts").jsonArray.first().jsonObject
                .getValue("functionResponse").jsonObject
            assertEquals("get_weather", response["name"].jsonStr())
            // ⚠️ Gemini 的 result 是**对象本身**（TS：`response: { result }`），不是 JSON 字符串；
            //    OpenAI / Claude 才是字符串化后的 content。
            val toolResult = response.getValue("response").jsonObject.getValue("result").jsonObject
            assertEquals("晴", toolResult["text"].jsonStr())
        }
    }

    @Test
    fun missingApiKeyIsRejectedWithTheTsMessage() = runBlocking {
        val calls = mutableListOf<McpCall>()
        runAgainst(
            FakeUpstream("/v1/chat/completions") { openAiText("不该发出") },
            servers = emptyList(),
            calls = calls,
            apiKey = "",
        ) { client, params ->
            val error = assertFailsWith<ApiException> { client.runToolChat(params, caller(calls)) }
            assertEquals("请先配置 测试 Provider API Key", error.message)
        }
    }

    // -----------------------------------------------------------------------------------------
    // 脚手架
    // -----------------------------------------------------------------------------------------

    /**
     * 跑一次 `runToolChat`：起 client、把 `upstream.baseUrl` 填进 provider、保证关掉连接与上游。
     */
    private suspend fun runAgainst(
        upstream: FakeUpstream,
        servers: List<McpServerConfig>,
        calls: MutableList<McpCall>,
        stepLimit: Int = 0,
        skills: List<AgentSkill> = emptyList(),
        providerType: ApiProviderType = ApiProviderType.OPENAI,
        baseUrlSuffix: String = "/v1",
        apiKey: String = "sk-test",
        block: suspend (ChatStreamClient, ToolChatParams) -> Unit,
    ) {
        val client = ChatStreamClient(HttpClientFactory.create())
        try {
            val params = ToolChatParams(
                provider = ApiProviderConfig(
                    id = "p-1",
                    name = "测试 Provider",
                    type = providerType,
                    baseUrl = upstream.baseUrl + baseUrlSuffix,
                    apiKey = apiKey,
                    defaultModel = "test-model",
                ),
                model = "test-model",
                messages = listOf(ToolChatMessage(role = "user", content = "北京天气如何")),
                activeMcpServers = servers,
                activeSkills = skills,
                toolCallStepLimit = stepLimit,
            )
            block(client, params)
        } finally {
            client.close()
            upstream.stop()
        }
    }

    private fun serverWith(toolName: String, serverId: String = "weather-mcp") = McpServerConfig(
        id = serverId,
        name = "天气服务",
        status = McpServerStatus.CONNECTED,
        tools = listOf(
            McpTool(
                name = toolName,
                description = "查天气",
                parametersSchema = """{"type":"object","properties":{"city":{"type":"string"}}}""",
            ),
        ),
    )
}

/** 一次 MCP 调用的实参记录 —— 工具名或服务回填错了这里会立刻看出来。 */
private data class McpCall(val serverId: String, val name: String, val args: JsonObject)

/** 记录每一次 MCP 调用的 mock 出口。 */
private fun caller(sink: MutableList<McpCall>): McpToolCaller = McpToolCaller { server, name, args ->
    sink += McpCall(server.id, name, args)
    buildJsonObject { put("text", "晴") }
}

/**
 * 假上游：按注册的 context 收请求，把请求体交给 `respond` 决定这一轮吐哪些 SSE 块。
 *
 * 用真 `HttpServer` 而不是 mock 客户端，是因为工具循环接在**真流式传输**之上
 * （`preparePost().execute{}` + `readLine()`）：只 mock 传输会把整层漏掉。
 */
private class FakeUpstream(private val path: String, private val respond: (JsonObject) -> String) {
    val bodies = mutableListOf<JsonObject>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext(path) { exchange: HttpExchange ->
            val body = WwJson.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject
            bodies += body
            val payload = respond(body).toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write(payload)
                out.flush()
            }
        }
        server.executor = null
        server.start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun stop() = server.stop(0)
}

// ---- 请求体查询 ------------------------------------------------------------------------------

/** OpenAI 请求体里注册的工具名（顺序即注册顺序）。 */
private fun JsonObject.toolNames(): List<String> =
    (this["tools"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("function") as? JsonObject }
        .mapNotNull { it["name"]?.jsonStr() }

/** 已经走过的工具轮次数 —— 用 assistant 消息里的 tool_calls 数判断。 */
private fun JsonObject.assistantToolCallRounds(): Int =
    (this["messages"] as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .count { it["role"]?.jsonStr() == "assistant" && it["tool_calls"] != null }

/** 是否已经带回了工具结果（用于判断这一轮该回「最终正文」）。 */
private fun JsonObject.hasToolResult(): Boolean = jsonText().contains("\"tool_call_id\"") ||
    jsonText().contains("\"tool_result\"") ||
    jsonText().contains("\"functionResponse\"")

/** 整个请求体的 JSON 文本 —— 各家 tool result 形状不同，比字符串最直接。 */
private fun JsonObject.jsonText(): String = WwJson.encodeToString(JsonElement.serializer(), this)

/** 工具结果所在的请求体文本（断言「回填给模型的内容」时用）。 */
private fun JsonObject.toolResultText(): String = jsonText()

private fun JsonElement?.jsonStr(): String? = (this as? JsonPrimitive)?.contentOrNull

// ---- SSE 响应构造 ----------------------------------------------------------------------------

/**
 * 与真实 fixture（`openai-toolcall-stream.txt`）同形：首帧带 `id`/`type`/`name`，
 * `arguments` 分片随后到达。
 *
 * ⚠️ 这里**只吐帧、不吐结束标记** —— `[DONE]` 会让 `SseParser` 立刻停止上游收集，
 * 一次请求要发多个工具调用时必须由调用方在末尾统一补 `openAiToolCallEnd()`。
 */
private fun openAiToolCallFrames(name: String, args: String, id: String, index: Int): String = buildString {
    append(
        sse(
            buildJsonObject {
                putJsonArray("choices") {
                    addJsonObject {
                        putJsonObject("delta") {
                            putJsonArray("tool_calls") {
                                addJsonObject {
                                    put("index", index)
                                    put("id", id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", name)
                                        put("arguments", "")
                                    }
                                }
                            }
                        }
                    }
                }
            }.toString(),
        ),
    )
    append(
        sse(
            buildJsonObject {
                putJsonArray("choices") {
                    addJsonObject {
                        putJsonObject("delta") {
                            putJsonArray("tool_calls") {
                                addJsonObject {
                                    put("index", index)
                                    // 真实上游后续分片里 type 是 null。
                                    put("type", JsonNull)
                                    putJsonObject("function") { put("arguments", args) }
                                }
                            }
                        }
                    }
                }
            }.toString(),
        ),
    )
}

/** 整轮工具调用的收尾（`finish_reason` + `[DONE]`）。 */
private fun openAiToolCallEnd(reason: String = "tool_calls"): String =
    sse("""{"choices":[{"delta":{},"finish_reason":"$reason"}]}""") + "data: [DONE]\n\n"

/** 单个工具调用的一整轮（帧 + 收尾）。 */
private fun openAiToolCall(name: String, args: String, reason: String = "tool_calls"): String =
    openAiToolCallFrames(name, args, id = "call-1", index = 0) + openAiToolCallEnd(reason)

private fun openAiText(text: String, reason: String = "stop"): String = buildString {
    append(sse("""{"choices":[{"delta":{"content":${JsonPrimitive(text)}}}]}"""))
    append(sse("""{"choices":[{"delta":{},"finish_reason":"$reason"}]}"""))
    append("data: [DONE]\n\n")
}

private fun claudeText(text: String): String = buildString {
    append(sse("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""))
    append(sse("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":${JsonPrimitive(text)}}}"""))
    append(sse("""{"type":"content_block_stop","index":0}"""))
    append(sse("""{"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""))
    append(sse("""{"type":"message_stop"}"""))
}

private fun claudeToolUse(name: String, args: String): String = buildString {
    append(
        sse(
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"$name","input":{}}}""",
        ),
    )
    append(
        sse(
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":${JsonPrimitive(args)}}}""",
        ),
    )
    append(sse("""{"type":"content_block_stop","index":0}"""))
    append(sse("""{"type":"message_delta","delta":{"stop_reason":"tool_use"}}"""))
    append(sse("""{"type":"message_stop"}"""))
}

private fun geminiText(text: String): String =
    sse("""{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}]},"finishReason":"STOP"}]}""")

private fun geminiToolCall(name: String, args: String): String =
    sse("""{"candidates":[{"content":{"parts":[{"functionCall":{"name":"$name","args":$args}}]},"finishReason":"STOP"}]}""")

private fun sse(json: String): String = "data: $json\n\n"
