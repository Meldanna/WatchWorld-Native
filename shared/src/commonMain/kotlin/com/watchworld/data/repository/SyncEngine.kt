package com.watchworld.data.repository

import com.watchworld.data.migration.normalizeChatSession
import com.watchworld.model.ChatSession
import com.watchworld.model.WebDavConfig
import com.watchworld.model.WwJson
import com.watchworld.net.SyncManifest
import com.watchworld.net.SyncSessionMeta
import com.watchworld.net.SyncImageMeta
import com.watchworld.net.WebDavClient
import com.watchworld.net.emptyManifest
import com.watchworld.platform.ImageStore
import com.watchworld.platform.StoredImage
import com.watchworld.platform.currentTimeMillis
import com.watchworld.platform.defaultImageStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * `src/lib/syncEngine.ts` —— WebDAV 增量同步引擎（T19）。
 *
 * ### 同步模型（与 Web 版逐条一致）
 *
 * - 云端根目录放一份 `index.json` 清单（各窗口的 `updatedAt`），窗口本体放
 *   `windows/<id>.json`，图片放 `media/<id>.json`；
 * - **冲突以时间戳较新为准**：`pushChanges` 只上传「本地 `updatedAt` > 清单记录」的窗口；
 *   `pullChanges` 只拉「远端 `updatedAt` > 本地」的窗口；
 * - `SyncState` 跨多次同步保留清单与「已建目录」标记，避免每次都重新 PROPFIND。
 *
 * ### 图片同步（T21 接上）
 *
 * `imageIdsOf` / `pushMissingImages` / 拉图循环已与 `syncEngine.ts` 逐条对齐：
 * 推窗口时**顺带**推它引用到的新图片，拉窗口时**顺带**补齐云端已有而本地没有的图片。
 * 两者都靠清单的 `images` 字段去重。⚠️ 图片是「窗口的附属品」，不单独产生一次同步：
 * 如果窗口本身没变（`updatedAt` 不新），它引用的新图片也不会被推上去 —— 与 TS 一致。
 *
 * ### 拉取后必须归一化
 *
 * 远端窗口是别台设备推上来的，可能来自更老的版本，因此拉下来先过
 * [normalizeChatSession]（`syncEngine.ts:124` 同样这么做）。
 */
class SyncState(
    var manifest: SyncManifest = emptyManifest(),
    var ready: Boolean = false,
)

/** `syncEngine.ts SyncOutcome`。 */
data class SyncOutcome(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val images: Int = 0,
    val message: String = "未配置同步",
)

/** `createSyncState()`。 */
fun createSyncState(): SyncState = SyncState()

/** `isSyncConfigured`（`syncEngine.ts:32`）—— 必须启用且地址/账号/应用密码齐全。 */
fun isSyncConfigured(config: WebDavConfig?): Boolean =
    config != null &&
        config.enabled &&
        config.url.isNotEmpty() &&
        config.username.isNotEmpty() &&
        !config.password.isNullOrEmpty()

/** 一个窗口（或一批窗口）引用到的全部图片 id（`syncEngine.ts:36 imageIdsOf`）。 */
fun imageIdsOf(sessions: List<ChatSession>): List<String> =
    sessions.flatMap { session -> session.messages.flatMap { it.imageIds.orEmpty() } }

/**
 * 上传窗口引用到、但清单里还没有的图片（`syncEngine.ts:40 pushMissingImages`）。
 *
 * 图片本体读不到（本地也没有）就跳过 —— 云端缺一张图不应该让整次同步失败。
 *
 * @return `(新增图片数, 新的清单)`。
 */
private suspend fun pushMissingImages(
    config: WebDavConfig,
    ids: List<String>,
    manifest: SyncManifest,
    client: WebDavClient,
    images: ImageStore,
): Pair<Int, SyncManifest> {
    var next = manifest
    var count = 0
    for (id in ids.filter { it.isNotEmpty() }.distinct()) {
        if (next.images.containsKey(id)) continue
        val image = images.get(id) ?: continue
        val result = client.pushImageFile(config, WwJson.encodeToJsonElement(image), id)
        if (!result.success) continue
        next = next.copy(
            images = next.images + (
                id to SyncImageMeta(bytes = image.bytes.toLong(), updatedAt = image.createdAt, mime = image.mime)
                ),
        )
        count++
    }
    return count to next
}

