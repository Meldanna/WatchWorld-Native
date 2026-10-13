package com.watchworld.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CssColors] 解析边界验收。
 *
 * 契约(见类注释):只认 theme.ts 产出的两种语法 —— `#RRGGBB` 与 `oklch(L% C H[ / A])`;
 * 其它语法(百分比色度、缺 `%` 的 L、3/8 位 hex、函数式颜色……)一律返回 null,**不静默猜色**。
 *
 * alpha 语义是最容易踩的一处:CSS 里无单位 `A` 是 0..1 比例(`/ 0.9` = 90%),
 * 只有带 `%` 才是百分数 —— 两者必须区分。
 */
class CssColorsTest {

    // ── hex ──────────────────────────────────────────────────────────────

    @Test
    fun hexParsesAsOpaqueColor() {
        assertEquals(Color(0xFF009966), CssColors.parse("#009966"))
        assertEquals(Color(0xFF009966), CssColors.parse("#009966".uppercase()), "hex 应大小写不敏感")
        assertEquals(Color(0xFF009966), CssColors.parse("  #009966  "), "前后空白应宽容")
        // parseHexOrNull 有/无 `#` 都行(正向矩阵用同一形状)
        assertEquals(0xFF009966.toInt(), CssColors.parseHexOrNull("009966"))
        assertEquals(0xFF009966.toInt(), CssColors.parseHexOrNull("#009966"))
    }

    @Test
    fun hexRejectsNonSixDigitForms() {
        // 3 位缩写 / 8 位带 alpha / 5 位 / 7 位 / 非 hex 字符 / 缺 `#`(parse 只在 `#` 开头才走 hex)/ 空
        for (bad in listOf("#abc", "#00996600", "#00996", "#0099667", "#GGGGGG", "009966", "#")) {
            assertNull(CssColors.parse(bad), "应拒绝:$bad")
        }
    }

    // ── oklch 语法 ────────────────────────────────────────────────────────

    @Test
    fun oklchMatchesBrowserGroundTruth() {
        // 与 OklchVectorTest 同一 ground truth:浏览器按 CSS Color 4 逆向后渲染的值。
        val expected = OklchColorMath.hexToSrgbInt("#009966")
        val actual = CssColors.parseStrict("oklch(59.6% 0.145 163.225)", "emerald-600").toArgb()
        assertTrue(
            OklchColorMath.srgbIntsAlmostEqual(actual, expected, tolerance = 1.0),
            "oklch emerald-600 应解回 #009966,实际 ${OklchColorMath.srgbIntToHex(actual)}",
        )
        assertEquals(1f, CssColors.parseStrict("oklch(59.6% 0.145 163.225)", "x").alpha, "无 alpha 应完全不透明")
    }

    @Test
    fun oklchAllowsNegativeHue() {
        assertTrue(CssColors.parse("oklch(59.6% 0.145 -163.225)") != null, "色相允许负角度")
    }

    @Test
    fun percentageChromaIsRejected() {
        // CSS 允许 `oklch(… 40% …)`,但 theme.ts 不产出;按契约显式 null,而非把它当数值 40。
        assertNull(CssColors.parse("oklch(59.6% 40% 163.225)"))
        assertNull(CssColors.parse("oklch(59.6% 40% 163.225 / 0.5)"))
    }

    @Test
    fun lightnessMustCarryPercentSign() {
        // theme.ts 的 L 一定带 `%`;`oklch(0.596 …)` 是另一种合法 CSS 写法,本仓不认。
        assertNull(CssColors.parse("oklch(0.596 0.145 163.225)"))
    }

    // ── alpha:无单位 = 比例;`%` = 百分数 ──────────────────────────────────

    @Test
    fun unitlessAlphaIsFractionNotPercent() {
        // 回归:`--m1-bubble-dark = oklch(20.8% 0.042 265.755 / 0.9)` 是 90% 不透明。
        // 曾把 0.9 当 0.9% 解析(→ alpha≈2/255,近乎全透明);浅色 E2E 与快照对拍都发现不了。
        val css = "oklch(20.8% 0.042 265.755 / 0.9)"
        assertEquals(0.9f, CssColors.parseStrict(css, "bubble-dark").alpha, 0.01f)
        // 前导点写法 `.9` 同义
        assertEquals(
            0.9f,
            CssColors.parseStrict("oklch(20.8% 0.042 265.755 / .9)", "bubble-dark").alpha,
            0.01f,
        )
    }

    @Test
    fun percentAlphaIsPercent() {
        assertEquals(
            0.9f,
            CssColors.parseStrict("oklch(20.8% 0.042 265.755 / 90%)", "bubble-dark").alpha,
            0.01f,
        )
    }

    @Test
    fun alphaBoundaries() {
        assertEquals(1f, CssColors.parseStrict("oklch(20.8% 0.042 265.755 / 1)", "a").alpha, 0.001f)
        assertEquals(1f, CssColors.parseStrict("oklch(20.8% 0.042 265.755 / 100%)", "a").alpha, 0.001f)
        assertEquals(0.5f, CssColors.parseStrict("oklch(20.8% 0.042 265.755 / 0.5)", "a").alpha, 0.01f)
    }

    // ── 未识别语法一律 null ────────────────────────────────────────────────

    @Test
    fun unrecognizedSyntaxesReturnNull() {
        val bad = listOf(
            "",
            "   ",
            "rgb(1, 2, 3)",
            "rgba(1, 2, 3, 0.5)",
            "hsl(120 50% 50%)",
            "color-mix(in oklab, red, blue)",
            "transparent",
            "currentColor",
            "oklch()",
            "oklch(59.6% 0.145)", // 缺 H
            "oklch(59.6% 0.145 163.225", // 缺右括号
            "not a color",
        )
        for (s in bad) assertNull(CssColors.parse(s), "应拒绝:$s")
    }

    @Test
    fun parseStrictThrowsWithContext() {
        val ex = assertFailsWith<IllegalStateException> { CssColors.parseStrict("oops", "some-context") }
        assertTrue(ex.message!!.contains("some-context"), "报错应带上下文:${ex.message}")
        assertTrue(ex.message!!.contains("oops"), "报错应回显原串:${ex.message}")
    }
}
