package com.watchworld.ui.settings

import com.watchworld.data.repository.ArchiveStore
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.model.Agent
import com.watchworld.model.AgentSkill
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.AppPreferences
import com.watchworld.model.ChatGroup
import com.watchworld.model.CustomTheme
import com.watchworld.model.KnowledgeItem
import com.watchworld.model.McpServerConfig
import com.watchworld.model.MessageDisplaySettings
import com.watchworld.model.ModelFavorite
import com.watchworld.model.PromptPreset
import com.watchworld.model.RegexRule
import com.watchworld.model.ThemePalette
import com.watchworld.model.UiMode
import com.watchworld.model.WebDavConfig
import com.watchworld.model.WebDavSyncStatus
import com.watchworld.net.ProviderApi
import com.watchworld.net.ProviderModelsResult
import com.watchworld.net.ProviderTestResult
import com.watchworld.net.WebDavClient
import com.watchworld.platform.currentTimeMillis
import com.watchworld.model.WwJson
import com.watchworld.ui.theme.UiFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 提示条的语义（决定配色），对标参考版 `BackupSyncModal` 的 `testResult.status`
 * （`BackupSyncModal.tsx:37-40`：`idle | testing | success | error`）。
 */
enum class BackupNoticeLevel { SUCCESS, BUSY, ERROR }

/** 一条操作结果提示（`BackupNoticeLevel` + 文案）。 */
data class BackupNotice(val level: BackupNoticeLevel, val message: String)

data class SettingsUiState(
    val providers: List<ApiProviderConfig> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val prompts: List<PromptPreset> = emptyList(),
    val regexRules: List<RegexRule> = emptyList(),
    val knowledgeBase: List<KnowledgeItem> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val customThemes: List<CustomTheme> = emptyList(),
    val groups: List<ChatGroup> = emptyList(),
    /** 跨 Provider 的模型收藏（参考版 `guanjie_model_favorites` 键）。 */
    val modelFavorites: List<ModelFavorite> = emptyList(),
    val displaySettings: MessageDisplaySettings = MessageDisplaySettings(),
    val appPreferences: AppPreferences = AppPreferences(),
    val themePalette: ThemePalette = "emerald-day",
    val uiModeRaw: String = "dark",
    /** 界面字体族原始串（`sans` / `serif`）；与 [uiModeRaw] 一样存原串。 */
    val uiFontFamilyRaw: String = "sans",
    val loading: Boolean = true,
    val error: String? = null,
    /** 最近一次本地 JSON 导入的结果（成功 / 失败）。 */
    val importNotice: BackupNotice? = null,
    /** WebDAV 配置；没配过时为 `null`（见 `SettingsRepository.webDavConfig`）。 */
    val webDavConfig: WebDavConfig? = null,
    /** 最近一次 WebDAV 测试/同步的结果提示；`null` = 还没操作过（参考版 `idle`）。 */
    val webDavNotice: BackupNotice? = null,
    /** 测试/同步进行中。 */
    val webDavBusy: Boolean = false,
)

