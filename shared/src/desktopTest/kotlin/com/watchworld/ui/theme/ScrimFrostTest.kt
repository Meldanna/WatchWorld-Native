package com.watchworld.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.watchworld.model.UiMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 模态遮罩的**离屏验收**（用户 2026-10-13：「检查遮罩，所有的遮罩，把它们统一款式，
 * 毛玻璃效果，不用压黑」）。
 *
 * 守两件事，各自都能被改回去：
 * ① **不压黑**：遮罩压在 `windowBase` 上不许比底色更暗（改成 `Black.copy(alpha = …)` 立刻爆，
 *    这正是改前 7 处弹窗干的事）。
 * ② **毛玻璃的模糊真的生效**：桌面端 `Modifier.blur` 是把内容糊掉、而不是被忽略。
 *    弹窗是独立窗口、跨窗口糊不到下层像素，所以「玻璃」全靠主窗口自己这一下 ——
 *    万一它在桌面端是 no-op，界面上就只剩一层平色薄雾，这条用例是唯一能自动发现的地方。
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScrimFrostTest {

    @Test
    fun scrimIsATranslucentFrostNotABlackDimmer() {
        UiMode.entries.forEach { mode ->
            val scrim = captureToken(mode) { WwSurfaces.scrim }
            val windowBase = captureToken(mode) { WwSurfaces.windowBase }

            assertTrue(
                scrim.alpha in 0.01f..0.5f,
                "$mode：遮罩应当是薄雾面（alpha ≤ 0.5），实际 ${scrim.alpha}",
            )

            val frosted = composite(over = scrim, under = windowBase)
            assertTrue(
                luminance(frosted) >= luminance(windowBase) - 2,
                "$mode：遮罩把主内容压黑了（底色 ${luminance(windowBase)} → 罩后 ${luminance(frosted)}）—— " +
                    "「不用压黑」= 罩上去只能同亮度或更亮",
            )
        }
    }

    @Test
    fun blurActuallySoftensTheContentUnderTheScrim() {
        val sharp = transitionWidth(blurRadius = 0.dp)
        val frosted = transitionWidth(blurRadius = WwSurfaces.scrimBlurRadius)

        assertTrue(sharp <= 1, "对照组：没加模糊时白块边缘应当是硬的，实际过渡像素 $sharp")
        assertTrue(
            frosted >= 3,
            "毛玻璃没生效：加了 ${WwSurfaces.scrimBlurRadius} 模糊后边缘过渡像素仍只有 $frosted —— " +
                "桌面端 `Modifier.blur` 若被忽略，弹窗后面就只是「一层平色」而不是玻璃",
        )
        assertTrue(frosted > sharp, "模糊后边缘过渡应当明显宽于未模糊（$sharp → $frosted）")
    }

    /**
     * 白块居中、黑底，数**满屏中心那一行**里处于「既不是黑也不是白」的像素个数。
     * 没模糊时是硬边（0 个），模糊后是一段渐变（十几个）。
     *
     * 模糊加在**整屏容器**上 —— 和 `App` 里给主内容 `Column` 加的那一下同一个用法：
     * 默认的 `BlurredEdgeTreatment.Rectangle` 会把模糊结果裁到节点自身边界，
     * 节点和窗口一样大时才没有「边缘被裁掉」的差别。
     */
    private fun transitionWidth(blurRadius: androidx.compose.ui.unit.Dp): Int {
        val size = 120
        val bitmap = renderBitmap(size, size) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(Color.White),
                )
            }
        }
        val pixels = bitmap.toPixelMap()
        val y = size / 2
        var count = 0
        for (x in 0 until size) {
            val l = luminance(pixels[x, y])
            if (l in 40..215) count++
        }
        return count
    }

    private fun renderBitmap(width: Int, height: Int, content: @Composable () -> Unit): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.DARK) { content() }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    /** 把主题色从组合里取出来（遮罩与底板都是 `@Composable` 取值）。 */
    private fun captureToken(uiMode: UiMode, token: @Composable () -> Color): Color {
        var captured: Color? = null
        val scene = ImageComposeScene(width = 8, height = 8, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = uiMode) { captured = token() }
        }
        scene.render()
        scene.close()
        return captured ?: error("主题色未取到值")
    }

    /** 标准 src-over：遮罩（[over]）压在底色（[under]）上之后的实际颜色。 */
    private fun composite(over: Color, under: Color): Color {
        val a = over.alpha
        return Color(
            red = over.red * a + under.red * (1 - a),
            green = over.green * a + under.green * (1 - a),
            blue = over.blue * a + under.blue * (1 - a),
        )
    }

    /** 感知亮度（Rec.601，0–255）。 */
    private fun luminance(color: Color): Int {
        val argb = color.toArgb()
        val r = (argb ushr 16) and 255
        val g = (argb ushr 8) and 255
        val b = argb and 255
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
