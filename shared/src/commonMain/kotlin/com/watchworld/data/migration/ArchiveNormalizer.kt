package com.watchworld.data.migration

import com.watchworld.model.ArchiveV5
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** v5 存档里存会话列表的键。 */
private const val KEY_SESSIONS = "sessions"

/** v5 存档里存 MCP 服务列表的键。 */
private const val KEY_MCP_SERVERS = "mcpServers"

/**
 * 把 v5 存档的**原始 JSON 树**揉成「一定能解码成 `ArchiveV5`」的形状。
 *
 * 为什么要在解码**之前**动 JSON，而不是解码完再修模型：
 *
 * 1. **类型错配会让解码直接抛**。TS 的 `Array.isArray(...)` / `typeof ... === 'boolean'` 是运行时
 *    兜底，Kotlin 的 `@Serializable` 没有等价物（`coerceInputValues` 只管 `null` 与未知 enum）。
 *    老存档里一个字段类型不对，整份存档就导入失败 —— 这比丢一个字段严重得多。
 * 2. **有些信息解码后就没了**。旧版 MCP 的 `type: "custom_api"` 会被 `coerceInputValues` 落成
 *    默认值 `streamable_http`，`endpoint` 字段模型里压根不存在。等拿到模型再判断，已经晚了。
 *
 * ⚠️ 本函数**不做业务规则**（除了 MCP 的形态迁移），六条兼容逻辑的语义仍只在
 * [normalizeChatSession] 与 [sanitizeSessionJson] 里各一份。它跑完还要接
 * [normalizeArchive] —— 少了那一步，第 ②③⑤ 条就不会生效。
 *
 * @param now 补 MCP `id` 用的时间戳（见 [normalizeMcpServerJson]）；显式传入以便测试可复现。
 */
fun normalizeArchiveJson(root: JsonObject, now: Long): JsonObject {
    var result = root.normalizeSessions()
    result = result.normalizeMcpServers(now)
    return result
}

/**
 * 解码**之后**的模型层归一化：对每个会话跑一遍 [normalizeChatSession]。
 *
 * 分两步走的原因：语义要在模型层（一处规则、一处实现），但类型错配必须在解码前解决。
 * [normalizeArchiveJson] 解决「解不开」，本函数解决「解开了但不对」。
 * 两步都必须跑 —— 这是 `parseArchive` 里最容易漏掉的一环。
 */
fun normalizeArchive(archive: ArchiveV5): ArchiveV5 =
    archive.copy(sessions = archive.sessions.map { normalizeChatSession(it) })

private fun JsonObject.normalizeSessions(): JsonObject {
    val sessions = this[KEY_SESSIONS] as? JsonArray ?: return this
    // TS `getSessions`：`filter(session => Boolean(session && typeof session === 'object'))`
    val normalized = sessions.mapNotNull { (it as? JsonObject)?.let(::sanitizeSessionJson) }
    return JsonObject(this + (KEY_SESSIONS to JsonArray(normalized)))
}

private fun JsonObject.normalizeMcpServers(now: Long): JsonObject {
    val servers = this[KEY_MCP_SERVERS] as? JsonArray ?: return this
    // TS 的 `.map(normalizeMcpServer).filter(Boolean)`：不支持的类型与无地址的配置直接消失。
    val normalized = servers.mapIndexedNotNull { index, element ->
        normalizeMcpServerJson(element, index = index, now = now)
    }
    return JsonObject(this + (KEY_MCP_SERVERS to JsonArray(normalized)))
}
