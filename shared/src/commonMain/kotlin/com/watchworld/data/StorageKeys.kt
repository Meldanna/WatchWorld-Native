package com.watchworld.data

/**
 * 存储键（`storage.ts:38-60` 的 21 个）与它们在原生版里的落点。
 *
 * ### 原生版（SQLite）为什么还要留着这些字符串
 *
 * 数据已经不放在 localStorage 上了，所以这些键**不承载**任何本地数据 —— 换存储介质不会
 * 因为改键而丢东西。留着有两个实打实的用处：
 *
 * 1. **`kv` 表的行键**：[SettingsRepository] 直接拿这些字符串当主键。于是「一个逻辑设置」
 *    在两版里有同一个名字，排查时不用在脑子里做映射。
 * 2. **对应关系**：T19（WebDAV）与「从浏览器搬迁数据」都要求知道「哪个表当年对应哪个键」。
 *
 * ### 全部 21 个键的去向（**唯一权威**，别在别处再抄一份）
 *
 * | Web 版 localStorage 键 | 原生版落点 |
 * |---|---|
 * | `omnichat_providers` | 表 `providers` |
 * | `omnichat_active_provider_id` | `kv` → [ACTIVE_PROVIDER_ID] |
 * | `omnichat_groups` | 表 `groups` |
 * | `omnichat_agents` | 表 `agents` |
 * | `omnichat_prompts` | 表 `prompts` |
 * | `omnichat_sessions` | 表 `sessions` + `messages` + `timelines` + `summaries`（**一个键存整棵会话树**） |
 * | `omnichat_active_session_id` | `kv` → [ACTIVE_SESSION_ID] |
 * | `omnichat_regex_rules` | 表 `regexRules` |
 * | `omnichat_knowledge_base` | 表 `knowledgeBase` |
 * | `omnichat_theme_palette` | `kv` → [THEME_PALETTE] |
 * | `guanjie_custom_themes` | 表 `customThemes`（**不进 v5 存档**） |
 * | `omnichat_ui_mode` | `kv` → [UI_MODE] |
 * | `omnichat_mcp_servers` | 表 `mcpServers` |
 * | `omnichat_skills` | 表 `skills` |
 * | `omnichat_display_settings` | `kv` → [DISPLAY_SETTINGS] |
 * | `guanjie_webdav_config` | `kv` → [WEBDAV_CONFIG] |
 * | `guanjie_world_documents` | 表 `worldDocuments` |
 * | `guanjie_world_groups` | 表 `worldGroups` |
 * | `guanjie_draft_<sessionId>` | `kv` → [inputDraft] |
 * | `guanjie_model_favorites` | 表 `modelFavorites` |
 * | `guanjie_app_preferences` | `kv` → [APP_PREFERENCES] |
 *
 * ### 值在 `kv` 里怎么存
 *
 * 这里**没有**跟着 Web 版走 `safeSet` 的 `JSON.stringify`。原因：那一层序列化是
 * 「localStorage 只能存字符串」逼出来的，不是语义的一部分，而 `kv` 的格式**不对外**——
 * 它既不进 v5 存档，也不进 WebDAV 同步（那两处走 [ArchiveStore]，格式由模型层保证）。
 * 所以约定按值的形状来，读一行代码就知道库里躺着什么：
 *
 * - **标量**（主题 id、明暗、当前会话/Provider、草稿）→ **原样字符串**，不套引号；
 * - **对象**（显示设置、应用偏好、WebDAV 配置）→ `WwJson` 序列化的 JSON 文本。
 *
 * ⚠️ `omnichat_` 与 `guanjie_` 的混用是历史遗留（**不是笔误**）。`MIGRATION_CONSTRAINTS.md` §6
 * 点名过：改任何一个都等于清空老用户数据。本文件里的值一律不许"顺手统一"。
 */
object StorageKeys {

    // ---- kv 行键（本文件里唯一会被代码引用的部分）----

    const val ACTIVE_PROVIDER_ID: String = "omnichat_active_provider_id"
    const val ACTIVE_SESSION_ID: String = "omnichat_active_session_id"
    const val THEME_PALETTE: String = "omnichat_theme_palette"
    const val UI_MODE: String = "omnichat_ui_mode"
    const val DISPLAY_SETTINGS: String = "omnichat_display_settings"
    const val WEBDAV_CONFIG: String = "guanjie_webdav_config"
    const val APP_PREFERENCES: String = "guanjie_app_preferences"
    const val INPUT_DRAFT_PREFIX: String = "guanjie_draft_"

    /**
     * 界面字体族（`sans` = 黑体 / `serif` = 宋体）。
     *
     * ⚠️ **原生版独有**：Web 版没有这个设置，所以它不在上面那张 21 键的对照表里，
     * 也**不进 v5 存档 / WebDAV**（那两处走 [ArchiveStore]，格式由模型层保证）——
     * 换个设备恢复数据时字体回落默认，这是有意的。
     */
    const val UI_FONT_FAMILY: String = "ww_ui_font_family"

    /** 每会话输入草稿的 kv 键：`guanjie_draft_<sessionId>`。 */
    fun inputDraft(sessionId: String): String = INPUT_DRAFT_PREFIX + sessionId
}
