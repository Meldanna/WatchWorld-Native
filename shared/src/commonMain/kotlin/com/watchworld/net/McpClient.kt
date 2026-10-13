package com.watchworld.net

import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.McpTransportType
import com.watchworld.model.WwJson
import com.watchworld.platform.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.prepareDelete
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * MCP 客户端（T10）。规格书：`src/lib/mcpApi.ts`（原生直连）+ `server.ts`（服务端参考实现）。
 *
 * ⚠️ **没有官方 Kotlin SDK，这里是从零实现**（PLAN §6.7）。协议细节全部照 `@modelcontextprotocol/client`
 *    v2.3.0 源码的观测结论（见 T10 交接单 §四），不要凭印象改：
 *  1. 默认走 legacy 握手：直接 `initialize`，不发 `server/discover` 探测。客户端 `capabilities` 传 `{}`。
 *  2. 服务端可能把 `protocolVersion` 降级（真机 `2025-06-18` → `2024-11-05`）：**必须接受并记住服务端返回的版本**，
 *     后续请求带 `mcp-protocol-version: <协商版本>`。
 *  3. StreamableHTTP 的每次 POST 都带 `content-type: application/json` 与
 *     `accept: application/json, text/event-stream`（两个都要）。handshake（initialize）时**不**带 session id。
 *  4. 响应分流：`text/event-stream` → 按 SSE 流读到关闭；`application/json` → 直接解析。**两条都必须实现**。
 *  5. 状态码 `202` = 已接受、无响应体，读掉 body 即可。`mcp-session-id` **可能没有**（真机没有），不能当必需。
 *  6. `terminateSession`：有 session 时发 `DELETE`，`405` 视为成功，失败只上报不外抛。
 *  7. 超时：SDK 默认 60s；连接 15s（文案「MCP 连接超时（15 秒）」），`tools/call` 120s。
 *  8. JSON-RPC 错误 → 直接抛服务端 `error.message` 原文。
 *  9. 收到的 frame 里不认识的（服务端主动请求 / 通知）**忽略**，不能因此报错。
 *
 * 与 TS 的**有意差异**：
 *  - TS 用官方 SDK 的 `SSEClientTransport` / `StreamableHTTPClientTransport`；这里两份都从零实现，行为对齐协议。
 *  - Android 专属的报错文案（[explainMcpError]）逐字照抄，即便当前桌面端也一样用 —— T22 加 Android 目标时口径不再分叉。
 */

/** 一次 MCP 连接测试的结果契约（`mcpApi.ts:142-233` 的归一化口径）。 */
data class McpConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val tools: List<McpTool> = emptyList(),
    /** `serverInfo`：`name` / `version` 两个都拿到了才给，否则 `null`（`App.tsx:1641-1682`）。 */
    val serverInfo: McpServerInfo? = null,
    /** 失败时为给用户看的文案；成功时为 `null`。 */
    val error: String? = null,
    /** 成功文案，供设置界面直接显示（`mcpApi.ts:145-155`）。 */
    val message: String? = null,
)

/** `serverInfo`（`InitializeResultSchema` 的必填两字段）。 */
data class McpServerInfo(val name: String, val version: String)

/** MCP 协议版本：这里发**实测过**的 `2025-06-18`（服务端会降级并回自己的版本，我们照单全收）。 */
private const val MCP_PROTOCOL_VERSION = "2025-06-18"
private const val MCP_CLIENT_VERSION = "0.2.5"

/** 客户端身份（`mcpApi.ts:29` 的 `callMcpToolNative` 与 `:105` 的 `testMcpConnectionNative`）。 */
private const val CALL_CLIENT_NAME = "watchworld-android-tools"
private const val TEST_CLIENT_NAME = "watchworld-android-mcp-client"

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val CALL_TIMEOUT_MS = 120_000L
private const val LIST_TIMEOUT_MS = 60_000L
private const val NOTIFY_TIMEOUT_MS = 15_000L
private const val CONNECT_TIMEOUT_MESSAGE = "MCP 连接超时（15 秒）"
private const val REQUEST_TIMEOUT_MESSAGE = "Request timed out"

/** `listTools` 翻页上限（SDK `DEFAULT_LIST_MAX_PAGES`）。 */
private const val MAX_LIST_PAGES = 64

