package com.watchworld.ui.sidebar

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
import com.watchworld.model.ChatGroup
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.WwTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T25 侧边栏配色的**离屏截图验收**（同 `TimelinePanelScreenshotTest` 的路数：无头、
 * 可进 CI）。侧边栏是左贴边的普通面板而非 `Dialog`，所以可以整块渲进
 * `ImageComposeScene` —— 这点比时间线那几个弹窗省事。
 *
 * ### 守的是用户抱怨的那件事
 * 「底板上面的框不要是灰白」。本主题体系里 `MaterialTheme.colorScheme.surfaceVariant`
 * 落到中性灰 `--surface-3`（亮色 `#E2E8F0`），用它铺卡片就是一块块灰白框。
 * 所以用例分两路：
 * ① **正向**：面板上应成片出现带主色倾向的浅底（`WwSurfaces.*`）；
 * ② **反向**：纯中性 `surfaceVariant` 的像素占比必须极低 —— 谁把卡片底改回去就立刻爆。
 */
@OptIn(ExperimentalComposeUiApi::class)
class SidebarPanelScreenshotTest {

    @Test
    fun sidebarSurfacesAreThemedAndNeutralGrayStaysRare() {
        val width = 320
        val height = 900
        val bitmap = renderBitmap(width, height, UiMode.LIGHT) { SidebarContent() }

        val pixels = bitmap.toPixelMap()
        val neutral = captureToken(UiMode.LIGHT) {
            MaterialTheme.colorScheme.surfaceVariant
        }.toArgb()

        var themed = 0
        var neutralCount = 0
        val total = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[x, y].toArgb()
                if (argb == neutral) neutralCount++
                if (isTintedLightSurface(pixels[x, y])) themed++
            }
        }

        assertTrue(
            themed > 20_000,
            "侧边栏的搜索框 / 激活会话行 / 底部导航网格应成片出现带主色倾向的浅底（当前 $themed 像素）。" +
                "接近 0 就说明卡片底退回中性色了。",
        )
        assertTrue(
            neutralCount * 50 < total,
            "纯中性 surfaceVariant 的像素不应超过面板的 2%（当前 $neutralCount / $total）—— " +
                "出现了就说明又拿中性灰当卡片底了。",
        )

        val file = File("build/t25-screenshots/sidebar-light.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T25] 侧边栏截图：${file.absolutePath}")
    }

    /**
     * 侧边栏顶栏的 ☀/☾ 走的是 `onToggleUiMode` → `SettingsViewModel.setUiMode`
     * → `uiModeRaw` → `resolvedUiMode` → `WwTheme(uiMode=…)`。
     *
     * 这条链路可以在无头环境里验到「暗色下侧边栏确实变暗」这一半；剩下「点按钮能不能
     * 触发回调」是纯 Compose 点击接线，只有跑起来才算数。
     */
    @Test
    fun sidebarRendersDarkUnderDarkUiMode() {
        val width = 320
        val height = 900
        val bitmap = renderBitmap(width, height, UiMode.DARK) { SidebarContent() }
        val pixels = bitmap.toPixelMap()

        var dark = 0
        val total = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (luminance(pixels[x, y]) < 100) dark++
            }
        }
        assertTrue(
            dark * 2 > total,
            "暗色模式下侧边栏应整体压暗（当前 $dark / $total 像素亮度 < 100）。" +
                "偏亮就说明 uiMode 没传到 WwTheme。",
        )

        val file = File("build/t25-screenshots/sidebar-dark.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T25] 侧边栏暗色截图：${file.absolutePath}")
    }

    /** 面板本体：两个用例共用，避免两处各拼一份参数。 */
    @Composable
    private fun SidebarContent() {
        SidebarSheet(
            sessions = screenshotSessions(),
            groups = screenshotGroups(),
            activeSessionId = "s-2",
            onSelect = {},
            onNewSession = {},
            onRename = { _, _ -> },
            onClear = {},
            onDelete = {},
            onMove = { _, _ -> },
            onTogglePinned = { _, _ -> },
            onCreateGroup = { _, _ -> },
            activeMcpCount = 3,
            connectedKnowledgeCount = 12,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** 主题化浅底：亮、且 G 明显高于 R 与 B（emerald 主色的方向）。中性灰三通道近似相等，必不满足。 */
    private fun isTintedLightSurface(color: Color): Boolean =
        isTinted(color) && run {
            val argb = color.toArgb()
            val r = (argb ushr 16) and 255
            val g = (argb ushr 8) and 255
            val b = argb and 255
            (argb ushr 24) and 255 >= 250 && r >= 190 && g >= 190 && b >= 190
        }

    /** 通道判据本体：G 至少比 R、B 各高出 3。中性灰三通道近似相等，必不满足。 */
    private fun isTinted(color: Color): Boolean {
        val argb = color.toArgb()
        val r = (argb ushr 16) and 255
        val g = (argb ushr 8) and 255
        val b = argb and 255
        return (g - r) >= 3 && (g - b) >= 3
    }

    /** 感知亮度（Rec.601）。用来判断整块面板有没有真的压暗。 */
    private fun luminance(color: Color): Int {
        val argb = color.toArgb()
        val r = (argb ushr 16) and 255
        val g = (argb ushr 8) and 255
        val b = argb and 255
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun renderBitmap(
        width: Int,
        height: Int,
        uiMode: UiMode,
        content: @Composable () -> Unit,
    ): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = uiMode) {
                Surface(color = Color(0xFF334155), modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    /** 把某个主题色从组合里取出来，好在渲染结果里做逐像素比对。 */
    private fun captureToken(uiMode: UiMode, token: @Composable () -> Color): Color {
        var captured: Color? = null
        val scene = ImageComposeScene(width = 8, height = 8, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = uiMode) { captured = token() }
        }
        scene.render()
        scene.close()
        return captured ?: error("主题色未取到值")
    }
}

private fun screenshotGroups(): List<ChatGroup> = listOf(
    ChatGroup(id = "g-1", name = "主线剧情", color = "#10B981", order = 0),
    ChatGroup(id = "g-2", name = "支线探索", color = "#8B5CF6", order = 1),
)

/**
 * 截图用会话样本：覆盖「激活态 / 普通态 / 置顶态」三条渲染路径，
 * 并让第二个分组有内容，好看出分组之间的 12dp 间距。
 */
private fun screenshotSessions(): List<ChatSession> = listOf(
    ChatSession(
        id = "s-1",
        title = "要塞攻防战推演",
        groupId = "g-1",
        agentId = "agent-1",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_300_000L,
        pinned = true,
        messages = listOf(
            ChatMessage(id = "m1", role = Role.USER, content = "五年后主角成了要塞指挥官", floorNumber = 1),
            ChatMessage(id = "m2", role = Role.ASSISTANT, content = "号角声从城墙外传来……", floorNumber = 2),
        ),
    ),
    ChatSession(
        id = "s-2",
        title = "学园线·结识好友",
        groupId = "g-1",
        agentId = "agent-2",
        createdAt = 1_700_000_100_000L,
        updatedAt = 1_700_000_200_000L,
        messages = listOf(
            ChatMessage(id = "m3", role = Role.USER, content = "转学第一天", floorNumber = 1),
        ),
    ),
    ChatSession(
        id = "s-3",
        title = "未命名对话",
        groupId = "g-2",
        agentId = "agent-1",
        createdAt = 1_700_000_200_000L,
        updatedAt = 1_700_000_100_000L,
        messages = emptyList(),
    ),
)
