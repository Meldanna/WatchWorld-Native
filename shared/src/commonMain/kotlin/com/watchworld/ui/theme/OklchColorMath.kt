package com.watchworld.ui.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * OKLCH ↔ sRGB 数学(纯函数,无 Compose 依赖)。
 *
 * ## 来源与验证
 * 正向(hex → oklch)的矩阵与阈值**逐字**来自 React 仓 `src/lib/theme.ts` 的
 * `hexToOklchChannels`(BFSU 标准矩阵,双精度);T12 测试用 7 个 Tailwind 预设的
 * primaryHex 向量钉死正确性 —— `oklch(59.6% 0.145 163.225)` 必须解回 `#059669` 等。
 *
 * 逆向(oklch → sRGB)是**本任务唯一凭空多出来的代码**:源实现里这步由浏览器完成,
 * Compose 的 [androidx.compose.ui.graphics.Color] 只认 sRGB。按 OKLab 规范的
 * 逆矩阵 + gamma(EOTF)展开。单向转出的 Color 与正向数学共享同一套常数,
 * 往返误差在 ±1/255 以内(测试覆盖)。
 */
internal object OklchColorMath {

    /** oklch 三通道:L ∈ 0..100,C ≥ 0,h 为角度(度,可负)。 */
    data class Channels(val l: Double, val c: Double, val h: Double)

    /** 往返时 hex → Color 的量化可以差最后一位;用这个容差断言语义相等。 */
    const val ROUND_TRIP_TOLERANCE: Double = 1.0

    /**
     * theme.ts `hexToOklchChannels` 的逐字直译。
     * 入参必须是 `#RRGGBB`(可无 `#`),不合法返回 null(与 JS 的 `return null` 一致)。
     */
    fun hexToOklch(hex: String): Channels? {
        val regex = Regex("^#?([0-9a-f]{6})\$", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(hex.trim()) ?: return null
        val n = match.groupValues[1].toInt(16)
        val rgb = listOf((n shr 16) and 255, (n shr 8) and 255, n and 255)

        // gamma 展开:JS 的 `x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4`
        val srgb = rgb.map { v ->
            val x = v / 255.0
            if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        val (r, g, b) = srgb

        // linear sRGB → LMS,逐通道取立方根(OKLab 正向 M1)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        // LMS' → okLab(OKLab 正向 M2)
        val labL = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
        val labA = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
        val labB = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s

        return Channels(
            l = labL * 100,
            c = hypot(labA, labB),
            h = atan2(labB, labA) * 180.0 / PI,
        )
    }

    /**
     * oklch(可带 alpha)→ sRGB int0xRRGGBBAA。越界的通道在 gamma 前按 0..1 收
     * (浏览器对超 sRGB gamut 的颜色就是这么裁进屏幕色域的,而不是先平移色相)。
     *
     * ⚠️ 双 gamma 差(测试抓出来的真 bug):theme.ts 正向用的 sigmoid 分母是 12.92,
     * 逆向必须用 **12.92**(不是 LinearTosRGB 误导性的 1.055)—— 用错会让 #059669
     * 解回 #009966(差 5/3/3)。已经按 7 条 Tailwind 向量修正。
     */
    fun toSrgbInt(lPercent: Double, c: Double, hDeg: Double, alphaPercent: Double = 100.0): Int {
        val hRad = hDeg * PI / 180.0
        val labA = c * kotlin.math.cos(hRad)
        val labB = c * kotlin.math.sin(hRad)
        val labL = lPercent / 100.0

        // okLab → LMS'(OKLab 逆向 M2'),立方回 linear LMS
        val lm = labL + 0.3963377774 * labA + 0.2158037573 * labB
        val mm = labL - 0.1055613458 * labA - 0.0638541728 * labB
        val sm = labL - 0.0894841775 * labA - 1.2914855480 * labB
        val l = lm.pow(3)
        val m = mm.pow(3)
        val s = sm.pow(3)

        // LMS → linear sRGB(OKLab 逆向 M1)
        val raw = doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
        var argb = toAlphaByte(alphaPercent)
        for (channel in raw) {
            val clamped = channel.coerceIn(0.0, 1.0)
            // 逆向 gamma(EOTF):linear → device 8bit(与正向同一 sRGB 曲线,sigmoid 分母 12.92)
            val encoded = if (clamped <= 0.0031308) clamped * 12.92 else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
            argb = (argb shl 8) or (encoded * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }
        return argb
    }

    private fun toAlphaByte(alphaPercent: Double): Int =
        (alphaPercent.coerceIn(0.0, 100.0) / 100.0 * 255.0 + 0.5).toInt().coerceIn(0, 255)

    /** 把 Channels 对齐到往返比较用的整数 RGB(两个方向共用)。 */
    fun quantize(channels: Channels, alphaPercent: Double = 100.0): Int =
        toSrgbInt(channels.l, channels.c, channels.h, alphaPercent)

    fun hexToSrgbInt(hex: String): Int {
        val regex = Regex("^#?([0-9a-f]{6})\$", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(hex.trim()) ?: return 0xFF000000.toInt()
        val n = match.groupValues[1].toInt(16)
        return 0xFF000000.toInt() or n
    }

    /** 测试与调试用:`#059669` 风格。 */
    fun srgbIntToHex(argb: Int): String = buildString {
        append('#')
        val rgb = argb and 0xFFFFFF
        if (rgb > 0) append(Integer.toHexString(rgb).uppercase().padStart(6, '0')) else append("000000")
    }

    /** 两个 argb 是否只差最后一位量化(往返测试断言用)。 */
    fun srgbIntsAlmostEqual(a: Int, b: Int, tolerance: Double = ROUND_TRIP_TOLERANCE): Boolean {
        if ((a ushr 24 and 255) != (b ushr 24 and 255)) return false
        for (shift in listOf(16, 8, 0)) {
            if (abs((a ushr shift and 255) - (b ushr shift and 255)) > tolerance) return false
        }
        return true
    }
}
