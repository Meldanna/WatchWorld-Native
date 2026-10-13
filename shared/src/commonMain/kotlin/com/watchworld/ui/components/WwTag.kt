package com.watchworld.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watchworld.ui.theme.WwFont
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType

/** 标签的默认横向内边距。 */
private val WwTagPaddingH = 6.dp

/**
 * **带文字的小标签 / 徽标的统一规格**（用户 2026-10-11 定案：标签高度必须一致）。
 *
 * 此前各处标签各自写 `Surface{Text}`：有的 `5×1`、有的 `6×2`、有的 `8×1`，字号也 10/11/12sp
 * 混用 —— 并排看高度参差不齐。统一到本组件后：
 * - 文字恒为 [WwType.hint]（12sp，**随「界面字体大小」缩放**）；
 * - 使用显式紧凑行高与固定内边距，边框只比文字内容高一点；
 * - 不设固定最小高度，字号变化由内容直接带动边框变化。
 *
 * 颜色 / 圆角 / 描边按语义由调用方传（`background` / `contentColor` / `shape` / `border`）。
 * 纯数字角标（未读计数等）不需要走这里 —— 它们是徽章，不是文字标签。
 *
 * 带图标的**小按钮**（控制栏里的「AI 操作说明」「新建分支」这类）同样走这里：
 * 传 [leadingIcon] + [onClick]，高度仍与旁边的标签一模一样 —— 这正是「一行里高低不齐」的解法。
 *
 * @param pill 胶囊（整圆 `RoundedCornerShape(100.dp)`）：**不画框线**（用户 2026-10-13）。
 *   非胶囊的方框默认补一根模块2 框线（[WwSurfaces.cardBorder]）；传 [border] 可覆盖。
 * @param monospace 等宽字体（模式串、名称等）。
 * @param leadingIcon 前置 12dp 图标（可点击的小按钮用）。
 */
@Composable
fun WwTag(
    text: String,
    modifier: Modifier = Modifier,
    background: Color? = null,
    contentColor: Color? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    border: BorderStroke? = null,
    pill: Boolean = false,
    monospace: Boolean = false,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val bg = background ?: WwSurfaces.inset
    val fg = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    // 非胶囊的方框默认补一根模块2 框线（用户 2026-10-13）；胶囊不画。
    val effectiveBorder = border ?: if (pill) null else BorderStroke(1.dp, WwSurfaces.cardBorder)
    Row(
        modifier = modifier
            .background(bg, shape)
            .then(if (effectiveBorder != null) Modifier.border(effectiveBorder, shape) else Modifier)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = WwSize.compactTagMinHeight)
            .padding(horizontal = WwTagPaddingH, vertical = WwSize.compactTagVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
                if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (enabled) fg else fg.copy(alpha = 0.38f),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = TextStyle(fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight),
            fontFamily = if (monospace) FontFamily.Monospace else WwFont.family,
            color = if (enabled) fg else fg.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
