package com.watchworld.ui.markdown

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownInlineContent
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownInlineContent
import com.watchworld.ui.theme.CssColors
import com.watchworld.ui.theme.WwTheme
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

/*
 * 流式打字光标（T13）。
 *
 * ## 为什么不能把光标写进正文
 * 库的流式状态是**追加式**的（`StreamingMarkdownState.append` 只接受后缀），而光标要
 * 「始终贴在最后一个字后面」—— 每来一个新字，光标都得往后挪一位，也就是**改动已提交的文本**。
 * 一旦非前缀追加，就得 `generation++` 重建整个状态整段重解析，正好把 PLAN §7.3 的
 * 「只重解析末块」毁掉。
 *
 * ## 所以走渲染期注入
 * 库 0.45 提供一对配套注入口，它自己渲染行内图片用的就是这条路：
 * - `MarkdownAnnotator`：逐个子节点被回调，`return true` 表示「这个节点我自己渲染」；
 * - `MarkdownInlineContent`：`id → InlineTextContent` 表，供 `AnnotatedString` 里的
 *   `appendInlineContent(id)` 占位查表渲染成**行内** composable。
 *
 * 本文件的做法是：注释器认出「文档末尾那个可见节点」，先用库的默认规则把它渲染完，
 * 再紧跟一个光标占位。于是光标是**行内**的（不另起一行），且完全不碰追加式流式状态。
 *
 * ## 已知边界
 * 1. **代码块里的末尾没有光标**：库的代码块走 `MarkdownBasicText(纯文本)`，不经过注释器。
 *    Web 版会把光标插进 `<pre>` 里；这里补它需要复制库的私有 `MarkdownCode`，不做（见任务文档「遗留」）。
 * 2. **末尾是硬换行（行尾两空格 + 换行）时光标落到下一行**：库把 `HARD_LINE_BREAK` 渲染成 `\n`。
 *    Web 版会先 `trimEnd()` 再插，所以贴在文字后。差别只在流式恰好停在硬换行上的那一瞬。
 * 3. `prefers-reduced-motion`（关掉闪烁）在 Compose MP 1.12 没有跨端对应物，未接。
 */

/** 光标在 inline content 表里的键；[rememberStreamCaretAnnotator] 与 [rememberStreamCaretInlineContent] 必须同源。 */
internal const val STREAM_CARET_ID = "ww-stream-caret"

/** 占位宽 = index.css `.stream-caret { width: 0.5rem }`（1rem = 16px）。 */
private val CARET_WIDTH = 8.dp

/** 占位高 = index.css `height: 1.05em`。 */
private val CARET_HEIGHT = 1.05.em

/** 闪烁周期 = index.css `animation: stream-caret-blink 0.8s steps(1, end) infinite`。 */
private const val CARET_BLINK_PERIOD_MS = 800

/**
 * 注册光标的行内占位。[enabled] 为 false 时给**空表** —— 此时注释器也不会追加占位，两边同源，
 * 不会出现「占了位却查不到内容」的空白缝。
 *
 * 用 `remember` 稳住实例：`InlineTextContent` 的 `equals` 认的是内部 composable lambda 的**实例**，
 * 每帧新建会让映射表每帧不等 → 读 `LocalMarkdownInlineContent` 的所有文本每帧失效。
 */
@Composable
internal fun rememberStreamCaretInlineContent(enabled: Boolean): MarkdownInlineContent {
    val density = LocalDensity.current
    val entries = remember(enabled, density) {
        if (!enabled) {
            emptyMap()
        } else {
            mapOf(
                STREAM_CARET_ID to InlineTextContent(
                    Placeholder(
                        width = with(density) { CARET_WIDTH.toSp() },
                        height = CARET_HEIGHT,
                        // index.css: vertical-align: text-bottom
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
                    ),
                    children = { StreamCaretBar() },
                ),
            )
        }
    }
    return markdownInlineContent(entries)
}

/**
 * 光标注释器：在 [isTrailingVisibleNode] 认出的那个节点之后追加光标占位。
 *
 * @param text 当前**已提交给库**的正文（渲染文本）。必须传「活」的值而不是注释器回调里的
 *   `content`：回调里那个字符串来自 `MarkdownComponentModel`，由 `remember(node, content, …)`
 *   缓存 —— 稳定块的模型**不会**随着流式增长而更新。若拿它判断「后面还有没有内容」，
 *   一个曾经在末尾的块会**永远**认为自己还在末尾，光标就卡死在段落中间。
 */
