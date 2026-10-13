package com.watchworld.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider as M3Slider
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `WwRangeSlider` 的**离屏截图 + 刻度点回归**。
 *
 * ### 守的是什么
 * 参考版的 `<input type="range">` 只有「一条轨道 + 一个拇指」；M3 `Slider` 在 `steps != 0` 时
 * 会在轨道上**画出一排刻度圆点**（用户截图指出的「滑杆里面不要看见圆点」就是这么来的）。
 * 本用例沿轨道中线数**颜色跳变次数**：只有轨道 + 拇指时最多几次；有刻度点时会密集跳变。
 *
 * 判据用跳变次数而不是色值 —— 换主题、调比例都不会误报，但把滑杆换回带刻度的 M3 `Slider`
 * 就会立刻失败。
 */
@OptIn(ExperimentalComposeUiApi::class)
class WwRangeSliderScreenshotTest {

    @Test
    fun sliderDrawsNoTickDotsAlongTheTrack() {
        val width = 700
        val height = 120
        val bitmap = renderBitmap(width, height) {
            WwRangeSlider(value = 0.65f, range = 0f..1.5f, step = 0.05f, onValueChange = {})
        }
        val ours = trackTransitions(bitmap.toPixelMap(), width, height)

        // 对照组：M3 `Slider` 带 29 个刻度（= 0–1.5 按 0.05 分档）——它在轨道上画一排圆点，
        // 正是要避免的那个观感。同一套渲染 + 同一个判据，跳变次数必须显著高于我们。
        val m3 = renderBitmap(width, height) {
            M3Slider(value = 0.65f, onValueChange = {}, valueRange = 0f..1.5f, steps = 29)
        }
        val m3Transitions = trackTransitions(m3.toPixelMap(), width, height)

        assertTrue(
            ours <= 16,
            "轨道中线只应有「淡色段 / 主色段 / 拇指」这几处过渡，实际 $ours 次 —— " +
                "跳变密集说明轨道上又画了刻度圆点。",
        )
        assertTrue(
            ours * 2 < m3Transitions,
            "判据本身要有区分力：带刻度的 M3 Slider 应明显更密集（我们 $ours vs M3 $m3Transitions）。",
        )

        val file = File("build/t28-screenshots/range-slider-light.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T28] 滑杆样张：${file.absolutePath}（跳变：我们 $ours 次 / M3 带刻度 $m3Transitions 次）")
    }

    /** 沿轨道中线数「与相邻像素不同」的次数：= 轨道 / 已选段 / 拇指 / 每个刻度点的边缘。 */
    private fun trackTransitions(
        pixels: androidx.compose.ui.graphics.PixelMap,
        width: Int,
        height: Int,
    ): Int {
        // 轨道所在行 = 颜色种类最多的那一行。
        var trackY = 0
        var best = -1
        for (y in 0 until height) {
            val distinct = (0 until width).map { pixels[it, y] }.distinct().size
            if (distinct > best) { best = distinct; trackY = y }
        }
        var transitions = 0
        for (x in 1 until width) if (pixels[x, trackY] != pixels[x - 1, trackY]) transitions++
        return transitions
    }

    private fun renderBitmap(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) =
        ImageComposeScene(width, height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.padding(24.dp)) {
                        Box(Modifier.width(600.dp)) { content() }
                    }
                }
            }
        }.let { scene -> val b = scene.render().toComposeImageBitmap(); scene.close(); b }
}
