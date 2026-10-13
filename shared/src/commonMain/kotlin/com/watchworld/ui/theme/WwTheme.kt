package com.watchworld.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.watchworld.model.CustomTheme
import com.watchworld.model.ThemePalette
import com.watchworld.model.UiMode

/**
 * 静态 Design tokens(等价 index.css `:root` / `html.dark` 两块)。
 * ⚠️ 它们**不随主题 id 变**(React 版同样只挂两套静态值),只随 uiMode 变。
 */
object DesignTokens {
    val surface0Light = Color(0xFFFFFFFF)
    val surface1Light = Color(0xFFF8FAFC)
    val surface2Light = Color(0xFFF1F5F9)
    val surface3Light = Color(0xFFE2E8F0)
    val surfaceElevatedLight = Color(0xFFFFFFFF)
    val textPrimaryLight = Color(0xFF0F172A)
    val textSecondaryLight = Color(0xFF475569)
    val textTertiaryLight = Color(0xFF94A3B8)
    val borderSubtleLight = Color(0xFFE2E8F0)
    val borderDefaultLight = Color(0xFFCBD5E1)
    val accentPrimaryLight = Color(0xFF3B82F6)
    val accentSecondaryLight = Color(0xFF06B6D4)

    val surface0Dark = Color(0xFF0A0D12)
    val surface1Dark = Color(0xFF0F131C)
    val surface2Dark = Color(0xFF161D2B)
    val surface3Dark = Color(0xFF1E2636)
    val surfaceElevatedDark = Color(0xFF1E2636)
    val textPrimaryDark = Color(0xFFF8FAFC)
    val textSecondaryDark = Color(0xFFCBD5E1)
    val textTertiaryDark = Color(0xFF64748B)
    val borderSubtleDark = Color(0xFF1E293B)
    val borderDefaultDark = Color(0xFF334155)
    val accentPrimaryDark = Color(0xFF60A5FA)
    val accentSecondaryDark = Color(0xFF22D3EE)
}

/**
 * dp token(等价 index.css `--radius-*` 与 `--space-*`;单位 dp,直译 px)。
 * 阴影 token(`--shadow-*`)不在这里 —— CSS box-shadow 与 Compose elevation 不同构,
 * 用 `Modifier.shadow(elevation)` 近似,见任务文档「心得:阴影」。
 *
 * ⚠️ 圆角已按「两档制」统一(2026-10-12,见 memory/ui-theme-surfaces.md「圆角规范」):
 *    UI 代码一律写 `10.dp`(控件) / `12.dp`(容器) / `100.dp`(胶囊) 字面值,
 *    下面的 RADIUS_* 只是 Web 版 `--radius-*` 的直译存档,与两档制不完全对齐(缺 10 这档),新代码不要用。
 */
object DesignTokensDp {
    const val RADIUS_SM = 8f
    const val RADIUS_MD = 12f
    const val RADIUS_LG = 16f
    const val RADIUS_XL = 20f
    const val RADIUS_PILL = 999f

    const val SPACE_XS = 4f
    const val SPACE_SM = 8f
    const val SPACE_MD = 12f
    const val SPACE_LG = 16f
    const val SPACE_XL = 24f
}

/**
 * 字号 token（用户 2026-10-13 定案：**不做倍率换算，设置值即字号**）。
 *
 * 不再沿用 Web 版 `chat-text-*` 的 `× 0.9333 / × 1.0667` 比例，而是：
 * - 默认正文 = [baseFontSize]（设置里的「界面字体大小」直接就是 sp 数）；
 * - 标题 = 正文 + 2sp，提示 = 正文 − 2sp；
 * - 三档行高也用 sp 直加（紧凑 +3sp / 正文 +5sp / 标题 +7sp），默认 15 时仍是 16 / 20 / 24。
 *
 * ⚠️ React 版语义:弹窗小字(text-[10px]/text-[11px])与角标徽章**不走**这套,
 *    Compose 里那些地方用固定 sp,不读本类。
 */
@Immutable
data class WwTypographyTokens(
    /** 用户设置里的「界面字体大小」(px);默认 15.0(与 AppPreferences.chatFontSize 默认一致)。 */
    val baseFontSize: Double = 15.0,
) {
    /** 提示（框外说明 / 注释浮层 / 次级说明）= 正文 − 2sp。 */
    val xsFontSize: Double get() = baseFontSize - 2.0
    /** 默认正文 = 设置里的界面字体大小（不再乘 0.9333）。 */
    val smFontSize: Double get() = baseFontSize
    /** 标题 = 正文 + 2sp（不再乘 1.0667）。 */
    val baseFontSizeScaled: Double get() = baseFontSize + 2.0

    /** 框内紧凑行高 = 提示字号 + 3sp（默认 15 时 16sp）。 */
    val xsLineHeight: Double get() = xsFontSize + 3.0
    /** 正文段落行高 = 正文字号 + 5sp（默认 15 时 20sp）。 */
    val smLineHeight: Double get() = smFontSize + 5.0
    /** 标题行高 = 标题字号 + 7sp（默认 15 时 24sp）。 */
    val baseLineHeight: Double get() = baseFontSizeScaled + 7.0
}

