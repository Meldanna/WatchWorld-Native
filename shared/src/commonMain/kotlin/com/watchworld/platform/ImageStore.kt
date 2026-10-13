package com.watchworld.platform

import com.watchworld.model.WwJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.random.Random

/*
 * 图片存储层（`src/lib/imageStore.ts` 的 T21 迁移）。
 *
 * ### 为什么不能塞进设置存档
 *
 * Web 版把图片放 IndexedDB 而**不是** localStorage，因为后者只有约 5MB，
 * 且 `storage.safeSet` 超配额时只打日志、静默失败 —— 一张手机照 base64 后 2–5MB，
 * 会把**整个聊天记录**的持久化一起拖垮。原生版同理：图片不进 Room、不进 v5 存档，
 * 而是落在应用数据目录的 `images/` 下，`ChatMessage.imageIds` 继续只存 id 引用
 * （存档形状不变 → 与 Web 版互操作）。
 *
 * ### 换掉的三样东西
 *
 * | Web | 原生 |
 * |---|---|
 * | IndexedDB object store（`guanjie_media.images`） | `%APPDATA%\WatchWorld\images\<id>.json`（Android 归 T22） |
 * | `canvas` 压缩（`compressImageFile`） | `javax.imageio` 缩放 + JPEG/PNG 重编码（[compressImageBytes]） |
 * | `FileReader.readAsDataURL` | 平台直接产出 dataURL 字符串 |
 *
 * ### 与 Web 的两处有意差异
 *
 * 1. **不存 `Blob`，只存 dataURL**：Web 的 `StoredImage` 本来就只有 dataUrl，
 *    没有原始二进制；这里保持一致，落盘的就是整个 `StoredImage` 的 JSON。
 *    好处是 WebDAV 的 `pushImageFile` 一 JSON 直接就是 payload，不用二次转换。
 * 2. **没有 `ImageStore` 这个对象常量**：改成 `class ImageStore`，[ImageBlobStore]
 *    从构造函数注入（测试传内存实现），默认走 [defaultImageBlobStore]。
 *
 * ⚠️ 顶层 `expect fun`（不用 `expect class`）与 [appDataDir] / [currentTimeMillis] 同一写法：
 * expect/actual 类在 Kotlin 2.4 仍是 Beta，会产生编译警告，而本仓要求零警告。
 */

/** `imageStore.ts StoredImage`。字段名**逐字**照抄，WebDAV 云端互认。 */
@Serializable
data class StoredImage(
    val id: String,
    val dataUrl: String,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Int = 0,
    val name: String? = null,
    val createdAt: Long = 0L,
)

/** `ImageStore.usage()` 的返回。 */
data class ImageUsage(val count: Int, val bytes: Int)

/** [compressImageBytes] 的返回（`compressImageFile` 去掉 id / name / createdAt 后的部分）。 */
data class CompressedImage(
    val dataUrl: String,
    val width: Int,
    val height: Int,
    val mime: String,
    val bytes: Int,
)

/**
 * 估算 dataURL 的实际字节数（`imageStore.ts:62 dataUrlBytes`）。
 * base64 每 4 个字符还原 3 字节，无逗号时退回字符串长度。
 *
 * 纯函数，与平台无关 —— 所以留在 commonMain，不放进 blob store。
 */
fun dataUrlBytes(dataUrl: String): Int {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) return dataUrl.length
    return Math.round(((dataUrl.length - comma - 1) * 3) / 4.0).toInt()
}

/**
 * 图片本体的低层存放 —— 对应 Web 版的 IndexedDB object store。
 *
 * 只负责「按 id 存/取一段 JSON」，不理解 [StoredImage]；
 * 序列化、去重、统计、清理孤儿都由 [ImageStore] 在 commonMain 完成。
 */
interface ImageBlobStore {
    /** 写入（覆盖语义，对应 `store.put`）。 */
    suspend fun put(id: String, json: String)

    /** 读取；不存在返回 `null`。 */
    suspend fun get(id: String): String?

    /** 删除；不存在或删除失败都**不抛**（对应 `imageStore.ts:178` 的 try/catch）。 */
    suspend fun remove(id: String)

