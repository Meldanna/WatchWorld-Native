package com.watchworld.ui.theme

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T12 颜色数学验收。
 *
 * ## ⚠️ 为什么向量**不是** primaryHex(试验后修正,见任务文档心得)
 * Tailwind v4 的 oklch 值与 v3 时代的 hex 值**不一致**(同名字两套表):
 * v4 表 oklch(59.6% 0.145 163.225) 按规范逆向解回 **#009969** 系(绿 0x99),
 * 而 v3 表 emerald-600 = #059669。React 仓 theme.ts 两者混用:色阶用 v4 oklch
 * (浏览器实际渲染的色),primaryHex 用 v3 hex(仅作列表圆点展示)。
 *
 * 因此这里的 ground truth 是**浏览器按 CSS Color 4 规范逆向的结果**
 * (DevTools 可复核),7 个色相全覆盖,对得上就说明矩阵和 gamma 都对。
 */
class OklchVectorTest {

    /** (期望 hex, oklch 串)向量;oklch 取自 FAMILY_SCALES,期望 = 浏览器渲染值。 */
    private val vectors = listOf(
        "#009966" to "oklch(59.6% 0.145 163.225)", // emerald-600
        "#155DFC" to "oklch(54.6% 0.245 262.881)", // blue-600
        "#7F22FE" to "oklch(54.1% 0.281 293.009)", // violet-600
        "#EC003F" to "oklch(58.6% 0.253 17.585)", // rose-600
        "#E17100" to "oklch(66.6% 0.179 58.318)", // amber-600
        "#0092B8" to "oklch(60.9% 0.126 221.723)", // cyan-600
        "#314158" to "oklch(37.2% 0.044 257.287)", // slate-700(midnight 主档)
    )

    @Test
    fun oklchToSrgbMatchesPresetPrimaryHexes() {
        for ((hex, oklch) in vectors) {
            val channels = parseOklchChannels(oklch)
                ?: error("测试向量解析失败:$oklch")
            val argb = OklchColorMath.toSrgbInt(channels.l, channels.c, channels.h)
            val expected = OklchColorMath.hexToSrgbInt(hex)
            assertTrue(
                OklchColorMath.srgbIntsAlmostEqual(argb, expected, tolerance = 1.0),
                "$oklch 应解回 $hex,实际 ${OklchColorMath.srgbIntToHex(argb)}(差 ${channelDiff(argb, expected)})",
            )
        }
    }

    @Test
    fun hexToOklchMatchesTailwindValues() {
        // 正向:Tailwind v3 hex → oklch,期望 = 手算参考值(theme.ts 同矩阵;
        // 这里守住「改矩阵不改测试」的人)。注意期望与 v4 oklch 表**有意不同**。
        val forward = listOf(
            Triple("#059669", 0.596, 0.1274), // emerald-600:L=59.6,C≈0.1274
            Triple("#2563eb", 0.546, 0.2136),
            Triple("#7c3aed", 0.541, 0.2449),
            Triple("#e11d48", 0.586, 0.2217),
            Triple("#d97706", 0.666, 0.1562),
            Triple("#0891b2", 0.609, 0.1082),
            Triple("#334155", 0.372, 0.0408),
        )
        for ((hex, wantL, wantC) in forward) {
            val got = OklchColorMath.hexToOklch(hex) ?: error("hex 解析失败:$hex")
            assertTrue(abs(got.l - wantL * 100) < 0.05, "$hex L=${got.l} vs ${wantL * 100}")
            assertTrue(abs(got.c - wantC) < 0.003, "$hex C=${got.c} vs $wantC")
        }
    }

    @Test
    fun oklchRoundTripStaysWithinOneBit() {
        // hex → oklch → sRGB → hex 的往返:浮点+gamma 的量化误差必须在 ±1/255 内。
        // (自家矩阵自洽;与 v4 表的「不可往返」无关。)
        for (hex in listOf("#34d399", "#818cf8", "#f59e0b", "#22d3ee", "#059669", "#334155")) {
            val channels = OklchColorMath.hexToOklch(hex) ?: error(hex)
            val back = OklchColorMath.toSrgbInt(channels.l, channels.c, channels.h)
            val forward = OklchColorMath.hexToSrgbInt(hex)
            assertTrue(
                OklchColorMath.srgbIntsAlmostEqual(back, forward, tolerance = 1.0),
                "$hex 往返偏差过大:${OklchColorMath.srgbIntToHex(back)}",
            )
        }
    }

    @Test
    fun invalidHexReturnsNullLikeJs() {
        assertNull(OklchColorMath.hexToOklch("not-a-color"))
        assertNull(OklchColorMath.hexToOklch("#12345"))
        assertNull(OklchColorMath.hexToOklch("#1234567"))
        assertNull(OklchColorMath.hexToOklch(""))
        // 带无 # 的 6 位与 3 位前缀空白都应正常
        assertEquals("#059669".uppercase(), OklchColorMath.srgbIntToHex(OklchColorMath.hexToSrgbInt("059669")))
    }

    /**
     * buildScale:期望值逐字取自 fixtures/theme-baseline/snapshots.json 的
     * custom_test01(React 原实现输出)。两条精度规则(快照 + node 复核实证):
     * ① L 通道 = JS 模板串 toString 语义 → 去尾零(97.9,不是 97.9000);
     * ② C toFixed(4) / H toFixed(2) → 固定小数位、保留尾零(0.0190、163.22)。
     * 500 档彩度 = 用户色本身的彩度(0.1535),不乘 C_BELL[5];其余档才乘钟形系数。
     */
    @Test
    fun buildScaleAlignsShade500ToUserColor() {
        val scale = buildScale("#34d399")!!
        assertEquals(11, scale.size)
        assertEquals("oklch(69.6% 0.1535 163.22)", scale[5])
        // 50 档:0.153490 × 0.124 = 0.0190328 → 0.0190;L=97.9(toString 去尾零)
        assertEquals("oklch(97.9% 0.0190 163.22)", scale[0])
        // 950 档:0.153490 × 0.3 = 0.0460471 → 0.0460;L=26.2
        assertEquals("oklch(26.2% 0.0460 163.22)", scale[10])
        // 100 档 L=95.0 → toString 去尾零成 "95"(快照 m1-100 = oklch(95% 0.0470 …))
        assertEquals("oklch(95% 0.0470 163.22)", scale[1])
    }

    @Test
    fun buildScaleRejectsBadHex() {
        assertNull(buildScale("zzz"))
    }

    private fun channelDiff(a: Int, b: Int): String =
        listOf(16, 8, 0).joinToString("/") { shift -> "${abs((a ushr shift and 255) - (b ushr shift and 255))}" }
}
