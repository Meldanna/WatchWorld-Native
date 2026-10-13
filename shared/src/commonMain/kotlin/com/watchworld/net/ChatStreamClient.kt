package com.watchworld.net

import com.watchworld.model.WwJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** 上游返回非 2xx、或流里出现错误帧时抛出。 */
class ApiException(val statusCode: Int, message: String) : Exception(message)

/**
 * 聊天流式客户端 —— **迁移的技术核心**（PLAN §6.1）。
 *
 * The raw line transport remains scoped to Ktor's `execute` block. T04 adds parsing and
 * protocol normalization above that transport without buffering the response.
 */
class ChatStreamClient(private val client: HttpClient) {

    /** 关闭底层 HttpClient（持有连接池与线程）。 */
    fun close() = client.close()

    /**
     * 上游 SSE 的**原始行流**。`preparePost(...).execute {}` 中逐行读，出块即释放连接。
     * 只有错误响应体允许调用 `bodyAsText()`，用于取得有限长度的诊断信息。
     */
    fun streamSse(
        url: String,
        headers: Map<String, String> = emptyMap(),
        buildBody: HttpRequestBuilder.() -> Unit = {},
    ): Flow<String> = flow {
        client.preparePost(url) {
            headers.forEach { (key, value) -> header(key, value) }
            buildBody()
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw ApiException(response.status.value, response.bodyAsText().take(300))
            }
            val channel: ByteReadChannel = response.body()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                emit(line)
            }
        }
    }

    /**
     * OpenAI-compatible event stream using T03's temporary provider configuration.
     * Claude/Gemini wire adapters are independently usable; their persisted request
     * configuration and request builders belong to later migration tasks.
     */
    fun streamAssistantEvents(prompt: String): Flow<ProviderStreamEvent> = flow {
        val config = HardcodedChatProvider.resolve()
        if (!config.isConfigured) {
            throw ApiException(
                0,
                "未配置 API Key。请设置环境变量 WW_T03_API_KEY（可选：WW_T03_BASE_URL / WW_T03_MODEL）后重启。",
            )
        }

        val adapter = ProviderStreamAdapters.forProvider(ProviderKind.OPENAI)
        val lines = streamSse(
            url = config.chatCompletionsUrl,
            headers = mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "Accept" to "text/event-stream",
            ),
        ) {
            contentType(ContentType.Application.Json)
            setBody(buildOpenAiRequest(config.model, prompt))
        }
        SseParser.parse(lines).collect { event ->
            adapter.adapt(event).forEach { emit(it) }
        }
    }

    /** UI-facing text-only view; reasoning and tool deltas remain available to later tasks. */
    fun streamAssistantText(prompt: String): Flow<String> = flow {
        streamAssistantEvents(prompt)
            .filterIsInstance<ProviderStreamEvent.TextDelta>()
            .collect { emit(it.text) }
    }

    /**
     * T09：把**已经装配好**的请求体发出去，边收边归一化成协议无关事件。
     *
     * 与 [streamAssistantEvents] 的区别只有一个 —— 请求体由调用方（`ToolCallLoop`）给，因为工具
     * 循环要按轮次重建 messages/tools。传输与解析仍是同一条路径：`preparePost().execute{}` +
     * [streamSse] 的逐行读，**不缓冲**（CONSTRAINTS §3.2）。
     *
     * @param adapter 由调用方按 provider 选好：**必须有状态的那一份**（Claude 的 block 表跨事件）。
     */
    fun streamEvents(
        url: String,
        headers: Map<String, String>,
        adapter: ProviderStreamAdapter,
        body: String,
    ): Flow<ProviderStreamEvent> = flow {
        val lines = streamSse(url, headers) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        SseParser.parse(lines).collect { frame ->
            adapter.adapt(frame).forEach { emit(it) }
        }
    }

    /**
     * T09：非流式的一次请求（`stream = false`）。走同一个 HttpClient，错误处理与流式一致：
     * 非 2xx 抛 [ApiException]，响应体里带 `error` 也抛。
     */
    suspend fun postJson(url: String, headers: Map<String, String>, body: String): JsonElement {
        val element = client.preparePost(url) {
            headers.forEach { (key, value) -> header(key, value) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw ApiException(response.status.value, response.bodyAsText().take(300))
            }
            WwJson.parseToJsonElement(response.bodyAsText())
        }
        val error = (element as? JsonObject)?.get("error")
        if (error != null) throw ApiException(0, error.toString())
        return element
    }
}

/** OpenAI-compatible request body for T03's temporary single-turn chat path. */
private fun buildOpenAiRequest(model: String, prompt: String): String = buildJsonObject {
    put("model", model)
    put("stream", true)
    putJsonArray("messages") {
        addJsonObject {
            put("role", "user")
            put("content", prompt)
        }
    }
}.toString()
