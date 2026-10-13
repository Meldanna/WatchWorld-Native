package com.watchworld.data.repository

import com.watchworld.data.db.withTransaction
import com.watchworld.data.DEFAULT_APP_PREFERENCES
import com.watchworld.data.DEFAULT_DISPLAY_SETTINGS
import com.watchworld.data.StorageKeys
import com.watchworld.data.db.KvEntity
import com.watchworld.data.db.WwDatabase
import com.watchworld.model.AppPreferences
import com.watchworld.model.MessageDisplaySettings
import com.watchworld.model.ThemePalette
import com.watchworld.model.UiMode
import com.watchworld.model.WebDavConfig
import com.watchworld.model.WwJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** `UiMode` → 存档/`kv` 里的字面量。与 `model/Enums.kt` 的 `@SerialName` 逐字对应。 */
fun UiMode.storageValue(): String = when (this) {
    UiMode.DARK -> "dark"
    UiMode.LIGHT -> "light"
}

/**
 * 单体设置（`kv` 表）的唯一读写入口。
 *
 * 「单体」= Web 版一个 localStorage 键存一个值、没有「集合」语义的那些：主题、明暗、
 * 显示设置、应用偏好、WebDAV 配置、当前会话/Provider、每会话输入草稿。
 * 集合类（providers、agents……）在 [ConfigRepository]，会话树在 [SessionRepository]。
 *
 * 值的存法见 `data/StorageKeys.kt` 的 KDoc：标量原样字符串，对象走 `WwJson`。
 *
 * ### 兜底默认值（T14 起已补齐）
 *
 * Web 版的每个 getter 都长这样：`safeGet(KEY, DEFAULT_XXX)`，那些 `DEFAULT_*` 常量住在
 * `src/lib/defaultData.ts`。T14 已把当前用得到的两组搬进 `data/DefaultData.kt`：
 *
 * | 默认值来源 | 本类怎么办 | 差在哪 |
 * |---|---|---|
 * | 字面量（`'emerald'` / `'dark'` / `'provider-gemini'` / `'session-welcome'`） | 照抄同一个字面量 | 无差别 |
 * | `DEFAULT_DISPLAY_SETTINGS` / `DEFAULT_APP_PREFERENCES` | 直接引用 `data/DefaultData.kt` | 无差别（T14 前这里用的是模型自身默认值，曾与 Web 版不同） |
 *
 * ⚠️ 改 `DefaultData.kt` 里那两个常量的**值**等于改「全新安装的默认行为」，
 *    也会同时改 `ArchiveStore.snapshot()` 对老存档缺字段时的补齐结果 ——
 *    `StorageRoundTripTest.withStorageDefaults` 是同步改的判据。
 *
 * 仍有未迁的 `DEFAULT_*`（agents / prompts / regexRules / worldDocuments …），属 T15/T18。
 *
 * 读接口一律**不抛错**：库里没有就返回默认值，与 Web 版 `safeGet` 的容错行为一致。
 */
class SettingsRepository(private val db: WwDatabase) {

    // ---------------- 主题 / 明暗 ----------------

    /** 主题调色板 id（内置 id 或 `custom_xxx`）。默认 `"emerald"`（`storage.ts getThemePalette`）。 */
    suspend fun themePalette(): ThemePalette =
        db.kvDao().get(StorageKeys.THEME_PALETTE) ?: DEFAULT_THEME_PALETTE

    suspend fun setThemePalette(value: ThemePalette) = put(StorageKeys.THEME_PALETTE, value)

    /** `"dark"` / `"light"`。默认 `"dark"`。取原始字符串而不是 [UiMode]，是为了导出时能原样回写。 */
    suspend fun uiMode(): String = db.kvDao().get(StorageKeys.UI_MODE) ?: DEFAULT_UI_MODE

    suspend fun setUiMode(mode: String) = put(StorageKeys.UI_MODE, mode)

    /**
     * 界面字体族原始串（`"sans"` 黑体 / `"serif"` 宋体）。
     *
     * 与 [uiMode] 同样存**原始字符串**而不是枚举：`kv` 的格式不对外，本类不必把
     * 枚举的序列化搬进来；认不出的值（含从没存过）由调用方（`UiFontFamily.fromStorage`）
     * 回落到默认。
     */
    suspend fun uiFontFamily(): String =
        db.kvDao().get(StorageKeys.UI_FONT_FAMILY) ?: DEFAULT_UI_FONT_FAMILY

    suspend fun setUiFontFamily(value: String) = put(StorageKeys.UI_FONT_FAMILY, value)

    // ---------------- 显示设置 / 应用偏好 ----------------

