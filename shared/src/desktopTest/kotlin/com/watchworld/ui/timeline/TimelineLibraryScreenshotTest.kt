package com.watchworld.ui.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.watchworld.domain.createDefaultMainTimeline
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.model.TimelineBranch
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 时间线库的**离屏截图验收**（同 [TimelinePanelScreenshotTest] 的路数：无头、可进 CI）。
 *
 * ### 为什么要拆 Content
 * `Dialog` 在 `ImageComposeScene` 里不渲染（它另开平台窗口），所以子弹窗整窗截不了图。
 * [TimelineLibraryBody] 就是为这条拆出来的正文。
 *
 * ### 守的是什么
 * 窗口卡片走 `WwSurfaces.card`：底色必须**带主色倾向**（emerald 下 G 明显高于 R、B）。
 * 一旦有人改回 `MaterialTheme.colorScheme.surfaceVariant`（本体系里是纯中性灰
 * `--surface-3`），这里立刻失败 —— 那正是之前「灰白块」的成因。
 */
@OptIn(ExperimentalComposeUiApi::class)
class TimelineLibraryScreenshotTest {

    @Test
    fun sessionCardsAreThemedAndScreenshotDumpsForReview() {
        val width = 720
        val height = 560
        val bitmap = renderBitmap(width, height) {
            Surface(color = WwTheme.colors.surfaceElevated, modifier = Modifier.fillMaxSize()) {
                TimelineLibraryBody(
                    groups = timelineLibraryGroups(screenshotSessions()),
                    activeSessionId = "session-a",
                    onJump = { _, _ -> },
                )
            }
        }
        val pixels = bitmap.toPixelMap()

        // 跳过顶部工具条区，统计窗口卡片里的主题化浅底。
        var themed = 0
        for (y in 90 until height) {
            for (x in 20 until width - 20) {
                if (isTintedLightSurface(pixels[x, y])) themed++
            }
        }
        assertTrue(
            themed > 8_000,
            "时间线库的窗口卡片应成片出现带主色倾向的浅底（当前 $themed 像素）。" +
                "接近 0 就说明卡片底又退回中性 surfaceVariant 了。",
        )

        val file = File("build/t26-screenshots/timeline-library-light.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T26] 时间线库截图：${file.absolutePath}")
    }

    @Test
    fun darkThemeAlsoRenders() {
        val file = File("build/t26-screenshots/timeline-library-dark.png")
        file.parentFile?.mkdirs()
        val bitmap = renderBitmap(720, 560, UiMode.DARK) {
            Surface(color = WwTheme.colors.surfaceElevated, modifier = Modifier.fillMaxSize()) {
                TimelineLibraryBody(
                    groups = timelineLibraryGroups(screenshotSessions()),
                    activeSessionId = "session-a",
                    onJump = { _, _ -> },
                )
            }
        }
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T26] 时间线库深色截图：${file.absolutePath}")
    }

    /** 没有时间线的窗口不进列表、`subtitle` 的窗口计数也只算这些。 */
    @Test
    fun onlySessionsWithTimelinesAreListed() {
        val groups = timelineLibraryGroups(screenshotSessions() + emptyTimelineSession())
        assertEquals(2, groups.size, "只有建立了时间线的窗口才应出现在时间线库里")
        assertEquals(3, groups.sumOf { it.timelines.size })
    }

    private fun renderBitmap(
        width: Int,
        height: Int,
        uiMode: UiMode = UiMode.LIGHT,
        content: @Composable () -> Unit,
    ): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = uiMode) {
                Surface(color = Color(0xFF334155), modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(8.dp)) {
                        content()
                    }
                }
            }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    /** 主题化浅底：亮、不透明，且 G 明显高于 R 与 B（emerald 主色的方向）。 */
    private fun isTintedLightSurface(color: Color): Boolean {
        val argb = color.toArgb()
        val r = (argb ushr 16) and 255
        val g = (argb ushr 8) and 255
        val b = argb and 255
        val a = (argb ushr 24) and 255
        return a >= 250 && r >= 190 && g >= 190 && b >= 190 && (g - r) >= 3 && (g - b) >= 3
    }
}

/**
 * 截图样本：一个当前窗口（2 条分支，其中一条 AI 不可见、一条聚焦）+ 另一个后台窗口。
 * 覆盖「当前窗口高亮 / 折叠箭头 / 色标 / AI 不可见 / 聚焦勾」五条渲染路径。
 */
private fun screenshotSessions(): List<ChatSession> = listOf(
    ChatSession(
        id = "session-a",
        title = "截图用会话 A",
        createdAt = 1_700_000_000_000L,
        messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, content = "这是一个时间线：五年后", floorNumber = 1),
        ),
        timelineMemoryEnabled = true,
        activeTimelineId = "timeline-main",
        timelines = listOf(
            createDefaultMainTimeline(now = 1_700_000_000_000L),
            TimelineBranch(
                id = "timeline-fork",
                name = "要塞战役",
                tag = "A1",
                codeTag = "A1",
                descriptionTag = "五年后·要塞线",
                visible = false,
                color = "indigo",
                createdAt = 1_700_000_100_000L,
                updatedAt = 1_700_000_100_000L,
            ),
        ),
    ),
    ChatSession(
        id = "session-b",
        title = "截图用会话 B（后台窗口）",
        createdAt = 1_700_000_200_000L,
        timelineMemoryEnabled = false,
        timelines = listOf(
            TimelineBranch(
                id = "timeline-b1",
                name = "学园线",
                tag = "B1",
                codeTag = "B1",
                descriptionTag = "学园线·结识好友",
                visible = true,
                color = "rose",
                createdAt = 1_700_000_300_000L,
                updatedAt = 1_700_000_300_000L,
            ),
        ),
    ),
)

/** 没开过记忆整理、没有任何分支的窗口 —— 不该出现在库列表里。 */
private fun emptyTimelineSession(): ChatSession = ChatSession(
    id = "session-empty",
    title = "没有时间线的窗口",
    createdAt = 1_700_000_400_000L,
    timelines = emptyList(),
)
