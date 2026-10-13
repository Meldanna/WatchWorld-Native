package com.watchworld.net

import com.watchworld.model.ChatSession
import com.watchworld.model.WebDavConfig
import com.watchworld.model.WwJson
import com.watchworld.platform.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.random.Random

private const val DEFAULT_BACKUP_PATH = "/guanjie_backup.json"
private const val DEFAULT_SYNC_ROOT = "/guanjie/"
private val TRAILING_SLASHES = Regex("/+$")
private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

/**
 * `src/lib/webdavSync.ts` —— WebDAV 同步的传输层（T19）。
 *
 * ### 与 Web 版最大的差异：**不走代理**
 *
 * Web 版所有请求都经 `server.ts` 的 `/api/webdav` 转发（浏览器有 CORS，直连坚果云
 * 会失败），原生桌面/Android 都是进程内应用、没有 CORS 限制，因此这里**直接对目标
 * URL 发 HTTP**，把 `x-webdav-url`/`x-webdav-method` 那套透传头全部去掉，
 * 改成真正的 PROPFIND/PUT/GET/DELETE/MKCOL。
 *
 * ### 坚果云要「应用密码」
 *
 * 用 HTTP Basic（`base64(username:password)`）。坚果云要求第三方客户端使用
 * **应用专用密码**而非登录密码，`WebDavConfig.password` 就是它。
 *
 * ### 方法名保持原样
 *
 * `testWebDavConnection` / `pullFromWebDav` / `pushToWebDav` / `ensureCollections` /
 * `fetchManifest` / `pushManifest` / `pushSessionFile` / `fetchSessionFile` 与 TS 一一对应，
 * 便于日后回查。
 */

/** `webdavSync.ts WebDavSyncResult`。 */
data class WebDavSyncResult(
    val success: Boolean,
    val message: String,
    val timestamp: Long? = null,
    val remoteData: String? = null,
)

/** `webdavSync.ts testWebDavConnection` 的返回。 */
data class WebDavConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
)

/** `webdavSync.ts SyncManifest` —— 增量同步的清单。 */
@Serializable
data class SyncManifest(
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val sessions: Map<String, SyncSessionMeta> = emptyMap(),
    val images: Map<String, SyncImageMeta> = emptyMap(),
)

/** 清单里单个窗口的元信息。 */
@Serializable
data class SyncSessionMeta(
    val updatedAt: Long = 0L,
    val title: String? = null,
)

/** 清单里单张图片的元信息（图片本体推送属 T21）。 */
@Serializable
data class SyncImageMeta(
    val bytes: Long = 0L,
    val updatedAt: Long = 0L,
    val mime: String? = null,
)

/** `emptyManifest()`。 */
fun emptyManifest(): SyncManifest = SyncManifest()

/** `config.syncPath || '/guanjie_backup.json'`（空串按 JS 真值视为缺省）后 `trim`。 */
private fun syncPath(config: WebDavConfig): String {
    val raw = config.syncPath ?: DEFAULT_BACKUP_PATH
    val base = if (raw.isEmpty()) DEFAULT_BACKUP_PATH else raw
    return base.trim()
}

/** `backupFileUrl` —— 全量备份文件的绝对 URL。路径没有前导 `/` 时补一个。 */
internal fun backupFileUrl(config: WebDavConfig): String {
    val path = syncPath(config)
    val normalized = if (path.startsWith("/")) path else "/$path"
    return config.url.replace(TRAILING_SLASHES, "") + normalized
}

/** `syncRoot` —— 增量同步根目录（默认 `/guanjie/`），保证以 `/` 结尾。 */
internal fun syncRoot(config: WebDavConfig): String {
    val raw = config.syncRootDir ?: DEFAULT_SYNC_ROOT
    val dir = if (raw.isEmpty()) DEFAULT_SYNC_ROOT else raw
    val withSlash = if (dir.endsWith("/")) dir else "$dir/"
    return config.url.replace(TRAILING_SLASHES, "") + withSlash
}

/** `safeFileName` —— 只保留 `[A-Za-z0-9._-]`，其余换成 `_`。 */
internal fun safeFileName(id: String): String = id.replace(UNSAFE_FILE_NAME_CHARS, "_")

/** 从 URL 里取路径部分（`new URL(url).pathname` 的轻量替代），只用于错误信息。 */
internal fun urlPath(url: String): String {
    val scheme = url.indexOf("://")
    val authorityStart = if (scheme >= 0) scheme + 3 else 0
    val slash = url.indexOf('/', authorityStart)
    return if (slash < 0) "/" else url.substring(slash)
}

class WebDavClient(private val client: HttpClient) {

