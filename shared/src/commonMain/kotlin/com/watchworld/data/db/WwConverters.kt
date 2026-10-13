package com.watchworld.data.db

import androidx.room.TypeConverter
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ApiProviderType
import com.watchworld.model.FileAccess
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTool
import com.watchworld.model.McpTransportType
import com.watchworld.model.MessageVersion
import com.watchworld.model.RegexScope
import com.watchworld.model.Role
import com.watchworld.model.TimelineDescriptionTagRequest
import com.watchworld.model.WwJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Room 的列类型转换：JSON 列 + 枚举列。
 *
 * ### 为什么枚举要手写 `when`，而不是用 kotlinx 的枚举序列化器自动取 `@SerialName`
 *
 * 自动取值要靠 `descriptor.getElementName(ordinal)` 这类反射式访问，读代码的人得先在脑子里
 * 还原一遍机制。这里只有 7 个枚举，写全 `when` 之后**存档字面量直接摆在代码里**，
 * 跟 `model/Enums.kt` 的 `@SerialName` 逐行对照就能核对 —— 这比省 30 行更重要。
 *
 * ### 为什么要给「可空」和「不可空」各写一份
 *
 * Room 的类型转换是**按签名精确匹配**的：可空列要 `T? -> String?`，非空列要 `T -> String`。
 * 只写可空版会让非空列找不到转换器；只写非空版则接不住 `null`。
 *
 * ### JSON 列的可空性是语义的一部分
 *
 * `List<String>?` 的 `null`（存档里字段缺失）与 `emptyList()`（存档里是 `[]`）**不是一回事**。
 * 转换器把 `null` 映射成 SQL `NULL`、把空数组映射成 `"[]"`，两态因此都能原样回来。
 * 这就是为什么这里**不能**写成 `value ?: emptyList()` 那种"顺手兜底"。
 */
class WwConverters {

    // ---------------- JSON 列：集合 ----------------

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? = value?.let { WwJson.encodeToString(it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? = value?.let { WwJson.decodeFromString(it) }

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>?): String? = value?.let { WwJson.encodeToString(it) }

    @TypeConverter
    fun jsonToStringMap(value: String?): Map<String, String>? = value?.let { WwJson.decodeFromString(it) }

    @TypeConverter
    fun mcpToolListToJson(value: List<McpTool>): String = WwJson.encodeToString(value)

    @TypeConverter
    fun jsonToMcpToolList(value: String): List<McpTool> = WwJson.decodeFromString(value)

    @TypeConverter
    fun messageVersionListToJson(value: List<MessageVersion>): String = WwJson.encodeToString(value)

    @TypeConverter
    fun jsonToMessageVersionList(value: String): List<MessageVersion> = WwJson.decodeFromString(value)

    @TypeConverter
    fun tagRequestListToJson(value: List<TimelineDescriptionTagRequest>?): String? =
        value?.let { WwJson.encodeToString(it) }

    @TypeConverter
    fun jsonToTagRequestList(value: String?): List<TimelineDescriptionTagRequest>? =
        value?.let { WwJson.decodeFromString(it) }

    // ---------------- JSON 列：会话配置 blob ----------------

    @TypeConverter
    fun sessionConfigToJson(value: SessionConfigBlob): String = WwJson.encodeToString(value)

    @TypeConverter
    fun jsonToSessionConfig(value: String): SessionConfigBlob = WwJson.decodeFromString(value)

    // ---------------- 枚举列 ----------------

    @TypeConverter
    fun apiProviderTypeToString(value: ApiProviderType): String = when (value) {
        ApiProviderType.GEMINI -> "gemini"
        ApiProviderType.OPENAI -> "openai"
        ApiProviderType.CLAUDE -> "claude"
        ApiProviderType.DEEPSEEK -> "deepseek"
        ApiProviderType.OLLAMA -> "ollama"
        ApiProviderType.OPENROUTER -> "openrouter"
        ApiProviderType.GROQ -> "groq"
        ApiProviderType.CUSTOM -> "custom"
    }

    @TypeConverter
    fun stringToApiProviderType(value: String): ApiProviderType = when (value) {
        "gemini" -> ApiProviderType.GEMINI
        "openai" -> ApiProviderType.OPENAI
        "claude" -> ApiProviderType.CLAUDE
        "deepseek" -> ApiProviderType.DEEPSEEK
        "ollama" -> ApiProviderType.OLLAMA
        "openrouter" -> ApiProviderType.OPENROUTER
        "groq" -> ApiProviderType.GROQ
        // 未知取值退回 CUSTOM:模型层 `ApiProviderType` 的默认值也是它(`coerceInputValues` 同语义)。
        else -> ApiProviderType.CUSTOM
    }

    @TypeConverter
    fun regexScopeToString(value: RegexScope): String = when (value) {
        RegexScope.INPUT -> "input"
        RegexScope.OUTPUT -> "output"
        RegexScope.BOTH -> "both"
    }

    @TypeConverter
    fun stringToRegexScope(value: String): RegexScope = when (value) {
        "input" -> RegexScope.INPUT
        "output" -> RegexScope.OUTPUT
        else -> RegexScope.BOTH
    }

    @TypeConverter
    fun mcpTransportTypeToString(value: McpTransportType): String = when (value) {
        McpTransportType.STREAMABLE_HTTP -> "streamable_http"
        McpTransportType.SSE -> "sse"
    }

    @TypeConverter
    fun stringToMcpTransportType(value: String): McpTransportType = when (value) {
        "sse" -> McpTransportType.SSE
        else -> McpTransportType.STREAMABLE_HTTP
    }

    @TypeConverter
    fun mcpServerStatusToString(value: McpServerStatus): String = when (value) {
        McpServerStatus.CONNECTED -> "connected"
        McpServerStatus.DISCONNECTED -> "disconnected"
        McpServerStatus.TESTING -> "testing"
    }

    @TypeConverter
    fun stringToMcpServerStatus(value: String): McpServerStatus = when (value) {
        "connected" -> McpServerStatus.CONNECTED
        "testing" -> McpServerStatus.TESTING
        else -> McpServerStatus.DISCONNECTED
    }

    @TypeConverter
    fun roleToString(value: Role): String = when (value) {
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
        Role.SYSTEM -> "system"
    }

    @TypeConverter
    fun stringToRole(value: String): Role = when (value) {
        "assistant" -> Role.ASSISTANT
        "system" -> Role.SYSTEM
        else -> Role.USER
    }

    @TypeConverter
    fun analysisVisibilityToString(value: AnalysisVisibility?): String? = when (value) {
        AnalysisVisibility.VISIBLE -> "visible"
        AnalysisVisibility.HIDDEN -> "hidden"
        null -> null
    }

    @TypeConverter
    fun stringToAnalysisVisibility(value: String?): AnalysisVisibility? = when (value) {
        "visible" -> AnalysisVisibility.VISIBLE
        "hidden" -> AnalysisVisibility.HIDDEN
        else -> null
    }

    @TypeConverter
    fun fileAccessToString(value: FileAccess?): String? = when (value) {
        FileAccess.NONE -> "none"
        FileAccess.READ -> "read"
        FileAccess.READWRITE -> "readwrite"
        null -> null
    }

    @TypeConverter
    fun stringToFileAccess(value: String?): FileAccess? = when (value) {
        "none" -> FileAccess.NONE
        "read" -> FileAccess.READ
        "readwrite" -> FileAccess.READWRITE
        else -> null
    }
}
