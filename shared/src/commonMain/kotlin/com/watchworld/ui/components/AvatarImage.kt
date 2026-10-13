package com.watchworld.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.platform.AvatarOwner
import com.watchworld.platform.AvatarStore
import com.watchworld.platform.defaultAvatarStore
import com.watchworld.ui.image.decodeImageBytes
import com.watchworld.ui.theme.WwSurfaces
import compose.icons.TablerIcons
import compose.icons.tablericons.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 头像图片来源。
 *
 * 三档对应三处落盘位置（见 [AvatarStore]），也对应「窗口覆盖 Agent 默认」的优先级 ——
 * 但**优先级由调用方决定**：调用方按「窗口头像 → Agent 默认」的顺序传，
 * 这里只负责把给到的那一个画出来。
 */
sealed interface AvatarSource {
    /** 用户头像（全局一个）。 */
    data class User(val fileName: String) : AvatarSource

    /** 窗口级 AI 头像，覆盖 Agent 默认。 */
    data class Session(val sessionId: String, val fileName: String) : AvatarSource

    /**
     * 窗口级**用户**头像，覆盖全局用户头像。
     *
     * ⚠️ 与 [Session] 分开是必须的：两者落盘目录不同（`sessions/<id>/user/` vs
     * `sessions/<id>/`）。共用同一个目录时 `AvatarStore.save` 会清掉上一张，
     * 两个头像互相删掉（用户 2026-10-13 报「窗口头像只能保持一个上传状态」）。
     */
    data class SessionUser(val sessionId: String, val fileName: String) : AvatarSource

    /**
     * Agent 默认头像：`agents/<agentId>/` 下有图片就用图片，
     * 没有就把 [emoji] 当兜底画出来（两者都没有则退回 [AvatarImage] 的图标）。
     */
    data class Agent(val agentId: String, val emoji: String) : AvatarSource
}

/**
 * 圆形头像：**有图就画图，没图才退兜底**。
 *
 * 上一版这里从不画图（`decodeDataUrlImage` 还没接上，TODO 留在了代码里），
 * 所以「上传了头像却只看见 U / AI 两个字」。现在图走
 * [AvatarStore] 读文件 → [decodeImageBytes] 解码，文字只作为 Agent emoji 的兜底。
 *
 * @param source 头像来源；`null` 表示不显示头像（调用方据此整块跳过）。
 * @param size 显示直径。
 * @param reloadToken 换图后用它触发重读 —— 同一个 [source] 值重画时 `LaunchedEffect`
 *   不会重跑，上传 / 移除后把计数器 +1 即可。
 */
@Composable
fun AvatarImage(
    source: AvatarSource?,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    reloadToken: Any? = null,
    fallbackIcon: ImageVector? = TablerIcons.User,
    store: AvatarStore = defaultAvatarStore,
) {
    var bitmap by remember(source, reloadToken) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source, reloadToken) {
        bitmap = loadAvatarBitmap(source, store)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(WwSurfaces.card)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val emoji = (source as? AvatarSource.Agent)?.emoji?.takeIf { it.isNotBlank() }
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // emoji 兜底按盒子的比例走（结构性徽章，不受三档字号约束）。
            emoji != null -> Text(emoji, fontSize = (size.value * 0.55f).sp)
            fallbackIcon != null -> Icon(
                fallbackIcon,
                null,
                Modifier.size(size * 0.55f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 读盘 + 解码；任何一步失败都返回 `null`，由调用方退兜底。 */
private suspend fun loadAvatarBitmap(source: AvatarSource?, store: AvatarStore): ImageBitmap? {
    if (source == null) return null
    val bytes = withContext(Dispatchers.IO) {
        when (source) {
            is AvatarSource.User -> store.read(AvatarOwner.User, "", source.fileName)
            is AvatarSource.Session -> store.read(AvatarOwner.Session, source.sessionId, source.fileName)
            is AvatarSource.SessionUser -> store.read(AvatarOwner.SessionUser, source.sessionId, source.fileName)
            is AvatarSource.Agent -> store.read(AvatarOwner.Agent, source.agentId)
        }
    } ?: return null
    return decodeImageBytes(bytes)
}
