package com.watchworld.domain

import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T18 验收：世界观分组的「等价翻译」与树操作纯函数。
 *
 * 规格书：`src/lib/storage.ts:360-373`（`group` → `groupId` 迁移）与
 * `src/components/DocumentModal.tsx`（分组树 / 后代集合 / 窗口勾选）。
 */
class WorldLibraryTest {

    private val groups = listOf(
        WorldGroup(id = "g-1", name = "主世界观"),
        WorldGroup(id = "g-2", name = "主世界观·地理", parentId = "g-1"),
        WorldGroup(id = "g-3", name = "第二条世界观"),
    )

    private fun doc(id: String, groupId: String? = null, legacy: String? = null) =
        WorldDocument(id = id, title = id, groupId = groupId, legacyGroup = legacy)

    // ---------------------------------------------------------------- group → groupId 迁移

    /** 已经是新形状（有 `groupId`）→ 原样返回，`group` 也不参与。 */
    @Test
    fun documentsWithGroupIdAreReturnedUntouched() {
        val input = listOf(doc("d1", groupId = "g-2", legacy = "主世界观"))
        val out = resolveWorldDocumentGroupIds(input, groups)
        assertEquals("g-2", out.single().groupId, "groupId 已经存在时不得被 group 覆盖")
        assertEquals(input, out)
    }

    /** `groupId` 为空 + 有 `group` 名字 → 命中同名分组，拿到它的 id。 */
    @Test
    fun legacyGroupNameIsResolvedToGroupId() {
        val out = resolveWorldDocumentGroupIds(listOf(doc("d1", legacy = "主世界观·地理")), groups)
        assertEquals("g-2", out.single().groupId)
        assertNull(out.single().legacyGroup, "迁移后 group 键必须消失（TS 的解构丢弃）")
    }

    /**
     * 反证用例：`group` 有值但**查无此名** → `groupId` 被显式写成 `undefined`（= Kotlin 的 `null`），
     * 文档变成「未分组」。**不是**「保持原样」。
     * 这两种实现只在「文档本来就没分组」时结果相同，构造一个「groupId 里躺着旧值」的输入就能区分。
     */
    @Test
    fun unknownLegacyGroupNameClearsGroupIdInsteadOfKeepingIt() {
        val out = resolveWorldDocumentGroupIds(listOf(doc("d1", legacy = "不存在的分组")), groups)
        assertNull(out.single().groupId, "命中不到时必须落成未分组，而不是保留旧 groupId")
        assertNull(out.single().legacyGroup)
    }

    /** 没有任何分组信息 → 原样返回（TS 的 `!d.group` 早退分支）。 */
    @Test
    fun documentsWithoutAnyGroupInfoAreUntouched() {
        val input = listOf(doc("d1"), doc("d2", groupId = null, legacy = null))
        val out = resolveWorldDocumentGroupIds(input, groups)
        assertEquals(input, out)
    }

    /** 空 `group` 字符串走早退分支，不会被当成「要迁移的名字」（TS 里 `!''` 为真）。 */
    @Test
    fun emptyLegacyGroupNameIsTreatedAsAbsent() {
        val input = listOf(doc("d1", legacy = ""))
        assertEquals(input, resolveWorldDocumentGroupIds(input, groups))
    }

    /** 同名分组有多个时取**第一个**（TS 的 `groups.find` 是首个命中，不是最后一个）。 */
    @Test
    fun firstMatchingGroupWins() {
        val duplicated = groups + WorldGroup(id = "g-9", name = "主世界观")
        val out = resolveWorldDocumentGroupIds(listOf(doc("d1", legacy = "主世界观")), duplicated)
        assertEquals("g-1", out.single().groupId)
    }

    /** 顺序与长度原样保留 —— 存档往返靠它。 */
    @Test
    fun migrationPreservesOrderAndSize() {
        val input = listOf(
            doc("d1", legacy = "主世界观"),
            doc("d2"),
            doc("d3", groupId = "g-3"),
            doc("d4", legacy = "不存在的分组"),
        )
        val out = resolveWorldDocumentGroupIds(input, groups)
        assertEquals(listOf("d1", "d2", "d3", "d4"), out.map { it.id })
        assertEquals(listOf("g-1", null, "g-3", null), out.map { it.groupId })
    }

    // ---------------------------------------------------------------- 分组树

    @Test
    fun childGroupsKeepInputOrder() {
        assertEquals(listOf("g-1", "g-3"), childWorldGroups(groups, null).map { it.id })
        assertEquals(listOf("g-2"), childWorldGroups(groups, "g-1").map { it.id })
        assertTrue(childWorldGroups(groups, "g-2").isEmpty())
    }

    @Test
    fun descendantIdsIncludeSelfAndGrandChildren() {
        val deep = groups + WorldGroup(id = "g-4", name = "更深", parentId = "g-2")
        assertEquals(setOf("g-1", "g-2", "g-4"), selfAndDescendantWorldGroupIds(deep, "g-1"))
    }

    /**
     * 环保护（判别性）：A ↔ B 互为父级时不得死循环/栈溢出。
     * 去掉 `guard` 这条用例会以 `StackOverflowError` 结束。
     */
    @Test
    fun cyclicParentsDoNotHangOrOverflow() {
        val cyclic = listOf(
            WorldGroup(id = "a", name = "A", parentId = "b"),
            WorldGroup(id = "b", name = "B", parentId = "a"),
        )
        assertEquals(setOf("a", "b"), selfAndDescendantWorldGroupIds(cyclic, "a"))
    }

    /** 某分组的文档 = 本组 + 所有后代组的文档；未分组文档不属于任何分组。 */
    @Test
    fun documentsInGroupTreeIncludeDescendantsOnly() {
        val docs = listOf(
            doc("d-root", groupId = "g-1"),
            doc("d-child", groupId = "g-2"),
            doc("d-other", groupId = "g-3"),
            doc("d-none"),
        )
        assertEquals(listOf("d-root", "d-child"), worldDocumentsInGroupTree(docs, groups, "g-1").map { it.id })
        assertEquals(listOf("d-child"), worldDocumentsInGroupTree(docs, groups, "g-2").map { it.id })
        assertTrue(worldDocumentsInGroupTree(docs, groups, "g-does-not-exist").isEmpty())
    }

    // ---------------------------------------------------------------- 窗口勾选

    /** 没保存过窗口选择（`null`）→ 默认带上**全部已启用**文档。 */
    @Test
    fun nullSessionSelectionFallsBackToGloballyEnabledDocuments() {
        val docs = listOf(
            doc("on").copy(enabled = true),
            doc("off").copy(enabled = false),
            doc("unset"),
        )
        assertEquals(listOf("on"), selectSessionWorldDocuments(docs, null).map { it.id })
    }

    /** 保存过窗口选择 → 勾选即唯一判据，**不看** 全局 `enabled`（与知识库不对称）。 */
    @Test
    fun explicitSessionSelectionIgnoresGlobalEnabledFlag() {
        val docs = listOf(
            doc("a").copy(enabled = false),
            doc("b").copy(enabled = true),
        )
        assertEquals(listOf("a"), selectSessionWorldDocuments(docs, listOf("a")).map { it.id })
        assertTrue(selectSessionWorldDocuments(docs, emptyList()).isEmpty())
    }
}
