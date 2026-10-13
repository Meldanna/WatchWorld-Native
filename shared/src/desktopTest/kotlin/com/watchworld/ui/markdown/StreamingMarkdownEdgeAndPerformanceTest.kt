package com.watchworld.ui.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.mikepenz.markdown.model.StreamingMarkdownState
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwTheme
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

/** T13 边界渲染与长回复增量性能冒烟。 */
@OptIn(ExperimentalComposeUiApi::class)
class StreamingMarkdownEdgeAndPerformanceTest {

    @Test
    fun incompleteFenceAndInlineMarkerRemainVisible() {
        val content = """
            未闭合粗体：**仍应可见

            ```kotlin
            val answer = 42
        """.trimIndent()

        val image = render(content)
        val pixels = image.toPixelMap()
        var darkPixels = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = pixels[x, y].toArgb()
                val lum = ((argb ushr 16 and 255) + (argb ushr 8 and 255) + (argb and 255)) / 3
                if (lum < 140) darkPixels++
            }
        }

        assertTrue(darkPixels > 100, "未闭合 Markdown 仍应渲染出可见正文，暗像素=$darkPixels")
    }

    @Test
    fun longReplyAppendAndRenderStaysWithinSmokeBudget() {
        val width = 720
        val height = 900
        lateinit var state: StreamingMarkdownState
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    state = rememberStreamingMarkdownState()
                    com.mikepenz.markdown.m3.Markdown(
                        streamingMarkdownState = state,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        try {
            scene.render()
            val chunks = buildList {
                repeat(360) { index ->
                    add("## 第 $index 段\n\n这是增量正文 **bold**、`code` 与链接 https://example.com/$index 。\n\n")
                }
            }

            val elapsed = measureTimeMillis {
                runBlocking {
                    chunks.forEach { chunk -> state.append(chunk) }
                }
                scene.render()
            }

            val snapshot = state.snapshot.value
            assertTrue(snapshot.stableAst.isNotEmpty(), "长回复应产生稳定 AST 块")
            assertTrue(state.content.length > 20_000, "压测正文应足够长，实际=${state.content.length}")
            assertTrue(elapsed < 10_000, "360 段增量解析并渲染耗时过长：${elapsed}ms")
        } finally {
            scene.close()
        }
    }

    private fun render(content: String) = ImageComposeScene(width = 520, height = 420, density = Density(1f)) {
        WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                StreamingMarkdown(content = content, streaming = false)
            }
        }
    }.let { scene ->
        try {
            scene.render()
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }
}
