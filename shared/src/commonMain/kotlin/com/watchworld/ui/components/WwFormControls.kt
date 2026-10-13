package com.watchworld.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.ui.theme.WwFont
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import kotlin.math.roundToInt

/**
 * 表单输入框的**统一规格**（对标 Web 版各 Modal 里的 `<input>` / `<textarea>`）。
 *
 * 参考版的类名是：
 * ```
 * label:  block text-[11px] text-slate-500 mb-1
 * input:  w-full bg-slate-50 p-2.5 rounded-xl border border-slate-300 text-slate-900
 * hint:   mt-1 text-[10px] text-slate-400
 * ```
 * 原生按「文字三档 + 紧凑框」口径落地（见 `memory/ui-typography-standard.md`）：
 * **label 与框内文字同为默认正文 14sp**、框内水平 10dp / 垂直每侧 1dp、10dp 圆角、1px 细描边；
 * 单行框不设固定高度 —— 高度由文字行高决定（字号调大时框随文字自然增高）。
 *
 * ⚠️ **不要用 `OutlinedTextField` 顶替**：M3 的它强制 `defaultMinSize(minHeight = 56.dp)`、
 *    正文 16sp、label 浮在框内，铺出来比参考版高一大截 —— 「文字与框的高度比例不对」就是这么来的。
 *    `ui/` 下已无 `OutlinedTextField` 用法，新表单一律走本组件。
 *
 * @param hint 字段说明；**不再直接铺在框下方**，收成标签行末尾的 [InfoHint] 浮层角标
 *   （用户 2026-10-11 定案：框外注释一律收起、默认不暴露文字）。
 * @param trailing 框内右侧的挂件（如密钥显隐按钮）。
 */
