package com.watchworld.ui.world

import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ArchiveStore
import com.watchworld.data.repository.ConfigRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T18 验收：[WorldViewModel] 的嵌套分组树与文档 CRUD。
 *
 * 所有写操作经 `ConfigRepository` → SQLite（in-memory）；读回时状态必须一致，
 * 且关库重开后仍在（用新的 ViewModel 复读来验证）。
 */
class WorldViewModelTest {

    private companion object {
        var clock = 1_800_000_000_000L
        fun tick(): Long {
            clock += 1_000
            return clock
        }
    }

    private fun withVm(block: suspend (WorldViewModel, WwDatabase) -> Unit) {
        val db = WwDatabase.openInMemory()
        try {
            runBlocking { block(WorldViewModel(ConfigRepository(db)) { tick() }, db) }
        } finally {
            db.close()
        }
    }

    /** 换一个 ViewModel 复读，验证真的落库了。 */
    private suspend fun reload(db: WwDatabase): WorldViewModel =
        WorldViewModel(ConfigRepository(db)) { tick() }.also { it.load() }

    // ---------------------------------------------------------------- 分组

    @Test
    fun createGroupPersistsAndSupportsNesting() = withVm { vm, db ->
        vm.load()
        val root = assertNotNull(vm.createGroup("  主世界观  "))
        val child = assertNotNull(vm.createGroup("地理", parentId = root.id))

        assertEquals("主世界观", root.name, "名字要 trim")
        assertEquals(root.id, child.parentId)
        assertEquals(listOf(root.id, child.id), vm.state.value.groups.map { it.id })
        assertEquals(vm.state.value.groups, reload(db).state.value.groups)
    }

    @Test
    fun blankGroupNameOrUnknownParentIsRejected() = withVm { vm, _ ->
        vm.load()
        assertNull(vm.createGroup("   "))
        assertNull(vm.createGroup("孤儿", parentId = "wg-does-not-exist"))
        assertTrue(vm.state.value.groups.isEmpty())
    }

    /**
     * 反证用例：把父级设成**自己的后代**必须被拒绝。
     * 若去掉环判断、只留环保护，这条会「成功」并把 A.parent 改成 C，形成 A ↔ B ↔ C 的环。
     */
    @Test
    fun settingParentToOwnDescendantIsRejected() = withVm { vm, db ->
        vm.load()
        val a = assertNotNull(vm.createGroup("A"))
        val b = assertNotNull(vm.createGroup("B", parentId = a.id))
        val c = assertNotNull(vm.createGroup("C", parentId = b.id))

        assertFalse(vm.setGroupParent(a.id, c.id), "不能把 A 挂到它的后代 C 下面")
        assertFalse(vm.setGroupParent(a.id, a.id), "不能把 A 挂到自己下面")
        assertNull(reload(db).state.value.groups.first { it.id == a.id }.parentId)
    }

    @Test
    fun settingParentToTopLevelOrAnotherBranchWorks() = withVm { vm, _ ->
        vm.load()
        val a = assertNotNull(vm.createGroup("A"))
        val b = assertNotNull(vm.createGroup("B"))
        val child = assertNotNull(vm.createGroup("A1", parentId = a.id))

        assertTrue(vm.setGroupParent(child.id, b.id))
        assertEquals(b.id, vm.state.value.groups.first { it.id == child.id }.parentId)

        assertTrue(vm.setGroupParent(child.id, null), "null = 移到顶层")
        assertNull(vm.state.value.groups.first { it.id == child.id }.parentId)
    }

    /** 删除分组连带整棵子树的分组 + 这些分组下的文档；未分组与其它分支的文档不受影响。 */
    @Test
    fun deleteGroupCascadesToSubtreeAndItsDocuments() = withVm { vm, db ->
        vm.load()
        val root = assertNotNull(vm.createGroup("根"))
        val child = assertNotNull(vm.createGroup("子", parentId = root.id))
        val grandChild = assertNotNull(vm.createGroup("孙", parentId = child.id))
        val other = assertNotNull(vm.createGroup("旁支"))

        assertNotNull(vm.createDocument("根文档", root.id, "a"))
        assertNotNull(vm.createDocument("孙文档", grandChild.id, "b"))
        assertNotNull(vm.createDocument("旁支文档", other.id, "c"))
        assertNotNull(vm.createDocument("未分组文档", null, "d"))

        vm.deleteGroup(child.id)

        assertEquals(listOf(root.id, other.id), vm.state.value.groups.map { it.id })
        // ⚠️ 根文档属于 `root`，只在删 `child` 时**不该**被删；被删的是 child 子树里的「孙文档」。
        assertEquals(listOf("根文档", "旁支文档", "未分组文档"), vm.state.value.documents.map { it.title })
        assertTrue(vm.state.value.documents.none { it.groupId in setOf(child.id, grandChild.id) })

        val reread = reload(db)
        assertEquals(vm.state.value.groups, reread.state.value.groups)
        assertEquals(vm.state.value.documents, reread.state.value.documents)
    }

    @Test
    fun renameGroupTrimsAndIgnoresBlank() = withVm { vm, _ ->
        vm.load()
        val g = assertNotNull(vm.createGroup("旧名"))
        vm.renameGroup(g.id, "  新名  ")
        assertEquals("新名", vm.state.value.groups.single().name)
        vm.renameGroup(g.id, "   ")
        assertEquals("新名", vm.state.value.groups.single().name, "空名字忽略")
    }

    // ---------------------------------------------------------------- 文档

