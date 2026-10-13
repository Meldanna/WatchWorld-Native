package com.watchworld.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.watchworld.model.UiMode

/**
 * 双轨①:由主题色阶生成 Material3 [ColorScheme],喂给 `MaterialTheme`
 * 让 `OutlinedTextField` / `Button` / `Surface` / `Switch` 等**现有 Material 组件**
 * 自动跟随主题,不再吃 M3 默认紫。
 *
 * ## 主档位由角色串驱动(不硬编码主题 id)
 * light 模式主档取 `primaryBg` 角色串解析出的那档(STANDARD=600,MIDNIGHT=700);
 * dark 模式取 `primaryHover` 的档位(STANDARD=500,MIDNIGHT=600)——
 * 与 Web 版「dark: 前缀换一档」的手感一致,且新增主题变体时无需改这里。
 *
 * ## 字段映射(T12 拍板的最小可用方案,并排走查后可调)
 * | ColorScheme | 取值 |
 * |---|---|
 * | primary / onPrimary | primaryBg 档 / White |
 * | primaryContainer / on | 主档−3(浅三档)/ 主档+3(深三档) |
 * | secondary / onSecondary | m2-600 / White |
 * | background·surface / on | token surface-1 / text-primary |
 * | surfaceVariant / on | token surface-3 / text-secondary |
 * | outline / outlineVariant | token border-default / border-subtle |
 * | error 系 | 保留 M3 默认(Web 版未主题化错误色) |
 */
object MaterialColorSchemeFactory {

    fun build(vars: ThemeVars, cfg: ThemeConfig, mode: UiMode): ColorScheme {
        val m1 = vars.m1.map { CssColors.parseStrict(it, "m1 (theme=${cfg.id})") }
        val m2 = vars.m2.map { CssColors.parseStrict(it, "m2 (theme=${cfg.id})") }
        val isDark = mode == UiMode.DARK
        val tokens = if (isDark) DarkTokens else LightTokens
        val base = if (isDark) darkColorScheme() else lightColorScheme()

        // 主档位:从 primaryBg / primaryHover 角色串解出「这是哪一档」。
        val primaryShade = extractShade(cfg.primaryBg) ?: 600
        val hoverShade = extractShade(cfg.primaryHover) ?: 500
        val primaryIdx = SHADE_KEYS.indexOf(if (isDark) hoverShade else primaryShade).coerceAtLeast(0)

        return base.copy(
            primary = m1[primaryIdx],
            onPrimary = Color.White,
            primaryContainer = m1[(primaryIdx - 3).coerceIn(0, 10)],
            onPrimaryContainer = m1[(primaryIdx + 3).coerceIn(0, 10)],
            inversePrimary = m2[primaryIdx],
            secondary = m2[6],
            onSecondary = Color.White,
            secondaryContainer = m2[3],
            onSecondaryContainer = m2[9],
            tertiary = m2[5],
            onTertiary = Color.White,
            tertiaryContainer = m2[2],
            onTertiaryContainer = m2[8],
            background = tokens.background,
            onBackground = tokens.onBackground,
            surface = tokens.surface,
            onSurface = tokens.onSurface,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.onSurfaceVariant,
            surfaceTint = m1[primaryIdx],
            inverseSurface = tokens.inverseSurface,
            inverseOnSurface = tokens.inverseOnSurface,
            outline = tokens.outline,
            outlineVariant = tokens.outlineVariant,
            scrim = Color.Black,
        )
    }

    /** `bg-m1-700` / `hover:bg-m1-500` → 700 / 500(取第一个 m1/m2 shade 数字)。 */
    private fun extractShade(roleClass: String): Int? =
        Regex("""(?:bg|text|border)-m[12]-(\d{1,3})""").find(roleClass)
            ?.groupValues?.get(1)?.toIntOrNull()

    /** index.css token 的 ColorScheme 投影(两套值,一个形状 —— 否则 if 推断成 Any)。 */
    private class SchemeTokens(
        val background: Color,
        val onBackground: Color,
        val surface: Color,
        val onSurface: Color,
        val surfaceVariant: Color,
        val onSurfaceVariant: Color,
        val inverseSurface: Color,
        val inverseOnSurface: Color,
        val outline: Color,
        val outlineVariant: Color,
    )

    private val LightTokens = SchemeTokens(
        background = DesignTokens.surface1Light,
        onBackground = DesignTokens.textPrimaryLight,
        surface = DesignTokens.surface1Light,
        onSurface = DesignTokens.textPrimaryLight,
        surfaceVariant = DesignTokens.surface3Light,
        onSurfaceVariant = DesignTokens.textSecondaryLight,
        inverseSurface = DesignTokens.surface3Dark,
        inverseOnSurface = DesignTokens.textPrimaryDark,
        outline = DesignTokens.borderDefaultLight,
        outlineVariant = DesignTokens.borderSubtleLight,
    )

    private val DarkTokens = SchemeTokens(
        background = DesignTokens.surface1Dark,
        onBackground = DesignTokens.textPrimaryDark,
        surface = DesignTokens.surface1Dark,
        onSurface = DesignTokens.textPrimaryDark,
        surfaceVariant = DesignTokens.surface3Dark,
        onSurfaceVariant = DesignTokens.textSecondaryDark,
        inverseSurface = DesignTokens.surface3Light,
        inverseOnSurface = DesignTokens.textPrimaryLight,
        outline = DesignTokens.borderDefaultDark,
        outlineVariant = DesignTokens.borderSubtleDark,
    )
}