/**
 * 面向 UI 的主题色集合:当前主题在当前 uiMode 下每类角色的实际 Color。
 * 字段名与 React 版 ThemeConfig 的 7 字段/Design token 一一对应,
 * 另附 vars(字符串原形,给 T15 编辑器与快照对拍)与 material scheme(双轨①)。
 */
@Immutable
data class WwColors(
    val vars: ThemeVars,
    /** 本主题的配置(名字/primaryHex/角色串原形)。 */
    val config: ThemeConfig,
    val uiMode: UiMode,
    val primaryBg: Color,
    val primaryHover: Color,
    val primaryText: Color,
    /** 用户消息气泡底(bg-m1-N,N 随变体;与 primaryBg 同色)。 */
    val userBubble: Color,
    /** 用户气泡上的文字(两套角色串均为 text-white)。 */
    val onUserBubble: Color,
    /** AI 消息气泡底(与用户气泡同色,均连接模块1主色 primaryBg)。 */
    val aiBubble: Color,
    /** 角色串 accentBadge 的「bg 分支」主色(容器底;dark 分支自行取 vars)。 */
    val accentBadge: Color,
    val badgeBg: Color,
    val badgeText: Color,
    /** 选中态浅底(等价 --m1-soft / --m2-soft:500 档 15% alpha)。 */
    val m1Soft: Color,
    val m2Soft: Color,
    // ── Design tokens(只随 uiMode 变)──
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    /** Material 本体 scheme(双轨①,与 MaterialTheme.colorScheme 同实例)。 */
    val material: androidx.compose.material3.ColorScheme,
    /** 字号 token(机制归 T12,设置界面归 T15)。 */
    val typography: WwTypographyTokens = WwTypographyTokens(),
)

/** UI 层入口:`WwTheme.colors` 取当前主题色集合。 */
val LocalWwColors = staticCompositionLocalOf<WwColors> {
    error("WwTheme 未包裹:请在 App 根部调用 WwTheme { ... }")
}

/** 当前明暗模式(主题 id 之外的另一维;多个 UI 逻辑需要)。 */
val LocalUiMode = staticCompositionLocalOf { UiMode.LIGHT }

/**
 * Ww 主题双轨入口。
 *
 * ## 双轨(用户已拍板)
 * ① 由主题生成 Material3 [androidx.compose.material3.ColorScheme] 喂 `MaterialTheme` ——
 *    现有 `OutlinedTextField` / `Button` / `Surface` 自动跟随主题,不再吃 M3 默认紫;
 * ② 新代码用 `WwTheme.colors`([LocalWwColors]),字段语义与 Web 版类名一一对应。
 *
 * @param palette 存档顶层 theme(单数字符串;custom_xxx 走自定义表,查不到回退 emerald)
 * @param customThemes DIY 主题列表(T15 才有来源;当前调用方传空表)
 * @param chatFontSize AppPreferences.chatFontSize(字号 token 的 base)
 * @param uiFontFamily 界面字体族(黑体 / 宋体),见 [UiFontFamily]
 * @param uiMode 强制明暗;不传跟随系统(isSystemInDarkTheme)
 */
@Composable
fun WwTheme(
    palette: ThemePalette,
    customThemes: List<CustomTheme> = emptyList(),
    chatFontSize: Double = 15.0,
    uiFontFamily: UiFontFamily = UiFontFamily.SANS,
    uiMode: UiMode? = null,
    content: @Composable () -> Unit,
) {
    val resolvedMode = uiMode ?: if (isSystemInDarkTheme()) UiMode.DARK else UiMode.LIGHT
    val cfg = resolveTheme(palette, customThemes)
    val vars = resolveThemeVars(cfg)
    val materialScheme = MaterialColorSchemeFactory.build(vars, cfg, resolvedMode)
    val wwc = buildWwColors(cfg, vars, resolvedMode, materialScheme, WwTypographyTokens(chatFontSize))
    val fontFamily = uiFontFamily.toFontFamily()

    CompositionLocalProvider(
        LocalUiMode provides resolvedMode,
        LocalWwColors provides wwc,
        LocalWwFontFamily provides fontFamily,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = wwTypography(fontFamily),
        ) {
            // 裸 `Text`（不传 `style`）的默认样式：**正文档 + 紧凑行高**。
            //
            // ⚠️ 不提供这一层的话，M3 的 `bodyLarge`（16sp / **24sp 行高**）会被继承：
            //    即使调用方把 `fontSize` 改成 14sp，`lineHeight` 还是 24sp（1.7em）——
            //    单行标签所在的框被凭空撑高约 8dp，看起来就是「框内边距很大 / 行距很大」
            //    （用户 2026-10-12 反复报的多数现象都是这一个根因）。
            //
            // 多行正文（聊天气泡、markdown、引导页）自己显式传 `WwType.bodyLineHeight`，
            // M3 组件（Button/TextField/…）内部直接用 `MaterialTheme.typography`，
            // 两者都不受本层影响。
            ProvideTextStyle(
                TextStyle(
                    fontFamily = fontFamily,
                    fontSize = wwc.typography.smFontSize.sp,
                    lineHeight = wwc.typography.xsLineHeight.sp,
                ),
                content = content,
            )
        }
    }
}

