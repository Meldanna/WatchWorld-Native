package com.watchworld.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.watchworld.ui.chat.WindowFunctionSheetContent
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T24 面板配色的**离屏截图验收**（与 T12 的 `DarkBubbleScreenshotTest` 同路数：
 * 无头、不弹窗、可进 CI）。
 *
 * ### 守的是什么
 * 早先底板上的卡片一律用 `MaterialTheme.colorScheme.surfaceVariant`，落到本主题体系就是
 * 纯中性灰 `--surface-3`（亮色 `#E2E8F0`），铺出来是一块块「灰白框」，与主题色脱节。
 * 现在卡片走 [WwSurfaces]，底色是「表面色掺主色」的结果。
 *
 * ### 判据为什么用通道差
 * 主题化之后的浅底**一定带主色倾向**（emerald 下 G 明显高于 R、B），中性灰则三通道近似相等。
 * 这个判据不依赖具体色值 —— 换主题、调比例都不会误报，而一旦有人把卡片底改回
 * `surfaceVariant` 就会立刻失败。
 *
 * ⚠️ `Dialog` 在 `ImageComposeScene` 里**不渲染**（它另开平台窗口），所以管理器与编辑弹窗
 * 无法整窗截图；对它们的验收走第二个用例 —— 直接检查面板实际消费的那几个色。
 */
@OptIn(ExperimentalComposeUiApi::class)
class TimelinePanelScreenshotTest {

    @Test
    fun managerPanelIsThemedAndScreenshotDumpsForReview() {
        val width = 720
        val height = 900
        val bitmap = renderBitmap(width, height) {
            TimelineManagerContent(
                session = screenshotSession(),
                onDismiss = {},
                onToggleMemory = {},
                onSelectTimeline = {},
                onToggleVisibility = {},
                onSetAllVisibility = {},
                onCreateBranch = {},
                onEditTimeline = {},
                onDeleteTimeline = {},
                onApproveRequest = {},
                onRejectRequest = {},
                onOpenAiGuide = {},
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
        val pixels = bitmap.toPixelMap()

        // 中部滚动区的卡片必须成片出现主题化浅底。
        var themed = 0
        for (y in 260 until height - 60) {
            for (x in 40 until width - 40) {
                if (isTintedLightSurface(pixels[x, y])) themed++
            }
        }
        assertTrue(
            themed > 10_000,
            "时间线管理器的分支卡片应成片出现带主色倾向的浅底（当前 $themed 像素）。" +
                "接近 0 就说明卡片底又退回中性 surfaceVariant 了。",
        )

        val file = File("build/t24-screenshots/timeline-manager-light.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T24] 时间线管理器截图：${file.absolutePath}")
    }

    @Test
    fun windowFunctionSheetCardsAreThemedNotNeutralGray() {
        val width = 520
        val height = 320
        val pixels = renderBitmap(width, height) {
            WindowFunctionSheetContent(
                onDismiss = {},
                onOpenSummary = {},
                onOpenSearch = {},
                onOpenKnowledge = {},
                onOpenWindowWorld = {},
                onAnalyzeRole = {},
                onOpenWindowApi = {},
                onOpenSessionActions = {},
                onOpenWindowRegex = {},
            )
        }.toPixelMap()

        // 自上而下扫，跳过顶部遮罩区，落在贴底弹层的卡片上。
        var themed = 0
        for (y in height / 3 until height) {
            for (x in 0 until width) {
                if (isTintedLightSurface(pixels[x, y])) themed++
            }
        }
        assertTrue(
            themed > 8_000,
            "窗口功能弹层的卡片应成片出现带主色倾向的浅底（当前 $themed 像素）。" +
                "接近 0 就说明卡片底又退回中性 surfaceVariant 了。",
        )
    }

    @Test
    fun surfaceLadderIsThemedAndVisiblyLayered() {
        val seen = mutableMapOf<String, Color>()
        val scene = ImageComposeScene(width = 8, height = 8, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                seen["card"] = WwSurfaces.card
                seen["cardStrong"] = WwSurfaces.cardStrong
                seen["inset"] = WwSurfaces.inset
                seen["cardSelected"] = WwSurfaces.cardSelected
                seen["accentSoft"] = WwSurfaces.accentSoft
            }
        }
        scene.render()
        scene.close()

        seen.forEach { (name, color) ->
            assertTrue(
                isTintedLightSurface(color),
                "WwSurfaces.$name 应为带主色倾向的浅底，实际 ${color.hex()}",
            )
        }
        assertTrue(
            seen.getValue("card") != seen.getValue("cardStrong"),
            "「卡片」与「强卡片」必须可区分，否则叠起来看不出层级：$seen",
        )
        assertTrue(
            seen.getValue("cardStrong") != seen.getValue("inset"),
            "「强卡片」与「凹槽」必须可区分：$seen",
        )
    }

    /**
     * 描边是**中间调**（主色掺进中性描边），不能套用「浅底」判据 ——
     * 它只要带主色倾向且明显区别于中性 `outlineVariant` 即可。
     */
    @Test
    fun cardBorderIsTintedNotNeutral() {
        val captured = mutableMapOf<String, Color>()
        val scene = ImageComposeScene(width = 8, height = 8, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                captured["border"] = WwSurfaces.cardBorder
                captured["neutral"] = MaterialTheme.colorScheme.outlineVariant
            }
        }
        scene.render()
        scene.close()

        val border = captured.getValue("border")
        assertTrue(
            isTinted(border),
            "WwSurfaces.cardBorder 应带主色倾向，实际 ${border.hex()}",
        )
        assertTrue(
            border != captured.getValue("neutral"),
            "cardBorder 不能等于中性的 outlineVariant（那正是要替换掉的东西）",
        )
    }