/**
 * MCP 客户端。实现 T09 定义的 [McpToolCaller]：**每次调用建连、用完即关**（`mcpApi.ts:74-77`）。
 *
 * 直接实现 [McpToolCaller]，因此可以把它自己作为 `runToolChat` 的 `mcpCaller` 传进去。
 * [HttpClient] 由调用方持有（与协议层共用同一个连接池），本类**不**负责关闭它。
 */
class McpClient(private val client: HttpClient) : McpToolCaller {

    /**
     * 发起一次真实的 MCP 工具调用，返回 `tools/call` 的 `result` 原文。
     *
     * ⚠️ 前两条校验**逐字照抄** `mcpApi.ts:29-36`：`status != connected` 或工具不在 `server.tools`
     *    里 → 抛「工具不属于当前可用的 MCP 服务」。它们发生在任何网络请求之前。
     */
    override suspend fun call(server: McpServerConfig, name: String, args: JsonObject): JsonElement {
        if (server.status != McpServerStatus.CONNECTED || server.tools.none { it.name == name }) {
            throw ApiException(0, "工具不属于当前可用的 MCP 服务")
        }
        val url = validateUrl(server.url.orEmpty(), "MCP 地址必须为 HTTP 或 HTTPS")
        return guarded {
            runSession(server, url, CALL_CLIENT_NAME) { session ->
                session.initialize(CONNECT_TIMEOUT_MS)
                session.request(
                    method = "tools/call",
                    params = buildJsonObject {
                        put("name", name)
                        put("arguments", args)
                    },
                    timeoutMs = CALL_TIMEOUT_MS,
                )
            }
        }
    }

    /**
     * 探测一个 MCP 服务：`initialize` → `tools/list`，把工具清单与 `serverInfo` 归一化返回
     * （`mcpApi.ts:83-233` + `App.tsx:1641-1682`）。**失败不抛**，以 `success = false` + `error` 返回。
     *
     * 调用方（T15 设置界面）拿这个结果写回 `McpServerConfig`（`status` / `tools` / `lastLatencyMs` /
     * `lastError` / `serverName` / `serverVersion`），本函数**不改传入的配置**。
     */
    suspend fun testConnection(server: McpServerConfig): McpConnectionTestResult {
        val url = try {
            validateUrl(server.url.orEmpty(), "远程 MCP 地址只允许 http 或 https")
        } catch (error: ApiException) {
            return McpConnectionTestResult(success = false, latencyMs = 0L, error = error.message)
        }
        val started = currentTimeMillis()
        return try {
            val listing = runSession(server, url, TEST_CLIENT_NAME) { session ->
                session.initialize(CONNECT_TIMEOUT_MS)
                ToolListing(session.listTools(), session.serverInfo)
            }
            McpConnectionTestResult(
                success = true,
                latencyMs = currentTimeMillis() - started,
                tools = listing.tools,
                serverInfo = listing.serverInfo,
                message = "Android 直连成功，发现 ${listing.tools.size} 个工具",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            McpConnectionTestResult(false, currentTimeMillis() - started, error = error.message)
        } catch (error: Exception) {
            McpConnectionTestResult(
                success = false,
                latencyMs = currentTimeMillis() - started,
                error = explainMcpError(error.message ?: error.toString()),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 会话装配
    // ---------------------------------------------------------------------------------------------

    private suspend fun <T> runSession(
        server: McpServerConfig,
        url: String,
        clientName: String,
        block: suspend (McpSession) -> T,
    ): T = coroutineScope {
        val headers = baseHeaders(server)
        val transport: McpTransport = when (server.type) {
            McpTransportType.SSE -> SseTransport(client, url, headers)
            McpTransportType.STREAMABLE_HTTP -> StreamableHttpTransport(client, url, headers)
        }
        val reader = transport.open(this, CONNECT_TIMEOUT_MS)
        try {
            block(McpSession(transport, clientName))
        } finally {
            transport.close()
            // SSE 的 GET 流是常驻后台任务，会话结束必须收掉（结构化并发也会兜底）。
            reader?.cancelAndJoin()
        }
    }

    private fun baseHeaders(server: McpServerConfig): Map<String, String> {
        val headers = LinkedHashMap(server.headers.orEmpty())
        val token = server.authToken?.trim().orEmpty()
        if (token.isNotEmpty()) headers["Authorization"] = "Bearer $token"
        return headers
    }
}

// -------------------------------------------------------------------------------------------------
// 一次 JSON-RPC 会话（id 分配 / 握手 / 分页工具清单）
// -------------------------------------------------------------------------------------------------

private data class ToolListing(val tools: List<McpTool>, val serverInfo: McpServerInfo?)

private class McpSession(
    private val transport: McpTransport,
    private val clientName: String,
) {
    private var nextId = 1
    private var sessionId: String? = null
    private var protocolVersion: String? = null

    var serverInfo: McpServerInfo? = null
        private set

    /** `initialize`：发最小客户端信息，**接受服务端返回的版本**，随后补 `notifications/initialized`。 */
    suspend fun initialize(timeoutMs: Long) {
        val result = request(
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", MCP_PROTOCOL_VERSION)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", clientName)
                    put("version", MCP_CLIENT_VERSION)
                }
            },
            timeoutMs = timeoutMs,
            handshake = true,
        )
        val body = result as? JsonObject
        body?.get("protocolVersion").stringOrNull()?.takeIf { it.isNotBlank() }?.let { protocolVersion = it }
        sessionId = transport.sessionId
        val info = body?.get("serverInfo") as? JsonObject
        val name = info?.get("name").stringOrNull()
        val version = info?.get("version").stringOrNull()
        if (name != null && version != null) serverInfo = McpServerInfo(name, version)
        sendNotification("notifications/initialized")
    }

    /** `tools/list`，按 `nextCursor` 翻页（重复 cursor 或超页数上限即停）。 */
    suspend fun listTools(): List<McpTool> {
        val tools = mutableListOf<McpTool>()
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        var pages = 0
        while (pages < MAX_LIST_PAGES) {
            pages++
            val page = request(
                method = "tools/list",
                params = cursor?.let { buildJsonObject { put("cursor", it) } },
                timeoutMs = LIST_TIMEOUT_MS,
            ) as? JsonObject
            (page?.get("tools") as? JsonArray)?.forEach { element ->
                (element as? JsonObject)?.let { raw -> mapTool(raw)?.let(tools::add) }
            }
            val next = page?.get("nextCursor").stringOrNull()
            if (next.isNullOrBlank() || !seen.add(next)) break
            cursor = next
        }
        return tools
    }

    suspend fun request(
        method: String,
        params: JsonObject?,
        timeoutMs: Long,
        handshake: Boolean = false,
    ): JsonElement {
        val id = nextId
        nextId++
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val response = transport.exchange(payload, requestHeaders(handshake), id, timeoutMs)
        if (handshake) transport.sessionId?.let { sessionId = it }
        val error = response["error"]
        // JSON-RPC 错误：直接抛服务端 `error.message` 原文（SDK 的 ProtocolError 同义）。
        if (error != null) throw ApiException(0, rpcErrorMessage(error))
        return response["result"] ?: JsonNull
    }

    private suspend fun sendNotification(method: String) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        transport.send(payload, requestHeaders(handshake = false), NOTIFY_TIMEOUT_MS)
    }

