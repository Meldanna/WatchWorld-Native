package com.watchworld.ui.markdown

import com.watchworld.model.RegexRule
import com.watchworld.model.RegexScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * T13 渲染前纯文本预处理（[preRenderMarkdown] / [markToolStatusLines]）。
 *
 * 与 Web 版对齐的语义：整段只有 `[工具…]` 的「块」包成行内代码（Web 是 `<p><code data-tool-status>`）；
 * 正则规则先于工具状态标记、只对 output scope 生效。
 */
class MarkdownPreRenderTest {

    // ── 工具状态行 ────────────────────────────────────────────────────────

    @Test
    fun marksStandaloneToolLineAsInlineCode() {
        assertEquals(
            "`[工具调用第 1 步：读文件]`",
            markToolStatusLines("[工具调用第 1 步：读文件]"),
        )
    }

    @Test
    fun marksEachStandaloneBlockAndKeepsBlankLines() {
        val src = "正文\n\n[工具返回错误，已记录到结果]\n\n结尾"
        assertEquals(
            "正文\n\n`[工具返回错误，已记录到结果]`\n\n结尾",
            markToolStatusLines(src),
        )
    }

    @Test
    fun leavesMultiLineBlockUnchanged() {
        // 同一块内还有别的行 → 不是「整段只有 [工具…]」（与 Web 的 <p> 粒度一致）。
        val src = "前一行\n[工具调用第 1 步：x]"
        assertEquals(src, markToolStatusLines(src))
    }

    @Test
    fun leavesInlineOccurrenceUnchanged() {
        val src = "结果 [工具调用第 1 步：x] 完成"
        assertEquals(src, markToolStatusLines(src))
    }

    @Test
    fun alreadyWrappedIsIdempotent() {
        val src = "`[工具调用第 1 步：x]`"
        assertEquals(src, markToolStatusLines(src))
    }

    @Test
    fun angleBracketsInsidePreventMatch() {
        // Web 正则用 [^<]* 排除块内标记；`[工具<call>]` 刻意不匹配。
        val src = "[工具<call>]"
        assertEquals(src, markToolStatusLines(src))
    }

    @Test
    fun noToolMarkerReturnsSameContent() {
        val src = "普通正文\n\n第二段"
        assertSame(src, markToolStatusLines(src), "无 [工具 时不应复制字符串（快速返回）")
    }

    // ── 正则规则（渲染前钩子）────────────────────────────────────────────

    @Test
    fun appliesOutputScopedRulesBeforeMarking() {
        val rule = RegexRule(
            id = "r", pattern = "foo", flags = "g", replacement = "bar",
            scope = RegexScope.BOTH, enabled = true,
        )
        assertEquals(
            "bar\n\n`[工具调用第 1 步：x]`",
            preRenderMarkdown("foo\n\n[工具调用第 1 步：x]", listOf(rule)),
        )
    }

    @Test
    fun inputScopedRuleIsNotAppliedToOutput() {
        val rule = RegexRule(
            id = "r", pattern = "foo", flags = "g", replacement = "bar",
            scope = RegexScope.INPUT, enabled = true,
        )
        assertEquals("foo", preRenderMarkdown("foo", listOf(rule)))
    }

    @Test
    fun disabledRuleIsIgnored() {
        val rule = RegexRule(id = "r", pattern = "foo", flags = "g", replacement = "bar", enabled = false)
        assertEquals("foo", preRenderMarkdown("foo", listOf(rule)))
    }

    @Test
    fun noRulesEqualsToolMarkingOnly() {
        val src = "正文\n\n[工具调用第 1 步：x]"
        assertEquals(markToolStatusLines(src), preRenderMarkdown(src, emptyList()))
    }
}
