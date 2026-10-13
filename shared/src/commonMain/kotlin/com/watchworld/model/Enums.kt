package com.watchworld.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * src/types.ts 里的字符串联合类型 → Kotlin enum + @SerialName。
 *
 * ⚠️ @SerialName 的值就是存档里的字面量，一个字母都不能改（MIGRATION_CONSTRAINTS §1、§6）。
 * ⚠️ 未知值的容错：WwJson 开了 coerceInputValues，字段**有默认值**时未知 enum 值会退回默认值；
 *    没有默认值的必填 enum 字段遇到未知值会直接抛错。每个用到 enum 的字段都按这个规则配默认值。
 */

/** types.ts:2 `ApiProviderType`（`ProviderType` 是它的别名，types.ts:12）。 */
@Serializable
enum class ApiProviderType {
    @SerialName("gemini") GEMINI,
    @SerialName("openai") OPENAI,
    @SerialName("claude") CLAUDE,
    @SerialName("deepseek") DEEPSEEK,
    @SerialName("ollama") OLLAMA,
    @SerialName("openrouter") OPENROUTER,
    @SerialName("groq") GROQ,
    @SerialName("custom") CUSTOM,
}

/** types.ts:12 `export type ProviderType = ApiProviderType;` */
typealias ProviderType = ApiProviderType

/** `Agent.fileAccess`（types.ts:63）。 */
@Serializable
enum class FileAccess {
    @SerialName("none") NONE,
    @SerialName("read") READ,
    @SerialName("readwrite") READWRITE,
}

/** `ChatMessage.role`（types.ts:123）。 */
@Serializable
enum class Role {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("system") SYSTEM,
}

/** `ChatMessage.analysisVisibility`（types.ts:137）。 */
@Serializable
enum class AnalysisVisibility {
    @SerialName("visible") VISIBLE,
    @SerialName("hidden") HIDDEN,
}

/** types.ts:107 `TimelineDescriptionTagRequestStatus`。 */
@Serializable
enum class TimelineDescriptionTagRequestStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
}

/** `MessageDisplaySettings.bubbleStyle`（types.ts:151）。 */
@Serializable
enum class BubbleStyle {
    @SerialName("small") SMALL,
    @SerialName("large") LARGE,
}

/** types.ts:154 `AiContextVisibilityFilter`。 */
@Serializable
enum class AiContextVisibilityFilter {
    @SerialName("all") ALL,
    @SerialName("hide_all") HIDE_ALL,
    @SerialName("latest_custom") LATEST_CUSTOM,
}

/** types.ts:187 `LaunchMode`。 */
@Serializable
enum class LaunchMode {
    @SerialName("last") LAST,
    @SerialName("new") NEW,
}

/** `RegexRule.scope`（types.ts:222）。 */
@Serializable
enum class RegexScope {
    @SerialName("input") INPUT,
    @SerialName("output") OUTPUT,
    @SerialName("both") BOTH,
}

/** types.ts:246 `McpTransportType`。 */
@Serializable
enum class McpTransportType {
    @SerialName("streamable_http") STREAMABLE_HTTP,
    @SerialName("sse") SSE,
}

/** `McpServerConfig.status`（types.ts:258）。 */
@Serializable
enum class McpServerStatus {
    @SerialName("connected") CONNECTED,
    @SerialName("disconnected") DISCONNECTED,
    @SerialName("testing") TESTING,
}

/**
 * types.ts:284 `BuiltinThemePalette`。
 *
 * ⚠️ 只用来列举内置主题；存档里的主题字段一律是 [ThemePalette]（= String），
 * 因为自定义主题 id（`custom_xxx`）不在这个枚举里。
 */
@Serializable
enum class BuiltinThemePalette {
    @SerialName("emerald") EMERALD,
    @SerialName("blue") BLUE,
    @SerialName("violet") VIOLET,
    @SerialName("rose") ROSE,
    @SerialName("amber") AMBER,
    @SerialName("cyan") CYAN,
    @SerialName("midnight") MIDNIGHT,
    @SerialName("coral") CORAL,
    @SerialName("honey") HONEY,
    @SerialName("mint") MINT,
    @SerialName("soda") SODA,
    @SerialName("grape") GRAPE,
    @SerialName("bubble") BUBBLE,
}

/**
 * types.ts:300 `ThemePalette = BuiltinThemePalette | (string & {})`：内置 id 或自定义主题 id。
 * 存档顶层 `theme`（单数！）就是这个字符串，默认 `"emerald"`（storage.ts `getThemePalette`）。
 */
typealias ThemePalette = String

/** types.ts:314 `UiMode`。 */
@Serializable
enum class UiMode {
    @SerialName("dark") DARK,
    @SerialName("light") LIGHT,
}

/** `WebDavConfig.syncStatus`（types.ts:327）。 */
@Serializable
enum class WebDavSyncStatus {
    @SerialName("idle") IDLE,
    @SerialName("syncing") SYNCING,
    @SerialName("success") SUCCESS,
    @SerialName("error") ERROR,
}

/** `ChatSession.tagDisplayMode`（types.ts:379）。 */
@Serializable
enum class TagDisplayMode {
    @SerialName("desc") DESC,
    @SerialName("code") CODE,
}

/** `ChatSession.imageContextMode`（types.ts:412），undefined 视为 `'once'`。 */
@Serializable
enum class ImageContextMode {
    @SerialName("once") ONCE,
    @SerialName("history") HISTORY,
}
