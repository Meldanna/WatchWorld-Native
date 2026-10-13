package com.watchworld.net

import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.platform.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Provider 连通性测试 + 模型列表拉取（对标 Web 版 `api.ts` 的
 * `testProviderConnection` / `fetchModelsFromProvider`，走的是 `apiNative.ts` 的原生直连路径）。
 *
 * ⚠️ 与 Web 版有两处**有意差异**：
 *  1. Web 浏览器端经 `server.ts` / Cloudflare Worker 的 `/api/…` 代理转发（受 CORS 限制）；
 *     原生桌面 / Android 是进程内应用，没有 CORS，因此**直接对目标 URL 发 HTTP**，
 *     与 `WebSearchClient` / `WebDavClient` 的处理一致。
 *  2. Web 的「测试连接」发一次 `Ping` 聊天请求；这里同样发最小聊天请求（`maxTokens` 很小），
 *     保持「真的打通了推理端点」的语义，而不是只 ping `/models`。
 */

/** 一条连通性测试结果（对标 `testProviderConnection` 的返回）。 */
data class ProviderTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
)

/** 模型列表拉取结果（对标 `fetchModelsFromProvider` 的返回）。 */
data class ProviderModelsResult(
    val models: List<String>,
    val error: String? = null,
)

private val DEFAULT_BASE_URLS: Map<ApiProviderType, String> = mapOf(
    ApiProviderType.OPENAI to "https://api.openai.com/v1",
    ApiProviderType.DEEPSEEK to "https://api.deepseek.com/v1",
    ApiProviderType.OPENROUTER to "https://openrouter.ai/api/v1",
    ApiProviderType.GROQ to "https://api.groq.com/openai/v1",
    ApiProviderType.OLLAMA to "http://localhost:11434/v1",
    ApiProviderType.CLAUDE to "https://api.anthropic.com/v1",
    ApiProviderType.GEMINI to "https://generativelanguage.googleapis.com/v1beta",
)

class ProviderApi(private val client: HttpClient) {

    /** 从 API 拉取该 Provider 支持的模型列表。 */
    suspend fun fetchModels(provider: ApiProviderConfig): ProviderModelsResult = try {
        val models = when (provider.type) {
            ApiProviderType.GEMINI -> fetchGeminiModels(provider)
            ApiProviderType.CLAUDE -> fetchClaudeModels(provider)
            else -> fetchOpenAiCompatibleModels(provider)
        }
        if (models.isEmpty()) {
            ProviderModelsResult(emptyList(), "未获取到任何模型，请检查 API Key 和 Base URL")
        } else {
            ProviderModelsResult(models)
        }
    } catch (error: Exception) {
        ProviderModelsResult(emptyList(), error.message ?: "网络请求失败")
    }

    /** 测试 Provider API 连通性：发一次最小聊天请求并计时。 */
    suspend fun testConnection(provider: ApiProviderConfig): ProviderTestResult {
        val started = currentTimeMillis()
        return try {
            val kind = provider.kind()
            val base = baseUrl(provider).trimEnd('/')
            val model = provider.defaultModel.ifBlank { provider.models.firstOrNull() ?: "gemini-2.0-flash" }
            val url = chatUrl(provider, kind, model)
            val response = client.request(url) {
                method = HttpMethod.Post
                headers(provider, kind).forEach { (key, value) -> header(key, value) }
                contentType(ContentType.Application.Json)
                setBody(chatPingBody(kind, model))
            }
            val latency = currentTimeMillis() - started
            val status = response.status.value
            if (status in 200..299) {
                ProviderTestResult(true, latency, "连接正常，耗时 ${latency}ms")
            } else {
                ProviderTestResult(false, latency, "连接失败 ($status)")
            }
        } catch (error: Exception) {
            ProviderTestResult(false, currentTimeMillis() - started, error.message ?: "网络连接超时或地址不可达")
        }
    }

    // ────────────────────────────────── 模型列表

    private suspend fun fetchGeminiModels(provider: ApiProviderConfig): List<String> {
        if (provider.apiKey.isNullOrBlank()) throw IllegalStateException("请先配置 Gemini API Key")
        val url = "https://generativelanguage.googleapis.com/v1beta/models" +
            "?key=${provider.apiKey.encodeURLParameter()}&pageSize=50"
        val body = client.getText(url, emptyMap())
        return body["models"].asArray().mapNotNull { it.asObject()?.get("name").asString() }
            .map { it.removePrefix("models/") }
            .filter { it.startsWith("gemini") }
    }

    private suspend fun fetchClaudeModels(provider: ApiProviderConfig): List<String> {
        val base = (provider.baseUrl?.takeIf { it.isNotBlank() } ?: "https://api.anthropic.com/v1").trimEnd('/')
        val body = client.getText(
            "$base/models",
            mapOf(
                "x-api-key" to provider.apiKey.orEmpty(),
                "anthropic-version" to "2023-06-01",
            ),
        )
        return body["data"].asArray().mapNotNull { it.asObject()?.get("id").asString() }
    }

