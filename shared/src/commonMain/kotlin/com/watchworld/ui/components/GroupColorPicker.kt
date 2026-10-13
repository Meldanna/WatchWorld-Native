package com.watchworld.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Palette
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 分组颜色的**取色板**（用户 2026-10-13：「取色版不是主题那种，而是色环那种」）。
 *
 * 结构：外圈**色相环**（角度 → 色相）+ 内圈**饱和度 × 明度方块**（左白右纯色、上亮下黑），
 * 底部实时预览 + 十六进制值，`确定` 出 `#rrggbb`。
 *
 * 与主题色板（`ThemeDialog` 的 32 色方片）**不是一个东西**：那边是主题的主/辅色阶，
 * 这里是自由取色。别相互替换。
 *
 * 颜色数学（hex ↔ HSV）是纯 Kotlin，不用平台 API（commonMain 里没有 `android.graphics`）。
 */

/** Web `GroupModal.tsx:26-34` 的 8 个预设色（新建分组的随机初值也取自这里）。 */
val GROUP_PRESET_COLORS: List<String> = listOf(
    "#64748b", // Slate
    "#3b82f6", // Blue
    "#10b981", // Emerald
    "#8b5cf6", // Violet
    "#f59e0b", // Amber
    "#ec4899", // Pink
    "#ef4444", // Red
    "#06b6d4", // Cyan
)

/** 新建分组的默认色：对标 Web `GroupModal.tsx:56`（随机取一个预设色，不再是写死的石板灰）。 */
fun randomGroupColor(): String = GROUP_PRESET_COLORS.random()

/** `#rrggbb` → HSV（h 0–360，s/v 0–1）；解不出时退回 [GROUP_PRESET_COLORS] 首色。 */
internal fun hexToHsv(hex: String): Triple<Float, Float, Float> {
    val body = hex.removePrefix("#")
    val rgb = if (body.length == 6) body.toLongOrNull(16) else null
    if (rgb == null) return hexToHsv(GROUP_PRESET_COLORS.first())
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val rawHue = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return Triple(if (rawHue < 0f) rawHue + 360f else rawHue, if (max == 0f) 0f else d / max, max)
}

/** HSV → RGB，各通道 0–1。 */
internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Triple<Float, Float, Float> {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Triple(r + m, g + m, b + m)
}

/** HSV → `#rrggbb`（小写，与 Web 存档里的写法一致）。 */
internal fun hsvToHex(hue: Float, saturation: Float, value: Float): String {
    val (r, g, b) = hsvToRgb(hue, saturation, value)
    fun ch(f: Float) = (f * 255f).roundToInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(ch(r), ch(g), ch(b))
}

/** HSV → Color（色环与内圈方块的实时上色用）。 */
private fun hsvColor(hue: Float, saturation: Float = 1f, value: Float = 1f): Color {
    val (r, g, b) = hsvToRgb(hue, saturation, value)
    return Color(r, g, b)
}

/** `#rrggbb` → Color（解不出时退回预设首色）。 */
internal fun hexToColor(hex: String): Color {
    val (h, s, v) = hexToHsv(hex)
    return hsvColor(h, s, v)
}

/** 色环上 13 个色相停靠点（每 30°）——`Brush.sweepGradient` 会把这 13 段平滑插值成整圈。 */
private val HUE_STOPS: List<Color> = (0..12).map { hsvColor(it * 30f) }

/**
 * 分组编辑弹窗（用户 2026-10-13：侧边栏分组头加「编辑」）。
 *
 * 内容 = 名字输入框 + 颜色圆点（点开 [GroupColorPickerDialog] 取色环）。
 * 现取名 / 现颜色由调用方传入，确定后一次性回调。
 */
