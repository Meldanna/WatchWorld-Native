package com.watchworld.domain

import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.model.WwJson
import com.watchworld.platform.currentTimeMillis
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 会话的轻量导入 / 导出 —— **「导出」与「导入」必须配套**：
 * 导出的 MD / JSON 都能被侧边栏「导入对话」读回来重建窗口
 * （对标参考版 `App.tsx` 的 `handleImportSession` / `SessionTextModal.tsx` 的 `buildExportText`）。
 *
 * 导出 JSON 的形状是 `{ title?, messages: [...] }`（或纯消息数组），正好是 `handleImportSession`
 * 接受的格式（`App.tsx:713-726`：数组包成 `{messages}`，对象直接读 `messages`）。
 */

/** 导入 / 导出用的轻量 JSON 对象形状。`messages` 是完整 [ChatMessage]（自带默认值容缺）。 */
@Serializable
internal data class ExportedConversation(
    val title: String? = null,
    val messages: List<ChatMessage> = emptyList(),
)

/** 解析完成的会话：标题 + 已按序重排楼号的消息。 */
data class ParsedConversation(
    val title: String,
    val messages: List<ChatMessage>,
)

@OptIn(ExperimentalSerializationApi::class)
private val conversationJson: Json = Json(WwJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

// ──────────────────────────────── 导出

/** 当前生效版本的正文（参考版 `textOf`）。 */
internal fun messageLatestText(m: ChatMessage): String {
    if (m.role == Role.ASSISTANT) {
        val version = m.versions.getOrNull(m.currentVersionIndex) ?: m.versions.firstOrNull()
        if (version != null) return version.content
    }
    return m.content
}

/**
 * MD 导出（参考版 `buildExportText`）。**必须用 `·` 分隔**（`## #楼 · 我|AI|系统 · [tag]`），
 * 与 [parseConversationMarkdown] 的正则配套 —— 老版本用的 `.` 会导出去读不回来。
 */
fun buildSessionExportMarkdown(session: ChatSession): String = buildString {
    appendLine("# ${session.title.ifBlank { "未命名窗口" }}")
    appendLine()
    appendLine("> 共 ${session.messages.size} 条消息")
    appendLine()
    appendLine("---")
    appendLine()
    session.messages.forEachIndexed { idx, m ->
        val floor = m.floorNumber ?: (idx + 1)
        val who = when (m.role) {
            Role.ASSISTANT -> "AI"
            Role.SYSTEM -> "系统"
            Role.USER -> "我"
        }
        val tag = m.codeTag?.let { code ->
            m.descriptionTag?.let { "$code·$it" } ?: code
        } ?: m.timelineTag ?: "通用"
        appendLine("## #$floor · $who · [$tag]")
        appendLine()
        appendLine(messageLatestText(m))
        appendLine()
        appendLine("---")
        appendLine()
    }
}

/** JSON 导出：`{ title, messages }`，可被 [parseConversationJson] 原样读回。 */
fun buildSessionExportJson(session: ChatSession): String =
    conversationJson.encodeToString(
        ExportedConversation(
            title = session.title.ifBlank { null },
            messages = session.messages,
        ),
    )

// ──────────────────────────────── 导入

/**
 * 按扩展名分发导入文件（侧边栏「导入对话」入口）。
 *
 * - `.json` → [parseConversationJson]（形状不符会抛）
 * - 其余 → [parseConversationMarkdown]（找不到消息返回 null，调用方报「格式不对」）
 *
 * @throws IllegalArgumentException 文件内容为空 / JSON 形状不符时。
 */
fun parseConversationFile(fileName: String, content: String): ParsedConversation {
    if (content.isEmpty()) throw IllegalArgumentException("文件内容是空的。")
    return if (fileName.lowercase().endsWith(".json")) {
        parseConversationJson(content)
    } else {
        parseConversationMarkdown(content)
            ?: throw IllegalArgumentException("Markdown 中未找到“## #楼层 · 我/AI”格式的对话消息。")
    }
}

/**
 * 解析导入的 JSON（参考版 `handleImportSession` 的 JSON 分支）：
 * 接受「消息数组」或「`{ title?, messages: [...] }` 对象」两种形状。
 * 角色非法的消息与空正文被丢弃；全空则报错。
 *
 * @throws IllegalArgumentException 形状不合法或没有可导入的消息时。
 */
fun parseConversationJson(text: String): ParsedConversation {
    val element = runCatching { WwJson.parseToJsonElement(text) }.getOrElse {
        throw IllegalArgumentException("JSON 解析失败：${it.message ?: it::class.simpleName}")
    }
    val messagesJson: JsonArray = when (element) {
        is JsonArray -> element
        is JsonObject -> element["messages"] as? JsonArray
            ?: throw IllegalArgumentException("JSON 必须是消息数组，或包含 messages 数组的窗口对象。")
        else -> throw IllegalArgumentException("JSON 必须是消息数组，或包含 messages 数组的窗口对象。")
    }
    val title = (element as? JsonObject)?.get("title")?.jsonPrimitive
        ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    val messages = messagesJson.mapIndexed { index, raw -> decodeImportedMessage(raw, index) }.filterNotNull()
    if (messages.isEmpty()) throw IllegalArgumentException("文件中没有可导入的有效消息。")
    return ParsedConversation(title ?: "导入的对话", messages)
}

/**
 * 解析导入的 MD（参考版 `parseMarkdown`）。找不到消息返回 `null`（标题回退到「导入的对话」）。
 */
fun parseConversationMarkdown(text: String): ParsedConversation? {
    val title = Regex("""^#\s+(.+)$""", setOf(RegexOption.MULTILINE))
        .find(text)?.groupValues?.get(1)?.trim()
    val marker = Regex(
        """^##\s+#?\d+\s+·\s+(我|AI|系统)(?:\s+·\s+\[[^\]]*\])?\s*$""",
        setOf(RegexOption.MULTILINE),
    )
    val matches = marker.findAll(text).toList()
    if (matches.isEmpty()) return null

    val stamp = currentTimeMillis()
    val messages = matches.mapIndexed { index, match ->
        val start = match.range.last + 1
        val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
        val content = text
            .substring(start, end)
            .trimStart()
            .replace(Regex("""\n\s*---\s*$""", setOf(RegexOption.MULTILINE)), "")
            .trim()
        ChatMessage(
            id = "imported-$stamp-$index",
            role = when (match.groupValues[1]) {
                "AI" -> Role.ASSISTANT
                "系统" -> Role.SYSTEM
                else -> Role.USER
            },
            content = content,
            floorNumber = index + 1,
            timestamp = stamp + index,
        )
    }.filter { it.content.isNotBlank() }
    if (messages.isEmpty()) return null
    return ParsedConversation(title?.trim() ?: "导入的对话", messages)
}

/**
 * 解码一条导入的消息（参考版 `normalizeMessage`）：缺 `id` 自动补；`role` 必须是
 * user / assistant / system（非法的整条丢弃）；正文取顶层、缺失回退 `versions[0]`；
 * 空正文丢弃；楼号统一重排为 `index+1`。
 */
private fun decodeImportedMessage(raw: kotlinx.serialization.json.JsonElement, index: Int): ChatMessage? {
    if (raw !is JsonObject) return null
    val role = (raw["role"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (role != null && role != "user" && role != "assistant" && role != "system") return null

    val patched = if (raw["id"] != null) {
        raw
    } else {
        JsonObject(raw.toMap() + ("id" to JsonPrimitive("imported-$index")))
    }
    val msg = runCatching {
        WwJson.decodeFromJsonElement(ChatMessage.serializer(), patched)
    }.getOrNull() ?: return null

    val content = msg.content.ifBlank { msg.versions.firstOrNull()?.content.orEmpty() }
    if (content.isBlank()) return null
    return msg.copy(content = content, floorNumber = index + 1)
}