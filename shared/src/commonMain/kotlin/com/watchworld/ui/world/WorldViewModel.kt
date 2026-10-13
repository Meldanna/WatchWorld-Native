package com.watchworld.ui.world

import com.watchworld.data.repository.ConfigRepository
import com.watchworld.domain.selfAndDescendantWorldGroupIds
import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup
import com.watchworld.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 世界观库（T18）的界面状态：嵌套分组树 + 文档。
 *
 * 对应 Web 版 `DocumentModal.tsx` 的**库形态**（侧边栏「世界观」入口）。窗口勾选形态
 * （`sessionDocIds` / `onToggleForSession`）归「本窗口配置」类任务，本类不含窗口上下文。
 */
data class WorldUiState(
    val loading: Boolean = true,
    val groups: List<WorldGroup> = emptyList(),
    val documents: List<WorldDocument> = emptyList(),
    val selectedDocumentId: String? = null,
    val query: String = "",
    /** 被收起来的分组 id。默认全展开，只有用户点过「折叠」的才在这里。 */
    val collapsedGroupIds: Set<String> = emptySet(),
    val error: String? = null,
) {
    val selectedDocument: WorldDocument? get() = documents.firstOrNull { it.id == selectedDocumentId }

    /** 搜索命中（标题 / 正文 / 标签）；空查询返回全部。对应 `KnowledgeBaseModal` 的 `filteredItems`。 */
    val filteredDocuments: List<WorldDocument>
        get() {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return documents
            return documents.filter { doc ->
                doc.title.lowercase().contains(needle) ||
                    doc.content.lowercase().contains(needle) ||
                    doc.tags.any { it.lowercase().contains(needle) }
            }
        }
}

/**
 * 世界观库的唯一读写入口。**UI 只拿它，不碰 DAO**（`MIGRATION_CONSTRAINTS.md` §5）。
 *
 * ### 为什么每个操作都「读全量 → 改 → 写全量」，还包在事务里
 *
 * `ConfigRepository.setWorldGroups` / `setWorldDocuments` 是**全量替换**
 * （对齐 Web 版 `Storage.setWorldGroups(整份数组)`），会删掉库里不在列表里的条目。
 * 所以「加一个分组」不能只 append；这段读改写必须包在 `ConfigRepository.transaction {}` 里，
 * 否则两次并发操作会互相吃掉对方的改动（Web 版靠单线程 UI 天然免了这个问题）。
 *
 * ### 删除分组是**连带删除**
 *
 * Web 版删分组时会连它的子分组、以及这些分组下的文档一起删。
 * 这里用 [selfAndDescendantWorldGroupIds] 求出整棵子树再一次性过滤，语义一致。
 *
 * @param now 生成 id 与 `updatedAt` 用的时间戳；测试传固定值以便可复现。
 */
