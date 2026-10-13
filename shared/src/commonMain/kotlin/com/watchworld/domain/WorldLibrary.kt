package com.watchworld.domain

import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup

/*
 * 世界观 / 知识库的领域逻辑（T18）。规格书：
 *  - `src/lib/storage.ts:360-373` `getWorldDocuments` 的旧数据迁移（`group` 组名字符串 → `groupId` 组 ID）；
 *  - `src/components/DocumentModal.tsx` 的分组树「后代集合」判定（`:113-…` `descendantDocIds` /
 *    `selfAndDescendantGroupIds`），以及「窗口形态 = 勾选 + 与全局 enabled 取交集」的语义。
 *
 * ⚠️ 这三个函数都是**纯函数**：不读库、不改传入列表（返回新列表）。Web 版把它们散在
 *    `Storage` 与组件的 `useMemo` 里，原生版集中到这里，避免 UI 与存储层各写一份。
 */

/** 未分组文档在 UI 里的哨兵分组 ID（`DocumentModal.tsx:44` 的 `UNGROUPED`）。 */
const val UNGROUPED_WORLD_GROUP_ID: String = "__ungrouped__"

/**
 * `storage.ts:360-373`：旧数据迁移，把文档的 `group`（**组名字符串**）解析成 `groupId`（组 ID）。
 *
 * TS 的三条分支逐行对应：
 * ```ts
 * if (d.groupId !== undefined || !d.group) return d;   // ← 已经是新形状 / 没有 group：原样返回
 * const match = groups.find((g) => g.name === d.group);
 * const { group: _legacy, ...rest } = d;               // ← 无论命中与否，group 键都必须消失
 * return { ...rest, groupId: match?.id };              // ← 命中不到时是 undefined（= Kotlin 的 null）
 * ```
 *
 * ⚠️ **命中不到不等于「保持原样」**：`groupId` 会被**显式写成 `undefined`**，
 *    在 `explicitNulls = false` 下等于「键消失」，也就是文档变成**未分组**。
 *    所以本函数在「有 group、但查无此名」时必须返回 `groupId = null`，不能返回 `doc`。
 * ⚠️ `groups.find` 是**按名字线性首个命中**，不是按 id，也不做大小写归一。
 * ⚠️ 「`groupId` 显式是 `null`」在 TS 里 `d.groupId !== undefined` 成立 → **不迁移**。
 *    Kotlin 侧模型层拿不到「键不存在」与「键是 null」的差别（`MIGRATION_CONSTRAINTS.md` §1.3），
 *    因此判据只能是 `doc.groupId == null`：两种输入都走迁移分支，结果与 TS 在
 *    「`group` 与 `groupId` 同时出现」这种自相矛盾的数据上可能不同。真实数据不会这样。
 *
 * @return 与入参等长的新列表；元素顺序原样保留。
 */
fun resolveWorldDocumentGroupIds(
    documents: List<WorldDocument>,
    groups: List<WorldGroup>,
): List<WorldDocument> = documents.map { doc ->
    val legacyGroupName = doc.legacyGroup
    if (doc.groupId != null || legacyGroupName.isNullOrEmpty()) {
        doc
    } else {
        val matched = groups.firstOrNull { it.name == legacyGroupName }
        WorldDocument(
            id = doc.id,
            title = doc.title,
            groupId = matched?.id,
            content = doc.content,
            enabled = doc.enabled,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            tags = doc.tags,
        )
    }
}

/** 直接子分组（`DocumentModal.tsx` 的 `childrenOf`），保持入参顺序。 */
fun childWorldGroups(groups: List<WorldGroup>, parentId: String?): List<WorldGroup> {
    val key = parentId.orEmpty()
    return groups.filter { it.parentId.orEmpty() == key }
}

/**
 * 某分组及其全部后代的 id 集合（`DocumentModal.tsx` 的 `selfAndDescendantGroupIds`）。
 *
 * ⚠️ **必须带环保护**：父级是可以被用户改成任意分组的，数据里一旦出现环
 * （A.parent = B、B.parent = A），没有 guard 的递归会栈溢出把界面打崩。
 * 与 `TimelineMemory.rearrangeMessagesForAi` 的 `depthOf` 同一手法。
 */
fun selfAndDescendantWorldGroupIds(groups: List<WorldGroup>, rootId: String): Set<String> {
    val result = mutableSetOf<String>()
    val guard = mutableSetOf<String>()
    fun walk(id: String) {
        if (id.isEmpty() || !guard.add(id)) return
        result += id
        childWorldGroups(groups, id).forEach { walk(it.id) }
    }
    walk(rootId)
    return result
}

/**
 * 某分组（含后代分组）下的**全部文档**（`DocumentModal.tsx` 的 `descendantDocIds`）。
 *
 * ⚠️ 后代分组里也有 `DocumentModal` 的挂载点：父级分组的文档列表是
 *    「本组文档 + 所有后代组文档」的合集，不是只算直接子级。
 */
fun worldDocumentsInGroupTree(
    documents: List<WorldDocument>,
    groups: List<WorldGroup>,
    groupId: String,
): List<WorldDocument> {
    val ids = selfAndDescendantWorldGroupIds(groups, groupId)
    return documents.filter { it.groupId != null && it.groupId in ids }
}

/**
 * 窗口勾选 ∩ 全局启用（`App.tsx` 的 `activeWorldDocuments` 语义，见
 * `ContextBuilder.selectActiveWorldDocuments` 的注释）。
 *
 * ⚠️ 与知识库**不对称**，不要「顺手统一」：
 *  - 知识库在 `selectConnectedKnowledge` 里要求 `it.enabled` **且** 在本窗口的 `connectedKnowledgeIds` 里；
 *  - 世界观这里**不看 `enabled`** —— 窗口的勾选就是唯一开关，`WorldDocument.enabled`
 *    （`types.ts:343`，`boolean?`）在 Web 版只用于**默认归属**：
 *    「没保存过窗口选择（`connectedWorldDocIds` 为 `null`）时，默认带上全部 `enabled` 文档」。
 *    保存过之后，用户取消勾选即为关闭，全局 `enabled` 不再参与判定。
 */
fun selectSessionWorldDocuments(
    worldDocuments: List<WorldDocument>,
    connectedWorldDocIds: List<String>?,
): List<WorldDocument> {
    val ids = connectedWorldDocIds ?: return worldDocuments.filter { it.enabled == true }
    return worldDocuments.filter { it.id in ids }
}
