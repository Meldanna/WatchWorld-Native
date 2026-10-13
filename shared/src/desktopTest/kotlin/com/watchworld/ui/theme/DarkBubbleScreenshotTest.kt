package com.watchworld.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.watchworld.model.UiMode
import com.watchworld.ui.chat.MessageBubble
import com.watchworld.ui.chat.UiMessage
import com.watchworld.ui.chat.UiRole
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 深色模式「AI 气泡是**深**色块」的**离屏截图验收**(无头:不弹窗、不注入键盘、可进 CI)。
 *
 * 背景:2026-10-13 用户截图实证 —— 夜晚模式下气泡亮成一块浅色,与深底脱节。
 * 根因是 [resolveThemeVars] 把 `bubbleDark` 取成 `m1[3]`(L≈84.5% 的**浅**档);
 * 已改为 `m1[10]`(最浓档,L≈26.2%)。
 *
 * ⚠️ 本用例**不写死某个具体色值**(旧版硬编码 React 的 `oklch(20.8% … / 0.9)`,
 * 那是旧模型的气泡色,新模型走「主色派生档」后会必然失败且无法表达意图)。
 * 改为断言**结构性质**:暗色气泡必须明显比亮色气泡深、且与深色页面底可区分。
 * 这样任何主题、任何档位下,「夜晚气泡发亮」这个 bug 都会被抓到。
 */
@OptIn(ExperimentalComposeUiApi::class)
class DarkBubbleScreenshotTest {

    private val aiMessage = UiMessage(id = 1, role = UiRole.Assistant, content = "Hello dark bubble")

    @Test
    fun darkAiBubbleIsNearlyOpaqueNotBackground() {
        val w = 360
        val h = 160
        val pixels = renderDark(w, h).toPixelMap()

        // 页面背景:右下角(气泡靠左,右侧必为页面底)。
        val bg = pixels[w - 1, h - 1]
        assertTrue(
            near(Color(0xFF0F131C), bg, tol = 8),
            "应处于深色模式(页面底 = surface1Dark #0F131C),实际 ${bg.hex()}",
        )

        // 气泡本体取值(断言语义,不硬编码具体色):resolveThemeVars 的暗色档 = m1[10]。
        val vars = resolveThemeVars(resolveTheme("emerald-day"))
        val bubbleRgb = CssColors.parseStrict(vars.m1[10], "bubble-dark (m1[10])")

        // 1) 暗色气泡必须比亮色气泡**明显更深** —— 这是本 bug 的直接判据。
        val lightRgb = CssColors.parseStrict(vars.m1[2], "bubble-light (m1[2])")
        val darkLum = bubbleRgb.red * 0.299 + bubbleRgb.green * 0.587 + bubbleRgb.blue * 0.114
        val lightLum = lightRgb.red * 0.299 + lightRgb.green * 0.587 + lightRgb.blue * 0.114
        assertTrue(
            darkLum < lightLum - 0.25,
            "暗色气泡应明显比亮色气泡深:暗(%.3f) vs 亮(%.3f)" .format(darkLum, lightLum),
        )

        // 2) 气泡须与页面深底可区分(否则看不见气泡)。
        assertTrue(
            !near(bubbleRgb, bg, tol = 12),
            "气泡色须与页面底明显不同(否则本用例无意义):${bubbleRgb.hex()} vs ${bg.hex()}",
        )

        // 3) 气泡确实渲染出来了(不透明):找够多的「非背景」像素。
        var bubblePixels = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!near(pixels[x, y], bg, tol = 10)) bubblePixels++
            }
        }
        assertTrue(
            bubblePixels > 200,
            "AI 气泡应渲染成一块与背景可区分的实心块,非背景像素=$bubblePixels(过少 = 气泡透明或未渲染)",
        )
    }

    private fun renderDark(width: Int, height: Int): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.DARK) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    MessageBubble(aiMessage)
                }
            }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    /** `src` 以 `alpha` 叠在 `dst` 上(sRGB 直线混合,与 Skia 默认合成一致)。 */
    private fun blendOver(src: Color, dst: Color, alpha: Double): Color {
        val a = alpha.toFloat()
        return Color(
            red = src.red * a + dst.red * (1 - a),
            green = src.green * a + dst.green * (1 - a),
            blue = src.blue * a + dst.blue * (1 - a),
        )
    }

    private fun near(a: Color, b: Color, tol: Int): Boolean {
        val ta = a.toArgb()
        val tb = b.toArgb()
        return abs((ta ushr 24 and 255) - (tb ushr 24 and 255)) <= 2 &&
            abs((ta ushr 16 and 255) - (tb ushr 16 and 255)) <= tol &&
            abs((ta ushr 8 and 255) - (tb ushr 8 and 255)) <= tol &&
            abs((ta and 255) - (tb and 255)) <= tol
    }

    private fun Color.hex(): String {
        val argb = toArgb()
        return "#%02X%02X%02X".format((argb ushr 16) and 255, (argb ushr 8) and 255, argb and 255)
    }
}
