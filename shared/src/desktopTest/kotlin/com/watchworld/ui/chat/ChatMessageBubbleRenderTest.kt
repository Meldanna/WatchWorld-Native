package com.watchworld.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.watchworld.model.ChatMessage
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T14 离屏渲染验收：正式模型 [ChatMessage] 能真的渲染出来，且**按角色取正文**。
 *
 * 这里守的是存档契约里最容易静默错的一处：正文存放位置**按角色不对称**
 * （legacy fixture 实测）——
 *  - assistant：正文在 `versions[currentVersionIndex]`，顶层 `content` 只是兜底；
 *  - user：正文在顶层 `content`，`versions` 是**空数组**。
 *
 * 取错字段不会报错，只会渲染成空气泡，所以这里数**像素**而不是断言 getter 返回值。
 */
@OptIn(ExperimentalComposeUiApi::class)
class ChatMessageBubbleRenderTest {

    private companion object {
        const val WIDTH = 420
        const val HEIGHT = 240

        /** 正文明显暗于浅色底（与 `StreamingMarkdownRenderTest` 同一判据）。 */
        const val INK_THRESHOLD = 140

        val USER = ChatMessage(id = "u1", role = Role.USER, content = "用户这一句必须看得见")

        /**
         * legacy 存档的 assistant 形状：顶层 `content` 为空，正文只在 `versions[0]`。
         * 若实现只读顶层，这条会渲染成空气泡。
         */
        val ASSISTANT_FROM_VERSION_ZERO = ChatMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            versions = listOf(MessageVersion(content = "助手这一句必须看得见")),
            currentVersionIndex = 0,
        )

        /**
         * **判别力最强的一条**：`versions[0]` 是空、正文在 `versions[1]`，顶层 `content` 也为空。
         * 只有按 `currentVersionIndex` 取值才会渲染出字；只读 `versions.first()` 或只读顶层都会空白。
         */
        val ASSISTANT_ON_SECOND_VERSION = ChatMessage(
            id = "a2",
            role = Role.ASSISTANT,
            content = "",
            versions = listOf(MessageVersion(content = ""), MessageVersion(content = "滚动重答后的第二版正文")),
            currentVersionIndex = 1,
        )

        /** 全空的助手消息：作为「零文字」基线。 */
        val EMPTY_ASSISTANT = ChatMessage(id = "a3", role = Role.ASSISTANT)
    }

    @Test
    fun userAndAssistantBubblesBothRenderVisibleText() {
        val ink = inkPixels(
            renderScene {
                Column {
                    MessageBubble(USER, streaming = false)
                    MessageBubble(ASSISTANT_FROM_VERSION_ZERO, streaming = false)
                }
            },
        )
        assertTrue(ink > 100, "两条气泡都应渲染出可见正文，文字像素=$ink")
    }

    @Test
    fun assistantTextIsReadFromTheCurrentVersionIndex() {
        val selected = inkPixels(renderScene { MessageBubble(ASSISTANT_ON_SECOND_VERSION, streaming = false) })
        val empty = inkPixels(renderScene { MessageBubble(EMPTY_ASSISTANT, streaming = false) })

        assertTrue(
            selected > empty + 100,
            "正文必须取自 versions[currentVersionIndex]（第二版=$selected，全空基线=$empty）",
        )
    }

    @Test
    fun assistantFallsBackToTopLevelContentWhenVersionsAreEmpty() {
        // TS `api.ts:328`：`(m.versions[i] || m.versions[0])?.content || m.content` —— 兜底是规格的一部分。
        val topLevelOnly = ChatMessage(id = "a4", role = Role.ASSISTANT, content = "只有顶层正文也要显示")
        val withFallback = inkPixels(renderScene { MessageBubble(topLevelOnly, streaming = false) })
        val empty = inkPixels(renderScene { MessageBubble(EMPTY_ASSISTANT, streaming = false) })

        assertTrue(
            withFallback > empty + 100,
            "versions 为空时助手正文应回落到顶层 content（实际=$withFallback，基线=$empty）",
        )
    }

    private fun renderScene(content: @Composable () -> Unit): ImageBitmap {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    private fun inkPixels(image: ImageBitmap): Int {
        val px = image.toPixelMap()
        var count = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val argb = px[x, y].toArgb()
                val lum = ((argb ushr 16 and 255) + (argb ushr 8 and 255) + (argb and 255)) / 3
                if (lum < INK_THRESHOLD) count++
            }
        }
        return count
    }
}
