package com.watchworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.InfoCircle

/**
 * 全局「同一时刻只开一个注释浮层」的仲裁器（对标参考版 `InfoHint.tsx` 的
 * 「点别处（含另一个图标）收起，同时只有一个浮层」）。
 *
 * 每个 [InfoHint] 实例在 `remember` 里拿一个唯一 key；点开时把它写进 [openKey]，
 * 其它实例读到不匹配就自动收起 —— 不需要额外的全局事件总线。
 */
private object InfoHintState {
    var openKey: Any? by mutableStateOf(null)
}

/** 浮层与范围边缘的最小间距（与位置计算里的 8dp 一致）。 */
private val HINT_MARGIN = 8.dp

/**
 * 浮层允许出现的范围（窗口坐标）。
 *
 * 弹窗在 Compose 里是**独立窗口**，只夹到「窗口边缘」的话，浮层会压在弹窗面板外面。
 * 宿主弹窗用 [HintBounds] 把面板范围注进来后，浮层就只在这个矩形里排布；
 * 没注入时退回整个窗口（与改造前一致）。
 */
private val LocalHintBounds = staticCompositionLocalOf<IntRect?> { null }

/**
 * 宿主弹窗面板调用：把子树里的 [InfoHint] 浮层限制在本面板范围内。
 *
 * ```
 * Dialog { BoxWithConstraints(...) { HintBounds { Surface { ... } } } }
 * ```
 */
@Composable
fun HintBounds(content: @Composable () -> Unit) {
    // 面板尺寸要等布局完成才知道，所以用 state 回填；变化时浮层会重排一次。
    var bounds by remember { mutableStateOf<IntRect?>(null) }
    Box(Modifier.onGloballyPositioned { bounds = it.boundsInWindow().roundToIntRect() }) {
        CompositionLocalProvider(LocalHintBounds provides bounds) { content() }
    }
}

/**
 * 行内「ⓘ」说明角标（对标 Web 版 `InfoHint.tsx`）。
 *
 * 把过去**直接暴露在界面上的长注释**收进浮层：默认收起，点图标才展开。
 * 交互语义严格照参考文件：
 * - 点图标展开 / 收起；点别处（含另一个图标）收起 —— **同时只会有一个浮层**；
 * - 浮层默认在图标下方，下方放不下就上翻，上下都放不下就贴下缘 —— **绝不飘出宿主弹窗**
 *   （面板范围由 [HintBounds] 注入；没注入才退回屏幕）；
 * - 水平居中对齐图标，再夹进该范围内；最大宽高同样不越界，放不下就内部滚动。
 *
 * 视觉：**12dp 上标图标**（主色，打开时不变色）+ `12sp` 说明浮层
 * （[WwType.hint]，浮层底 `surfaceElevated`、1dp 描边、12dp 圆角、10×8dp 内边距）。
 *
 * ⚠️ 图标是**上标**：比起所在行的基线抬高约 3dp（参考版的 `top: -0.3em`）。
 *    调用方把它放在标签行里即可，不要自己再调垂直对齐。
 *
 * @param text 浮层里的说明文字。
 */
@Composable
fun InfoHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    val key = remember { Any() }
    val open = InfoHintState.openKey == key
    val density = LocalDensity.current
    val bounds = LocalHintBounds.current
    val provider = remember(density, bounds) { HintPositionProvider(density, bounds) }
    // 浮层的最大尺寸跟着宿主走：弹窗又窄又矮时浮层一起缩，而不是压到面板外。
    val marginPx = with(density) { HINT_MARGIN.roundToPx() }
    // 有宿主面板时，浮层可以一直用到面板边缘（两侧各留 8dp）；没注入才退回 260dp 阅读宽。
    val maxWidth = bounds?.let { with(density) { (it.width - 2 * marginPx).toDp() } } ?: 260.dp
    val maxHeight = bounds?.let { with(density) { (it.height - 2 * marginPx).toDp() } } ?: Dp.Infinity

    Box(modifier = modifier) {
        // 上标：layout 里把图标整体抬 3dp，不影响所在行的行高与其它元素。
        Icon(
            imageVector = TablerIcons.InfoCircle,
            contentDescription = "说明",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(12.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, -(3.dp.toPx().toInt()))
                    }
                }
                .clickable { InfoHintState.openKey = if (open) null else key },
        )
        if (open) {
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = { InfoHintState.openKey = null },
                properties = PopupProperties(focusable = true),
            ) {
                Text(
                    text = text,
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .heightIn(max = maxHeight)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .background(WwSurfaces.dialogBase, RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        // 面板太矮时（浮层不许超出弹窗）改为可滚动，而不是把文字截掉。
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * 浮层定位：照参考版 `InfoHint.tsx` 的算位规则——
 * 默认图标下方 6dp；下方越界就翻到上方；上下都放不下就贴住下缘；
 * 水平居中图标后夹进视口（留 8dp 边距）。
 *
 * `PopupPositionProvider.calculatePosition` 拿不到 Density，所以在组合里构造时注入。
 */
private class HintPositionProvider(
    density: Density,
    /** 宿主弹窗面板的范围；null = 整个窗口。 */
    private val bounds: IntRect?,
) : PopupPositionProvider {
    private val margin = with(density) { HINT_MARGIN.roundToPx() }
    private val gap = with(density) { 6.dp.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // 可用范围：宿主弹窗面板；没接面板时退回整个窗口。
        val left0 = bounds?.left ?: 0
        val top0 = bounds?.top ?: 0
        val right = bounds?.let { it.left + it.width } ?: windowSize.width
        val bottom = bounds?.let { it.top + it.height } ?: windowSize.height

        // 垂直：优先下方，放不下翻上方，仍放不下贴下缘（再夹一次，保证不越界）。
        var top = anchorBounds.bottom + gap
        if (top + popupContentSize.height > bottom - margin) {
            val above = anchorBounds.top - popupContentSize.height - gap
            top = if (above >= top0 + margin) {
                above
            } else {
                (bottom - popupContentSize.height - margin).coerceAtLeast(top0 + margin)
            }
        }

        // 水平：居中对齐图标，再夹进范围。
        val centered = anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2
        val maxLeft = (right - popupContentSize.width - margin).coerceAtLeast(left0 + margin)
        val left = centered.coerceIn(left0 + margin, maxLeft)

        return IntOffset(left, top)
    }
}