class WorldViewModel(
    private val config: ConfigRepository,
    private val now: () -> Long = ::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(WorldUiState())
    val state: StateFlow<WorldUiState> = mutableState.asStateFlow()

    suspend fun load() {
        runCatching {
            mutableState.value = mutableState.value.copy(
                loading = false,
                groups = config.worldGroups(),
                documents = config.worldDocuments(),
                error = null,
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: error.toString())
        }
    }

    // ------------------------------------------------------------------ 视图状态（不落库）

    fun select(documentId: String?) {
        mutableState.value = mutableState.value.copy(
            selectedDocumentId = documentId?.takeIf { id -> mutableState.value.documents.any { it.id == id } },
        )
    }

    fun setQuery(text: String) {
        mutableState.value = mutableState.value.copy(query = text)
    }

    fun toggleCollapsed(groupId: String) {
        val current = mutableState.value.collapsedGroupIds
        mutableState.value = mutableState.value.copy(
            collapsedGroupIds = if (groupId in current) current - groupId else current + groupId,
        )
    }

    /** 该分组当前是否折叠。默认展开（`collapsedGroupIds` 是「例外集合」）。 */
    fun isCollapsed(groupId: String): Boolean = groupId in mutableState.value.collapsedGroupIds

    // ------------------------------------------------------------------ 分组

    /** 新建分组；`parentId` 为空 = 顶层大组。名字 trim 后为空返回 `null`（对应 Web 版 `!name.trim()` 早退）。 */
    suspend fun createGroup(name: String, parentId: String? = null): WorldGroup? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        // 父级必须真实存在，否则会造出一个永远不可见的孤儿子树（Web 版的下拉框只能选已存在的分组）。
        if (parentId != null && mutableState.value.groups.none { it.id == parentId }) return null
        val group = WorldGroup(id = "wg-${now()}", name = trimmed, parentId = parentId)
        val next = mutateGroups { it + group }
        mutableState.value = mutableState.value.copy(groups = next)
        return group
    }

    /** 改名；空名字忽略。 */
    suspend fun renameGroup(groupId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val next = mutateGroups { list -> list.map { if (it.id == groupId) it.copy(name = trimmed) else it } }
        mutableState.value = mutableState.value.copy(groups = next)
    }

    /**
     * 改父级（`null` = 移到顶层）。
     *
     * ⚠️ **拒绝成环**：不能把分组挂到它自己或它的任一后代下面。`selfAndDescendantWorldGroupIds`
     *    本身有环保护（不会栈溢出），这里再拦一道业务校验 —— 数据里真存进一个环，
     *    UI 的递归渲染会无限展开。
     */
    suspend fun setGroupParent(groupId: String, parentId: String?): Boolean {
        val groups = mutableState.value.groups
        if (groups.none { it.id == groupId }) return false
        if (parentId != null && groups.none { it.id == parentId }) return false
        if (parentId != null && parentId in selfAndDescendantWorldGroupIds(groups, groupId)) return false
        val next = mutateGroups { list -> list.map { if (it.id == groupId) it.copy(parentId = parentId) else it } }
        mutableState.value = mutableState.value.copy(groups = next)
        return true
    }

    /** 删除分组：连带整棵子树的分组与它们名下的文档（Web 版同一语义）。 */
    suspend fun deleteGroup(groupId: String) {
        val snapshot = mutableState.value
        if (snapshot.groups.none { it.id == groupId }) return
        val doomed = selfAndDescendantWorldGroupIds(snapshot.groups, groupId)
        var remainingGroups: List<WorldGroup> = snapshot.groups
        var remainingDocuments: List<WorldDocument> = snapshot.documents
        config.transaction {
            // ⚠️ 两个列表都要**从库里读**再过滤，不能用内存快照：
            //    `setWorldGroups`/`setWorldDocuments` 是全量替换（会删掉不在列表里的条目），
            //    拿内存快照写回会把别处（例如刚导入的存档）的改动一起抹掉。
            //    文档取「读接口给的对象」而不是快照里的：读接口已经跑过 group→groupId 迁移。
            val liveDocuments = config.worldDocuments()
            remainingGroups = config.worldGroups().filterNot { it.id in doomed }
            remainingDocuments = liveDocuments.filterNot { it.groupId != null && it.groupId in doomed }
            config.setWorldGroups(remainingGroups)
            config.setWorldDocuments(remainingDocuments)
        }
        mutableState.value = snapshot.copy(
            groups = remainingGroups,
            documents = remainingDocuments,
            selectedDocumentId = snapshot.selectedDocumentId?.takeIf { id -> remainingDocuments.any { it.id == id } },
        )
    }
    // ------------------------------------------------------------------ 文档

    /** 新建文档。空标题忽略（对应 `KnowledgeBaseModal` 的 `if (!title.trim()) return`）。 */
    suspend fun createDocument(title: String, groupId: String?, content: String): WorldDocument? {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return null
        val stamp = now()
        val doc = WorldDocument(
            id = "world-doc-$stamp",
            title = trimmed,
            groupId = groupId,
            content = content,
            enabled = true,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val next = mutateDocuments { it + doc }
        mutableState.value = mutableState.value.copy(documents = next, selectedDocumentId = doc.id)
        return doc
    }

    /** 保存文档（编辑表单的「保存」）。`updatedAt` 由这里盖，调用方不用管。 */
    suspend fun saveDocument(document: WorldDocument) {
        val next = mutateDocuments { list ->
            list.map { if (it.id == document.id) document.copy(updatedAt = now()) else it }
        }
        mutableState.value = mutableState.value.copy(documents = next)
    }

    suspend fun deleteDocument(documentId: String) {
        val next = mutateDocuments { list -> list.filterNot { it.id == documentId } }
        mutableState.value = mutableState.value.copy(
            documents = next,
            selectedDocumentId = mutableState.value.selectedDocumentId?.takeIf { it != documentId },
        )
    }

    /**
     * 文档的**全局**启用开关（`WorldDocument.enabled`）。
     *
     * ⚠️ 与「本窗口勾选」是两回事（见 `ContextBuilder.selectActiveWorldDocuments`）：
     *    没保存过窗口选择的窗口，默认带上全部 `enabled == true` 的文档 —— 所以这个开关决定「默认是否注入」。
     */
    suspend fun setDocumentEnabled(documentId: String, enabled: Boolean) {
        val next = mutateDocuments { list ->
            list.map { if (it.id == documentId) it.copy(enabled = enabled, updatedAt = now()) else it }
        }
        mutableState.value = mutableState.value.copy(documents = next)
    }

    /** 把文档移到另一个分组（`null` = 未分组）。目标分组必须存在。 */
    suspend fun moveDocumentToGroup(documentId: String, groupId: String?) {
        if (groupId != null && mutableState.value.groups.none { it.id == groupId }) return
        val next = mutateDocuments { list ->
            list.map { if (it.id == documentId) it.copy(groupId = groupId, updatedAt = now()) else it }
        }
        mutableState.value = mutableState.value.copy(documents = next)
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 读改写包在一个事务里（见类注释）。返回写完之后的权威列表，调用方据此更新 UI 状态。
     *
     * ⚠️ 读的是 `config.xxx()`（**不是**内存里的 `mutableState`）：库里才是权威，
     *    内存副本可能落后（例如别处刚导入过存档）。
     */
    private suspend fun mutateGroups(transform: (List<WorldGroup>) -> List<WorldGroup>): List<WorldGroup> {
        var result: List<WorldGroup> = mutableState.value.groups
        config.transaction {
            result = transform(config.worldGroups())
            config.setWorldGroups(result)
        }
        return result
    }

    private suspend fun mutateDocuments(transform: (List<WorldDocument>) -> List<WorldDocument>): List<WorldDocument> {
        var result: List<WorldDocument> = mutableState.value.documents
        config.transaction {
            // `config.worldDocuments()` 读的时候已跑过 group→groupId 迁移（`legacyGroup` 变 `null`），
            // 所以整份写回等于把迁移结果固化进库 —— 老 `group` 从此消失，不会复活。
            result = transform(config.worldDocuments())
            config.setWorldDocuments(result)
        }
        return result
    }
}


