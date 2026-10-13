package com.watchworld.model

import kotlinx.serialization.Serializable

/**
 * `exportAllData()` 的产物（v5 存档），对应 `src/lib/storage.ts:417`。
 *
 * 这是**跨版本契约**：被 WebDAV 远端备份与「导入全部数据」依赖，
 * 也被 `fixtures/sse/archive-v5-*.json` 三个样本钉住。见 `MIGRATION_CONSTRAINTS.md` §1.1。
 *
 * ### 易错点（模型高频犯错处）
 * - `theme` 是**单数**（映射到 `omnichat_theme_palette`，值是**一个字符串**：内置 id 或 `custom_xxx`），
 *   不是 `themes` 数组。用户 DIY 主题存在**另一个键** `guanjie_custom_themes`，**不进这个存档**。
 * - `worldDocuments` / `worldGroups` 是**复数**。
 * - `version` 固定 `5`：不可改数字，除非同时升级导入的兼容分支。
 *
 * ### 顶层字段是「可以有、也可以没有」的
 * `importAllData` 是 `if (data.xxx) this.setXxx(...)` 的逐项判断，**不做版本校验、也不要求字段齐全**：
 * - `archive-v5-legacy.json` 实测**缺 `worldGroups` 与 `appPreferences`**；
 * - 每个集合字段都可能整个不存在。
 *
 * 因此这里**所有集合/对象字段都给默认值**，`version` 也给了默认值 —— 缺字段时按「该分类为空」处理，
 * 与 Web 版行为一致。**唯一不能缺的是 `version` 的语义**：导入前仍要显式读出来判断。
 */
@Serializable
data class ArchiveV5(
    val version: Int = 5,
    val exportTime: Long = 0L,
    val providers: List<ApiProviderConfig> = emptyList(),
    val groups: List<ChatGroup> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val prompts: List<PromptPreset> = emptyList(),
    val regexRules: List<RegexRule> = emptyList(),
    val knowledgeBase: List<KnowledgeItem> = emptyList(),
    /** ⚠️ 单数！值是主题 id 字符串，默认 `"emerald-day"`。 */
    val theme: ThemePalette? = null,
    /** `"dark"` / `"light"`，默认 `"dark"`。这里保留原始字符串以便原样回写。 */
    val uiMode: String? = null,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val displaySettings: MessageDisplaySettings? = null,
    val webdavConfig: WebDavConfig? = null,
    /** ⚠️ 复数。 */
    val worldDocuments: List<WorldDocument> = emptyList(),
    /** ⚠️ 复数。legacy fixture 里整个字段不存在。 */
    val worldGroups: List<WorldGroup> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    /** legacy fixture 里整个字段不存在。 */
    val appPreferences: AppPreferences? = null,
    /** 跨 Provider 的模型收藏（参考版独立键 `guanjie_model_favorites`）。 */
    val modelFavorites: List<ModelFavorite> = emptyList(),
)
