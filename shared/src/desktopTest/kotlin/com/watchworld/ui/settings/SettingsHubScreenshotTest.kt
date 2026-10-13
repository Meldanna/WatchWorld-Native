package com.watchworld.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 设置总入口（[SettingsHub]）「功能行框内底色」的**离屏截图验收**。
 *
 * ## 为什么有它（2026-10-13）
 *
 * 用户截图实证：设置弹窗顶栏的图标徽章是淡主题色，而下面每行的框内却是**灰白**。
 * 根因是 `SettingsCard` 的 `Surface` 没传 `color` —— M3 于是取
 * `MaterialTheme.colorScheme.surface`（本主题体系里是 `#F8FAFC` 的**中性灰**），
 * 完全绕开了主题模块1。已改为 `WwSurfaces.accentSoft`（与顶栏徽章同一个取值）。
 *
 * ⚠️ 本用例**不写死具体色值**，而是断言「行框底色 == `WwSurfaces.accentSoft`」
 * 且「!= 中性灰 surface」——换任何主题都成立，且若有人再把 `color` 删掉就会红。
 * 截图落在 `shared/build/t28-screenshots/`，供肉眼比对。
 */
@OptIn(ExperimentalComposeUiApi::class)
class SettingsHubScreenshotTest {

    @Test
    fun settingsRowUsesThemeModule1NotGreySurface() {
        val w = 560
        val h = 460
        val bitmap = render(w, h)

        val file = File("build/t28-screenshots/settings-hub-light.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)

        val expected = captureToken(UiMode.LIGHT) { WwSurfaces.accentSoft }
        val greySurface = captureToken(UiMode.LIGHT) { MaterialTheme.colorScheme.surface }

        assertTrue(
            !near(expected, greySurface, tol = 6),
            "本用例前提：模块1 底色必须与中性灰 surface 不同（否则测不出回归）" +
                "：accentSoft=${expected.hex()} vs surface=${greySurface.hex()}",
        )

        val pixels = bitmap.toPixelMap()
        var themePixels = 0
        var greyPixels = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (near(pixels[x, y], expected, tol = 6)) themePixels++
                if (near(pixels[x, y], greySurface, tol = 4)) greyPixels++
            }
        }

        assertTrue(
            themePixels > 3_000,
            "行框内底色应为主题模块1（accentSoft）：匹配像素=$themePixels（过少 = 又变回灰白）",
        )
        assertTrue(
            greyPixels < themePixels / 4,
            "中性灰 surface 不应大面积出现：灰像素=$greyPixels vs 主题像素=$themePixels",
        )
    }

    private fun render(width: Int, height: Int): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                // 弹窗底板（FeatureDialog 的外层 Surface 取 dialogBase）。
                Surface(color = WwSurfaces.dialogBase, modifier = Modifier.fillMaxSize()) {
                    SettingsHub(state = SettingsUiState(), onOpen = {})
                }
            }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    /** 从组合里取出某个主题色，好在渲染结果里做逐像素比对。 */
    private fun captureToken(uiMode: UiMode, token: @Composable () -> Color): Color {
        var captured: Color? = null
        val scene = ImageComposeScene(width = 8, height = 8, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = uiMode) { captured = token() }
        }
        scene.render()
        scene.close()
        return captured ?: error("主题色未取到值")
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