    /**
     * `testWebDavConnection`（`webdavSync.ts:43`）。
     *
     * 顺序逐条照抄：填齐校验 → PROPFIND(`Depth: 0`) 探目录 → PUT 一个随机临时文件 →
     * DELETE 清理。**任何一步失败都返回 `success = false` 并带可读原因**，不抛异常。
     */
    suspend fun testConnection(config: WebDavConfig): WebDavConnectionTestResult {
        if (config.url.isEmpty() || config.username.isEmpty() || config.password.isNullOrEmpty()) {
            return WebDavConnectionTestResult(false, 0L, "请填写完整的 WebDAV 地址、用户名和应用密码")
        }
        val start = currentTimeMillis()
        return try {
            val root = call("PROPFIND", config.url, config, extraHeaders = mapOf("Depth" to "0"))
            if (!root.ok && root.status != 207) {
                val suffix = if (root.body.isNotEmpty()) ": ${root.body.take(200)}" else ""
                return WebDavConnectionTestResult(
                    false,
                    currentTimeMillis() - start,
                    "WebDAV 目录访问失败 (${root.status})$suffix",
                )
            }

            val testName = "watchworld-write-test-${currentTimeMillis()}-${Random.nextLong().toString(36).removePrefix("-")}.json"
            val testUrl = config.url.replace(TRAILING_SLASHES, "") + "/$testName"
            val testPath = urlPath(config.url).replace(TRAILING_SLASHES, "") + "/$testName"
            val write = call("PUT", testUrl, config, body = "{\"watchWorld\":\"webdav-write-test\"}")
            if (!write.ok) {
                val suffix = if (write.body.isNotEmpty()) ": ${write.body.take(200)}" else ""
                return WebDavConnectionTestResult(
                    false,
                    currentTimeMillis() - start,
                    "目录可访问，但上传测试失败 (${write.status})，目标路径 $testPath$suffix",
                )
            }

            try {
                val delete = call("DELETE", testUrl, config)
                if (!delete.ok && delete.status != 404) {
                    val suffix = if (delete.body.isNotEmpty()) ": ${delete.body.take(200)}" else ""
                    return WebDavConnectionTestResult(
                        false,
                        currentTimeMillis() - start,
                        "上传测试成功，但临时文件清理失败 (${delete.status})，请检查 $testPath$suffix",
                    )
                }
            } catch (error: Exception) {
                return WebDavConnectionTestResult(
                    false,
                    currentTimeMillis() - start,
                    "上传测试成功，但清理临时文件时网络异常，请检查 $testPath: ${error.message ?: "网络异常"}",
                )
            }

            val latency = currentTimeMillis() - start
            WebDavConnectionTestResult(
                true,
                latency,
                "WebDAV 读写测试成功，临时文件已清理。耗时 ${latency}ms",
            )
        } catch (error: Exception) {
            WebDavConnectionTestResult(false, currentTimeMillis() - start, "网络异常: ${error.message ?: "连接超时"}")
        }
    }

    /** `pullFromWebDav`（`webdavSync.ts:87`）—— 读全量备份。 */
    suspend fun pullBackup(config: WebDavConfig): WebDavSyncResult {
        if (!isConfigured(config)) return WebDavSyncResult(false, "WebDAV 未启用或配置不完整")
        val fullUrl = backupFileUrl(config)
        return try {
            val response = call("GET", fullUrl, config)
            when {
                response.status == 404 ->
                    WebDavSyncResult(true, "远程尚无备份文件，后续将自动推送本地数据")
                !response.ok ->
                    WebDavSyncResult(false, "获取远程备份失败 (${response.status})")
                else ->
                    WebDavSyncResult(true, "成功拉取远程数据", remoteData = response.body)
            }
        } catch (error: Exception) {
            WebDavSyncResult(false, "拉取失败: ${error.message}")
        }
    }

    /**
     * `pushToWebDav`（`webdavSync.ts:130`）—— 写全量备份。
     *
     * ⚠️ 成功文案**有意去掉 Web 版的本地时间**（`new Date().toLocaleTimeString()`）。
     * commonMain 没有时区/本地时间格式化能力，为一句提示引依赖不划算；
     * `timestamp` 字段仍在，需要时间的地方读它。
     */
    suspend fun pushBackup(config: WebDavConfig, dataStr: String): WebDavSyncResult {
        if (!isConfigured(config)) return WebDavSyncResult(false, "WebDAV 未启用或配置不完整")
        val fullUrl = backupFileUrl(config)
        return try {
            val response = call("PUT", fullUrl, config, body = dataStr)
            if (response.ok || response.status == 201 || response.status == 204) {
                WebDavSyncResult(true, "同步成功", timestamp = currentTimeMillis())
            } else {
                val suffix = if (response.body.isNotEmpty()) ": ${response.body.take(200)}" else ""
                WebDavSyncResult(false, "推送失败 (${response.status})，目标路径 ${urlPath(fullUrl)}$suffix")
            }
        } catch (error: Exception) {
            WebDavSyncResult(false, "网络推送失败: ${error.message}")
        }
    }