@Composable
internal fun rememberStreamCaretAnnotator(
    enabled: Boolean,
    text: () -> String,
    typography: MarkdownTypography,
    colors: MarkdownColors,
    referenceLinkHandler: ReferenceLinkHandler,
): MarkdownAnnotator {
    // `inlineCode.copy(background = colors.inlineCodeBackground).toSpanStyle()` 不能直接用扩展属性
    // `codeSpanStyle`（它是 @Composable 取 LocalMarkdownColors.current），所以手工重建这个 SpanStyle。
    val codeSpan = remember(typography, colors) {
        typography.inlineCode.copy(background = colors.inlineCodeBackground).toSpanStyle()
    }
    return remember(enabled, typography, codeSpan, referenceLinkHandler) {
        if (!enabled) {
            markdownAnnotator()
        } else {
            // 递归渲染「末尾子树」用它：里面的注释器是**默认**的，所以我们不会被自己再次回调
            // （否则末尾那个节点会被插两次光标）。链接交互传 null 即 Compose 默认（走 UriHandler）。
            val nested = DefaultAnnotatorSettings(
                linkTextSpanStyle = typography.textLink,
                codeSpanStyle = codeSpan,
                annotator = markdownAnnotator(),
                referenceLinkHandler = referenceLinkHandler,
                linkInteractionListener = null,
            )
            markdownAnnotator { _, child ->
                val live = text()
                if (!isTrailingVisibleNode(live, child)) {
                    false
                } else {
                    // 先按库的默认规则把这个子树渲染出来，再紧跟一个光标占位 —— 光标紧贴最后一个字。
                    buildMarkdownAnnotatedString(live, listOf(child), nested)
                    appendInlineContent(STREAM_CARET_ID, CARET_ALTERNATE_TEXT)
                    true
                }
            }
        }
    }
}

/** 查不到 [STREAM_CARET_ID] 时的兜底文本（零宽空格：装饰性元素，不该被读出来）。 */
private const val CARET_ALTERNATE_TEXT = "​"

/**
 * 「这个节点是不是文档末尾那个可见节点」—— 是的话光标就挂在它后面。
 *
 * 判据两条：
 * 1. 它是父节点里**最后一个会渲染出东西的子节点**（跳过 `EOL` / `WHITE_SPACE` 这类纯排版 token）；
 * 2. 它之后只剩空白字符 —— 后面若还有要渲染的内容，必然要覆盖某个非空白字符，反之亦然。
 *
 * 为什么第 1 条要「跳过 EOL/空白」而不是「最后一个子节点」：多行段落的子节点形如
 * `[TEXT, EOL]`，两者之后都只剩空白 → 会**双双命中**，末尾被插两个光标。
 * 跳过 EOL 后只剩 `TEXT` 一个候选，且光标紧贴最后一个字（而不是它后面那个空格）。
 *
 * 纯函数，可直接拿 `MarkdownParser` 解析出的真实 AST 单测（见 `StreamCaretMarkingTest`）。
 */
internal fun isTrailingVisibleNode(text: String, child: ASTNode): Boolean {
    val parent = child.parent ?: return false
    val lastVisible = parent.children.lastOrNull {
        it.type != MarkdownTokenTypes.EOL && it.type != MarkdownTokenTypes.WHITE_SPACE
    }
    if (lastVisible !== child) return false

    val end = child.endOffset
    if (end > text.length) return false
    for (index in end until text.length) {
        if (!text[index].isWhitespace()) return false
    }
    return true
}

/**
 * 光标本体：一条竖条，硬切闪烁（对应 CSS `steps(1, end)`：前半周期实心、后半透明）。
 *
 * 淡入淡出与硬切观感差别明显，所以这里不用 `RepeatMode.Reverse`，而是在 `graphicsLayer`
 * 的 lambda 里读动画值 —— 读数发生在**绘制**阶段，闪烁只触发重绘，不触发重组。
 */
@Composable
private fun StreamCaretBar() {
    // index.css: background-color: var(--m1-500)
    val color = caretColor()
    val transition = rememberInfiniteTransition(label = "ww-stream-caret")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CARET_BLINK_PERIOD_MS, easing = LinearEasing),
        ),
        label = "ww-stream-caret-blink",
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (phase.value < 0.5f) 1f else 0f }
            .background(color),
    )
}

/** m1-500 的实色；`WwColors.m1Soft` 就是它加了 15% alpha，所以去 alpha 即得。 */
@Composable
private fun caretColor(): Color {
    val colors = WwTheme.colors
    return remember(colors) {
        CssColors.parse(colors.vars.m1[5]) ?: colors.primaryBg
    }
}