@Composable
fun GroupEditDialog(
    initialName: String,
    initialColor: String,
    onConfirm: (name: String, color: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var colorPickerOpen by remember { mutableStateOf(false) }
    val trimmed = name.trim()

    FeatureDialog(
        title = "编辑分组",
        icon = TablerIcons.Folder,
        onDismiss = onDismiss,
        maxWidth = 360.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            WwTextField(
                value = name,
                onValueChange = { name = it },
                label = "分组名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WwPad.fieldGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "分组颜色",
                    fontSize = WwType.body,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(22.dp).clickable { colorPickerOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(hexToColor(color), CircleShape)
                            .border(BorderStroke(1.dp, WwSurfaces.cardBorder), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(WwPad.content * 2))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WwSecondaryButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                WwPrimaryButton(
                    text = "确定",
                    enabled = trimmed.isNotEmpty(),
                    onClick = { onConfirm(trimmed, color) },
                )
            }
        }
    }

    if (colorPickerOpen) {
        GroupColorPickerDialog(
            initialHex = color,
            onConfirm = {
                color = it
                colorPickerOpen = false
            },
            onDismiss = { colorPickerOpen = false },
        )
    }
}

/**
 * 分组颜色取色弹窗（色环 + 明度/饱和度方块）。
 *
 * @param initialHex 打开时的初始色（`#rrggbb`）。
 * @param onConfirm 点「确定」时回调选中的 `#rrggbb`。
 */
@Composable
fun GroupColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(initialHex) { hexToHsv(initialHex) }
    var hue by remember { mutableStateOf(initial.first) }
    var saturation by remember { mutableStateOf(initial.second) }
    var value by remember { mutableStateOf(initial.third) }
    val picked = hsvToHex(hue, saturation, value)

    FeatureDialog(
        title = "选择分组颜色",
        icon = TablerIcons.Palette,
        onDismiss = onDismiss,
        maxWidth = 360.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ColorWheel(
                hue = hue,
                saturation = saturation,
                value = value,
                onHue = { hue = it },
                onSaturationValue = { s, v -> saturation = s; value = v },
            )
            Spacer(Modifier.height(WwPad.content))
            // 当前色预览 + 十六进制值
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(hexToColor(picked), CircleShape)
                        .border(BorderStroke(1.dp, WwSurfaces.cardBorder), CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = picked,
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(WwPad.content))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                WwSecondaryButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                WwPrimaryButton(text = "确定", onClick = { onConfirm(picked) })
            }
        }
    }
}

/** 色环（外圈色相 + 内圈饱和度/明度方块）。触摸：外圈取角度、内圈取坐标。 */
@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHue: (Float) -> Unit,
    onSaturationValue: (Float, Float) -> Unit,
) {
    val wheelSize = 236.dp
    val stroke = 22.dp
    val innerSize = 132.dp
    val ringColor = hsvColor(hue)

    Box(
        modifier = Modifier.size(wheelSize),
        contentAlignment = Alignment.Center,
    ) {
        // 外圈：色相环 —— 按下即取角度，随后跟随拖动。
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onHue(hueAt(down.position, size))
                    drag(down.id) { change ->
                        onHue(hueAt(change.position, size))
                        change.consume()
                    }
                }
            },
        ) {
            val radius = (size.minDimension - stroke.toPx()) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(brush = Brush.sweepGradient(HUE_STOPS), radius = radius, style = Stroke(width = stroke.toPx()), center = center)
            // 色相手柄：白圈 + 当前色内芯。
            val radians = Math.toRadians(hue.toDouble())
            val thumb = Offset(
                center.x + (radius * cos(radians)).toFloat(),
                center.y + (radius * sin(radians)).toFloat(),
            )
            drawCircle(Color.White, radius = stroke.toPx() / 2f, center = thumb)
            drawCircle(ringColor, radius = stroke.toPx() / 2f - 2.dp.toPx(), center = thumb)
        }

        // 内圈：横向饱和度（白 → 纯色）、纵向明度（透明 → 黑）。
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, ringColor)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .border(BorderStroke(1.dp, WwSurfaces.cardBorderSubtle), RoundedCornerShape(8.dp))
                .pointerInput(hue) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onSaturationValue(saturationAt(down.position.x, size), valueAt(down.position.y, size))
                        drag(down.id) { change ->
                            onSaturationValue(saturationAt(change.position.x, size), valueAt(change.position.y, size))
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * saturation.coerceIn(0f, 1f)
                val y = size.height * (1f - value.coerceIn(0f, 1f))
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(hexToColor(hsvToHex(hue, saturation, value)), radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

/** 触点相对圆心的角度（0° = 3 点方向、顺时针增大），与 `Brush.sweepGradient` 的起点一致。 */
private fun hueAt(offset: Offset, size: IntSize): Float {
    val dx = offset.x - size.width / 2f
    val dy = offset.y - size.height / 2f
    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return (deg + 360f) % 360f
}

private fun saturationAt(x: Float, size: IntSize): Float =
    if (size.width <= 0) 0f else (x / size.width).coerceIn(0f, 1f)

private fun valueAt(y: Float, size: IntSize): Float =
    if (size.height <= 0) 1f else 1f - (y / size.height).coerceIn(0f, 1f)
