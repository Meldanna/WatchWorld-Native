package com.watchworld.data.repository

import com.watchworld.data.db.withTransaction
import com.watchworld.data.db.WwDatabase
import com.watchworld.model.ArchiveV5
import com.watchworld.model.WwJson
import com.watchworld.platform.currentTimeMillis
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * v5 存档 ↔ SQLite 的桥（`storage.ts` 的 `importAllData` / `exportAllData`，PLAN §5.4）。
 *
 * 分工：**格式**（解析、六条归一化、顶层键铺齐、`redactSecrets`）全在 [ArchiveRepository]；
 * 本类只负责「存档里的哪一块落到哪张表 / 哪个 kv 键」。两边不重复实现任何规则。
 *
 * ### 导入：逐项判断，不是整库覆盖
 *
 * Web 版是 `if (data.xxx) this.setXxx(data.xxx)` 的 16 条独立判断 —— **存档里没有的分类保持原样**。
 * 而 [ArchiveV5] 解码后，「字段缺失」和「空数组」都成了 `emptyList()`，判断依据已经丢了。
 * 所以这里**回到原始 JSON 树**，按 JS 的真值规则（[isJsTruthy]）重做一遍判断：
 * - 缺字段 / `null` / `""` / `0` / `false` → 跳过，库里原有数据不动；
 * - 空数组 `[]` 在 JS 里是**真值** → 照样执行 `setXxx([])`，即清空该分类。
 *
 * ### 不进存档、因此导入导出都不碰的东西
 *
 * `customThemes`（独立键 `guanjie_custom_themes`）、`modelFavorites`、`kv` 里的当前会话/Provider
 * 与每会话草稿。这与 Web 版 `exportAllData` 的 17 个键一致，不是遗漏。
 *
 * @param now 导出 `exportTime` 与归一化补 MCP id 用的时间戳来源；测试传固定值。
 */
class ArchiveStore(
    private val db: WwDatabase,
    private val now: () -> Long = ::currentTimeMillis,
) {
    private val config = ConfigRepository(db)
    private val settings = SettingsRepository(db)
    private val sessions = SessionRepository(db, now)

    /**
     * 导入一份 v5 存档 JSON。对应 `importAllData` —— 坏存档返回 `false`，不抛给 UI。
     *
     * 整段在**一个事务**里：中途任何一张表写失败都整体回滚，不会留下「Provider 导进去了、
     * 会话没导进去」的半个存档（Web 版做不到这一点，这是原生版唯一有意的行为增强）。
     */
    suspend fun importJson(json: String): Boolean {
        val root = try {
            WwJson.parseToJsonElement(json) as? JsonObject
        } catch (error: IllegalArgumentException) {
            null
        } ?: return false
        val archive = ArchiveRepository.tryImportArchive(json, now()) ?: return false
        importArchive(archive, presentKeys(root))
        return true
    }

    /**
     * 把已解析的存档写进库。
     *
     * @param present 原始 JSON 里「JS 真值」的顶层键（见类注释）。传 [ALL_KEYS] 等于全量接管。
     */
    suspend fun importArchive(archive: ArchiveV5, present: Set<String> = ALL_KEYS) = db.withTransaction {
        // 顺序照抄 importAllData（storage.ts:456-471）。
        if ("providers" in present) config.setProviders(archive.providers)
        if ("groups" in present) config.setGroups(archive.groups)
        if ("agents" in present) config.setAgents(archive.agents)
        if ("prompts" in present) config.setPrompts(archive.prompts)
        if ("regexRules" in present) config.setRegexRules(archive.regexRules)
        if ("knowledgeBase" in present) config.setKnowledgeBase(archive.knowledgeBase)
        if ("theme" in present) archive.theme?.let { settings.setThemePalette(it) }
        if ("uiMode" in present) archive.uiMode?.let { settings.setUiMode(it) }
        if ("mcpServers" in present) config.setMcpServers(archive.mcpServers)
        if ("skills" in present) config.setSkills(archive.skills)
        if ("displaySettings" in present) archive.displaySettings?.let { settings.setDisplaySettings(it) }
        if ("webdavConfig" in present) archive.webdavConfig?.let { settings.setWebDavConfig(it) }
        if ("worldDocuments" in present) config.setWorldDocuments(archive.worldDocuments)
        if ("worldGroups" in present) config.setWorldGroups(archive.worldGroups)
        // ⚠️ 不盖 updatedAt：导入是「恢复」，不是「修改」。Web 版 setSessions 同样原样写入。
        if ("sessions" in present) sessions.replaceAll(archive.sessions)
        if ("appPreferences" in present) archive.appPreferences?.let { settings.setAppPreferences(it) }
        if ("modelFavorites" in present) config.setModelFavorites(archive.modelFavorites)
    }

    /** 从库里拼出一份 [ArchiveV5]（未盖 `version`/`exportTime`，未擦密钥）。测试与导出共用。 */
    suspend fun snapshot(): ArchiveV5 = db.withTransaction {
        ArchiveV5(
            providers = config.providers(),
            groups = config.groups(),
            agents = config.agents(),
            prompts = config.prompts(),
            regexRules = config.regexRules(),
            knowledgeBase = config.knowledgeBase(),
            theme = settings.themePalette(),
            uiMode = settings.uiMode(),
            mcpServers = config.mcpServers(),
            skills = config.skills(),
            displaySettings = settings.displaySettings(),
            webdavConfig = settings.webDavConfig(),
            worldDocuments = config.worldDocuments(),
            worldGroups = config.worldGroups(),
            sessions = sessions.all(),
            appPreferences = settings.appPreferences(),
            modelFavorites = config.modelFavorites(),
        )
    }

    /**
     * 导出 v5 存档 JSON。对应 `exportAllData`。格式细节（顶层键铺齐、2 空格缩进、
     * 归一化、`redactSecrets` 擦成 `''`）全部交给 [ArchiveRepository.exportArchive]。
     */
    suspend fun exportJson(redactSecrets: Boolean = false): String =
        ArchiveRepository.exportArchive(snapshot(), redactSecrets = redactSecrets, exportTime = now())

    companion object {
        /** 存档的数据键（`version` / `exportTime` 不是数据）。参考版是 16 键，
         *  原生多一个 `modelFavorites`（参考版把它放在独立键里，导出时漏了）。 */
        val ALL_KEYS: Set<String> = setOf(
            "providers", "groups", "agents", "prompts", "regexRules", "knowledgeBase",
            "theme", "uiMode", "mcpServers", "skills", "displaySettings", "webdavConfig",
            "worldDocuments", "worldGroups", "sessions", "appPreferences", "modelFavorites",
        )

        /** 原始顶层对象里，按 JS 真值规则成立的数据键。 */
        fun presentKeys(root: JsonObject): Set<String> =
            ALL_KEYS.filterTo(mutableSetOf()) { key -> root[key]?.let(::isJsTruthy) == true }

        /**
         * JS 的 `if (value)`：`null` / `false` / `0` / `NaN` / `""` 为假，**数组与对象（含空的）一律为真**。
         */
        fun isJsTruthy(value: JsonElement): Boolean = when (value) {
            is JsonNull -> false
            is JsonArray, is JsonObject -> true
            is JsonPrimitive -> when {
                value.isString -> value.content.isNotEmpty()
                value.booleanOrNull != null -> value.booleanOrNull == true
                else -> value.doubleOrNull?.let { it != 0.0 && !it.isNaN() } ?: true
            }
        }
    }
}
