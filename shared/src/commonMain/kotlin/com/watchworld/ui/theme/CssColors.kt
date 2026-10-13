package com.watchworld.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 把 theme.ts 产出的 CSS 颜色字符串解析成 [Color]。
 * 本仓出现的格式只有两种:`#RRGGBB`(hex)与 `oklch(L% C H[ / A])`(theme.ts 的严格输出,
 * 档位值与 `--m1-bubble-dark` 都不带空格变体;但此处按通用宽容解析,略空格)。
 * 其它函数/颜色写法(Web 版有 `color-mix` 等)一律返回 null,由调用方显式处理 ——
 * 静默猜色比显式失败更危险。
 */
object CssColors {

    // 色度只接受数值形式(theme.ts 的产出);CSS 的百分比色度(`oklch(… 40% …)`)本仓不产生,
    // 按模块契约「不认识的语法返回 null,不静默猜色」直接不匹配,而不是当数字用。
    private val oklchRegex =
        Regex("""^oklch\(\s*([0-9.]+)%\s+([0-9.]+)\s+(-?[0-9.]+)(?:\s*/\s*([0-9.]+)(%)?)?\s*\)$""")

    /**
     * 解析单个颜色。返回 null 表示「不是本仓认识的语法」。
     */
    fun parse(value: String): Color? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("#")) return parseHexOrNull(trimmed)?.let(::Color)

        val match = oklchRegex.matchEntire(trimmed) ?: return null
        val l = match.groupValues[1].toDouble()
        val c = match.groupValues[2].toDouble()
        val h = match.groupValues[3].toDouble()
        val alpha = parseAlphaPercent(match.groupValues[4], match.groupValues[5])
        return Color(OklchColorMath.toSrgbInt(l, c, h, alpha))
    }

    /**
     * CSS `oklch(… / A)` 的 A:缺省 = 100%;带 `%` 是百分数(0..100);
     * **无单位是 0..1 比例**(theme.ts 的 `--m1-bubble-dark` 就是 `/ 0.9` = 90%)。
     * 折算成 [OklchColorMath.toSrgbInt] 要的 0..100 百分数。
     * ⚠️ 无单位值**不能**直接当百分数 —— 那会把 `/ 0.9` 渲染成近乎全透明。
     */
    private fun parseAlphaPercent(number: String, percentSign: String): Double = when {
        number.isEmpty() -> 100.0
        percentSign == "%" -> number.toDouble()
        else -> number.toDouble() * 100.0
    }

    /** `#RRGGBB`(可缩写为 3 位?)不;theme.ts 只产出 6 位。 */
    fun parseHexOrNull(hex: String): Int? {
        val regex = Regex("^#?([0-9a-f]{6})\$", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(hex.trim()) ?: return null
        return 0xFF000000.toInt() or match.groupValues[1].toInt(16)
    }

    /** 必须成功的版本:解析失败要拿到谁参数传错了的现场信息,而不是屏幕上悄悄换了色。 */
    fun parseStrict(value: String, context: String): Color =
        parse(value) ?: error("无法解析 CSS 颜色「$value」($context)")
}
