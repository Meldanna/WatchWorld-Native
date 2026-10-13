package com.watchworld.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watchworld.platform.ImageStore
import com.watchworld.platform.StoredImage
import com.watchworld.platform.defaultImageStore
import com.watchworld.ui.components.WwModalScrim

/*
 * 消息附图（`MessageItem.tsx:332-347` 的原生版）。
 *
 * Web 直接 `<img src={dataUrl}>` —— 浏览器会自己解码。原生没有这个便利：
 * dataURL 要先变成 Compose 的 [ImageBitmap]，而解码是平台能力（桌面走 Skia），
 * 所以 [decodeDataUrlImage] 是 expect。
 *
 * 缩略图 180×180（`max-w-[180px] max-h-[180px]` 逐字），点击弹大图 ——
 * 与 Web 的 `previewSrc` 预览态同一语义。
 */

/** dataURL → 位图；解不开（格式不认 / 已被清理）返回 `null`。 */
expect fun decodeDataUrlImage(dataUrl: String): ImageBitmap?

/** 原始图片字节 → 位图（头像文件走这条路，它们不是 dataURL）；解不开返回 `null`。 */
expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?

/**
 * 按 id 从图片存储读回一组附图。
 *
 * ⚠️ 图片本体可能还没同步下来（WebDAV 拉取未完成 / 云端就没有）。因此这里
 * **只负责读**、不藏失败：读不到就返回空表，由调用方决定是否退回
 * `📷 N 张图片` 占位（T16 的行为）。
 */
@Composable
fun rememberMessageImages(
    imageIds: List<String>?,
    store: ImageStore = defaultImageStore,
): List<StoredImage> {
    if (imageIds.isNullOrEmpty()) return emptyList()
    // 用拼接串当 key：`imageIds` 是每次都新建的 List，直接当 key 会反复重载。
    val key = imageIds.joinToString(",")
    var images by remember(key) { mutableStateOf<List<StoredImage>>(emptyList()) }
    LaunchedEffect(key) {
        images = imageIds.mapNotNull { store.get(it) }
    }
    return images
}

/** 渲染一组已读回的附图（缩略图 + 点击预览）。 */
@Composable
fun MessageImageAttachments(
    images: List<StoredImage>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    var preview by remember(images) { mutableStateOf<StoredImage?>(null) }

    Row(
        modifier = modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            val bitmap = remember(image.id) { decodeDataUrlImage(image.dataUrl) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = image.name ?: "消息附图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { preview = image },
                )
            }
        }
    }

    preview?.let { image ->
        val bitmap = remember(image.id) { decodeDataUrlImage(image.dataUrl) }
        ImagePreviewOverlay(bitmap = bitmap, name = image.name, onDismiss = { preview = null })
    }
}

/**
 * 点击附图后的全屏预览（`MessageItem.tsx:478-490`）。
 *
 * 用 [Dialog] 而不是在气泡里叠盒子：气泡是 [Row] 的子节点，直接 `fillMaxSize()`
 * 只会撑满气泡自身的约束，罩不住整屏。`usePlatformDefaultWidth = false` 才是全宽。
 */
@Composable
private fun ImagePreviewOverlay(bitmap: ImageBitmap?, name: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 遮罩：全应用统一款式（毛玻璃雾面，不压黑）；图片叠在它上面。
            WwModalScrim(onDismiss = onDismiss)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = name ?: "消息附图",
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            } else {
                Text(
                    "图片已不可用",
                    // 遮罩不压黑之后底板是浅雾面，`onPrimary`（白）在这里会看不见，改用文字主色。
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                )
            }
        }
    }
}