    private suspend fun fetchOpenAiCompatibleModels(provider: ApiProviderConfig): List<String> {
        val base = baseUrl(provider).trimEnd('/')
        val requestHeaders = headers(provider, ProviderKind.OPENAI)
        val body = client.getText("$base/models", requestHeaders)
        var models: List<String> = when {
            body["models"] is JsonArray -> body["models"].asArray().mapNotNull { element ->
                val obj = element.asObject()
                (obj?.get("name") ?: obj?.get("id")).asString() ?: (element as? JsonPrimitive)?.contentOrNull
            }
            body["data"] is JsonArray -> body["data"].asArray().mapNotNull { element ->
                val obj = element.asObject()
                (obj?.get("id") ?: obj?.get("name")).asString() ?: (element as? JsonPrimitive)?.contentOrNull
            }
            else -> emptyList()
        }
        // OpenAI 官方（没填自定义 baseUrl）只保留对话模型，避免刷屏。
        if (provider.type == ApiProviderType.OPENAI && provider.baseUrl.isNullOrBlank()) {
            models = models.filter {
                it.startsWith("gpt") || it.startsWith("o1") || it.startsWith("o3") || it.startsWith("chatgpt")
            }
        }
        return models
    }

    // ────────────────────────────────── 私有工具

    private suspend fun HttpClient.getText(url: String, headers: Map<String, String>): JsonObject {
        val response = request(url) {
            method = HttpMethod.Get
            headers.forEach { (key, value) -> header(key, value) }
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val detail = text.trim().take(300)
            val suffix = if (detail.isEmpty()) "" else "：$detail"
            throw IllegalStateException("模型列表获取失败 (${response.status.value})$suffix")
        }
        val json = try {
            com.watchworld.model.WwJson.parseToJsonElement(text)
        } catch (error: SerializationException) {
            val contentType = response.headers[HttpHeaders.ContentType] ?: "未知"
            throw IllegalStateException(
                "模型接口返回了非 JSON 响应（HTTP ${response.status.value}，Content-Type: $contentType），请检查 Base URL 和 API 路径",
                error,
            )
        }
        return json as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun baseUrl(provider: ApiProviderConfig): String =
        provider.baseUrl?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URLS[provider.type]
            ?: DEFAULT_BASE_URLS.getValue(ApiProviderType.OPENAI)
}

/** gemini → Gemini、claude → Claude，其余（含 custom）全当 OpenAI 兼容。 */
internal fun ApiProviderConfig.kind(): ProviderKind = when (type) {
    ApiProviderType.GEMINI -> ProviderKind.GEMINI
    ApiProviderType.CLAUDE -> ProviderKind.CLAUDE
    else -> ProviderKind.OPENAI
}

/**
 * 补全后的聊天端点。与 `ToolCallLoop.chatUrl` 同构；Gemini 的 key 走 query 参数，
 * 且这里只做**非流式**测试，所以直接用 `:generateContent`。
 */
private fun chatUrl(provider: ApiProviderConfig, kind: ProviderKind, model: String): String {
    val base = (
        provider.baseUrl?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URLS[provider.type]
            ?: DEFAULT_BASE_URLS.getValue(ApiProviderType.OPENAI)
        ).trimEnd('/')
    return when (kind) {
        ProviderKind.GEMINI ->
            "$base/models/${model.encodeURLPathPart()}:generateContent?key=${provider.apiKey.orEmpty().encodeURLParameter()}"
        ProviderKind.CLAUDE -> if (base.endsWith("/messages")) base else "$base/messages"
        ProviderKind.OPENAI -> if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }
}

private fun headers(provider: ApiProviderConfig, kind: ProviderKind): Map<String, String> {
    val result = linkedMapOf<String, String>()
    result.putAll(provider.customHeaders.orEmpty())
    val apiKey = provider.apiKey.orEmpty()
    when (kind) {
        ProviderKind.CLAUDE -> {
            result["x-api-key"] = apiKey
            result["anthropic-version"] = "2023-06-01"
        }
        ProviderKind.OPENAI -> if (
            apiKey.isNotEmpty() && result.keys.none { it.equals("Authorization", ignoreCase = true) }
        ) {
            result["Authorization"] = "Bearer " + apiKey
        }
        ProviderKind.GEMINI -> Unit // key 在 URL 上
    }
    return result
}

private fun chatPingBody(kind: ProviderKind, model: String): String = buildJsonObject {
    put("model", model)
    when (kind) {
        ProviderKind.OPENAI -> {
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "Ping") } }
            put("max_tokens", 5)
            put("stream", false)
        }
        ProviderKind.CLAUDE -> {
            put("max_tokens", 5)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "Ping") } }
            put("stream", false)
        }
        ProviderKind.GEMINI -> {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", "Ping") } }
                }
            }
            putJsonObject("generationConfig") { put("maxOutputTokens", 5) }
        }
    }
}.toString()

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
