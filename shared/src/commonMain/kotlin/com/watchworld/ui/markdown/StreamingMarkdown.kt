package com.watchworld.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.watchworld.model.RegexRule
import com.watchworld.ui.theme.WwType
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberStreamingMarkdownState

/**
 * 流式 Markdown 渲染(T13 起改用 mikepenz `multiplatform-markdown-renderer`)。
 *
 * ## 为什么换库(决策 #2 定案)
 * T02 的自建极简渲染器只支持 `**粗体**` / 行内代码 / 围栏代码块,撑不起完整 GFM
 * (标题/列表/表格/任务列表/删除线/自动链接/GitHub 警告)。PLAN §2.4 建议 `compose-markdown`;
 * 该库自 **0.42.0** 起提供 `StreamingMarkdownState` —— **追加式**:每次 `append` 只重解析
 * 文档中「不稳定尾部」,而不是整段重排,正对 PLAN §7.3「只重解析末块」的验收。
 *
 * ## 追加式契约(与调用方约定)
 * 同一 [resetKey] 下,`content` **只允许追加**(流式逐字增长)。
 * - 消息切换 / 内容被整体替换(不是前缀追加)时,调用方必须换 [resetKey](本仓传 `message.id`),
 *   由 `key(resetKey)` 重建状态;否则尾部解析会错位。
 * - 首帧起累计已 append 的长度,只把新增后缀喂给 [rememberStreamingMarkdownState]。
 *
 * ## 未闭合语法
 * 交给库的解析器(CommonMark/GFM):未闭合的围栏代码块与半截行内标记都按稳定方式渲染,
 * 不再需要 T02 那套手写 `stripFence`。
 *
 * ## 渲染前预处理
 * 正文先经 [preRenderMarkdown] —— ①应用用户正则规则(`OUTPUT` scope)②标记工具状态行 ——
 * 再喂给库。若预处理使「渲染文本」不再是前缀追加(如某条 `[工具…]` 块刚被补全),
 * 则重建流式状态整段重解析一次(少见,不是每帧)。
 *
 * ## 流式光标
 * [streaming] 为 true 时,末尾多一个闪烁光标(等价 Web 版 `.stream-caret`)。
 * 它**不在正文里** —— 追加式状态挪不动末尾字符 —— 而是渲染期由注释器 +
 * 行内占位注入,细节见 `StreamCaret.kt`。
 *
 * @param content 当前完整正文(同一 resetKey 下单调追加)
 * @param streaming 是否仍在流式(决定要不要挂光标)
 * @param resetKey 消息身份;变化即重建(本仓传 `message.id`)
 * @param regexRules 用户正则规则(T08 `RegexProcessor`);未接线时为空表
 */

/**
 * 追加式增量:[committed] 是已喂给流式状态的渲染文本。
 * 若 [rendered] 是它的**前缀追加**则返回新后缀(可为空串);否则返回 null(调用方重建)。
 * 拆成纯函数以便单测(库的 append 只重解析尾部,正确性取决于这个增量的正确性)。
 */
internal fun streamingDelta(committed: String, rendered: String): String? =
    if (rendered.startsWith(committed)) rendered.substring(committed.length) else null
@Composable
fun StreamingMarkdown(
    content: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    resetKey: Any? = Unit,
    regexRules: List<RegexRule> = emptyList(),
) {
    val rendered = remember(content, regexRules) { preRenderMarkdown(content, regexRules) }
    // 颜色/字体跟随双轨①(MaterialTheme)与 Ww 主题;此处只接 M3 默认即可随主题。
    val colors = markdownColor()
    // 正文跟随「界面字体大小」：字号与行高各取 body 档的数值 token。
    val typography = markdownTypography(
        text = LocalTextStyle.current.copy(
            fontSize = WwType.body,
            lineHeight = WwType.bodyLineHeight,
        ),
    )
    val caretInlineContent = rememberStreamCaretInlineContent(enabled = streaming)

    key(resetKey) {
        // generation:预处理发生「非前缀」变化时 +1 → 重建流式状态整段重解析。
        var generation by remember { mutableStateOf(0) }
        key(generation) {
            val streamState = rememberStreamingMarkdownState()
            // 已喂给流式状态的「渲染文本」;只 append 新增后缀(库内部据此只重解析稳定尾部)。
            var committed by remember { mutableStateOf("") }
            // 光标注释器要按「活」的正文判断末尾在哪 —— 见 StreamCaret 的注释。
            val annotator = rememberStreamCaretAnnotator(
                enabled = streaming,
                text = { committed },
                typography = typography,
                colors = colors,
                referenceLinkHandler = streamState.referenceLinkHandler,
            )

            LaunchedEffect(rendered) {
                val delta = streamingDelta(committed, rendered)
                if (delta == null) {
                    // 预处理把已渲染部分也改了(如工具块刚补全)→ 重建,不是每帧。
                    generation++
                } else if (delta.isNotEmpty()) {
                    streamState.append(delta)
                    committed = rendered
                }
            }

            Markdown(
                streamingMarkdownState = streamState,
                modifier = modifier.fillMaxWidth(),
                colors = colors,
                typography = typography,
                annotator = annotator,
                inlineContent = caretInlineContent,
            )
        }
    }
}
