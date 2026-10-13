package com.watchworld.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 主题基线快照对拍：**拦住对色板/派生规则的无意改动**。
 *
 * fixture（`fixtures/theme-baseline/snapshots.json`）由本侧生成器
 * [ThemeBaselineGenerator] 导出：20 套内置（白天 7 + 黑夜 7 + 多巴胺 6）
 * + 2 个自定义样本 × 24 个 CSS 变量。本侧 [resolveThemeVars] 的输出必须
 * 逐键逐值相等 —— oklch 字符串本身可比，不经过色转换，差一个小数位都算失败。
 *
 * ⚠️ **改了主题取值就必须显式重跑生成器**，并在 review 里逐条确认 diff；
 *    否则这里会红 —— 这正是它的用处（不是「和 React 一致」的证明，见 [ThemeBaselineGenerator]）。
 */
class ThemeSnapshotTest {

    private fun snapshotsFile(): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .map { File(it, "fixtures/theme-baseline/snapshots.json") }
            .firstOrNull { it.isFile }
            ?: error("找不到 fixtures/theme-baseline/snapshots.json(从 ${start.absolutePath} 向上找)")
    }

    @Test
    fun allSnapshotThemesMatchImplementation() {
        val result = ThemeSnapshotCompat.compareAll(snapshotsFile().readText())
        val failures = result.filterValues { it.isNotEmpty() }
        assertTrue(
            failures.isEmpty(),
            "快照对拍失败 ${failures.size} 套:\n" +
                failures.entries.joinToString("\n") { (id, diffs) -> "[$id]\n  " + diffs.joinToString("\n  ") },
        )
        // 22 套 = 20 内置（白天 7 + 黑夜 7 + 多巴胺 6）+ 2 自定义样本。
        // ⚠️ 2026-10-13 由 15 改 22：主题模型换成「白天/黑夜 × 饱和度分级」后，
        //    内置从 13（React 的 family 7 + custom 6）变成 20；fixture 已按新模型重生成
        //    （见 [ThemeBaselineGenerator]）。
        assertEquals(22, result.size, "快照应覆盖 22 套主题(20 内置 + 2 custom 样本)")
    }

    @Test
    fun midnightUsesSlateScaleAndCustomBubble() {
        val midnight = resolveThemeVars(resolveTheme("slate-day"))
        // slate-day 走白天的 LOW 表；slate 三张表的 L 与 C 全等（灰阶无饱和度可分），
        // 700 档 = 37.2% / 0.044。
        assertEquals("oklch(37.2% 0.044 257.287)", midnight.m1[7])
        // ⚠️ 2026-10-13：气泡不再是 React 时代的固定值（预设 `#ffffff`、
        //    暗色 `oklch(20.8% … / 0.9)`），改为**主色派生档**：亮 m1[2]、暗 m1[10]。
        //    见 [resolveThemeVars] 与 [DarkBubbleScreenshotTest]（夜晚气泡必须够深）。
        val slate = resolveThemeVars(resolveTheme("slate-day"))
        assertEquals(slate.m1[2], midnight.bubble, "亮色气泡 = m1[2]")
        assertEquals(slate.m1[10], midnight.bubbleDark, "暗色气泡 = m1[10]（最浓档）")
    }

    @Test
    fun customThemeBubbleFollowsTheme() {
        val cfg = customThemeToConfig("custom_x", "测试", "#34d399", "#818cf8")
        val vars = resolveThemeVars(cfg)
        // 2026-10-13：自定义与预设主题**统一**走主色派生档（原来自定义是 m1[0]/m1[10]）。
        assertEquals(vars.m1[2], vars.bubble, "气泡 = m1[2]")
        assertEquals(vars.m1[10], vars.bubbleDark, "暗气泡 = m1[10]")
    }

    @Test
    fun fallbackEmeraldWhenBuildScaleFails() {
        // 非法 m1 → 整条回退 emerald family 表（JS `?? FAMILY_SCALES.emerald`）
        val cfg = customThemeToConfig("custom_bad", "坏主题", "###", "#818cf8")
        val vars = resolveThemeVars(cfg)
        assertEquals(FAMILY_SCALES.getValue(ThemeFamily.EMERALD), vars.m1)
        // 非法 m2 → 回退 m1（JS `?? m1`）
        val bad2 = customThemeToConfig("custom_bad2", "坏二", "#34d399", "###")
        val vars2 = resolveThemeVars(bad2)
        assertEquals(vars2.m1, vars2.m2)
    }

    @Test
    fun resolveThemeFallsBackToEmeraldForUnknownId() {
        assertEquals("emerald-day", resolveTheme("custom_deleted").id)
        assertEquals("emerald-day", resolveTheme("totally-unknown").id)
        assertEquals("slate-day", resolveTheme("slate-day").id)
    }

    @Test
    fun allTwentyBuiltInsResolve() {
        val ids = listOf(
            "emerald-day", "blue-day", "violet-day", "rose-day", "amber-day", "cyan-day", "slate-day",
            "emerald-night", "blue-night", "violet-night", "rose-night", "amber-night", "cyan-night", "slate-night",
            "coral", "honey", "mint", "soda", "grape", "bubble",
        )
        assertEquals(20, builtInThemes().size)
        for (id in ids) {
            val cfg = resolveTheme(id)
            assertEquals(id, cfg.id)
            assertTrue(cfg.primaryHex.startsWith("#"), "$id 的 primaryHex 应是 hex")
        }
    }

    @Test
    fun colorSwatchesCountIs32() {
        assertEquals(32, COLOR_SWATCHES.size)
    }
}
