package com.watchworld.ui.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T13 离屏渲染冒烟：换用 mikepenz 库后，[StreamingMarkdown] 在真实主题下确实渲染出内容。
 *
 * 只做「有字可读」的粗校验（正文像素远暗于背景），不锁具体排版——排版属库的职责，
 * 我们这层要守的是：依赖接进来了、能组合、流式状态能把内容显示出来。
 */
@OptIn(ExperimentalComposeUiApi::class)
class StreamingMarkdownRenderTest {

    private val gfm = buildString {
        appendLine("# 标题")
        appendLine()
        appendLine("- 第一项")
        appendLine("- 第二项")
        appendLine()
        appendLine("```kotlin")
        appendLine("val x = 1")
        appendLine("```")
    }

    @Test
    fun rendersGfmContentWithVisibleText() {
        val w = 420
        val h = 360
        val image: ImageBitmap = render(w, h)
        val px = image.toPixelMap()

        // 背景 = surface1Light（浅色）；正文像素应明显更暗。
        var darkPixels = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = px[x, y].toArgb()
                val lum = ((argb ushr 16 and 255) + (argb ushr 8 and 255) + (argb and 255)) / 3
                if (lum < 140) darkPixels++
            }
        }
        assertTrue(darkPixels > 100, "GFM 内容应渲染出可见正文,暗像素=$darkPixels(过少 = 空白/未渲染)")
    }

    private fun render(width: Int, height: Int): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    StreamingMarkdown(content = gfm, streaming = false)
                }
            }
        }
        return try {
            scene.render() // 首帧：提交组合与 LaunchedEffect
            scene.render().toComposeImageBitmap() // 次帧：取出 append 后的内容
        } finally {
            scene.close()
        }
    }
}
