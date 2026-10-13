package com.watchworld.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * 桌面端的图片存放与压缩。目标平台只有 Windows（与 [appDataDir] 一致），
 * 不做 macOS / Linux 分支。
 *
 * ⚠️ 文件布局：`%APPDATA%\WatchWorld\images\<id>.json`，一图一文件。
 *    不建子目录、不改文件名 —— id 由 `img-<毫秒>-<随机>` 生成（见 commonMain），
 *    只含 `[A-Za-z0-9._-]`，可直接当文件名（WebDAV 的 `safeFileName` 也是这个假设）。
 */

/** `imageStore.ts` 体量判断用的一组阈值，与 Web 版逐字一致。 */
private const val SMALL_IMAGE_BYTES = 400 * 1024
private const val STORE_DIR_NAME = "images"

/** 磁盘上的图片目录（**不含**文件名），Windows 上是 `%APPDATA%\WatchWorld\images`。 */
private fun imageDir(): File = File(appDataDir(), STORE_DIR_NAME)

actual fun defaultImageBlobStore(): ImageBlobStore = FileImageBlobStore()

/**
 * 一图一文件的实现。所有磁盘操作都切到 [Dispatchers.IO]：
 * 一次 dataURL 写入可能是几 MB，不能让它在 Compose 的主线程上跑。
 */
private class FileImageBlobStore(
    private val dir: File = imageDir(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ImageBlobStore {

    private fun fileOf(id: String): File = File(dir, "$id.json")

    override suspend fun put(id: String, json: String) = withContext(io) {
        dir.mkdirs()
        // 先写临时文件再改名：中途崩溃/断电时不会留下半截 JSON 被当成有效图片。
        val target = fileOf(id)
        val temp = File(dir, "$id.json.tmp")
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    override suspend fun get(id: String): String? = withContext(io) {
        val file = fileOf(id)
        if (!file.isFile) null else runCatching { file.readText() }.getOrNull()
    }

    override suspend fun remove(id: String) = withContext(io) {
        runCatching { fileOf(id).delete() }
        Unit
    }

    override suspend fun listIds(): List<String> = withContext(io) {
        val files = dir.listFiles() ?: return@withContext emptyList()
        files.asSequence()
            .filter { it.isFile && it.name.endsWith(".json") && !it.name.endsWith(".tmp") }
            .map { it.name.removeSuffix(".json") }
            .toList()
    }
}

/**
 * `compressImageFile` 的解码/缩放/编码段（`imageStore.ts:90`）。
 *
 * 与 Web 版同样的两条规则：
 * 1. 原图 ≤ 400KB **且**最长边 ≤ [maxDim] → 原样保留（不二次编码）；
 * 2. 否则等比缩放到 [maxDim] 内：PNG 保持 PNG（可能带透明通道），其余转 JPEG。
 *
 * 读不出图像（非图片字节 / 无 ImageIO 解码器）时不抛，退回原图 —— 有损好过丢图。
 */
@OptIn(ExperimentalEncodingApi::class)
actual fun compressImageBytes(
    bytes: ByteArray,
    mime: String,
    maxDim: Int,
    quality: Double,
): CompressedImage {
    val sourceMime = mime.ifEmpty { "image/png" }
    val original = "data:$sourceMime;base64,${Base64.encode(bytes)}"
    val fallback = CompressedImage(
        dataUrl = original,
        width = 0,
        height = 0,
        mime = sourceMime,
        bytes = dataUrlBytes(original),
    )

    val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return fallback
    val w0 = decoded.width
    val h0 = decoded.height
    if (w0 <= 0 || h0 <= 0) return fallback

    if (bytes.size <= SMALL_IMAGE_BYTES && maxOf(w0, h0) <= maxDim) {
        return CompressedImage(
            dataUrl = original,
            width = w0,
            height = h0,
            mime = sourceMime,
            bytes = dataUrlBytes(original),
        )
    }

    val scale = minOf(1.0, maxDim.toDouble() / maxOf(w0, h0))
    val w = maxOf(1, Math.round(w0 * scale).toInt())
    val h = maxOf(1, Math.round(h0 * scale).toInt())

    val isPng = sourceMime.contains("png")
    val outMime = if (isPng) "image/png" else "image/jpeg"
    // JPEG 没有透明通道：用 RGB 画布（与 canvas 转 JPEG 时把透明处压成黑同理）。
    val canvas = if (isPng) {
        BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    } else {
        BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    }
    val graphics = canvas.createGraphics()
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    )
    graphics.drawImage(decoded, 0, 0, w, h, null)
    graphics.dispose()

    val encoded = runCatching {
        if (isPng) encodePng(canvas) else encodeJpeg(canvas, quality)
    }.getOrNull() ?: return fallback

    val dataUrl = "data:$outMime;base64,${Base64.encode(encoded)}"
    return CompressedImage(
        dataUrl = dataUrl,
        width = w,
        height = h,
        mime = outMime,
        bytes = dataUrlBytes(dataUrl),
    )
}

private fun encodePng(image: BufferedImage): ByteArray {
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

private fun encodeJpeg(image: BufferedImage, quality: Double): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality.toFloat().coerceIn(0f, 1f)
        }
        writer.write(null, IIOImage(image, null, null), param)
    }
    writer.dispose()
    return out.toByteArray()
}
