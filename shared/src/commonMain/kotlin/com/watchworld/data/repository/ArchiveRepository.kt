package com.watchworld.data.repository

import com.watchworld.data.migration.normalizeArchive
import com.watchworld.data.migration.normalizeArchiveJson
import com.watchworld.model.ArchiveV5
import com.watchworld.model.WwJson
import com.watchworld.platform.currentTimeMillis
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * v5 存档（`storage.ts` 的 `exportAllData` / `importAllData`）的读写出口。
 *
 * ### 与 Web 版的分工差异（有意为之，不是漏做）
 *
 * Web 版把「读」和「归一化」绑在 `Storage.getXxx()` 上：`importAllData` 只是把原始 JSON
 * 塞进 localStorage，真正跑 `normalizeChatSession` 的是之后的 `getSessions()`。
 * 原生版没有 localStorage 这一层中间态，归一化**放在入口**（[parseArchive]）更合适：
 *
 * - 落到存储里的数据天然是干净的，非法值不会先进库再被读出来修；
 * - 对外可观察的行为完全一致 —— Web 版任何组件读到的也都是归一化后的结果。
 *
 * ### 导出为什么要再归一化一次
 *
 * `exportAllData` → `getSessions()` → `normalizeChatSession`，所以 Web 版**导出的存档里
 * 一定是归一化过的数据**（例如 `webSearchEnabled` 已被拍成 `false`、
 * `timelineDescriptionTagRequests` 已补成 `[]`）。[exportArchive] 照做，
 * 保证两版导出的形状一致。[normalizeChatSession] 幂等，重复归一化不会改变结果。
 */
object ArchiveRepository {

    /** 存档版本号。⚠️ 不可改数字，除非同时升级导入兼容分支（`MIGRATION_CONSTRAINTS.md` §1.1）。 */
    const val ARCHIVE_VERSION: Int = 5

    /**
     * 导出用的 Json：在 [WwJson] 之上只加「美化输出」。
     *
     * ⚠️ 必须是 `Json(WwJson) { … }`（复制配置再覆盖），**不能**新建一个 `Json { }` ——
     * 那会把 `explicitNulls = false` / `encodeDefaults = false` 一起丢掉，
     * 于是导出里凭空多出成片的 `"…": null`，与 Web 版形状不一致。
     * 缩进也用 2 空格，对齐 `JSON.stringify(dump, null, 2)`。
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val exportJson: Json = Json(WwJson) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * 只用来取**顶层默认骨架**：`encodeDefaults = true` 让「等于默认值」的键也写出来。
     *
     * ⚠️ **绝不能拿它编码整份存档**。`encodeDefaults` 是递归生效的，会连带把每个
     * `ChatSession` / `ChatMessage` 里等于默认值的字段全部写出来（`title: ""`、`messages: []` …），
     * 而 Web 版在**嵌套实体**上是 spread 语义 —— 老存档里没有的键就一直没有。
     * 所以它只用在构造 [ArchiveV5] 的空实例上，作用范围恰好是顶层。
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val defaultsJson: Json = Json(WwJson) { encodeDefaults = true }

    /**
     * 解析 v5 存档：解码前先解决「解不开」（[normalizeArchiveJson]），解码后再解决
     * 「解开了但不对」（[normalizeArchive]）。**两步缺一不可。**
     *
     * @param now 归一化时补 MCP `id` 用的时间戳；默认取当前时间，测试传固定值。
     * @throws kotlinx.serialization.SerializationException JSON 格式错误或缺少必填字段。
     * @throws IllegalArgumentException 顶层不是 JSON 对象。
     */
    fun parseArchive(json: String, now: Long = currentTimeMillis()): ArchiveV5 {
        val root = WwJson.parseToJsonElement(json).jsonObject
        val decoded = WwJson.decodeFromJsonElement<ArchiveV5>(normalizeArchiveJson(root, now))
        return normalizeArchive(decoded)
    }