    @Test
    fun createAndSaveDocumentStampsUpdatedAt() = withVm { vm, db ->
        vm.load()
        val group = assertNotNull(vm.createGroup("组"))
        val created = assertNotNull(vm.createDocument("  标题  ", group.id, "内容"))
        assertEquals("标题", created.title, "标题要 trim")
        assertEquals(true, created.enabled, "新建文档默认全局启用")
        assertEquals(created.createdAt, created.updatedAt)
        assertEquals(created.id, vm.state.value.selectedDocumentId, "新建后自动选中")

        vm.saveDocument(created.copy(title = "改过", content = "新内容"))
        val saved = assertNotNull(vm.state.value.documents.singleOrNull())
        assertEquals("改过", saved.title)
        assertTrue(saved.updatedAt > created.updatedAt, "保存必须盖 updatedAt")

        assertEquals(vm.state.value.documents, reload(db).state.value.documents)
    }

    @Test
    fun blankDocumentTitleIsRejected() = withVm { vm, _ ->
        vm.load()
        assertNull(vm.createDocument("   ", null, "内容"))
        assertTrue(vm.state.value.documents.isEmpty())
    }

    @Test
    fun deleteDocumentClearsSelection() = withVm { vm, _ ->
        vm.load()
        val doc = assertNotNull(vm.createDocument("文档", null, "内容"))
        vm.deleteDocument(doc.id)
        assertTrue(vm.state.value.documents.isEmpty())
        assertNull(vm.state.value.selectedDocumentId)
    }

    @Test
    fun toggleEnabledAndMoveBetweenGroups() = withVm { vm, _ ->
        vm.load()
        val g1 = assertNotNull(vm.createGroup("组一"))
        val g2 = assertNotNull(vm.createGroup("组二"))
        val doc = assertNotNull(vm.createDocument("文档", g1.id, "内容"))

        vm.setDocumentEnabled(doc.id, false)
        assertEquals(false, vm.state.value.documents.single().enabled)

        vm.moveDocumentToGroup(doc.id, g2.id)
        assertEquals(g2.id, vm.state.value.documents.single().groupId)

        vm.moveDocumentToGroup(doc.id, null)
        assertNull(vm.state.value.documents.single().groupId)

        // 目标分组不存在时**忽略**（不得顺手清成未分组）。
        vm.moveDocumentToGroup(doc.id, g1.id)
        vm.moveDocumentToGroup(doc.id, "wg-does-not-exist")
        assertEquals(g1.id, vm.state.value.documents.single().groupId)
    }

    // ---------------------------------------------------------------- 视图状态

    @Test
    fun searchAndCollapseAreViewOnlyState() = withVm { vm, db ->
        vm.load()
        val group = assertNotNull(vm.createGroup("学园"))
        assertNotNull(vm.createDocument("剑术部", group.id, "社团活动"))
        assertNotNull(vm.createDocument("魔法课", group.id, "课程"))

        vm.setQuery("剑")
        assertEquals(listOf("剑术部"), vm.state.value.filteredDocuments.map { it.title })
        vm.setQuery("课程")
        assertEquals(listOf("魔法课"), vm.state.value.filteredDocuments.map { it.title }, "正文也应被搜索")
        vm.setQuery("")
        assertEquals(2, vm.state.value.filteredDocuments.size)

        assertFalse(vm.isCollapsed(group.id), "默认展开")
        vm.toggleCollapsed(group.id)
        assertTrue(vm.isCollapsed(group.id))
        vm.toggleCollapsed(group.id)
        assertFalse(vm.isCollapsed(group.id))

        // 折叠是纯视图状态，不该落库。
        assertTrue(reload(db).state.value.collapsedGroupIds.isEmpty())
    }

    @Test
    fun selectingUnknownDocumentClearsSelection() = withVm { vm, _ ->
        vm.load()
        val doc = assertNotNull(vm.createDocument("文档", null, "内容"))
        vm.select(doc.id)
        assertEquals(doc.id, vm.state.value.selectedDocumentId)
        vm.select("doc-does-not-exist")
        assertNull(vm.state.value.selectedDocumentId)
    }

    /**
     * 导入老存档（文档用 `group` 组名字符串）后，读出来必须是**已归组**的。
     * 若 Mapper 的 `toEntity` 丢掉 `legacyGroup`、或读接口没跑迁移，这条就会失败。
     */
    @Test
    fun importedLegacyDocumentsShowUpAlreadyGrouped() = withVm { vm, db ->
        val store = ArchiveStore(db) { tick() }
        assertTrue(
            runBlocking {
                store.importJson(
                    """{"version":5,"worldGroups":[{"id":"g-1","name":"主世界观"}],
                       "worldDocuments":[{"id":"d1","title":"旧文档","group":"主世界观","content":"旧"}]}""",
                )
            },
        )
        vm.load()
        assertEquals(listOf("g-1"), vm.state.value.documents.map { it.groupId })
        assertNull(vm.state.value.documents.single().legacyGroup)
    }

    /** 文档与分组的顺序在多次写操作后保持（`sortIndex` 生效）。 */
    @Test
    fun orderIsPreservedAcrossMutations() = withVm { vm, _ ->
        vm.load()
        assertNotNull(vm.createGroup("A"))
        assertNotNull(vm.createGroup("B"))
        assertNotNull(vm.createGroup("C"))
        val a = vm.state.value.groups.first { it.name == "A" }
        vm.renameGroup(a.id, "A2")
        assertEquals(listOf("A2", "B", "C"), vm.state.value.groups.map { it.name })

        assertNotNull(vm.createDocument("d1", null, "1"))
        assertNotNull(vm.createDocument("d2", null, "2"))
        vm.setDocumentEnabled(vm.state.value.documents.first().id, false)
        assertEquals(listOf("d1", "d2"), vm.state.value.documents.map { it.title })
    }
}
