package com.watchworld.ui.world

import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 世界观左栏树的**行构造**体检（`buildWorldTreeRows`）。
 *
 * 这几条都踩过或极易踩：
 * - 组内顺序必须是「子分组 → 本文档」（参考版 `renderGroup` 就是这个顺序）；
 * - 收起只跳过子树，**不跳过分组自己**，否则用户点开就回不来了；
 * - 挂在不存在父级下的孤儿分组必须露出来，否则永远看不到也删不掉；
 * - 老存档里的环**不能**把界面递归到卡死；
 * - 搜索时「子树里一句都没命中」的分组整组隐藏，但命中的文档要留下；
 * - 有未分组文档时才有「未分组」伪分组。
 */
class WorldTreeRowsTest {

    private fun group(id: String, name: String = id, parentId: String? = null) =
        WorldGroup(id = id, name = name, parentId = parentId)

    private fun doc(id: String, title: String, groupId: String? = null, content: String = "") =
        WorldDocument(id = id, title = title, groupId = groupId, content = content)

    private fun label(row: WorldTreeRow): String = when (row) {
        is WorldTreeRow.GroupNode -> "G:${row.group?.id ?: UNGROUPED_WORLD_KEY}@${row.depth}"
        is WorldTreeRow.DocNode -> "D:${row.doc.id}@${row.depth}"
    }

    @Test
    fun childGroupsComeBeforeOwnDocuments() {
        val rows = buildWorldTreeRows(
            groups = listOf(group("a"), group("a1", parentId = "a")),
            documents = listOf(doc("d-a", "A 的文档", "a"), doc("d-a1", "A1 的文档", "a1")),
            collapsedKeys = emptySet(),
        )

        assertEquals(
            listOf("G:a@0", "G:a1@1", "D:d-a1@2", "D:d-a@1"),
            rows.map(::label),
        )
    }

    @Test
    fun collapsingSkipsSubtreeButKeepsTheGroupItself() {
        val rows = buildWorldTreeRows(
            groups = listOf(group("a"), group("a1", parentId = "a")),
            documents = listOf(doc("d-a", "A 的文档", "a"), doc("d-a1", "A1 的文档", "a1")),
            collapsedKeys = setOf("a"),
        )

        assertEquals(listOf("G:a@0"), rows.map(::label))
    }

    @Test
    fun orphanGroupsAndCyclesDoNotHangOrDisappear() {
        val rows = buildWorldTreeRows(
            groups = listOf(
                // 父级不存在 → 必须作为顶层露出来
                group("orphan", parentId = "ghost"),
                // 环：x → y → x，两边都不可达顶层
                group("x", parentId = "y"),
                group("y", parentId = "x"),
            ),
            documents = emptyList(),
            collapsedKeys = emptySet(),
        )

        val ids = rows.map(::label)
        assertTrue("G:orphan@0" in ids, "孤儿分组要露出来，实际：$ids")
        // 成环的两个分组必须都出现（哪个在上层由遍历顺序决定），且不死循环。
        assertTrue(ids.any { it.startsWith("G:x@") }, "环里的 x 要出现，实际：$ids")
        assertTrue(ids.any { it.startsWith("G:y@") }, "环里的 y 要出现，实际：$ids")
    }

    @Test
    fun searchHidesGroupsWithoutHitsAndKeepsMatchingDocs() {
        val rows = buildWorldTreeRows(
            groups = listOf(group("a"), group("b"), group("empty")),
            documents = listOf(
                doc("d1", "要塞设定", "a"),
                doc("d2", "学园设定", "b"),
            ),
            collapsedKeys = emptySet(),
            query = "要塞",
        )

        val ids = rows.map(::label)
        assertEquals(listOf("G:a@0", "D:d1@1"), ids, "只有命中的组与文档留下，空组在搜索态下隐藏")
    }

    @Test
    fun searchMatchesBodyTooAndKeepsUnrelatedGroupsOut() {
        val rows = buildWorldTreeRows(
            groups = listOf(group("a")),
            documents = listOf(
                doc("d1", "标题不命中", "a", content = "正文里有 13812345678 这串数字"),
                doc("d2", "另一篇", "a", content = "无关内容"),
            ),
            collapsedKeys = emptySet(),
            query = "13812345678",
        )

        assertEquals(listOf("G:a@0", "D:d1@1"), rows.map(::label))
    }

    @Test
    fun ungroupedPseudoGroupAppearsOnlyWhenNeeded() {
        val withUngrouped = buildWorldTreeRows(
            groups = listOf(group("a")),
            documents = listOf(doc("d1", "散篇")),
            collapsedKeys = emptySet(),
        )
        assertEquals(listOf("G:a@0", "G:${UNGROUPED_WORLD_KEY}@0", "D:d1@1"), withUngrouped.map(::label))

        val without = buildWorldTreeRows(
            groups = listOf(group("a")),
            documents = listOf(doc("d1", "归组篇", "a")),
            collapsedKeys = emptySet(),
        )
        assertEquals(listOf("G:a@0", "D:d1@1"), without.map(::label))
    }

    @Test
    fun groupDocCountIncludesDescendants() {
        val groups = listOf(group("a"), group("a1", parentId = "a"))
        val documents = listOf(doc("d1", "一", "a"), doc("d2", "二", "a1"))

        val rows = buildWorldTreeRows(groups, documents, emptySet())
        val rootCount = rows.filterIsInstance<WorldTreeRow.GroupNode>().first { it.group?.id == "a" }.docCount

        assertEquals(2, rootCount, "父分组的计数要含后代分组里的文档")
    }
}