    /** handshake 时**主动**不带 `mcp-session-id`（SDK `index.mjs:5448-5478`）。 */
    private fun requestHeaders(handshake: Boolean): Map<String, String> {
        val headers = linkedMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/event-stream",
        )
        if (!handshake) {
            sessionId?.let { headers["mcp-session-id"] = it }
            protocolVersion?.let { headers["mcp-protocol-version"] = it }
        }
        return headers
    }
}

/** `tools/list` 里单个工具 → [McpTool]（`mcpApi.ts:140-144`）。名字空白的项直接丢弃。 */
private fun mapTool(raw: JsonObject): McpTool? {
    val name = raw["name"].stringOrNull()?.trim().orEmpty()
    if (name.isEmpty()) return null
    val description = raw["description"].stringOrNull()?.takeIf { it.isNotBlank() } ?: "MCP 工具方法: $name"
    val schema = raw["inputSchema"]?.takeIf { it !is JsonNull } ?: emptyObjectSchema()
    return McpTool(name = name, description = description, parametersSchema = schema.toString())
}

private fun emptyObjectSchema(): JsonObject = buildJsonObject { put("type", "object") }

// -------------------------------------------------------------------------------------------------
// 传输层
// -------------------------------------------------------------------------------------------------

/**
 * 一次会话的底层传输。`open` 建立连接（SSE 需要常驻的 GET 流），`exchange` 发请求取响应，
 * `send` 发通知不等响应，`close` 关闭连接。
 */
private interface McpTransport {
    /** StreamableHTTP 响应头里的 `mcp-session-id`（没有则为 `null`）。 */
    val sessionId: String?