@Composable
fun WwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    hint: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    minHeight: Dp = WwSize.formFieldMinHeight,
    fontSize: TextUnit = WwType.body,
    monospace: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    ) {
    val shape = RoundedCornerShape(10.dp)
    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = WwSize.compactTextLineHeight,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = if (monospace) FontFamily.Monospace else WwFont.family,
    )

    Column(modifier = modifier) {
        if (label != null || hint != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (label != null) {
                    Text(
                        text = label,
                        fontSize = WwType.body,
                        lineHeight = WwSize.compactTextLineHeight,
                        // ⚠️ 必须限一行：调用方漏给权重把框挤窄时，label 会一个字一行地竖排，整行被撑高。
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hint != null) {
                    if (label != null) Spacer(Modifier.width(6.dp))
                    InfoHint(hint)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WwSurfaces.inset, shape)
                .border(
                    width = if (isError) 1.5.dp else 1.dp,
                    color = if (isError) MaterialTheme.colorScheme.error else WwSurfaces.cardBorder,
                    shape = shape,
                )
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = 10.dp, vertical = WwSize.formFieldVerticalPadding),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                        maxLines = maxLines,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    enabled = enabled,
                    readOnly = readOnly,
                    keyboardOptions = keyboardOptions,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

/**
 * 参考版表单里的**主操作按钮**（`px-4 py-1.5 rounded-lg bg-m1-600 text-white`；文字走默认正文 14sp）。
 * 与 M3 `Button` 的差别同样在高度：M3 默认 40dp 起且内边距更大。
 */
@Composable
fun WwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .background(
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = WwSize.buttonVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 参考版表单里的**下拉选择框**（原生 `<select>`：`p-2.5 rounded-xl border`；文字与标签同走默认正文 14sp）。
 *
 * ⚠️ 用自绘触发器而不是 M3 `ExposedDropdownMenuBox`：后者的最小高度与文字规格
 *    同样比参考版大一截（理由见 [WwTextField] 的注释）。
 */
@Composable
fun WwSelectField(
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icons: Map<String, ImageVector> = emptyMap(),
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WwSurfaces.inset, shape)
                    .border(1.dp, WwSurfaces.cardBorder, shape)
                    .defaultMinSize(minHeight = WwSize.formFieldMinHeight)
                    .clickable { open = true }
                    .padding(horizontal = 10.dp, vertical = WwSize.formFieldVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current,
                    style = TextStyle(fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = WwSurfaces.dialogBase,
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                shadowElevation = 8.dp,
            ) {
                options.forEachIndexed { index, option ->
                    WwDropdownItem(
                        text = option,
                        icon = icons[option],
                        dividerAbove = index > 0,
                        onClick = { onSelect(option); open = false },
                    )
                }
            }
        }
    }
}

/**
 * 参考版表单里的**次操作按钮**（`px-3 py-1.5 rounded-lg bg-slate-100`；文字走默认正文 14sp）。
 *
 * @param leadingIcon 可选前置图标（参考版的一批次按钮带 12-14px 图标，如「新建规则」「全部跟随默认」）。
 */
@Composable
fun WwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(WwSurfaces.inset, shape)
            .border(1.dp, WwSurfaces.cardBorderSubtle, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = WwSize.secondaryButtonVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(13.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
        Text(
            text,
            style = TextStyle(fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * 参考版 `<input type="range">` 的**紧凑等价物**（表单里的滑杆，如 Agent 的「发散度」）。
 *
 * 视觉规格对齐浏览器原生滑杆：**4dp 细轨道 + 12dp 圆点**，控件整体 24dp 高
 * （多出的上下留白是给鼠标抓取用的）。轨道全宽画淡色，已选段叠主色，圆点压在取值处。
 *
 * ⚠️ 不用 M3 `Slider`：
 *   1. 它自带 48dp 最小交互高度与更大的拇指，几个叠起来会把弹窗撑高一截；
 *   2. `steps != 0` 时它会在轨道上**画出一排刻度圆点**，而参考版的原生 `input[type=range]`
 *      只有主轴和拇指，没有刻度点 —— 这就是「滑杆里看见圆点」的来源。
 *
 * 吸附由调用方自己做（参考版是 `step` 属性）：传 `step > 0` 即按该步长吸附落点。
 *
 * @param step 吸附步长；`0`（默认）表示连续取值。
 */
@Composable
fun WwRangeSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 0f,
) {
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val emit: (Float) -> Unit = { raw ->
        val snapped = if (step > 0f && step.isFinite()) {
            (range.start + ((raw - range.start) / step).roundToInt() * step)
                .coerceIn(range.start, range.endInclusive)
        } else {
            raw.coerceIn(range.start, range.endInclusive)
        }
        onValueChange(snapped)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WwSize.sliderHeight)
            .pointerInput(range, step) {
                // 每次手势都重读一次布局宽度：窗口缩放后 pointerInput 不会自动重建。
                fun emitAt(x: Float) {
                    val width = size.width.toFloat()
                    if (width <= 0f) return
                    emit(range.start + (x / width).coerceIn(0f, 1f) * span)
                }
                awaitEachGesture {
                    // 按下即取值（等价浏览器的「点轨道跳转」），随后跟随拖动。
                    val down = awaitFirstDown()
                    emitAt(down.position.x)
                    drag(down.id) { change ->
                        emitAt(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 6.dp.toPx()
            val centerY = size.height / 2f
            // 圆点两端留出半径，避免贴边被裁掉（浏览器同样把拇指限制在轨道内）。
            val usable = (size.width - thumbRadius * 2).coerceAtLeast(0f)
            val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
            val thumbX = thumbRadius + usable * fraction

            drawLine(
                color = trackColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accent,
                start = Offset(thumbRadius, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            // 唯一的圆点是拇指本身 —— 不再画任何刻度点。
            drawCircle(color = accent, radius = thumbRadius, center = Offset(thumbX, centerY))
        }
    }
}

/** 开关图形的视觉缩放（布局尺寸仍是 M3 的 52×32，只有绘制变小）。 */
private const val WW_SWITCH_SCALE = 0.85f

/**
 * 全局统一的小图标按钮（消息动作行、输入栏图标按钮等共用）。
 *
 * 收在一处的理由：图标按钮一度各写各的 —— 消息气泡用 emoji / `◀ ▶` 字符，
 * 输入栏自绘 24dp 透明点击区，两边图标尺寸、点击区、底色都不一样。
 *
 * - **点击区 [size] 默认 24dp**：M3 `IconButton` 默认 32dp、并强制 48dp 最小交互尺寸，
 *   会把所在行撑高（§九.2 的同类问题）；
 * - 图标 [iconSize] 默认 14dp（参考版消息动作行 12–14px）；
 * - 底色默认 `WwSurfaces.inset`（与 `WwTag` 同族），透明底传 `Color.Transparent`；
 * - 禁用 = 图标降到 38% 透明（与 M3 的 disabled 一致）。
 */
@Composable
fun WwIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = WwSurfaces.inset,
    border: BorderStroke? = null,
    iconSize: Dp = 14.dp,
    size: Dp = 24.dp,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) tint else tint.copy(alpha = tint.alpha * 0.38f),
        )
    }
}

/**
 * 紧凑开关（用户 2026-10-12：「消息元数据显示开关」每行框的内边距也太大）。
 *
 * M3 的 `Switch` 自带 **48dp 最小交互尺寸**（`minimumInteractiveComponentSize`）：
 * 裸放一个开关，所在行就被硬撑到 48dp 高（文字上下各空出 ~10dp，看着就是「内边距很大」）。
 * 这里把交互尺寸局部放开、并把图形缩到 [WW_SWITCH_SCALE]，行高回到文字行。
 */
@Composable
fun WwSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier.graphicsLayer {
                scaleX = WW_SWITCH_SCALE
                scaleY = WW_SWITCH_SCALE
            },
        )
    }
}

/** 参考版的 `border-dashed`（表单里的空态提示框）。 */
fun Modifier.dashedRoundedBorder(
    color: Color,
    cornerRadius: Dp = 10.dp,
    dash: Dp = 4.dp,
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
        ),
    )
}

/**
 * 下拉菜单的**统一样式**（用户 2026-10-10 定案，以世界观 ＋ 菜单为基准）：
 * 每项「前置图标 + 文字」，项更高（12×9 内边距）、文字走默认正文 14sp，项间一条分隔线。
 * 容器样式由调用方的 `DropdownMenu` 传（圆角 12dp + 细描边 + 阴影）——
 * 见 [WwSelectField]；世界观的 ＋ 菜单、改上级弹窗也用这一套。
 */
@Composable
fun WwDropdownItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    dividerAbove: Boolean = false,
) {
    if (dividerAbove) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(1.dp)
                .background(WwSurfaces.cardBorderSubtle),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = TextStyle(fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