    /** `ensureCollections`（`webdavSync.ts:220`）—— MKCOL 三个目录；已被存在时返回 405，一律忽略。 */
    suspend fun ensureCollections(config: WebDavConfig) {
        val root = syncRoot(config)
        runCatching { call("MKCOL", root, config) }
        runCatching { call("MKCOL", "${root}windows/", config) }
        runCatching { call("MKCOL", "${root}media/", config) }
    }

    /** `fetchManifest`（`webdavSync.ts:227`）—— 读不到或解析失败返回 `null`。 */
    suspend fun fetchManifest(config: WebDavConfig): SyncManifest? {
        val text = getFile(config, "${syncRoot(config)}index.json") ?: return null
        return runCatching { WwJson.decodeFromString<SyncManifest>(text) }.getOrNull()
    }

    /**
     * `pushManifest`（`webdavSync.ts:238`）—— 写清单。
     * `updatedAt` 由调用方（`SyncEngine`）在传入前盖好，成功与否的回写也在那里。
     */
    suspend fun pushManifest(config: WebDavConfig, manifest: SyncManifest): WebDavSyncResult =
        putFile(config, "${syncRoot(config)}index.json", WwJson.encodeToString(manifest))

    /** `pushSessionFile`（`webdavSync.ts:248`）。 */
    suspend fun pushSessionFile(config: WebDavConfig, session: ChatSession): WebDavSyncResult {
        val payload = buildJsonObject {
            put("kind", "session")
            put("payload", WwJson.encodeToJsonElement(session))
        }
        return putFile(config, "${syncRoot(config)}windows/${safeFileName(session.id)}.json", payload.toString())
    }

    /** `fetchSessionFile`（`webdavSync.ts:260`）—— 取 `payload`，读不到/解不开返回 `null`。 */
    suspend fun fetchSessionFile(config: WebDavConfig, id: String): JsonElement? {
        val text = getFile(config, "${syncRoot(config)}windows/${safeFileName(id)}.json") ?: return null
        return runCatching { (WwJson.parseToJsonElement(text) as? JsonObject)?.get("payload") }.getOrNull()
    }

    /** `pushImageFile`（`webdavSync.ts:270`）。图片本体由 T21 的 `ImageStore` 提供。 */
    suspend fun pushImageFile(config: WebDavConfig, image: JsonElement, id: String): WebDavSyncResult {
        val payload = buildJsonObject {
            put("kind", "image")
            put("payload", image)
        }
        return putFile(config, "${syncRoot(config)}media/${safeFileName(id)}.json", payload.toString())
    }

    /** `fetchImageFile`（`webdavSync.ts:282`）。 */
    suspend fun fetchImageFile(config: WebDavConfig, id: String): JsonElement? {
        val text = getFile(config, "${syncRoot(config)}media/${safeFileName(id)}.json") ?: return null
        return runCatching { (WwJson.parseToJsonElement(text) as? JsonObject)?.get("payload") }.getOrNull()
    }

    // -----------------------------------------------------------------------------------------
    // 私有：`webdavSync.ts` 的 `putFile` / `getFile` / `callWebDavProxy` 三件套
    // -----------------------------------------------------------------------------------------

    private suspend fun putFile(config: WebDavConfig, url: String, body: String): WebDavSyncResult = try {
        val response = call("PUT", url, config, body = body)
        if (response.ok || response.status == 201 || response.status == 204) {
            WebDavSyncResult(true, "ok", timestamp = currentTimeMillis())
        } else {
            WebDavSyncResult(false, "PUT 失败 (${response.status})")
        }
    } catch (error: Exception) {
        WebDavSyncResult(false, error.message ?: "网络异常")
    }

    private suspend fun getFile(config: WebDavConfig, url: String): String? = try {
        val response = call("GET", url, config)
        if (!response.ok) null else response.body
    } catch (error: Exception) {
        null
    }

    private suspend fun call(
        method: String,
        url: String,
        config: WebDavConfig,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): WebDavResponse {
        val auth = "Basic " + Base64.encode("${config.username}:${config.password.orEmpty()}".encodeToByteArray())
        val response = client.request(url) {
            this.method = HttpMethod.parse(method)
            header("Authorization", auth)
            extraHeaders.forEach { (key, value) -> header(key, value) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return WebDavResponse(response.status.value, response.bodyAsText())
    }

    private data class WebDavResponse(val status: Int, val body: String) {
        val ok: Boolean get() = status in 200..299
    }
}

/**
 * `webdavSync.ts` 的 `if (!config.enabled || !config.url || !config.username || !config.password)`。
 * ⚠️ **必须含 `enabled`**：停用后即使地址还留着也不该再发请求。
 */
private fun isConfigured(config: WebDavConfig): Boolean =
    config.enabled &&
        config.url.isNotEmpty() &&
        config.username.isNotEmpty() &&
        !config.password.isNullOrEmpty()
