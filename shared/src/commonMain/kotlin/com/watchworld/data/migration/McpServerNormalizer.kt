package com.watchworld.data.migration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 已不再支持的 MCP 传输类型：读到就直接丢弃（`storage.ts:118-119`）。 */
private val UNSUPPORTED_MCP_TYPES = setOf("custom_api", "stdio")

/** `storage.ts:130` 的兜底名称。 */
private const val FALLBACK_MCP_NAME = "未命名 MCP"

/**
 * `storage.ts:114-144 normalizeMcpServer()` —— 把旧版 MCP 配置迁移到远程 HTTP / SSE 形态。
 *
 * ⚠️ **必须在 JSON 层做，不能等解码成 [com.watchworld.model.McpServerConfig] 之后再做**：
 * - 旧版的 `type: "custom_api"` 会被 `coerceInputValues` 悄悄落成默认值 `streamable_http`，
 *   此时已经分不出「本来就是 streamable_http」和「是待丢弃的 custom_api」了；
 * - 旧版的 `endpoint` 模型里根本没有这个字段，解码即丢 —— 这正是「导入老存档不丢数据」
 *   最容易被漏掉的一处（`fixtures/sse/archive-v5-legacy.json` 的两条 SSE 服务就靠它取 URL）。
 *
 * 与 TS 的**唯一有意偏差**：`id` 缺失时 TS 会生成 `mcp-<Date.now()>-<random>`，本实现用
 * `mcp-<now>-<index>`。`Math.random()` 在 commonMain 里没有对等物，且 TS 那种写法每次
 * 读取都会重新生成 id（`getMcpServers` 也调用本函数），是个已知毛病，不照抄。
 *
 * @param index 该服务在数组中的序号，仅用于补 `id` 时保证同一份存档内不重复。
 * @param now 补 `id` 用的时间戳；显式传入以便测试可复现。
 * @return 归一化后的对象；该服务应被丢弃时返回 `null`。
 */
fun normalizeMcpServerJson(raw: JsonElement, index: Int, now: Long): JsonObject? {
    val server = raw as? JsonObject ?: return null

    // `String(server.type || '').toLowerCase()`
    val oldType = server.stringValue("type").orEmpty().lowercase()
    if (oldType in UNSUPPORTED_MCP_TYPES) return null

    val type = if (oldType == "sse") "sse" else "streamable_http"

    // `String(server.url || endpoint || '').trim()`，其中 endpoint 只认非空字符串。
    val endpoint = server.stringValue("endpoint").orEmpty().trim()
    val url = (server.stringValue("url").takeIf { !it.isNullOrEmpty() } ?: endpoint).trim()
    if (url.isEmpty()) return null

    // `String(server.authToken || server.apiKey || '').trim() || undefined`
    val authToken = (
        server.stringValue("authToken").takeIf { !it.isNullOrEmpty() }
            ?: server.stringValue("apiKey")
            ?: ""
        ).trim()

    return buildJsonObject {
        put("id", server.stringValue("id").orEmpty().ifEmpty { "mcp-$now-$index" })
        put("name", server.stringValue("name").orEmpty().ifEmpty { FALLBACK_MCP_NAME })
        put("type", type)
        put("url", url)
        if (authToken.isNotEmpty()) put("authToken", authToken)
        server.stringMap("headers")?.let { put("headers", it) }
        put("status", if (server.stringValue("status") == "connected") "connected" else "disconnected")
        put("tools", server.toolArray())
        server.stringValue("description")?.let { put("description", it) }
        server.longValue("lastTestedAt")?.let { put("lastTestedAt", it) }
        server.longValue("lastLatencyMs")?.let { put("lastLatencyMs", it) }
        server.stringValue("lastError")?.let { put("lastError", it) }
        server.stringValue("serverName")?.let { put("serverName", it) }
        server.stringValue("serverVersion")?.let { put("serverVersion", it) }
    }
}

/** 非 JsonNull 的 JsonPrimitive 才取得到文本（数字/布尔按 TS 的 `String(...)` 语义转成字面量）。 */
private fun JsonElement?.asPrimitiveText(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.stringValue(key: String): String? = this[key].asPrimitiveText()

/** 对应 TS 的 `typeof x === 'number'`：字符串形式的数字不算。 */
private fun JsonObject.longValue(key: String): Long? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    return primitive.content.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

/** `Record<string, string>`：值不是字符串的键一律剔除，否则解码成 `Map<String, String>` 会炸。 */
private fun JsonObject.stringMap(key: String): JsonObject? {
    val entries = (this[key] as? JsonObject) ?: return null
    val stringsOnly = entries.mapNotNull { (name, value) ->
        value.asPrimitiveText()?.let { name to JsonPrimitive(it) }
    }
    return if (stringsOnly.isEmpty()) null else JsonObject(stringsOnly.toMap())
}

/** `Array.isArray(server.tools) ? server.tools : []`，并剔除解不成 `McpTool` 的条目。 */
private fun JsonObject.toolArray(): JsonArray {
    val tools = this["tools"] as? JsonArray ?: return JsonArray(emptyList())
    return JsonArray(
        tools.mapNotNull { tool ->
            val obj = tool as? JsonObject ?: return@mapNotNull null
            val name = obj.stringValue("name") ?: return@mapNotNull null
            // McpTool.name 是必填，缺 name 的条目留着只会在解码时炸掉整份存档。
            buildJsonObject {
                put("name", name)
                put("description", obj.stringValue("description").orEmpty())
                obj.stringValue("parametersSchema")?.let { put("parametersSchema", it) }
            }
        },
    )
}