    /** 建立连接；返回需要随会话存活的后台任务（StreamableHTTP 返回 `null`）。 */
    suspend fun open(scope: CoroutineScope, connectTimeoutMs: Long): Job?

    suspend fun exchange(payload: JsonObject, headers: Map<String, String>, id: Int, timeoutMs: Long): JsonObject

    suspend fun send(payload: JsonObject, headers: Map<String, String>, timeoutMs: Long)

    suspend fun close()
}

/** StreamableHTTP：单端点 POST，响应可能是 `application/json` 也可能是 `text/event-stream`。 */
private class StreamableHttpTransport(
    private val client: HttpClient,
    private val url: String,
    private val baseHeaders: Map<String, String>,
) : McpTransport {

    override var sessionId: String? = null
        private set

    @Suppress("UNUSED_PARAMETER")
    override suspend fun open(scope: CoroutineScope, connectTimeoutMs: Long): Job? = null

    override suspend fun exchange(
        payload: JsonObject,
        headers: Map<String, String>,
        id: Int,
        timeoutMs: Long,
    ): JsonObject = withTimeoutApi(timeoutMs, REQUEST_TIMEOUT_MESSAGE) {
        client.preparePost(url) {
            merged(baseHeaders, headers).forEach { (key, value) -> header(key, value) }
            setBody(payload.toString())
        }.execute { response ->
            response.headers["mcp-session-id"]?.let { sessionId = it }
            val status = response.status.value
            if (status == 202) {
                response.bodyAsText()
                throw McpTransportException("MCP 服务未返回响应")
            }
            if (!response.status.isSuccess()) {
                throw McpTransportException("HTTP $status ${response.bodyAsText().take(300)}")
            }
            val contentType = response.headers[HttpHeaders.ContentType]?.lowercase().orEmpty()
            val frames = if (contentType.contains("text/event-stream")) {
                readSseFrames(response.body<ByteReadChannel>())
            } else {
                parseJsonFrames(response.bodyAsText())
            }
            selectResponse(frames, id) ?: throw McpTransportException("MCP 服务未返回匹配的响应")
        }
    }

    override suspend fun send(payload: JsonObject, headers: Map<String, String>, timeoutMs: Long) {
        withTimeoutApi(timeoutMs, REQUEST_TIMEOUT_MESSAGE) {
            client.preparePost(url) {
                merged(baseHeaders, headers).forEach { (key, value) -> header(key, value) }
                setBody(payload.toString())
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw McpTransportException("HTTP ${response.status.value} ${response.bodyAsText().take(300)}")
                }
                response.bodyAsText()
            }
        }
    }

    /** 有 session 才发 `DELETE`；`405` 视为服务端不支持终止；失败只上报不外抛。 */
    override suspend fun close() {
        val id = sessionId ?: return
        runCatching {
            client.prepareDelete(url) {
                merged(baseHeaders, mapOf("mcp-session-id" to id)).forEach { (key, value) -> header(key, value) }
            }.execute { response ->
                response.bodyAsText()
            }
        }
    }
}

/**
 * 旧式 HTTP+SSE 传输：常驻 GET 事件流，服务端先发 `event: endpoint` 给出 POST 地址，
 * 响应经 GET 流回。仅在 `McpServerConfig.type == sse` 时使用。
 */
