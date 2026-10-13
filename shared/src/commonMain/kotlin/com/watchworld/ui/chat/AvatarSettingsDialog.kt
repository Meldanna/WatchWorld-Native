package com.watchworld.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.watchworld.ui.components.AvatarImage
import com.watchworld.ui.components.AvatarSource
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwRangeSlider
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwSwitch
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Message
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Upload
import compose.icons.tablericons.User
import kotlin.math.roundToInt

/** 头像大小的取值范围（用户 2026-10-13：上限 48 → 96px）。 */
private val AVATAR_SIZE_RANGE = 24f..96f

/** 相邻气泡间距的取值范围（用户 2026-10-13：上限 32 → 256px，默认 8）。 */
private val BUBBLE_SPACING_RANGE = 0f..256f

/**
 * 头像设置弹窗（窗口功能 →「头像设置」）。
 *
 * 四件事（用户 2026-10-12 补充：**窗口级用户头像也要加进来**）：
 * 1. 上传 / 移除**本窗口**的用户头像（覆盖全局用户头像，仅在本窗口生效）；
 * 2. 上传 / 移除**本窗口**的 AI 头像（覆盖该窗口所用 Agent 的默认头像）；
 * 3. 「显示头像」开关；
 * 4. 头像大小滑杆 24–96px、气泡间距滑杆 0–32px —— 款式与窗口 API 参数面板同一支 [WwRangeSlider]
 *    （不是 M3 `Slider`：后者 `steps != 0` 会在轨道上画一排刻度圆点）。
 *
 * 头像大小与开关是**全局显示设置**（[com.watchworld.model.MessageDisplaySettings]），
 * 所以这里存的是草稿，按「保存」统一写回。
 *
 * 所有文字走全局字号（[WwType]）— 用户 2026-10-12。
 */
@Composable
fun AvatarSettingsDialog(
    sessionId: String,
    currentUserAvatarFileName: String?,
    currentAiAvatarFileName: String?,
    currentAvatarSize: Int,
    currentShowAvatars: Boolean,
    currentBubbleSpacing: Int,
    /** 换过图之后 +1，用于让预览重新读盘（文件名不变时也刷新）。 */
    previewToken: Any? = null,
    onUploadUserAvatar: () -> Unit,
    onRemoveUserAvatar: () -> Unit,
    onUploadAiAvatar: () -> Unit,
    onRemoveAiAvatar: () -> Unit,
    onSave: (size: Int, showAvatars: Boolean, bubbleSpacing: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var avatarSize by remember(currentAvatarSize) { mutableStateOf(currentAvatarSize) }
    var showAvatars by remember(currentShowAvatars) { mutableStateOf(currentShowAvatars) }
    var bubbleSpacing by remember(currentBubbleSpacing) { mutableStateOf(currentBubbleSpacing) }

    FeatureDialog(
        title = "头像设置",
        subtitle = "本窗口的 AI 头像与消息区头像显示",
        icon = TablerIcons.User,
        onDismiss = onDismiss,
        maxWidth = 512.dp,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                WwSecondaryButton("取消", onClick = onDismiss)
                WwPrimaryButton(text = "保存", onClick = { onSave(avatarSize, showAvatars, bubbleSpacing) })
            }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── 窗口用户头像 ──
            SectionTitle(TablerIcons.User, "窗口用户头像", "上传后覆盖全局用户头像，仅在本窗口生效")
            Spacer(Modifier.height(WwSize.cardListGap))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(
                    source = AvatarSource.SessionUser(sessionId, currentUserAvatarFileName.orEmpty())
                        .takeIf { currentUserAvatarFileName != null },
                    size = avatarSize.dp,
                    reloadToken = previewToken,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (currentUserAvatarFileName != null) "已设置窗口用户头像" else "未设置窗口用户头像",
                        fontSize = WwType.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (currentUserAvatarFileName != null) "覆盖全局用户头像" else "使用全局用户头像",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(WwSize.cardListGap))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WwSecondaryButton(
                    text = "上传头像",
                    onClick = onUploadUserAvatar,
                    leadingIcon = TablerIcons.Upload,
                    modifier = Modifier.weight(1f),
                )
                if (currentUserAvatarFileName != null) {
                    WwSecondaryButton(
                        text = "移除",
                        onClick = onRemoveUserAvatar,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(WwPad.content))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(WwPad.content))

            // ── 窗口 AI 头像 ──
            SectionTitle(TablerIcons.Photo, "窗口 AI 头像", "上传后覆盖该窗口所用 Agent 的默认头像")
            Spacer(Modifier.height(WwSize.cardListGap))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 预览按当前滑杆大小画，调大小当场看得见。
                AvatarImage(
                    source = AvatarSource.Session(sessionId, currentAiAvatarFileName.orEmpty())
                        .takeIf { currentAiAvatarFileName != null },
                    size = avatarSize.dp,
                    reloadToken = previewToken,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (currentAiAvatarFileName != null) "已设置窗口头像" else "未设置窗口头像",
                        fontSize = WwType.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (currentAiAvatarFileName != null) "覆盖 Agent 默认头像" else "使用 Agent 默认头像",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(WwSize.cardListGap))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WwSecondaryButton(
                    text = "上传头像",
                    onClick = onUploadAiAvatar,
                    leadingIcon = TablerIcons.Upload,
                    modifier = Modifier.weight(1f),
                )
                if (currentAiAvatarFileName != null) {
                    WwSecondaryButton(
                        text = "移除",
                        onClick = onRemoveAiAvatar,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(WwPad.content))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(WwPad.content))

            // ── 显示开关 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (showAvatars) WwSurfaces.cardSelected else WwSurfaces.card,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("在消息区显示头像", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
                    Spacer(Modifier.width(6.dp))
                    InfoHint("关掉后气泡上方不再画头像框（模型名 / 时间等元信息照常）")
                    Spacer(Modifier.weight(1f))
                    WwSwitch(checked = showAvatars, onCheckedChange = { showAvatars = it })
                }
            }

            Spacer(Modifier.height(WwPad.content))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(WwPad.content))

            // ── 头像大小 ──
            SectionTitle(TablerIcons.Photo, "头像大小", "调节聊天气泡里的头像直径")
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${AVATAR_SIZE_RANGE.start.roundToInt()}",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WwRangeSlider(
                    value = avatarSize.toFloat(),
                    range = AVATAR_SIZE_RANGE,
                    step = 1f,
                    onValueChange = { avatarSize = it.roundToInt() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    "${AVATAR_SIZE_RANGE.endInclusive.roundToInt()}",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${avatarSize}px",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(WwPad.content))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(WwPad.content))

            // ── 相邻气泡间距（用户 2026-10-13 加） ──
            SectionTitle(TablerIcons.Message, "气泡间距", "调节聊天区里上下两条消息之间的垂直间距")
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${BUBBLE_SPACING_RANGE.start.roundToInt()}",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WwRangeSlider(
                    value = bubbleSpacing.toFloat(),
                    range = BUBBLE_SPACING_RANGE,
                    step = 1f,
                    onValueChange = { bubbleSpacing = it.roundToInt() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    "${BUBBLE_SPACING_RANGE.endInclusive.roundToInt()}",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${bubbleSpacing}px",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = WwType.title)
        if (hint != null) {
            Spacer(Modifier.width(6.dp))
            InfoHint(hint)
        }
    }
}
