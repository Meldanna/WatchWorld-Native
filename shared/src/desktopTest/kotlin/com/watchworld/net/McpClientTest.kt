package com.watchworld.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.McpTransportType
import com.watchworld.model.WwJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCP 客户端（T10）的单测。
 *
 * 用进程内 `com.sun.net.httpserver.HttpServer` 假装真实 MCP 服务：只 mock 客户端会把
 * 「每次 POST 的请求头 / JSON 形状 / 响应分流」这一整层漏掉。真机响应原文见
 * `fixtures/mcp/tools-list.json` 与 `fixtures/mcp/tools-call-list-repos.json`。
 */
class McpClientTest {

    // -----------------------------------------------------------------------------------------
    // StreamableHTTP
    // -----------------------------------------------------------------------------------------

    @Test
    fun testConnectionRunsInitializeThenToolsListAndRemembersDowngradedVersion() = runBlocking {
        val fake = FakeMcpServer()
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(server(fake.url))

            assertTrue(result.success, "应成功：${result.error}")
            assertNull(result.error)
            assertEquals("Android 直连成功，发现 1 个工具", result.message)
            assertEquals(McpServerInfo("Fake MCP Server", "9.9.9"), result.serverInfo)
            assertEquals(1, result.tools.size)
            assertEquals("list_repos", result.tools.single().name)
            assertEquals("列出仓库", result.tools.single().description)

            // initialize → notifications/initialized（无 id）→ tools/list，三步齐全。
            assertEquals(
                listOf("initialize", "notifications/initialized", "tools/list"),
                fake.requests.map { it["method"]?.jsonPrimitive?.contentOrNull },
            )

            val initialize = fake.requests[0]
            assertEquals("2.0", initialize["jsonrpc"]?.jsonPrimitive?.contentOrNull)
            val params = initialize.getValue("params").jsonObject
            // 客户端发实测过的 2025-06-18（服务端会降级，我们照收）。
            assertEquals("2025-06-18", params["protocolVersion"]?.jsonPrimitive?.contentOrNull)
            val clientInfo = params.getValue("clientInfo").jsonObject
            assertEquals("watchworld-android-mcp-client", clientInfo["name"]?.jsonPrimitive?.contentOrNull)
            assertEquals("0.2.5", clientInfo["version"]?.jsonPrimitive?.contentOrNull)

            // 服务端把版本降级成 2024-11-05，后续请求必须带协商后的版本。
            assertEquals(
                "2024-11-05",
                fake.headers[2]["mcp-protocol-version"],
                "tools/list 应带服务端协商版本",
            )
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun handshakeOmitsSessionIdAndLaterRequestsCarryItThenDeleteOnClose() = runBlocking {
        val fake = FakeMcpServer(options = FakeMcpOptions(sessionId = "sess-123"))
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(server(fake.url))
            assertTrue(result.success, "应成功：${result.error}")

            // SDK：handshake（initialize）时主动 delete mcp-session-id。
            assertNull(fake.headers[0]["mcp-session-id"], "initialize 不应带 session id")
            assertEquals("sess-123", fake.headers[2]["mcp-session-id"], "tools/list 应带 session id")
        } finally {
            http.close()
            fake.stop()
        }
        // 有 session 时，会话结束发 DELETE（terminateSession）。
        assertEquals(1, fake.deleteCount.get())
        assertEquals("sess-123", fake.deleteHeaders.single()["mcp-session-id"])
    }

