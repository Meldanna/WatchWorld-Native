package com.watchworld.ui.markdown

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T13 流式光标末尾判定：用库的真实 GFM AST 验证，不手造节点。 */
class StreamCaretMarkingTest {

    private val parser = MarkdownParser(
        flavour = GFMFlavourDescriptor(),
        cancellationToken = object : CancellationToken {
            override fun checkCancelled() = Unit
        },
    )

    @Test
    fun plainParagraphMarksOnlyLastVisibleNode() {
        val content = "第一段\n\n最后一个字"
        val marked = trailingNodes(content)

        assertEquals(1, marked.size)
        assertEquals("最后一个字", content.substring(marked.single().startOffset, marked.single().endOffset))
    }

    @Test
    fun trailingWhitespaceDoesNotMoveCaretAwayFromLastText() {
        val content = "正文末尾   \n"
        val marked = trailingNodes(content)

        assertEquals(1, marked.size)
        val tail = marked.single()
        assertEquals("正文末尾", content.substring(tail.startOffset, tail.endOffset).trimEnd())
        assertTrue(content.substring(tail.endOffset).all(Char::isWhitespace))
    }

    @Test
    fun earlierParagraphIsNotMistakenForDocumentTail() {
        val content = "旧段落\n\n新段落"
        val all = flatten(parser.buildMarkdownTreeFromString(content as CharSequence))
        val oldText = all.first { node ->
            node.endOffset <= content.indexOf("\n\n") &&
                content.substring(node.startOffset, node.endOffset) == "旧段落"
        }

        assertFalse(isTrailingVisibleNode(content, oldText))
        assertTrue(trailingNodes(content).isNotEmpty())
    }

    @Test
    fun incompleteInlineMarkerStillHasSingleTailNode() {
        val content = "半截粗体 **bo"
        val marked = trailingNodes(content)

        assertEquals(1, marked.size, "未闭合行内标记不能产生多个光标落点")
        assertTrue(marked.single().endOffset <= content.length)
    }

    private fun trailingNodes(content: String): List<ASTNode> {
        val candidates = flatten(parser.buildMarkdownTreeFromString(content as CharSequence))
            .filter { isTrailingVisibleNode(content, it) }

        // Annotator 遇到外层候选后会 return true 并消费整棵子树，内部候选不会再次回调。
        // 因此测试只保留「没有候选祖先」的实际落点，模拟真实注释器执行顺序。
        return candidates.filter { candidate ->
            candidates.none { ancestor -> ancestor !== candidate && isAncestorOf(ancestor, candidate) }
        }
    }

    private fun isAncestorOf(ancestor: ASTNode, node: ASTNode): Boolean {
        var current = node.parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun flatten(root: ASTNode): List<ASTNode> = buildList {
        fun visit(node: ASTNode) {
            add(node)
            node.children.forEach(::visit)
        }
        visit(root)
    }
}
