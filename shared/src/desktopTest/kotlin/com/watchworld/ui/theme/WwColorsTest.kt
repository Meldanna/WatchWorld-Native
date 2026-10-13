package com.watchworld.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.watchworld.model.UiMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [buildWwColors] / [MaterialColorSchemeFactory] 验收:双轨的两侧都要对。
 */
class WwColorsTest {

    @Test
    fun wwColorsResolvesRolesAndTokens() {
        val cfg = resolveTheme("emerald-day")
        val vars = resolveThemeVars(cfg)
        val material = lightColorScheme()
        val wwc = buildWwColors(cfg, vars, UiMode.LIGHT, material, WwTypographyTokens(15.0))

        assertEquals(UiMode.LIGHT, wwc.uiMode)
                assertEquals(CssColors.parseStrict(vars.m1[6], "m1-600"), wwc.primaryBg)
        assertEquals(Color.Black, wwc.onUserBubble, "light 模式用户气泡文字 = #000000")
        assertEquals(CssColors.parseStrict(vars.m1[2], "m1-200"), wwc.aiBubble, "亮色气泡 = m1[2]")
        // tokens 择边:light 取浅色侧
        assertEquals(DesignTokens.surface1Light, wwc.surface1)
        assertEquals(DesignTokens.textPrimaryLight, wwc.textPrimary)
        // soft = 500 档 15%
        assertEquals(0.15f, wwc.m1Soft.alpha, 0.001f)
    }

    @Test
    fun darkModeSwitchesBubbleAndTokens() {
        val cfg = resolveTheme("emerald-day")
        val vars = resolveThemeVars(cfg)
        val wwc = buildWwColors(cfg, vars, UiMode.DARK, MaterialColorSchemeFactory.build(vars, cfg, UiMode.DARK), WwTypographyTokens())

        assertEquals(CssColors.parseStrict(vars.bubbleDark, "bubble-dark"), wwc.aiBubble)
        assertEquals(DesignTokens.surface1Dark, wwc.surface1)
        assertEquals(DesignTokens.textPrimaryDark, wwc.textPrimary)
        assertEquals(DesignTokens.accentPrimaryDark, wwc.accentPrimary)
    }

    @Test
    fun materialSchemeFollowsThemeNotDefaultPurple() {
        // M3 默认 light scheme 的 primary 是紫色系(R≈0.4~0.5,G 低,B 高);
        // emerald 主题下 primary 必须是 m1-600(#059669 附近),与默认紫拉开。
        val cfg = resolveTheme("emerald-day")
        val vars = resolveThemeVars(cfg)
        val scheme = MaterialColorSchemeFactory.build(vars, cfg, UiMode.LIGHT)
        val m600 = CssColors.parseStrict(vars.m1[6], "m1-600")
        assertEquals(m600, scheme.primary)
        // 对比:裸 M3 默认(不喂主题)的 primary 与 m600 必然不同
        assertTrue(lightColorScheme().primary != scheme.primary)
    }

    @Test
    fun materialSchemePrimaryShadeComesFromRoles() {
        // midnight 的 primaryBg 是 bg-m1-700 → light 模式 primary = slate-700
        val cfg = resolveTheme("slate-day")
        val vars = resolveThemeVars(cfg)
        val scheme = MaterialColorSchemeFactory.build(vars, cfg, UiMode.LIGHT)
        assertEquals(CssColors.parseStrict(vars.m1[7], "slate-700"), scheme.primary)

        // emerald(dark)的主档来自 primaryHover(bg-m1-500)→ dark 模式 primary = m1-500
        val emerald = resolveTheme("emerald-day")
        val emeraldVars = resolveThemeVars(emerald)
        val darkScheme = MaterialColorSchemeFactory.build(emeraldVars, emerald, UiMode.DARK)
        assertEquals(CssColors.parseStrict(emeraldVars.m1[5], "m1-500"), darkScheme.primary)
    }

    @Test
    fun customThemeEndToEnd() {
        val cfg = customThemeToConfig("custom_x", "测试主题", "#34d399", "#818cf8")
        val vars = resolveThemeVars(cfg)
        val wwc = buildWwColors(cfg, vars, UiMode.LIGHT, MaterialColorSchemeFactory.build(vars, cfg, UiMode.LIGHT), WwTypographyTokens())
        // 自定义主题气泡统一走主色派生档(m1[2];生命周期与预设主题同样的 resolveThemeVars 规则)
        assertEquals(CssColors.parseStrict(vars.m1[2], "m1[2]"), wwc.aiBubble)
        // primaryBg 解出的档位 = 600(角色串仍是 STANDARD)
        assertEquals(CssColors.parseStrict(vars.m1[6], "m1-600"), wwc.primaryBg)
    }

    @Test
    fun fontFamilySizeDefaultsTo15() {
        val cfg = resolveTheme("emerald-day")
        val vars = resolveThemeVars(cfg)
        val wwc = buildWwColors(cfg, vars, UiMode.LIGHT, MaterialColorSchemeFactory.build(vars, cfg, UiMode.LIGHT), WwTypographyTokens())
        assertEquals(15.0, wwc.typography.baseFontSize)
    }
}