    @Test
    fun surfaceLadderScreenshotDumpsForVisualReview() {
        val width = 720
        val height = 520
        val bitmap = renderBitmap(width, height) {
            Surface(color = WwSurfaces.card, modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = WwSurfaces.cardStrong,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Surface(
                        color = WwSurfaces.inset,
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                    ) {
                        Text(
                            text = "cardStrong → inset 分层样张",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
        val file = File("build/t24-screenshots/surfaces-light.png")
        file.parentFile?.mkdirs()
        ImageIO.write(bitmap.toAwtImage(), "png", file)
        println("[T24] 分层底色样张：${file.absolutePath}")
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

    private fun renderBitmap(width: Int, height: Int, content: @Composable () -> Unit): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            WwTheme(palette = "emerald-day", uiMode = UiMode.LIGHT) {
                Surface(color = Color(0xFF334155), modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        return try {
            scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    private fun Color.hex(): String {
        val argb = toArgb()
        return "#%02X%02X%02X".format((argb ushr 16) and 255, (argb ushr 8) and 255, argb and 255)
    }
}

/**
 * 截图用会话样本：一条通用主线 + 一条分叉子分支，
 * 覆盖「激活态 / 非激活态 / 父分支溯源 / 里程碑药丸」四条渲染路径。
 */
private fun screenshotSession(): ChatSession = ChatSession(
    id = "session-shot",
    title = "截图用会话",
    createdAt = 1_700_000_000_000L,
    messages = listOf(
        ChatMessage(id = "u1", role = Role.USER, content = "这是一个时间线：五年后", floorNumber = 1),
        ChatMessage(id = "a2", role = Role.ASSISTANT, content = "……", floorNumber = 2),
    ),
    timelineMemoryEnabled = false,
    activeTimelineId = "timeline-main",
    timelines = listOf(
        createDefaultMainTimeline(now = 1_700_000_000_000L),
        TimelineBranch(
            id = "timeline-fork",
            name = "要塞战役",
            tag = "A1",
            codeTag = "A1",
            descriptionTag = "五年后·要塞线",
            visible = true,
            color = "emerald",
            description = "离开团队 5 年后的平行分支，主角已成为要塞指挥官。",
            plotSummary = "用户: 五年后…… | 回复: 要塞的号角响起……",
            keyMilestones = listOf("建立时间线【要塞战役】"),
            parentId = "timeline-main",
            createdAt = 1_700_000_100_000L,
            updatedAt = 1_700_000_100_000L,
        ),
    ),
)
