package com.watchworld.ui.theme

import com.watchworld.model.CustomTheme
import com.watchworld.model.ThemePalette

/**
 * theme.ts `buildScale` 的直译:由一个 HEX 推出一条 11 级 oklch 色阶。
 * L 阶梯([L_LADDER])+ C 钟形([C_BELL])沿用 Tailwind 手感,500 档对齐用户选的颜色。
 *
 * 与 JS 行为一致:hex 非法返回 null(调用方回退 family 表)。
 */
fun buildScale(hex: String): List<String>? {
    val base = OklchColorMath.hexToOklch(hex) ?: return null
    val hue = (base.h % 360.0 + 360.0) % 360.0
    val c500 = base.c
    return L_LADDER.indices.map { i ->
        val l = L_LADDER[i]
        val c = maxOf(0.0, c500 * C_BELL[i])
        // L 走 toString 语义(toCssNumber);C/H 走 toFixed —— 与反应快照逐字对上。
        // (theme.ts 模板是 ${L}% ${c.toFixed(4)} ${h.toFixed(2)},JS 模板串对 number
        //  隐式 toString,于是同一模板内两种精度并存 —— 诡异但实证如此。)
        "oklch(${toCssNumber(l)}% ${c.toFixedCss4()} ${hue.toFixedCss2()})"
    }
}

/**
 * 对齐 JS `Number.toFixed(4)`(theme.ts 里 C 的精度)。**固定小数位,不丢尾零**
 * (node 实测 `(0.068).toFixed(4) === "0.0680"`;把尾零去掉的是 toString,别混):
 * L 通道在 buildScale 模板里走的是 toString 语义(见 [toCssNumber]),那是快照对拍的实证结论。
 */
internal fun Double.toFixedCss4(): String = toFixedImpl(this, 4)

/** 对齐 JS `Number.toFixed(2)`(theme.ts 里 H 的精度),同样固定位不丢尾零(`64.6`→`"64.60"`)。 */
internal fun Double.toFixedCss2(): String = toFixedImpl(this, 2)

private fun toFixedImpl(value: Double, digits: Int): String {
    var scale = 1.0
    repeat(digits) { scale *= 10.0 }
    val scaled = value * scale
    // JS toFixed 对正数近似 half-up;theme.ts 的输入不是 .5 恰好卡边的值,此处一致。
    val rounded = (scaled + 0.5).toLong()
    var divisor = 1L
    repeat(digits) { divisor *= 10L }
    val intPart = rounded / divisor
    val fracPart = rounded % divisor
    return if (digits == 0) {
        intPart.toString()
    } else {
        "$intPart." + fracPart.toString().padStart(digits, '0')
    }
}

/**
 * 等价 JS 模板串 `${number}` 的 toString 语义:**去尾零**(`97.9`→`"97.9"`,`95.0`→`"95"`)。
 * 快照实证:buildScale 产出的 L 通道就是这个形状(m1-50=97.9 而非 97.9000),
 * 而 C/H 走 toFixed 固定位 —— 同一模板两种精度,诡异但必须逐字对上。
 */
internal fun toCssNumber(value: Double): String {
    val fixed = toFixedImpl(value, 4)
    return fixed.trimEnd('0').trimEnd('.')
}

/**
 * theme.ts `resolveThemeById` 的直译:先查内置,再查自定义;
 * 自定义被删后仍指向它时回退内置默认 emerald。永不返回 null。
 */
fun resolveTheme(
    id: ThemePalette,
    customThemes: List<CustomTheme> = emptyList(),
): ThemeConfig {
    builtInThemes()[id]?.let { return it }
    customThemes.firstOrNull { it.id == id }?.let { custom ->
        return customThemeToConfig(custom.id, custom.name, custom.m1, custom.m2)
    }
    return builtInThemes().getValue("emerald-day")
}