private class SseTransport(
    private val client: HttpClient,
    private val url: String,
    private val baseHeaders: Map<String, String>,
) : McpTransport {

    private val responses = Channel<JsonObject>(Channel.UNLIMITED)
    private val endpoint = CompletableDeferred<String>()

    override val sessionId: String? get() = null

    override suspend fun open(scope: CoroutineScope, connectTimeoutMs: Long): Job {
        val job = scope.launch { runStream() }
        withTimeoutApi(connectTimeoutMs, CONNECT_TIMEOUT_MESSAGE) { endpoint.await() }
        return job
    }

    private suspend fun runStream() {
        try {
            client.prepareGet(url) {
                baseHeaders.forEach { (key, value) -> header(key, value) }
                header("Accept", "text/event-stream")
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val detail = response.bodyAsText().take(300)
                    endpoint.completeExceptionally(McpTransportException("HTTP ${response.status.value} $detail"))
                    return@execute
                }
                readSseStream(response.body())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!endpoint.isCompleted) endpoint.completeExceptionally(error)
        } finally {
            responses.close()
        }
    }

    private suspend fun readSseStream(channel: ByteReadChannel) {
        val data = StringBuilder()
        var event: String? = null
        while (!channel.isClosedForRead) {
            val line = channel.readLine() ?: break
            val current = line.removeSuffix("\r")
            when {
                current.isEmpty() -> {
                    deliver(event, data.toString())
                    data.clear()
                    event = null
                }
                current.startsWith("event:") -> event = current.removePrefix("event:").trim()
                current.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(current.removePrefix("data:").removePrefix(" "))
                }
            }
        }
        deliver(event, data.toString())
    }

    private suspend fun deliver(event: String?, payload: String) {
        if (payload.isBlank()) return
        if (event == "endpoint") {
            if (!endpoint.isCompleted) endpoint.complete(resolveEndpoint(payload))
            return
        }
        // 通知帧（无 id）与不认识的数据帧一律忽略，不能因此报错。
        val message = runCatching { WwJson.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return
        if (message["id"] != null && message["method"] == null) responses.send(message)
    }

    override suspend fun exchange(
        payload: JsonObject,
        headers: Map<String, String>,
        id: Int,
        timeoutMs: Long,
    ): JsonObject {
        val target = withTimeoutApi(timeoutMs, REQUEST_TIMEOUT_MESSAGE) { endpoint.await() }
        post(target, payload, headers, timeoutMs)
        return withTimeoutApi(timeoutMs, REQUEST_TIMEOUT_MESSAGE) {
            var found: JsonObject? = null
            while (found == null) {
                val message = responses.receiveCatching().getOrNull()
                    ?: throw McpTransportException("MCP SSE 连接已关闭")
                if (matchesId(message, id)) found = message
            }
            found
        }
    }

    override suspend fun send(payload: JsonObject, headers: Map<String, String>, timeoutMs: Long) {
        val target = withTimeoutApi(timeoutMs, REQUEST_TIMEOUT_MESSAGE) { endpoint.await() }
        post(target, payload, headers, timeoutMs)
    }

    private suspend fun post(
        target: String,
        payload: JsonObject,
        headers: Map<String, String>,
        timeoutMs: Long,
    ) {
        withTimeoutApi(timeoutMs, REQUEST_TIMEOUT_MESSAGE) {
            client.preparePost(target) {
                merged(baseHeaders, headers).forEach { (key, value) -> header(key, value) }
                setBody(payload.toString())
            }.execute { response ->
                // 旧式 SSE：POST 通常回 202，真正的响应经 GET 流到达。
                if (!response.status.isSuccess()) {
                    throw McpTransportException("HTTP ${response.status.value} ${response.bodyAsText().take(300)}")
                }
                response.bodyAsText()
            }
        }
    }

    override suspend fun close() {
        responses.close()
    }

    /** 旧式 SSE 规范允许 `endpoint` 是相对路径；拼到 SSE 地址的 origin 上。 */
    private fun resolveEndpoint(payload: String): String {
        val value = payload.trim()
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return value
        val origin = url.substring(0, schemeEnd + 3) + url.substring(schemeEnd + 3).substringBefore('/')
        return if (value.startsWith("/")) origin + value else "$origin/$value"
    }
}

// -------------------------------------------------------------------------------------------------
// 响应解析 / 小工具
// -------------------------------------------------------------------------------------------------

/** `application/json` 响应：单条 JSON-RPC 消息。 */
private fun parseJsonFrames(raw: String): List<JsonObject> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    val element = runCatching { WwJson.parseToJsonElement(trimmed) }.getOrNull() ?: return emptyList()
    return (element as? JsonObject)?.let { listOf(it) } ?: emptyList()
}

/**
 * `text/event-stream` 响应：读到流关闭，把每个 `data:` 帧解析成 JSON-RPC 消息。
 *
 * ⚠️ 这份解析**故意比 [SseParser] 宽松**：MCP 的 SSE 里会有服务端通知帧（无 `id`、可能是
 *    `notifications/…` 这类）乃至非 JSON 数据，拿模型流那套（遇到 `error` 帧就抛）会误伤。
 */
private suspend fun readSseFrames(channel: ByteReadChannel): List<JsonObject> {
    val frames = mutableListOf<JsonObject>()
    val data = StringBuilder()
    while (!channel.isClosedForRead) {
        val line = channel.readLine() ?: break
        val current = line.removeSuffix("\r")
        if (current.isEmpty()) {
            flushSseFrame(data, frames)
        } else if (current.startsWith("data:")) {
            if (data.isNotEmpty()) data.append('\n')
            data.append(current.removePrefix("data:").removePrefix(" "))
        }
    }
    flushSseFrame(data, frames)
    return frames
}