/**
 * 把界面字体族铺到 M3 [Typography] 的每一档（用户 2026-10-11 加「宋体」时定案）。
 *
 * 为什么必须逐档 copy，而不是只提供 `LocalTextStyle`：
 * 1. `MaterialTheme.typography.xxx` 的样式**自带** `fontFamily`（默认 [FontFamily.Default]），
 *    而显式传的 `style` 优先于 `LocalTextStyle` —— 只改 LocalTextStyle 的话，
 *    凡是指定了 `style = MaterialTheme.typography.…` 的文字（项目里很多）都会退回黑体；
 * 2. M3 的 `MaterialTheme` 内部会 `ProvideTextStyle(typography.bodyLarge)`，
 *    逐档带上 family 之后，裸 `Text(...)`（不传 style）也一并跟上。
 */
internal fun wwTypography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.withFamily(): TextStyle = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily(),
    )
}

/** 惯用入口:`WwTheme.colors.xxx`。 */
object WwTheme {
    val colors: WwColors
        @Composable get() = LocalWwColors.current
}

/** 组装 [WwColors]:角色串解引用(两维中的当前档)+ tokens 择边 + bubble 解析。 */
internal fun buildWwColors(
    cfg: ThemeConfig,
    vars: ThemeVars,
    mode: UiMode,
    material: androidx.compose.material3.ColorScheme,
    typography: WwTypographyTokens,
): WwColors {
    // 解引用上下文:parseRoleClass → shadeToColor 需要当前主题的色阶。
    roleCurrentVars = vars
    fun resolve(clazz: String): Color = parseRoleClass(clazz)[mode] ?: Color.Transparent
    val isDark = mode == UiMode.DARK

    val primaryBg = resolve(cfg.primaryBg)
    // AI 气泡从 vars.bubble/bubbleDark 解析（预设主题：白/深灰；自定义主题：m1[0]/m1[10]）
    val aiBubble = CssColors.parseStrict(
        if (isDark) vars.bubbleDark else vars.bubble,
        "aiBubble (${if (isDark) "dark" else "light"})"
    )

    return WwColors(
        vars = vars,
        config = cfg,
        uiMode = mode,
        primaryBg = primaryBg,
        primaryHover = resolve(cfg.primaryHover),
        primaryText = resolve(cfg.primaryText),
        userBubble = resolve(cfg.userBubble),
                                 onUserBubble = if (isDark) Color.White else Color.Black,
        aiBubble = aiBubble,
        accentBadge = resolve(cfg.accentBadge),
        badgeBg = resolve(cfg.badgeBg),
        badgeText = resolve(cfg.badgeText),
        m1Soft = colorMixSoft(vars.m1[5]),
        m2Soft = colorMixSoft(vars.m2[5]),
        surface0 = if (isDark) DesignTokens.surface0Dark else DesignTokens.surface0Light,
        surface1 = if (isDark) DesignTokens.surface1Dark else DesignTokens.surface1Light,
        surface2 = if (isDark) DesignTokens.surface2Dark else DesignTokens.surface2Light,
        surface3 = if (isDark) DesignTokens.surface3Dark else DesignTokens.surface3Light,
        surfaceElevated = if (isDark) DesignTokens.surfaceElevatedDark else DesignTokens.surfaceElevatedLight,
        textPrimary = if (isDark) DesignTokens.textPrimaryDark else DesignTokens.textPrimaryLight,
        textSecondary = if (isDark) DesignTokens.textSecondaryDark else DesignTokens.textSecondaryLight,
        textTertiary = if (isDark) DesignTokens.textTertiaryDark else DesignTokens.textTertiaryLight,
        borderSubtle = if (isDark) DesignTokens.borderSubtleDark else DesignTokens.borderSubtleLight,
        borderDefault = if (isDark) DesignTokens.borderDefaultDark else DesignTokens.borderDefaultLight,
        accentPrimary = if (isDark) DesignTokens.accentPrimaryDark else DesignTokens.accentPrimaryLight,
        accentSecondary = if (isDark) DesignTokens.accentSecondaryDark else DesignTokens.accentSecondaryLight,
        material = material,
        typography = typography,
    )
}

/** 等价 index.css `color-mix(in oklab, var(--m1-500) 15%, transparent)`:取 15% alpha。 */
private fun colorMixSoft(cssColor: String): Color =
    CssColors.parse(cssColor)?.copy(alpha = 0.15f) ?: Color.Transparent
