package com.watchworld.ui.theme

import androidx.compose.ui.graphics.Color
import com.watchworld.model.UiMode

/**
 * 两套角色串(theme.ts STANDARD_CLASSES / MIDNIGHT_CLASSES **类名字符串** 1:1)。
 * 每个角色是「明暗两份类名」合成的 Tailwind 串,不能在编译期算成 Color ——
 * 解析职责:按 uiMode 挑出对应分支,再从 [ThemeVars] 解出那档 Color(两维映射)。
 */
object ThemeRoles {

    val STANDARD = StandardRoles(
        primaryBg = "bg-m1-600",
        primaryHover = "hover:bg-m1-500",
        primaryText = "text-m1-500 dark:text-m1-400",
        userBubble = "bg-m1-200 text-m1-900 dark:bg-m1-900 dark:text-white shadow-sm",
        accentBadge = "bg-m1-100 text-m1-800 border-m1-300 dark:bg-m1-950/60 dark:text-m1-300 dark:border-m1-500/40",
        badgeBg = "bg-m1-500/15",
        badgeText = "text-m1-600 dark:text-m1-400",
    )

    val MIDNIGHT = StandardRoles(
        primaryBg = "bg-m1-700",
        primaryHover = "hover:bg-m1-600",
        primaryText = "text-m1-600 dark:text-m1-300",
        userBubble = "bg-m1-200 text-m1-900 dark:bg-m1-900 dark:text-white border border-m1-200 shadow-sm",
        accentBadge = "bg-m1-200 text-m1-800 border-m1-300 dark:bg-m1-800/80 dark:text-m1-200 dark:border-m1-600/50",
        badgeBg = "bg-m1-500/15",
        badgeText = "text-m1-700 dark:text-m1-300",
    )
}

/** 7 字段角色配置(theme.ts ThemeConfig 的后 7 个字段同名载体)。 */
data class StandardRoles(
    val primaryBg: String,
    val primaryHover: String,
    val primaryText: String,
    val userBubble: String,
    val accentBadge: String,
    val badgeBg: String,
    val badgeText: String,
)

/**
 * 解析一个角色的 Tailwind 类名串 → 「uiMode → Color?」两维映射。
 * 支持:`bg|text|border-m1|2-N`、`/NN` 百分比 alpha、`dark:` 与 `hover:` 前缀。
 * 串里**第一个**可解析的 light 片段与第一个 dark 片段分别决定两档
 * (与 React 版「首类决定主色」的用法一致;`shadow-sm`、`border` 裸词等非颜色片段跳过)。
 * 无对应分支时该档为 null(调用方决定回退,如 accentBadge 的 border 分支在 UI 层近似)。
 */
fun parseRoleClass(clazz: String): Map<UiMode, Color?> {
    val parts = clazz.trim().split(Regex("\\s+"))
    var light: Color? = null
    var dark: Color? = null
    for (p in parts) {
        val withoutHover = p.removePrefix("hover:") // hover: 只改交互态,不改变颜色
        val isDark = withoutHover.startsWith("dark:")
        val body = if (isDark) withoutHover.removePrefix("dark:") else withoutHover
        val color = parseUtility(body) ?: continue
        if (isDark) {
            if (dark == null) dark = color
        } else {
            if (light == null) light = color
        }
    }
    return mapOf(UiMode.LIGHT to light, UiMode.DARK to dark)
}

/** `bg-m1-600` / `text-m2-500/15` → Color(经 [shadeToColor],依赖 [roleCurrentVars])。 */
private fun parseUtility(body: String): Color? {
    val m = Regex("^(?:bg|text|border)-m([12])-(\\d{1,3})(?:/([0-9.]+))?$").find(body) ?: return null
    val module = if (m.groupValues[1] == "1") Module.M1 else Module.M2
    val shade = m.groupValues[2].toInt()
    val alpha = if (m.groupValues[3].isEmpty()) 100.0 else m.groupValues[3].toDouble()
    return shadeToColor(module, shade, alpha)
}

internal enum class Module { M1, M2 }

/**
 * 解引用上下文:当前主题的 [ThemeVars]。
 * Compose 组合在主线程串行执行,「先赋值再解析」的窗口不会被打断;
 * T22 若引入多线程组合再改成显式传参。
 */
internal var roleCurrentVars: ThemeVars? = null

internal fun shadeToColor(module: Module, shade: Int, alphaPercent: Double): Color? {
    val vars = roleCurrentVars ?: return null
    val idx = SHADE_KEYS.indexOf(shade)
    if (idx < 0) return null
    val css = if (module == Module.M1) vars.m1[idx] else vars.m2[idx]
    val base = CssColors.parse(css) ?: return null
    return if (alphaPercent >= 100.0) base else base.copy(alpha = (alphaPercent / 100.0).toFloat())
}

/** `oklch(69.6% 0.17 162.48)` → Channels(theme.ts 档位串的通用形状,不带 alpha)。 */
internal fun parseOklchChannels(value: String): OklchColorMath.Channels? {
    val m = Regex("""^oklch\(\s*([0-9.]+)%\s+([0-9.]+)\s+(-?[0-9.]+)\s*\)$""").find(value.trim()) ?: return null
    return OklchColorMath.Channels(
        l = m.groupValues[1].toDouble(),
        c = m.groupValues[2].toDouble(),
        h = m.groupValues[3].toDouble(),
    )
}