    @Test
    fun sseFormattedResponseIsParsed() = runBlocking {
        val fake = FakeMcpServer(options = FakeMcpOptions(sseResponse = true))
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(server(fake.url))
            assertTrue(result.success, "SSE 形状的响应也应解析成功：${result.error}")
            assertEquals(1, result.tools.size)
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun toolsListPaginatesAcrossCursor() = runBlocking {
        val fake = FakeMcpServer(
            options = FakeMcpOptions(
                tools = listOf(toolJson("list_repos", "列出仓库")),
                secondPageTools = listOf(toolJson("read_file", "读文件")),
            ),
        )
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(server(fake.url))
            assertTrue(result.success, "应成功：${result.error}")
            assertEquals(listOf("list_repos", "read_file"), result.tools.map { it.name })
            val listCalls = fake.requests.filter { it["method"]?.jsonPrimitive?.contentOrNull == "tools/list" }
            assertEquals(2, listCalls.size)
            assertEquals("page-2", (listCalls[1]["params"] as? JsonObject)?.get("cursor")?.jsonPrimitive?.contentOrNull)
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun toolWithoutDescriptionFallsBackToMethodText() = runBlocking {
        val fake = FakeMcpServer(options = FakeMcpOptions(tools = listOf(toolJson("list_repos", null))))
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(server(fake.url))
            assertEquals("MCP 工具方法: list_repos", result.tools.single().description)
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun testConnectionMapsHttpErrorToTransportHint() = runBlocking {
        val fake = FakeMcpServer(options = FakeMcpOptions(httpStatus = 404))
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(server(fake.url))
            assertTrue(!result.success)
            assertContains(result.error.orEmpty(), "端点或传输类型可能不匹配")
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun testConnectionRejectsInvalidUrlsBeforeAnyRequest() = runBlocking {
        val fake = FakeMcpServer()
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            assertEquals("MCP 地址不能为空", client.testConnection(server("")).error)
            assertEquals("MCP 地址格式不正确", client.testConnection(server("not-a-url")).error)
            assertEquals(
                "远程 MCP 地址只允许 http 或 https",
                client.testConnection(server("ftp://example.com/mcp")).error,
            )
            assertEquals(
                "MCP URL 不允许内嵌用户名或密码",
                client.testConnection(server("http://user:pass@example.com/mcp")).error,
            )
            assertEquals(0, fake.requestCount(), "URL 校验失败不应发任何请求")
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun callSendsArgumentsAndReturnsToolResult() = runBlocking {
        val fake = FakeMcpServer()
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.call(
                server(fake.url, status = McpServerStatus.CONNECTED, tools = listOf(McpTool("list_repos", "列出仓库"))),
                name = "list_repos",
                args = buildJsonObject { put("per_page", 30) },
            )
            val content = (result as JsonObject).getValue("content").jsonArray.single().jsonObject
            assertEquals("text", content["type"]?.jsonPrimitive?.contentOrNull)
            assertEquals("共 9 个仓库", content["text"]?.jsonPrimitive?.contentOrNull)

            val callRequest = fake.requests.first { it["method"]?.jsonPrimitive?.contentOrNull == "tools/call" }
            assertEquals("watchworld-android-tools", initializeClientName(fake))
            val params = callRequest.getValue("params").jsonObject
            assertEquals("list_repos", params["name"]?.jsonPrimitive?.contentOrNull)
            assertEquals(30, params.getValue("arguments").jsonObject.getValue("per_page").jsonPrimitive.content.toInt())
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun callRejectsUnavailableToolBeforeAnyRequest() = runBlocking {
        val fake = FakeMcpServer()
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val disconnected = server(fake.url, status = McpServerStatus.DISCONNECTED, tools = listOf(McpTool("list_repos")))
            assertEquals(
                "工具不属于当前可用的 MCP 服务",
                assertFailsWith<ApiException> { client.call(disconnected, "list_repos", JsonObject(emptyMap())) }.message,
            )
            // 状态已连接但没有这个工具，同样拒绝。
            val missing = server(fake.url, status = McpServerStatus.CONNECTED, tools = listOf(McpTool("other_tool")))
            assertEquals(
                "工具不属于当前可用的 MCP 服务",
                assertFailsWith<ApiException> { client.call(missing, "list_repos", JsonObject(emptyMap())) }.message,
            )
            assertEquals(0, fake.requestCount(), "两条校验必须在任何网络请求之前")
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun callSurfacesJsonRpcErrorMessageVerbatim() = runBlocking {
        val fake = FakeMcpServer(options = FakeMcpOptions(callError = "未知工具: no_such_tool"))
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val error = assertFailsWith<ApiException> {
                client.call(
                    server(fake.url, status = McpServerStatus.CONNECTED, tools = listOf(McpTool("list_repos"))),
                    "list_repos",
                    JsonObject(emptyMap()),
                )
            }
            assertEquals("未知工具: no_such_tool", error.message)
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun callPassesThroughIsErrorResult() = runBlocking {
        val fake = FakeMcpServer(options = FakeMcpOptions(toolIsError = true))
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.call(
                server(fake.url, status = McpServerStatus.CONNECTED, tools = listOf(McpTool("read_file"))),
                "read_file",
                JsonObject(emptyMap()),
            )
            assertEquals(true, (result as JsonObject)["isError"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        } finally {
            http.close()
            fake.stop()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 旧式 HTTP+SSE 传输
    // -----------------------------------------------------------------------------------------

    @Test
    fun legacySseTransportCompletesHandshakeOverGetStream() = runBlocking {
        val fake = FakeSseMcpServer()
        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            val result = client.testConnection(
                server(fake.sseUrl, type = McpTransportType.SSE),
            )
            assertTrue(result.success, "旧式 SSE 应成功：${result.error}")
            assertEquals(listOf("list_repos"), result.tools.map { it.name })
            assertEquals(McpServerInfo("SSE MCP Server", "1.0.0"), result.serverInfo)
        } finally {
            http.close()
            fake.stop()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 错误解释 / 纯函数
    // -----------------------------------------------------------------------------------------

    @Test
    fun explainMcpErrorCoversAllFiveBranches() {
        assertContains(explainMcpError("connect to 127.0.0.1 refused"), "指向手机自身")
        assertContains(explainMcpError("CLEARTEXT communication not permitted"), "明文 HTTP")
        assertContains(explainMcpError("NetworkError when attempting to fetch"), "检查网络、服务监听地址")
        assertContains(explainMcpError("HTTP 404 Not Found"), "端点或传输类型可能不匹配")
        assertEquals("Android 直连 MCP 失败：weird boom", explainMcpError("weird boom"))
    }

    // -----------------------------------------------------------------------------------------
    // 脚手架
    // -----------------------------------------------------------------------------------------

    private fun server(
        url: String,
        status: McpServerStatus = McpServerStatus.DISCONNECTED,
        type: McpTransportType = McpTransportType.STREAMABLE_HTTP,
        tools: List<McpTool> = emptyList(),
    ) = McpServerConfig(id = "mcp-1", name = "测试 MCP", type = type, url = url, status = status, tools = tools)

    private fun initializeClientName(fake: FakeMcpServer): String =
        fake.requests.first { it["method"]?.jsonPrimitive?.contentOrNull == "initialize" }
            .getValue("params").jsonObject.getValue("clientInfo").jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun toolJson(name: String, description: String?, schema: JsonObject = defaultSchema()): JsonObject =
        buildJsonObject {
            put("name", name)
            if (description != null) put("description", description)
            put("inputSchema", schema)
        }

    private fun defaultSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("per_page") { put("type", "number") } }
    }
}

/** 主假上游：实现 `initialize` / `tools/list` / `tools/call`，可按选项切换响应形状。 */
private class FakeMcpServer(private val options: FakeMcpOptions = FakeMcpOptions()) {

    val requests = Collections.synchronizedList(mutableListOf<JsonObject>())
    val headers = Collections.synchronizedList(mutableListOf<Map<String, String>>())
    val deleteHeaders = Collections.synchronizedList(mutableListOf<Map<String, String>>())
    val deleteCount = AtomicInteger()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/mcp") { exchange -> handle(exchange) }
        server.executor = null
        server.start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}/mcp"

    fun requestCount(): Int = requests.size

    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod == "DELETE") {
            deleteCount.incrementAndGet()
            deleteHeaders += exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.firstOrNull().orEmpty() }
            exchange.sendResponseHeaders(options.deleteStatus, -1)
            exchange.close()
            return
        }

        val body = WwJson.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject
        requests += body
        headers += exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.firstOrNull().orEmpty() }

        val id = body["id"]
        if (id == null || id is JsonNull) {
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
            return
        }
        if (options.httpStatus != 200) {
            val bytes = "boom".toByteArray()
            exchange.sendResponseHeaders(options.httpStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            return
        }

        val payload = response(body, id)
        options.sessionId?.let { exchange.responseHeaders.add("mcp-session-id", it) }
        if (options.sseResponse) {
            val bytes = "event: message\ndata: $payload\n\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } else {
            val bytes = payload.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun response(body: JsonObject, id: JsonElement): String {
        val method = body["method"]?.jsonPrimitive?.contentOrNull
        return when (method) {
            "initialize" -> result(
                id,
                buildJsonObject {
                    put("protocolVersion", options.protocolVersion)
                    putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                    putJsonObject("serverInfo") {
                        put("name", "Fake MCP Server")
                        put("version", "9.9.9")
                    }
                },
            )
            "tools/list" -> {
                val cursor = (body["params"] as? JsonObject)?.get("cursor")?.jsonPrimitive?.contentOrNull
                if (cursor == null && options.secondPageTools.isNotEmpty()) {
                    result(id, buildJsonObject {
                        put("tools", JsonArray(options.tools))
                        put("nextCursor", "page-2")
                    })
                } else {
                    val list = if (cursor != null) options.secondPageTools else options.tools
                    result(id, buildJsonObject { put("tools", JsonArray(list)) })
                }
            }
            "tools/call" -> {
                options.callError?.let { return error(id, -32602, it) }
                result(id, buildJsonObject {
                    put("content", JsonArray(listOf(buildJsonObject {
                        put("type", "text")
                        put("text", options.toolText)
                    })))
                    if (options.toolIsError) put("isError", true)
                })
            }
            else -> error(id, -32601, "Method not found: $method")
        }
    }

    /** 服务端主动发的通知帧（无 id）—— 客户端必须忽略而不是报错。 */
    @Suppress("unused")
    private fun notificationFrame(): String = """{"jsonrpc":"2.0","method":"notifications/tools/list_changed"}"""

    private fun result(id: JsonElement, result: JsonObject): String =
        buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }.toString()

    private fun error(id: JsonElement, code: Int, message: String): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }.toString()
}

private data class FakeMcpOptions(
    val sessionId: String? = null,
    val protocolVersion: String = "2024-11-05",
    val tools: List<JsonObject> = listOf(
        buildJsonObject {
            put("name", "list_repos")
            put("description", "列出仓库")
            put("inputSchema", buildJsonObject { put("type", "object") })
        },
    ),
    val secondPageTools: List<JsonObject> = emptyList(),
    val toolText: String = "共 9 个仓库",
    val toolIsError: Boolean = false,
    val callError: String? = null,
    val sseResponse: Boolean = false,
    val httpStatus: Int = 200,
    val deleteStatus: Int = 200,
)

/**
 * 旧式 HTTP+SSE 假上游：GET `/sse` 常驻事件流并先发 `event: endpoint`；
 * POST `/messages` 只回 202，真正的 JSON-RPC 响应写回 GET 流。
 *
 * ⚠️ 必须用线程池 executor：GET handler 会一直阻塞直到测试结束，默认单线程 executor
 *    会让 POST 永远排不上队。
 */
private class FakeSseMcpServer {

    private val requests = Collections.synchronizedList(mutableListOf<JsonObject>())
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newFixedThreadPool(4)
    private val finished = CountDownLatch(1)
    private val streamLock = Any()
    private var stream: OutputStream? = null

    init {
        server.createContext("/sse") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.responseBody
            synchronized(streamLock) {
                stream = out
                out.write("event: endpoint\ndata: /messages\n\n".toByteArray())
                out.flush()
            }
            finished.await(5, TimeUnit.SECONDS)
            runCatching { out.close() }
        }
        server.createContext("/messages") { exchange ->
            val body = WwJson.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject
            requests += body
            val id = body["id"]
            if (id != null && id !is JsonNull) {
                val payload = response(body, id)
                synchronized(streamLock) {
                    stream?.write("event: message\ndata: $payload\n\n".toByteArray())
                    stream?.flush()
                }
            }
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
        }
        server.executor = executor
        server.start()
    }

    val sseUrl: String get() = "http://127.0.0.1:${server.address.port}/sse"

    fun stop() {
        finished.countDown()
        server.stop(0)
        executor.shutdownNow()
    }

    private fun response(body: JsonObject, id: JsonElement): String {
        val result = when (body["method"]?.jsonPrimitive?.contentOrNull) {
            "initialize" -> buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {}
                putJsonObject("serverInfo") {
                    put("name", "SSE MCP Server")
                    put("version", "1.0.0")
                }
            }
            "tools/list" -> buildJsonObject {
                put("tools", JsonArray(listOf(buildJsonObject {
                    put("name", "list_repos")
                    put("description", "列出仓库")
                    put("inputSchema", buildJsonObject { put("type", "object") })
                })))
            }
            else -> buildJsonObject {}
        }
        return buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }.toString()
    }
}