    /** 未存过时返回 `DEFAULT_DISPLAY_SETTINGS`（七个字段全开 + small 气泡，与 Web 版一致）。 */
    suspend fun displaySettings(): MessageDisplaySettings =
        db.kvDao().get(StorageKeys.DISPLAY_SETTINGS)?.let {
            WwJson.decodeFromString<MessageDisplaySettings>(it)
        } ?: DEFAULT_DISPLAY_SETTINGS

    suspend fun setDisplaySettings(value: MessageDisplaySettings) =
        put(StorageKeys.DISPLAY_SETTINGS, WwJson.encodeToString(value))

    /** 未存过时返回 `DEFAULT_APP_PREFERENCES`（联网/流式默认开、工具步数 0）。 */
    suspend fun appPreferences(): AppPreferences =
        db.kvDao().get(StorageKeys.APP_PREFERENCES)?.let {
            WwJson.decodeFromString<AppPreferences>(it)
        } ?: DEFAULT_APP_PREFERENCES

    suspend fun setAppPreferences(value: AppPreferences) =
        put(StorageKeys.APP_PREFERENCES, WwJson.encodeToString(value))

    // ---------------- WebDAV ----------------

    /**
     * WebDAV 配置；**没配过时返回 `null`**，而不是 Web 版那个 `DEFAULT_WEBDAV_CONFIG` 常量。
     *
     * 为什么这里破了「读接口一律给默认值」的惯例：`DEFAULT_WEBDAV_CONFIG` 里
     * `syncPath = '/guanjie_backup.json'`、`password = ''` 这些是**属于同步功能的默认值**，
     * 由 T19 决定才有意义。在存储层凭空造一个「看起来已配置」的对象，
     * 会让「用户到底配没配过 WebDAV」变得不可判 —— 而这正是同步开关要做判断的事。
     */
    suspend fun webDavConfig(): WebDavConfig? =
        db.kvDao().get(StorageKeys.WEBDAV_CONFIG)?.let { WwJson.decodeFromString<WebDavConfig>(it) }

    suspend fun setWebDavConfig(value: WebDavConfig) =
        put(StorageKeys.WEBDAV_CONFIG, WwJson.encodeToString(value))

    // ---------------- 当前选中项 ----------------

    /** 默认 `"provider-gemini"` —— 照抄 `storage.ts getActiveProviderId`（该 id 来自 `defaultData`）。 */
    suspend fun activeProviderId(): String =
        db.kvDao().get(StorageKeys.ACTIVE_PROVIDER_ID) ?: DEFAULT_ACTIVE_PROVIDER_ID

    suspend fun setActiveProviderId(id: String) = put(StorageKeys.ACTIVE_PROVIDER_ID, id)

    /** 默认 `"session-welcome"` —— 照抄 `storage.ts getActiveSessionId`（`INITIAL_SESSION.id`）。 */
    suspend fun activeSessionId(): String =
        db.kvDao().get(StorageKeys.ACTIVE_SESSION_ID) ?: DEFAULT_ACTIVE_SESSION_ID

    suspend fun setActiveSessionId(id: String) = put(StorageKeys.ACTIVE_SESSION_ID, id)

    // ---------------- 输入草稿 ----------------

    /** 会话输入框草稿；没有就是空串（`storage.ts getInputDraft`）。 */
    suspend fun inputDraft(sessionId: String): String {
        if (sessionId.isEmpty()) return ""
        return db.kvDao().get(StorageKeys.inputDraft(sessionId)).orEmpty()
    }

    /**
     * 写草稿。**空串等于删除该行** —— 对应 `storage.ts setInputDraft` 里的
     * `if (text) setItem else removeItem`：草稿被清空后不该在库里留一行空值。
     */
    suspend fun setInputDraft(sessionId: String, text: String) {
        if (sessionId.isEmpty()) return
        val key = StorageKeys.inputDraft(sessionId)
        db.withTransaction {
            if (text.isEmpty()) db.kvDao().delete(key) else db.kvDao().upsert(KvEntity(key, text))
        }
    }

    /** 删除会话时顺手清掉它的草稿。会话号为空直接忽略，与 [setInputDraft] 一致。 */
    suspend fun clearInputDraft(sessionId: String) {
        if (sessionId.isEmpty()) return
        db.kvDao().delete(StorageKeys.inputDraft(sessionId))
    }

    // ---------------- 内部 ----------------

    private suspend fun put(key: String, value: String) = db.kvDao().upsert(KvEntity(key, value))

    private companion object {
        const val DEFAULT_THEME_PALETTE: ThemePalette = "emerald-day"
        const val DEFAULT_UI_MODE: String = "dark"
        const val DEFAULT_UI_FONT_FAMILY: String = "sans"
        const val DEFAULT_ACTIVE_PROVIDER_ID: String = "provider-gemini"
        const val DEFAULT_ACTIVE_SESSION_ID: String = "session-welcome"
    }
}
