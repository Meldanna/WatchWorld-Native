package com.watchworld.ui.markdown

import com.watchworld.domain.RegexApplyScope
import com.watchworld.domain.applyRegexRules
import com.watchworld.model.RegexRule

/**
 * Markdown **渲染前的纯文本预处理**(PLAN §7.3:「`applyRegexRules` 应在渲染前对纯文本应用」)。
 *
 * 顺序与 Web 版一致:先在**管道层**应用用户正则规则(output scope),再标记工具状态行。
 * 两者都作用在**解析前的纯文本**上 —— 而不是解析结果(HTML/AST)。
 *
 * ## 为什么工具状态行要在这里做(而不是 MarkdownComponents)
 * Web 版是在 marked 产出的 HTML 上把「整段只有 `[工具…]`」的 `<p>` 换成
 * `<p><code data-tool-status>…</code></p>`。本仓在**文本层**做等价的事:把「整个块只有
 * `[工具…]` 一行」的块包成行内代码(`` `[工具…]` ``),让库渲染成 code span。
 * 好处:纯函数、可单测、且与流式的「只追加」天然兼容(库内部只重解析不稳定尾部)。
 *
 * `note()` 的文案(见 `net/ToolCallLoop.kt`)以 `[工具调用第 N 步：…]` / `[工具返回错误，…]`
 * 这类**方括号包裹**的形式写进正文,正好与 Web 的正则 `\[工具…\]` 对得上。
 */
fun preRenderMarkdown(content: String, rules: List<RegexRule> = emptyList()): String {
    val withRules = if (rules.isEmpty()) content else applyRegexRules(content, rules, RegexApplyScope.OUTPUT)
    return markToolStatusLines(withRules)
}

/** 连续非空行 = 一个 markdown「块」(空行分隔);与 Web 的 `<p>` 粒度对应。 */
private val MARKDOWN_BLOCK = Regex("""[^\n]+(?:\n[^\n]+)*""")

/** 整块只有一个 `[工具…]`(Web: `<p>[工具…]</p>`)。`[^<>]` 排除块内标记,与 Web 正则一致。 */
private val TOOL_STATUS_LINE = Regex("""^\[工具[^<>]*\]$""")

/**
 * 把「整段只有一行 `[工具…]`」的块包成行内代码,其余原样。
 * 幂等:已包裹的块(`` `[工具…]` ``)不含裸 `[工具…]` 锚定匹配,不会被二次处理。
 */
internal fun markToolStatusLines(content: String): String {
    if (!content.contains("[工具")) return content
    return MARKDOWN_BLOCK.replace(content) { match ->
        val block = match.value
        val trimmed = block.trim()
        if (TOOL_STATUS_LINE.matches(trimmed)) "`$trimmed`" else block
    }
}