private fun flushSseFrame(data: StringBuilder, out: MutableList<JsonObject>) {
    if (data.isEmpty()) return
    val payload = data.toString()
    data.clear()
    if (payload.isBlank()) return
    val element = runCatching { WwJson.parseToJsonElement(payload) }.getOrNull() ?: return
    (element as? JsonObject)?.let { out += it }
}

/** 只认「无 `method`、有 `id`」的响应帧；通知 / 服务端请求一律跳过。 */
private fun selectResponse(frames: List<JsonObject>, id: Int): JsonObject? =
    frames.firstOrNull { matchesId(it, id) }

private fun matchesId(message: JsonObject, id: Int): Boolean =
    message["method"] == null && (message["id"] as? JsonPrimitive)?.contentOrNull?.trim() == id.toString()

private fun rpcErrorMessage(error: JsonElement): String = when (error) {
    is JsonObject -> error["message"].stringOrNull()?.takeIf { it.isNotBlank() } ?: error.toString()
    is JsonPrimitive -> error.contentOrNull?.takeIf { it.isNotBlank() } ?: error.toString()
    else -> error.toString()
}

private fun merged(base: Map<String, String>, extra: Map<String, String>): Map<String, String> {
    val out = LinkedHashMap(base)
    out.putAll(extra)
    return out
}

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

/** 上游返回非 2xx / 形状不符时抛；由上层统一转成 [explainMcpError] 的文案。 */
private class McpTransportException(message: String) : Exception(message)

private suspend fun <T> withTimeoutApi(timeoutMs: Long, message: String, block: suspend () -> T): T =
    withTimeoutOrNull(timeoutMs) { block() } ?: throw ApiException(0, message)

/** 除取消外，把任何异常转成用户/模型可读的文案（`mcpApi.ts:80-104`）。 */
private suspend fun <T> guarded(block: suspend () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: ApiException) {
    throw error
} catch (error: Exception) {
    throw ApiException(0, explainMcpError(error.message ?: error.toString()))
}

// -------------------------------------------------------------------------------------------------
// URL 校验 / 错误解释
// -------------------------------------------------------------------------------------------------

/**
 * 只允许 http(s)，并拒绝 URL 内嵌用户名/密码（`mcpApi.ts:61-67` + `server.ts:274`）。
 *
 * @param schemeMessage 协议不对时的文案：`call` 用「MCP 地址必须为 HTTP 或 HTTPS」，
 *        `testConnection` 用「远程 MCP 地址只允许 http 或 https」，两处是不同字符串，不能合并。
 */
private fun validateUrl(raw: String, schemeMessage: String): String {
    if (raw.isBlank()) throw ApiException(0, "MCP 地址不能为空")
    val scheme = raw.substringBefore("://", "").lowercase()
    if (scheme.isEmpty()) throw ApiException(0, "MCP 地址格式不正确")
    if (scheme != "http" && scheme != "https") throw ApiException(0, schemeMessage)
    val authority = raw.substringAfter("://").substringBefore('/')
    if (authority.contains('@')) throw ApiException(0, "MCP URL 不允许内嵌用户名或密码")
    return raw
}

/**
 * `explainNativeMcpError`（`mcpApi.ts:80-104`）的 5 条分支，**文案逐字照抄**，顺序也不能变。
 */
internal fun explainMcpError(message: String): String = when {
    Regex("localhost|127\\.0\\.0\\.1", RegexOption.IGNORE_CASE).containsMatchIn(message) ->
        "Android 中 localhost / 127.0.0.1 指向手机自身，不是运行 MCP 的电脑。请改用电脑的局域网 IP。原始错误：$message"
    Regex("cleartext|CLEARTEXT").containsMatchIn(message) ->
        "Android 阻止了明文 HTTP 请求。请确认 APK 已允许 HTTP 流量。原始错误：$message"
    Regex("Failed to fetch|NetworkError|Load failed|network request failed").containsMatchIn(message) ->
        "手机无法访问 MCP 地址。请检查网络、服务监听地址、防火墙和跨域配置。原始错误：$message"
    Regex("404|405|Not Found|Method Not Allowed").containsMatchIn(message) ->
        "MCP 地址可访问，但端点或传输类型可能不匹配。请确认选择的是 Streamable HTTP 还是 SSE。原始错误：$message"
    else -> "Android 直连 MCP 失败：$message"
}