    /** 列出全部 id。读不出来时返回空表（对应 `listAll` 的 catch）。 */
    suspend fun listIds(): List<String>
}

/** 平台默认的图片存放位置。桌面 = `%APPDATA%\WatchWorld\images\`，Android 归 T22。 */
expect fun defaultImageBlobStore(): ImageBlobStore

/**
 * 压缩图片：只在小图时保留原图，否则等比缩放到 [maxDim] 内并转 JPEG
 * （PNG 因可能带透明通道而保留 PNG）。对应 `imageStore.ts:90 compressImageFile`。
 *
 * 解码 / 缩放 / 重编码是平台能力（桌面走 `javax.imageio`），因此是 expect。
 * 无法解码时**不抛**，退回原样返回（有损总好过丢图）。
 */
expect fun compressImageBytes(
    bytes: ByteArray,
    mime: String,
    maxDim: Int = 1600,
    quality: Double = 0.85,
): CompressedImage

/**
 * `imageStore.ts:160 ImageStore` —— 图片的增删查与统计。
 *
 * 所有方法都是 `suspend`：桌面实现会切到 IO 调度器，UI 不会卡在磁盘上。
 */
class ImageStore(private val blobs: ImageBlobStore = defaultImageBlobStore()) {

    suspend fun put(image: StoredImage) = blobs.put(image.id, WwJson.encodeToString(image))

    suspend fun get(id: String): StoredImage? {
        val json = blobs.get(id) ?: return null
        return runCatching { WwJson.decodeFromString<StoredImage>(json) }.getOrNull()
    }

    /** 批量读取，返回 `id -> 图片`；缺失的 id **不会**出现在结果里（`getMany`）。 */
    suspend fun getMany(ids: List<String>): Map<String, StoredImage> {
        val unique = ids.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, StoredImage>()
        for (id in unique) get(id)?.let { out[id] = it }
        return out
    }

    suspend fun remove(id: String) = blobs.remove(id)

    suspend fun removeMany(ids: List<String>) {
        for (id in ids.distinct()) blobs.remove(id)
    }

    suspend fun listAll(): List<StoredImage> = blobs.listIds().mapNotNull { get(it) }

    /** 统计占用，便于在界面上提示体积。 */
    suspend fun usage(): ImageUsage {
        val all = listAll()
        return ImageUsage(count = all.size, bytes = all.sumOf { it.bytes })
    }

    /** 清理没有被任何消息引用的孤儿图片，返回清掉的数量。 */
    suspend fun pruneOrphans(usedIds: List<String>): Int {
        val used = usedIds.toSet()
        val orphans = listAll().filterNot { used.contains(it.id) }
        if (orphans.isNotEmpty()) removeMany(orphans.map { it.id })
        return orphans.size
    }

    /**
     * `imageStore.ts:144 saveImageFile` —— 压缩后落盘，返回可直接写进
     * `ChatMessage.imageIds` 的图片。
     */
    suspend fun saveFile(bytes: ByteArray, mime: String, name: String? = null): StoredImage {
        val compressed = compressImageBytes(bytes, mime)
        val image = StoredImage(
            id = "img-${currentTimeMillis()}-${randomSuffix()}",
            dataUrl = compressed.dataUrl,
            mime = compressed.mime,
            width = compressed.width,
            height = compressed.height,
            bytes = compressed.bytes,
            name = name,
            createdAt = currentTimeMillis(),
        )
        put(image)
        return image
    }
}

/**
 * 全局默认实例。调用点（`ChatViewModel` / `SyncEngine` / UI）把它作为**默认参数**，
 * 测试传自己的 [ImageStore]（配内存 blob store）即可完全绕开磁盘。
 */
val defaultImageStore: ImageStore = ImageStore()

/** `Math.random().toString(36).slice(2, 8)` —— 6 位 36 进制随机串。 */
private fun randomSuffix(): String {
    val mask = 36L * 36 * 36 * 36 * 36 * 36
    return Random.nextLong(mask).toString(36).padStart(6, '0')
}