class SettingsViewModel(
    private val configRepository: ConfigRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val archiveStore: ArchiveStore,
    private val webDavClient: WebDavClient,
    private val providerApi: ProviderApi,
) {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    suspend fun load() {
        runCatching {
            mutableState.value = SettingsUiState(
                providers = configRepository.providers(),
                agents = configRepository.agents(),
                prompts = configRepository.prompts(),
                regexRules = configRepository.regexRules(),
                knowledgeBase = configRepository.knowledgeBase(),
                mcpServers = configRepository.mcpServers(),
                skills = configRepository.skills(),
                customThemes = configRepository.customThemes(),
                groups = configRepository.groups(),
                modelFavorites = configRepository.modelFavorites(),
                displaySettings = settingsRepository.displaySettings(),
                appPreferences = settingsRepository.appPreferences(),
                themePalette = settingsRepository.themePalette(),
                uiModeRaw = settingsRepository.uiMode(),
                uiFontFamilyRaw = settingsRepository.uiFontFamily(),
                webDavConfig = settingsRepository.webDavConfig(),
                loading = false,
            )
        }.onFailure { e ->
            mutableState.value = mutableState.value.copy(loading = false, error = e.message)
        }
    }

    suspend fun saveProvider(p: ApiProviderConfig) = upsert({ configRepository.providers() }, { configRepository.setProviders(it) }, p) { it.id }
        .also { mutableState.value = mutableState.value.copy(providers = configRepository.providers()) }
    suspend fun deleteProvider(id: String) = remove({ configRepository.providers() }, { configRepository.setProviders(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(providers = configRepository.providers()) }

    suspend fun saveAgent(a: Agent) = upsert({ configRepository.agents() }, { configRepository.setAgents(it) }, a) { it.id }
        .also { mutableState.value = mutableState.value.copy(agents = configRepository.agents()) }
    suspend fun deleteAgent(id: String) = remove({ configRepository.agents() }, { configRepository.setAgents(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(agents = configRepository.agents()) }

    suspend fun savePrompt(p: PromptPreset) = upsert({ configRepository.prompts() }, { configRepository.setPrompts(it) }, p) { it.id }
        .also { mutableState.value = mutableState.value.copy(prompts = configRepository.prompts()) }
    suspend fun deletePrompt(id: String) = remove({ configRepository.prompts() }, { configRepository.setPrompts(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(prompts = configRepository.prompts()) }

    suspend fun saveRegexRule(r: RegexRule) = upsert({ configRepository.regexRules() }, { configRepository.setRegexRules(it) }, r) { it.id }
        .also { mutableState.value = mutableState.value.copy(regexRules = configRepository.regexRules()) }
    suspend fun deleteRegexRule(id: String) = remove({ configRepository.regexRules() }, { configRepository.setRegexRules(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(regexRules = configRepository.regexRules()) }
    suspend fun toggleRegexRule(id: String) {
        configRepository.transaction {
            configRepository.setRegexRules(configRepository.regexRules().map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
        }
        mutableState.value = mutableState.value.copy(regexRules = configRepository.regexRules())
    }

    suspend fun saveKnowledgeItem(k: KnowledgeItem) = upsert({ configRepository.knowledgeBase() }, { configRepository.setKnowledgeBase(it) }, k) { it.id }
        .also { mutableState.value = mutableState.value.copy(knowledgeBase = configRepository.knowledgeBase()) }
    suspend fun deleteKnowledgeItem(id: String) = remove({ configRepository.knowledgeBase() }, { configRepository.setKnowledgeBase(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(knowledgeBase = configRepository.knowledgeBase()) }

    suspend fun saveMcpServer(s: McpServerConfig) = upsert({ configRepository.mcpServers() }, { configRepository.setMcpServers(it) }, s) { it.id }
        .also { mutableState.value = mutableState.value.copy(mcpServers = configRepository.mcpServers()) }
    suspend fun deleteMcpServer(id: String) = remove({ configRepository.mcpServers() }, { configRepository.setMcpServers(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(mcpServers = configRepository.mcpServers()) }

    suspend fun saveSkill(s: AgentSkill) = upsert({ configRepository.skills() }, { configRepository.setSkills(it) }, s) { it.id }
        .also { mutableState.value = mutableState.value.copy(skills = configRepository.skills()) }
    suspend fun deleteSkill(id: String) = remove({ configRepository.skills() }, { configRepository.setSkills(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(skills = configRepository.skills()) }

    suspend fun saveCustomTheme(t: CustomTheme) = upsert({ configRepository.customThemes() }, { configRepository.setCustomThemes(it) }, t) { it.id }
        .also { mutableState.value = mutableState.value.copy(customThemes = configRepository.customThemes()) }
    suspend fun deleteCustomTheme(id: String) = remove({ configRepository.customThemes() }, { configRepository.setCustomThemes(it) }, id) { it.id }
        .also { mutableState.value = mutableState.value.copy(customThemes = configRepository.customThemes()) }

    /**
     * 收藏 / 取消收藏一个模型（参考版 `App.tsx:2644-2658` 的 `onToggleFavorite`）。
     *
     * 复合主键 `(providerId, modelName)`：已存在就删掉，否则追加到末尾并盖 `addedAt`。
     * 排序由 `ConfigRepository.setModelFavorites` 按列表顺序重写 `sortIndex`。
     */
    suspend fun toggleModelFavorite(providerId: String, modelName: String) {
        val current = configRepository.modelFavorites()
        val exists = current.any { it.providerId == providerId && it.modelName == modelName }
        val next = if (exists) {
            current.filterNot { it.providerId == providerId && it.modelName == modelName }
        } else {
            current + ModelFavorite(providerId = providerId, modelName = modelName, addedAt = currentTimeMillis())
        }
        configRepository.setModelFavorites(next)
        mutableState.value = mutableState.value.copy(modelFavorites = configRepository.modelFavorites())
    }

    suspend fun saveDisplaySettings(s: MessageDisplaySettings) {
        settingsRepository.setDisplaySettings(s)
        mutableState.value = mutableState.value.copy(displaySettings = s)
    }
    suspend fun saveAppPreferences(p: AppPreferences) {
        settingsRepository.setAppPreferences(p)
        mutableState.value = mutableState.value.copy(appPreferences = p)
    }
    suspend fun setThemePalette(palette: ThemePalette) {
        settingsRepository.setThemePalette(palette)
        mutableState.value = mutableState.value.copy(themePalette = palette)
    }
    suspend fun setUiMode(mode: UiMode) {
        val raw = when (mode) { UiMode.DARK -> "dark"; UiMode.LIGHT -> "light" }
        settingsRepository.setUiMode(raw)
        mutableState.value = mutableState.value.copy(uiModeRaw = raw)
    }

    /** 切换界面字体族（黑体 / 宋体）；下一帧 [com.watchworld.ui.theme.WwTheme] 就会换字体。 */
    suspend fun setUiFontFamily(family: UiFontFamily) {
        settingsRepository.setUiFontFamily(family.storageValue)
        mutableState.value = mutableState.value.copy(uiFontFamilyRaw = family.storageValue)
    }

    // ---------------- Provider 连通性 / 模型列表（对标 Web `api.ts`） ----------------

    /** 测试 Provider 连通性（对标 Web `testProviderConnection`）。 */
    suspend fun testProvider(provider: ApiProviderConfig): ProviderTestResult = providerApi.testConnection(provider)

    /** 拉取 Provider 支持的模型列表（对标 Web `fetchModelsFromProvider`）。 */
    suspend fun fetchProviderModels(provider: ApiProviderConfig): ProviderModelsResult = providerApi.fetchModels(provider)

    /**
     * 导出 v5 全量存档 JSON（参考版 `storage.ts:417-451 exportAllData`）。
     *
     * @param includeApiKeys `false`（默认）时把 Provider 的 `apiKey` 与 WebDAV 密码擦成空串
     *   （`redactSecrets`）—— 换设备恢复后需重新填；`true` 则原样带走，恢复即可用。
     */
    /**
     * 导出**全量**存档 JSON（对标 `storage.ts:417-451 exportAllData`，含它的全部 16 个数据键：
     * 会话（连同每条消息 / 时间线分支 / 前文总结）、世界观文档与分组、知识库、正则、
     * MCP、Skill、提示词、顾问、Provider、分组、主题配色、明暗、显示设置、应用偏好、
     * WebDAV 配置），外加参考版漏掉的 `modelFavorites`。
     *
     * @param includeApiKeys 备份里要不要带 Provider 的 `apiKey` 与 WebDAV 密码。
     *   - `false`（默认）：与参考版 `redactSecrets` 同语义，两处都擦成空串，换设备恢复后需重填；
     *   - `true`：原样带走，恢复即可直接使用。
     *   这个选择由用户在导出卡上勾。
     */
    /**
     * 导出**全量**存档 JSON（对标 `storage.ts:417-451 exportAllData`，含它的全部 16 个数据键：
     * 会话（连同每条消息 / 时间线分支 / 前文总结）、世界观文档与分组、知识库、正则、
     * MCP、Skill、提示词、顾问、Provider、分组、主题配色、明暗、显示设置、应用偏好、
     * WebDAV 配置），外加参考版漏掉的 `modelFavorites`。
     *
     * @param includeApiKeys 备份里要不要带 Provider 的 `apiKey` 与 WebDAV 密码。
     *   - `false`（默认）：与参考版 `redactSecrets` 同语义，两处都擦成空串，换设备恢复后需重填；
     *   - `true`：原样带走，恢复即可直接使用。
     *   这个选择由用户在导出卡上勾。
     */
    /**
     * 导出**全量**存档 JSON（对标 `storage.ts:417-451 exportAllData`，含它的全部 16 个数据键：
     * 会话（连同每条消息 / 时间线分支 / 前文总结）、世界观文档与分组、知识库、正则、
     * MCP、Skill、提示词、顾问、Provider、分组、主题配色、明暗、显示设置、应用偏好、
     * WebDAV 配置），外加参考版漏掉的 `modelFavorites`。
     *
     * @param includeApiKeys 备份里要不要带 Provider 的 `apiKey` 与 WebDAV 密码。
     *   - `false`（默认）：与参考版 `redactSecrets` 同语义，两处都擦成空串，换设备恢复后需重填；
     *   - `true`：原样带走，恢复即可直接使用。
     *   这个选择由用户在导出卡上勾。
     */
    /**
     * 导出**全量**存档 JSON（对标 `storage.ts:417-451 exportAllData`，含它的全部 16 个数据键：
     * 会话（连同每条消息 / 时间线分支 / 前文总结）、世界观文档与分组、知识库、正则、
     * MCP、Skill、提示词、顾问、Provider、分组、主题配色、明暗、显示设置、应用偏好、
     * WebDAV 配置），外加参考版漏掉的 `modelFavorites`。
     *
     * @param includeApiKeys 备份里要不要带 Provider 的 `apiKey` 与 WebDAV 密码。
     *   - `false`（默认）：与参考版 `redactSecrets` 同语义，两处都擦成空串，换设备恢复后需重填；
     *   - `true`：原样带走，恢复即可直接使用。
     *   这个选择由用户在导出卡上勾。
     */
    suspend fun exportData(includeApiKeys: Boolean = false): String =
        archiveStore.exportJson(redactSecrets = !includeApiKeys)

    /**
     * 导入「导出」出来的全量 JSON（对标 `storage.ts:453-471 importAllData`）：**整库接管** ——
     * 备份里有的键全部覆盖本地，缺的键保持不动（`ArchiveStore.presentKeys` 的语义，
     * 所以老备份照样能导入，只是缺的那部分不覆盖）。
     *
     * 成功提示带一句「恢复了什么」的收据：覆盖不可撤销，用户需要能核对结果。
     */
    suspend fun importData(json: String): Boolean {
        val summary = summarizeArchive(json)
        val ok = archiveStore.importJson(json)
        if (ok) load()
        mutableState.value = mutableState.value.copy(
            importNotice = if (ok) {
                BackupNotice(BackupNoticeLevel.SUCCESS, "数据导入成功！工作台已完整恢复$summary。")
            } else {
                BackupNotice(BackupNoticeLevel.ERROR, "数据格式解析失败，请检查是否为「导出」出来的 JSON。")
            },
        )
        return ok
    }

    /**
     * 只读数一数这份存档里有什么（不写库），给导入成功提示拼一句收据。
     * 解析不了就返回空串 —— 提示退化成「已完整恢复」，不影响导入本身。
     */
    private fun summarizeArchive(json: String): String {
        val root = runCatching { WwJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return ""
        fun count(key: String): Int = (root[key] as? JsonArray)?.size ?: 0
        val parts = buildList {
            count("sessions").takeIf { it > 0 }?.let { add("$it 个会话") }
            count("providers").takeIf { it > 0 }?.let { add("$it 个服务商") }
            count("agents").takeIf { it > 0 }?.let { add("$it 位顾问") }
            count("prompts").takeIf { it > 0 }?.let { add("$it 条提示词") }
            count("regexRules").takeIf { it > 0 }?.let { add("$it 条正则") }
            count("knowledgeBase").takeIf { it > 0 }?.let { add("$it 篇知识库") }
            count("worldDocuments").takeIf { it > 0 }?.let { add("$it 篇世界观") }
            count("worldGroups").takeIf { it > 0 }?.let { add("$it 个世界观分组") }
            count("mcpServers").takeIf { it > 0 }?.let { add("$it 个 MCP 服务") }
            count("skills").takeIf { it > 0 }?.let { add("$it 个 Skill") }
        }
        return if (parts.isEmpty()) "" else "：共 " + parts.joinToString("、")
    }


    /** 清掉本地导入提示（弹窗关掉 / 重新选文件时调）。 */
    fun clearImportNotice() {
        mutableState.value = mutableState.value.copy(importNotice = null)
    }

    // ---------------- WebDAV（T19） ----------------

    /**
     * 保存 WebDAV 配置。界面里每个字段的改动都会触发一次（对齐 Web 版 `BackupSyncModal`
     * 的 `onChangeWebDavConfig`，边输入边落库），因此这里**不做校验** ——
     * 配置是否完整由 [testWebDav] / [pushBackupToWebDav] / [pullBackupFromWebDav] 判断。
     */
    suspend fun saveWebDavConfig(config: WebDavConfig) {
        settingsRepository.setWebDavConfig(config)
        mutableState.value = mutableState.value.copy(webDavConfig = config)
    }

    /**
     * 手动保存配置（参考版「保存配置」按钮：`BackupSyncModal.tsx:279-293`）。
     * 只是把 `enabled` 打开并记一次同步时间，**不测连接**。
     */
    suspend fun markWebDavConfigSaved() {
        val config = mutableState.value.webDavConfig ?: WebDavConfig()
        val updated = config.copy(
            enabled = true,
            lastSyncTime = currentTimeMillis(),
            syncStatus = WebDavSyncStatus.SUCCESS,
        )
        settingsRepository.setWebDavConfig(updated)
        mutableState.value = mutableState.value.copy(webDavConfig = updated)
    }

    /** 连通性测试（= Web 版「测试连接」）。⚠️ 三个字段缺一即报错，不发起请求。 */
    suspend fun testWebDav(): String {
        val config = mutableState.value.webDavConfig ?: WebDavConfig()
        if (config.url.isBlank() || config.username.isBlank() || config.password.isNullOrBlank()) {
            val message = "请完整填写 WebDAV 地址、用户名和密码"
            mutableState.value = mutableState.value.copy(
                webDavBusy = false,
                webDavNotice = BackupNotice(BackupNoticeLevel.ERROR, message),
            )
            return message
        }
        mutableState.value = mutableState.value.copy(
            webDavBusy = true,
            webDavNotice = BackupNotice(BackupNoticeLevel.BUSY, "正在测试 WebDAV 连接..."),
        )
        val result = webDavClient.testConnection(config)
        mutableState.value = mutableState.value.copy(
            webDavBusy = false,
            webDavNotice = notice(result.success, result.message),
        )
        return result.message
    }

    /**
     * 手动全量推送（= Web 版「立即同步到云端」）。
     * `includeApiConfig != true` 时擦掉密钥（`redactSecrets = true`），与 Web 版默认一致。
     */
    suspend fun pushBackupToWebDav(): String {
        val config = mutableState.value.webDavConfig ?: WebDavConfig()
        mutableState.value = mutableState.value.copy(
            webDavBusy = true,
            webDavNotice = BackupNotice(BackupNoticeLevel.BUSY, "正在同步备份至 WebDAV..."),
        )
        val json = archiveStore.exportJson(redactSecrets = config.includeApiConfig != true)
        val result = webDavClient.pushBackup(config, json)
        if (result.success) {
            val updated = config.copy(
                enabled = true,
                lastSyncTime = result.timestamp ?: currentTimeMillis(),
                syncStatus = WebDavSyncStatus.SUCCESS,
            )
            settingsRepository.setWebDavConfig(updated)
            mutableState.value = mutableState.value.copy(webDavConfig = updated)
        }
        mutableState.value = mutableState.value.copy(
            webDavBusy = false,
            webDavNotice = notice(result.success, result.message),
        )
        return result.message
    }

    /** 手动全量拉取（= Web 版「从云端拉取」）—— 拉到即导入整库。 */
    suspend fun pullBackupFromWebDav(): String {
        val config = mutableState.value.webDavConfig ?: WebDavConfig()
        mutableState.value = mutableState.value.copy(
            webDavBusy = true,
            webDavNotice = BackupNotice(BackupNoticeLevel.BUSY, "正在从 WebDAV 拉取最新备份..."),
        )
        val result = webDavClient.pullBackup(config)
        var success = result.success
        var message = result.message
        if (result.success && result.remoteData != null) {
            val ok = archiveStore.importJson(result.remoteData)
            success = ok
            message = if (ok) "已从 WebDAV 成功恢复最新数据！" else "解析远程数据失败"
        }
        // 导入成功时整库已换，重新读一遍；失败时也只刷新状态文案。
        if (success && message.startsWith("已从")) load()
        mutableState.value = mutableState.value.copy(
            webDavBusy = false,
            webDavNotice = notice(success, message),
        )
        return message
    }

    private fun notice(success: Boolean, message: String) = BackupNotice(
        if (success) BackupNoticeLevel.SUCCESS else BackupNoticeLevel.ERROR,
        message,
    )

    private suspend fun <T> upsert(read: suspend () -> List<T>, write: suspend (List<T>) -> Unit, item: T, idOf: (T) -> String) {
        configRepository.transaction {
            val list = read().toMutableList()
            val idx = list.indexOfFirst { idOf(it) == idOf(item) }
            if (idx >= 0) list[idx] = item else list.add(item)
            write(list)
        }
    }
    private suspend fun <T> remove(read: suspend () -> List<T>, write: suspend (List<T>) -> Unit, id: String, idOf: (T) -> String) {
        configRepository.transaction { write(read().filterNot { idOf(it) == id }) }
    }
}

