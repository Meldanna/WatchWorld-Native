package com.watchworld.ui.theme

import androidx.compose.ui.graphics.Color
import com.watchworld.model.UiMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 角色层「解引用」验收:类名串 → (主题变体 × uiMode) → Color 的两维解析。
 * 这是任务分析里指出的第 B 号难点:角色值不能只存一遍。
 */
class ThemeRolesTest {

    private val vars = resolveThemeVars(resolveTheme("emerald-day"))

    private fun withVars(block: () -> Unit) {
        roleCurrentVars = vars
        try {
            block()
        } finally {
            roleCurrentVars = null
        }
    }

    @Test
    fun primaryTextResolvesBothModesDifferently() {
        withVars {
            // text-m1-500 dark:text-m1-400:两维必须不同档
            val map = parseRoleClass(ThemeRoles.STANDARD.primaryText)
            val lightColor: Color = colorOrThrow(map[UiMode.LIGHT], "primaryText light")
            val darkColor: Color = colorOrThrow(map[UiMode.DARK], "primaryText dark")
            assertEquals(CssColors.parseStrict(vars.m1[5], "m1-500"), lightColor)
            assertEquals(CssColors.parseStrict(vars.m1[4], "m1-400"), darkColor)
        }
    }

    @Test
    fun badgeBgFoldsAlphaIntoColor() {
        withVars {
            // bg-m1-500/15 → 500 档色 + 15% alpha
            val map = parseRoleClass(ThemeRoles.STANDARD.badgeBg)
            val c = colorOrThrow(map[UiMode.LIGHT], "badgeBg light")
            val base = CssColors.parseStrict(vars.m1[5], "m1-500")
            assertEquals(0.15f, c.alpha, 0.001f)
            assertEquals(base.red, c.red, 0.0001f)
            assertEquals(base.green, c.green, 0.0001f)
            assertEquals(base.blue, c.blue, 0.0001f)
        }
    }

    @Test
    fun hoverPrefixDoesNotChangeColor() {
        withVars {
            // hover:bg-m1-500 与 bg-m1-500 同色(hover 只改交互态)
            val hover = parseRoleClass(ThemeRoles.STANDARD.primaryHover)
            val plain = parseRoleClass("bg-m1-500")
            assertEquals(colorOrThrow(plain[UiMode.LIGHT], "plain"), colorOrThrow(hover[UiMode.LIGHT], "hover"))
        }
    }

    @Test
    fun midnightVariantPicksDeeperShades() {
        val midnightVars = resolveThemeVars(resolveTheme("slate-day"))
        roleCurrentVars = midnightVars
        try {
            // STANDARD.primaryBg=600 档;MIDNIGHT=700 档 —— 两套角色串解出不同 Color
            val standardColor = colorOrThrow(parseRoleClass(ThemeRoles.STANDARD.primaryBg)[UiMode.LIGHT], "standard")
            val midnightColor = colorOrThrow(parseRoleClass(ThemeRoles.MIDNIGHT.primaryBg)[UiMode.LIGHT], "midnight")
            val s600 = CssColors.parseStrict(midnightVars.m1[6], "slate-600")
            val s700 = CssColors.parseStrict(midnightVars.m1[7], "slate-700")
            assertEquals(s600, standardColor)
            assertEquals(s700, midnightColor)
        } finally {
            roleCurrentVars = null
        }
    }

    @Test
    fun nonColorFragmentsAreSkipped() {
        withVars {
            // shadow-sm / 裸 border 无颜色 → 跳过;第一可解析片段 bg-m1-200 决定主色。
            // ⚠️ 2026-10-13:MIDNIGHT.userBubble 亮色档已由 m1-800 改为 m1-200(与 STANDARD 统一),
            //    本用例的目的只是「非颜色片段被跳过、第一个颜色片段生效」,不锁具体档位设计。
            val user = parseRoleClass(ThemeRoles.MIDNIGHT.userBubble)
            assertEquals(shadeColor(1, 2), colorOrThrow(user[UiMode.LIGHT], "userBubble light"))
        }
    }

    @Test
    fun darkOnlyAccentBadgeBranches() {
        withVars {
            // accentBadge:light 首片段 bg-m1-100;dark 首片段 bg-m1-950/60(带 alpha)
            val map = parseRoleClass(ThemeRoles.STANDARD.accentBadge)
            assertEquals(shadeColor(1, 1), colorOrThrow(map[UiMode.LIGHT], "badge light"))
            val darkC = colorOrThrow(map[UiMode.DARK], "badge dark")
            val base950 = shadeColor(1, 10)
            assertEquals(0.6f, darkC.alpha, 0.001f, "dark:bg-m1-950/60 → 60% alpha")
            assertEquals(base950.red, darkC.red, 0.0001f)
        }
    }

    @Test
    fun noVarsContextReturnsNull() {
        roleCurrentVars = null
        val map = parseRoleClass("bg-m1-600")
        assertNull(map[UiMode.LIGHT], "无解引用上下文时应返回 null(而不是猜色)")
    }

    @Test
    fun unknownShadeReturnsNull() {
        withVars {
            // Tailwind 没有 m1-450
            assertNull(parseRoleClass("bg-m1-450")[UiMode.LIGHT])
        }
    }

    @Test
    fun fontSizeTokensUseSpDirectly() {
        // 用户 2026-10-13 定案：不做倍率换算 —— 设置值即正文字号，标题 +2sp、提示 −2sp；
        // 行高也用 sp 直加（紧凑 +3 / 正文 +5 / 标题 +7）。
        val t = WwTypographyTokens(15.0)
        assertEquals(13.0, t.xsFontSize, 1e-9) // 15 − 2
        assertEquals(15.0, t.smFontSize, 1e-9) // 设置值本身
        assertEquals(17.0, t.baseFontSizeScaled, 1e-9) // 15 + 2
        assertEquals(16.0, t.xsLineHeight, 1e-9) // 13 + 3
        assertEquals(20.0, t.smLineHeight, 1e-9) // 15 + 5
        assertEquals(24.0, t.baseLineHeight, 1e-9) // 17 + 7
        // 自定义基准：三档整体平移 3sp。
        val t2 = WwTypographyTokens(18.0)
        assertEquals(16.0, t2.xsFontSize, 1e-9)
        assertEquals(18.0, t2.smFontSize, 1e-9)
        assertEquals(20.0, t2.baseFontSizeScaled, 1e-9)
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun colorOrThrow(c: Color?, what: String): Color = c ?: error("$what 未解析出颜色")

    private fun shadeColor(module: Int, shadeIdx: Int): Color {
        val css = if (module == 1) vars.m1[shadeIdx] else vars.m2[shadeIdx]
        return CssColors.parseStrict(css, "test helper")
    }
}