/**
 * `pushChanges`（`syncEngine.ts:59`）—— 只上传比清单新的窗口。
 *
 * @param now 清单 `updatedAt` 的时间来源；测试传固定值让结果可复现。
 */
suspend fun pushChanges(
    config: WebDavConfig,
    sessions: List<ChatSession>,
    state: SyncState,
    client: WebDavClient,
    now: () -> Long = ::currentTimeMillis,
    images: ImageStore = defaultImageStore,
): SyncOutcome {
    if (!isSyncConfigured(config)) return SyncOutcome()

    if (!state.ready) {
        client.ensureCollections(config)
        state.manifest = client.fetchManifest(config) ?: emptyManifest()
        state.ready = true
    }

    var changed = false
    var pushed = 0
    var imageCount = 0
    var manifest = state.manifest
    for (session in sessions) {
        val known = manifest.sessions[session.id]?.updatedAt ?: 0L
        if (session.updatedAt <= known) continue

        val result = client.pushSessionFile(config, session)
        if (!result.success) continue

        manifest = manifest.copy(
            sessions = manifest.sessions +
                (session.id to SyncSessionMeta(updatedAt = session.updatedAt, title = session.title)),
        )
        pushed++
        changed = true

        // 窗口变了才顺带推它的图片（与 TS 一致：图片是窗口的附属品）。
        val (added, withImages) = pushMissingImages(
            config = config,
            ids = imageIdsOf(listOf(session)),
            manifest = manifest,
            client = client,
            images = images,
        )
        manifest = withImages
        imageCount += added
        if (added > 0) changed = true
    }

    if (changed) {
        val next = manifest.copy(updatedAt = now())
        state.manifest = if (client.pushManifest(config, next).success) next else manifest
    }

    return SyncOutcome(
        pushed = pushed,
        images = imageCount,
        message = if (changed) "已上传 $pushed 个窗口 / $imageCount 张图片" else "无改动",
    )
}

/**
 * `pullChanges`（`syncEngine.ts:101`）—— 只拉比本地新的窗口。
 *
 * @return `(拉到的窗口, 结果)`。调用方负责把拉到的窗口合并回本地并落库。
 */
suspend fun pullChanges(
    config: WebDavConfig,
    localSessions: List<ChatSession>,
    state: SyncState,
    client: WebDavClient,
    images: ImageStore = defaultImageStore,
): Pair<List<ChatSession>, SyncOutcome> {
    if (!isSyncConfigured(config)) return emptyList<ChatSession>() to SyncOutcome()

    client.ensureCollections(config)
    val manifest = client.fetchManifest(config) ?: emptyManifest()
    state.manifest = manifest
    state.ready = true

    val localMap = localSessions.associateBy { it.id }
    val pulled = mutableListOf<ChatSession>()
    var imageCount = 0

    for ((id, meta) in manifest.sessions) {
        val local = localMap[id]
        if (local != null && local.updatedAt >= meta.updatedAt) continue

        val element = client.fetchSessionFile(config, id) ?: continue
        val raw = element as? JsonObject ?: continue
        val decoded = runCatching { WwJson.decodeFromJsonElement<ChatSession>(raw) }.getOrNull() ?: continue
        // 第 ④ 条（toolCallStepLimit）要求从原始 JSON 取值，与存档导入路径一致。
        val session = normalizeChatSession(decoded, raw["toolCallStepLimit"])

        // 补齐云端有、本地没有的图片（`syncEngine.ts:126`）。
        for (imageId in imageIdsOf(listOf(session)).filter { it.isNotEmpty() }.distinct()) {
            if (!manifest.images.containsKey(imageId)) continue
            if (images.get(imageId) != null) continue
            val payload = client.fetchImageFile(config, imageId) ?: continue
            val image = runCatching { WwJson.decodeFromJsonElement<StoredImage>(payload) }.getOrNull() ?: continue
            images.put(image)
            imageCount++
        }

        pulled += session
    }

    return pulled to SyncOutcome(
        pulled = pulled.size,
        images = imageCount,
        message = if (pulled.isNotEmpty()) "已拉取 ${pulled.size} 个窗口" else "无更新",
    )
}