    /**
     * [parseArchive] 的容错版本，对应 `importAllData` 的 `try/catch` + 返回 `false`：
     * 坏存档返回 `null`，不抛给调用方（导入是用户点按钮触发的，不能崩界面）。
     *
     * ⚠️ 只捕获 `IllegalArgumentException`：kotlinx 的 `SerializationException` 就是它的子类，
     * 顶层非对象的 `jsonObject` 抛的也是它。捕获 `Throwable` 会连 `OutOfMemoryError` 一起吞掉。
     */
    fun tryImportArchive(json: String, now: Long = currentTimeMillis()): ArchiveV5? = try {
        parseArchive(json, now)
    } catch (error: IllegalArgumentException) {
        null
    }

    /**
     * 导出 v5 存档，对应 `exportAllData`（`storage.ts:417`）。
     *
     * - `version` 与 `exportTime` **无条件写成本次导出的值**（Web 版同样硬编码 `5` / `Date.now()`）；
     * - 导出前对每个会话跑一遍归一化，与 Web 版的 `getSessions()` 对齐。
     *
     * ⚠️ **顶层的键必须写齐，不能只靠 `archive.copy(...)`**：本仓 `encodeDefaults = false`，
     * 而 `ArchiveV5` 各集合字段的默认值就是 `emptyList()` ——「等于默认值」的键会被**整个略过**。
     * 老存档缺 `worldGroups`，直接编码出来就没有 `worldGroups` 这个键。
     * Web 版不会这样：`exportAllData` 是显式的对象字面量，17 个键一个不少（值来自各自的
     * getter，缺数据时回退到 `DEFAULT_*`）。所以这里先用 [defaultsJson] 铺一层顶层骨架再叠加实际值。
     *
     * ⚠️ 只铺**顶层**。嵌套实体继续走 `encodeDefaults = false`，因为 Web 版在那一层是
     * spread 语义 —— 老存档里没有的键就一直没有，不该补。
     *
     * 已知差距（归 `defaultData.ts` 的迁移）：Web 版缺数据时的兜底值是 `DEFAULT_*` 常量
     * （如 `DEFAULT_WORLD_GROUPS` 非空、`theme` 兜底 `'emerald'`），本仓暂时只能用模型自身的
     * 默认值（空集合 / `null`）。键齐了，**值在「老存档本来就没有这个字段」时还不一样**。
     *
     * @param redactSecrets `true` 时擦掉 `providers[].apiKey` 与 `webdavConfig.password`，
     *   用于推送到 WebDAV 远端备份。见 [redactSecrets]。
     */
    fun exportArchive(
        archive: ArchiveV5,
        redactSecrets: Boolean = false,
        exportTime: Long = currentTimeMillis(),
    ): String {
        val normalized = normalizeArchive(archive).copy(version = ARCHIVE_VERSION, exportTime = exportTime)
        val out = if (redactSecrets) redactSecrets(normalized) else normalized

        // 叠加顺序：骨架提供键与顺序，实际编码结果覆盖值（`Map.plus` 保留骨架里的原位置）。
        val topLevel = JsonObject(
            defaultsJson.encodeToJsonElement(ArchiveV5()).jsonObject +
                exportJson.encodeToJsonElement(out).jsonObject,
        )
        return exportJson.encodeToString(JsonObject.serializer(), topLevel)
    }

    /**
     * 抹掉存档里的密钥（`storage.ts:439-448`）。
     *
     * ⚠️ **替换成空字符串 `''`，不是 `null`**。恢复逻辑按「是不是空串」判断
     * （见 `MIGRATION_CONSTRAINTS.md` §1.2）。这一条是最容易被"顺手改成 null"的地方：
     * `apiKey = null` 与 `apiKey = ""` 在 `explicitNulls = false` 下**行为相反** ——
     * 前者会让 `apiKey` 这个键从存档里整个消失，后者才写出 `"apiKey": ""`。
     *
     * Web 版还有个 `includeApiConfig` 分支（手动全量备份时可选带上密钥），
     * 归 T19 WebDAV 同步任务：本函数只管「擦干净」这一种语义。
     */
    fun redactSecrets(archive: ArchiveV5): ArchiveV5 = archive.copy(
        providers = archive.providers.map { it.copy(apiKey = "") },
        webdavConfig = archive.webdavConfig?.copy(password = ""),
    )
}
