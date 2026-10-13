package com.watchworld.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * 桌面端的 dataURL 解码。Compose Desktop 底层是 Skia，`org.jetbrains.skia.Image`
 * 自带 PNG / JPEG / WebP / GIF 解码，比自己接 ImageIO 更贴近 Compose 的位图表示。
 *
 * ⚠️ `toComposeImageBitmap()` 只在 skiko 系列目标（桌面 / iOS / Web）可见，
 * 因此这条实现**不能**上提进 commonMain —— 与 Android 目标统一时（T22）应由
 * `androidx.compose.ui.graphics.ImageBitmap` 的 Android 变体各自提供。
 */

@OptIn(ExperimentalEncodingApi::class)
actual fun decodeDataUrlImage(dataUrl: String): ImageBitmap? {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) return null
    return decodeImageBytes(Base64.decode(dataUrl.substring(comma + 1)))
}

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
