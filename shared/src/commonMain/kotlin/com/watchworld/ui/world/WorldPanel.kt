package com.watchworld.ui.world

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.watchworld.domain.selfAndDescendantWorldGroupIds
import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup
import com.watchworld.platform.copyTextToClipboard
import com.watchworld.ui.components.SearchField
import com.watchworld.ui.components.WwDropdownItem
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwSelectField
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Edit
import compose.icons.tablericons.FileText
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.Globe
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Star
import compose.icons.tablericons.ToggleLeft
import compose.icons.tablericons.ToggleRight
import compose.icons.tablericons.Trash

/*
 * 世界观库面板（对标 `DocumentModal.tsx` 的**库形态**，1067 行）。
 *
 * ### 结构（与参考版逐段对应）
 * ```
 * ┌───────────────────────────────────────────────┐
 * │ 🔍 搜索设定文档…          [⇕ 折叠全部] [＋]    │ ← 工具行（＋ 菜单：新建分组 / 新建文档）
 * │ 共 N 篇 · M 个分组                             │ ← 统计行
 * │───────────────────────────────────────────────│
 * │ ▾ ● 世界观 A         3  ＋ ✎ 🌐 🗑            │ ← 左栏：分组树，文档嵌在组里
 * │      📄 总纲                                   │
 * │      📄 势力设定     ← 点一行 = 选中            │
 * │ ▾ ★ 未分组           1                        │
 * │───────────────────────────────────────────────│
 * │ 文档标题  [分组名]      [复制][编辑][删除]      │ ← 右栏：只读详情（选中后出现）
 * │ 正文……（可选中复制）                            │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * ⚠️ **双栏各自滚动**：调用点必须用 `fixedHeight = true` + `scrollable = false` 打开
 *    （对应参考版选中文档时的 `h-[90vh]`），否则两栏拿不到有界高度、内部滚动不生效。
 * ⚠️ 只有回调、没有状态：折叠、搜索词、选中项都在 [WorldViewModel] 里，界面不自己存一份。
 * ⚠️ 库形态**不显示**「本窗口是否注入」开关 —— 参考版用 `hasWindowScope &&` 守住了它。
 *    窗口形态不是另一个组件：传 [sessionDocIds] / [onToggleForSession] 即切过去
 *    （参考版 `DocumentModal` 也是同一个组件挂两个实例，见 `App.tsx:2558` 与 `:2577`）。
 * ⚠️ 两种形态下开关的**写路径完全不同**：窗口形态只写 `session.connectedWorldDocIds`
 *    （注入本窗口），库形态的新建/编辑/删除才动文档与分组的全局状态。
 *    **窗口状态绝不能反过来改库**（用户 2026-10-10 定案）。
 */

/** 「未分组」伪分组的键（参考版 `UNGROUPED = '__ungrouped__'`）。 */
internal const val UNGROUPED_WORLD_KEY = "__ungrouped__"

@Composable
fun WorldPanel(
    state: WorldUiState,
    onCreateGroup: (String, String?) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onSetGroupParent: (String, String?) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onSelectDocument: (String?) -> Unit,
    onSetQuery: (String) -> Unit,
    onCreateDocument: (String, String?, String) -> Unit,
    onSaveDocument: (WorldDocument) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onMoveDocumentToGroup: (String, String?) -> Unit,
    /** 窗口形态：本窗口注入的文档 id（`null` = 库形态）。 */
    sessionDocIds: List<String>? = null,
    /** 窗口形态：单篇文档注入 / 移出本窗口。 */
    onToggleForSession: ((String) -> Unit)? = null,
    /** 窗口形态：整组注入 / 移出、全选 / 全不选。 */
    onSetSessionDocs: ((List<String>) -> Unit)? = null,
) {
    var createMenuOpen by remember { mutableStateOf(false) }
    var docFormOpen by remember { mutableStateOf(false) }
    var groupFormOpen by remember { mutableStateOf(false) }
    var deletingDoc by remember { mutableStateOf<WorldDocument?>(null) }
    var parentPickerFor by remember { mutableStateOf<WorldGroup?>(null) }

    // 参考版 `DocumentModal.tsx:90`：只由 `sessionDocIds` + `onToggleForSession` 判定
    // （`onSetSessionDocs` 不参与），库形态下没有开关、也不做「未注入」的弱化。
    val sessionIds = sessionDocIds.orEmpty()
    val windowScope = sessionDocIds != null && onToggleForSession != null
    /** 参考版 `isDocOn`（`:167-168`）：库形态恒为 true。 */
    fun isDocOn(doc: WorldDocument): Boolean = !windowScope || doc.id in sessionIds
    /** 某分组子树（含自身）里的全部文档 id（参考版 `descendantDocIds` `:114-122`）。 */
    fun descendantDocIds(groupId: String): List<String> {
        val ids = selfAndDescendantWorldGroupIds(state.groups, groupId)
        return state.documents.filter { it.groupId != null && it.groupId in ids }.map { it.id }
    }

    val selected = state.selectedDocument
    val rows = remember(state.groups, state.documents, state.collapsedGroupIds, state.query) {
        buildWorldTreeRows(
            groups = state.groups,
            documents = state.documents,
            collapsedKeys = state.collapsedGroupIds,
            query = state.query,
        )
    }
    val allGroupKeys = remember(state.groups, state.documents) {
        buildList {
            state.groups.forEach { add(it.id) }
            if (state.documents.any { it.groupId == null }) add(UNGROUPED_WORLD_KEY)
        }
    }
    val allCollapsed = allGroupKeys.isNotEmpty() && allGroupKeys.all { it in state.collapsedGroupIds }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 工具行：搜索 + 折叠全部 + 新建菜单 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                value = state.query,
                onValueChange = onSetQuery,
                placeholder = "搜索设定文档...",
                modifier = Modifier.weight(1f),
            )
            if (windowScope) {
                // 参考版 `:656-677`：窗口形态独有的两个按钮。
                // ⚠️「全选」= **全部**文档（不分分组、也不看全局 enabled）——与参考版逐字一致。
                WwSecondaryButton(
                    text = "全选",
                    onClick = { onSetSessionDocs?.invoke(state.documents.map { it.id }) },
                )
                WwSecondaryButton(text = "全不选", onClick = { onSetSessionDocs?.invoke(emptyList()) })
            }
            WwTag(
                text = if (allCollapsed) "展开全部" else "折叠全部",
                enabled = allGroupKeys.isNotEmpty(),
                onClick = {
                    // 目标状态：全折叠时一键清空，否则全部收起；只对「状态不一致」的键发一次 toggle。
                    allGroupKeys.forEach { key ->
                        if ((key in state.collapsedGroupIds) != allCollapsed) onToggleCollapsed(key)
                    }
                },
                leadingIcon = if (allCollapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                background = WwSurfaces.inset,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            )
            Box {
                IconButton(onClick = { createMenuOpen = true }) {
                    Icon(
                        TablerIcons.Plus,
                        contentDescription = "新建",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(
                    expanded = createMenuOpen,
                    onDismissRequest = { createMenuOpen = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = WwSurfaces.dialogBase,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                    shadowElevation = 8.dp,
                ) {
                    WwDropdownItem(
                        text = "新建分组",
                        icon = TablerIcons.FolderPlus,
                        onClick = { createMenuOpen = false; groupFormOpen = true },
                    )
                    WwDropdownItem(
                        text = "新建文档",
                        icon = TablerIcons.FileText,
                        dividerAbove = true,
                        onClick = { createMenuOpen = false; docFormOpen = true },
                    )
                }
            }
        }

        // ── 统计行 ──
        Spacer(Modifier.height(4.dp))
        Text(
            if (windowScope) "开关即本窗口是否注入"
            else "共 ${state.documents.size} 篇 · ${state.groups.size} 个分组",
            fontSize = WwType.hint,
            lineHeight = WwSize.compactTextLineHeight,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))

        // ── 双栏：左树 + 右详情 ──
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .then(if (selected == null) Modifier.fillMaxWidth() else Modifier.width(320.dp))
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (rows.isEmpty()) {
                    Text(
                        if (state.groups.isEmpty()) {
                            "还没有分组。先建一个「大组」当世界观，再往里加子分组与文档。"
                        } else {
                            "没有匹配的文档。"
                        },
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = WwSize.emptyStateVerticalPadding),
                    )
                }
                rows.forEach { row ->
                    when (row) {
                        is WorldTreeRow.GroupNode -> {
                            val groupId = row.group?.id
                            val groupDocIds = groupId?.let(::descendantDocIds).orEmpty()
                            // 参考版 `:171-178`：组内**全部**文档都在本窗口才算「整组已注入」（空组恒 false）。
                            val groupOn = groupDocIds.isNotEmpty() && groupDocIds.all { it in sessionIds }
                            WorldGroupRow(
                                row = row,
                                collapsed = (groupId ?: UNGROUPED_WORLD_KEY) in state.collapsedGroupIds,
                                windowScope = windowScope,
                                groupOn = groupOn,
                                onToggleGroup = {
                                    // 参考版 `:186-197`：整组加入 / 整组移出。
                                    val next = if (groupOn) sessionIds.filter { it !in groupDocIds }
                                    else (sessionIds + groupDocIds).distinct()
                                    onSetSessionDocs?.invoke(next)
                                },
                                onToggle = { onToggleCollapsed(groupId ?: UNGROUPED_WORLD_KEY) },
                                onRename = { name -> row.group?.let { onRenameGroup(it.id, name) } },
                                onDelete = { row.group?.let { onDeleteGroup(it.id) } },
                                onPickParent = { row.group?.let { parentPickerFor = it } },
                                onAddDocument = {
                                    onSelectDocument(null)
                                    docFormOpen = true
                                },
                            )
                        }

                        is WorldTreeRow.DocNode -> WorldDocumentRow(
                            doc = row.doc,
                            depth = row.depth,
                            selected = row.doc.id == state.selectedDocumentId,
                            windowScope = windowScope,
                            injected = isDocOn(row.doc),
                            onToggleInjected = { onToggleForSession?.invoke(row.doc.id) },
                            onClick = { onSelectDocument(row.doc.id) },
                        )
                    }
                }
            }

            if (selected != null) {
                VerticalDivider(color = WwSurfaces.cardBorderSubtle)
                WorldDocumentDetail(
                    doc = selected,
                    groups = state.groups,
                    groupName = selected.groupId?.let { id ->
                        state.groups.firstOrNull { it.id == id }?.name
                    },
                    onMove = { groupId -> onMoveDocumentToGroup(selected.id, groupId) },
                    onEdit = { docFormOpen = true },
                    onDelete = { deletingDoc = selected },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }

    // ── 文档表单（叠层）──
    if (docFormOpen) {
        WorldDocumentFormDialog(
            existing = selected,
            groups = state.groups,
            onDismiss = { docFormOpen = false },
            onSubmit = { title, groupId, content ->
                val current = selected
                if (current == null) {
                    onCreateDocument(title, groupId, content)
                } else {
                    onSaveDocument(current.copy(title = title, groupId = groupId, content = content))
                }
                docFormOpen = false
            },
        )
    }

    // ── 分组表单（叠层）──
    if (groupFormOpen) {
        WorldGroupFormDialog(
            groups = state.groups,
            onDismiss = { groupFormOpen = false },
            onSubmit = { name, parentId ->
                onCreateGroup(name, parentId)
                groupFormOpen = false
            },
        )
    }

    // ── 删除文档确认 ──
    deletingDoc?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingDoc = null },
            title = { Text("删除文档") },
            text = { Text("确认删除「${target.title.ifBlank { target.id }}」？该操作不可撤销。") },
            confirmButton = {
                WwTag(
                    text = "删除",
                    onClick = {
                        onDeleteDocument(target.id)
                        deletingDoc = null
                    },
                    background = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            dismissButton = {
                WwTag(
                    text = "取消",
                    onClick = { deletingDoc = null },
                    background = WwSurfaces.inset,
                )
            },
        )
    }

    // ── 改父级弹层（只列合法候选，防环）──
    parentPickerFor?.let { target ->
        GroupParentDialog(
            target = target,
            groups = state.groups,
            onCancel = { parentPickerFor = null },
            onPick = { parent ->
                onSetGroupParent(target.id, parent)
                parentPickerFor = null
            },
        )
    }
}

// ────────────────────────────────── 树的行模型

/** 树里的一行：分组行或文档行（已扁平化，直接顺序渲染即可）。 */
internal sealed interface WorldTreeRow {
    val depth: Int

    /** 分组行。`group == null` 表示「未分组」伪分组。 */
    data class GroupNode(
        val group: WorldGroup?,
        override val depth: Int,
        val docCount: Int,
    ) : WorldTreeRow

    data class DocNode(val doc: WorldDocument, override val depth: Int) : WorldTreeRow
}

/**
 * 把分组与文档铺成一串带深度的行：分组行在前、组内文档在后，子分组先于本文档。
 *
 * @param query 搜索词。非空时只留命中的文档，且**子树里一个都没命中的分组整组隐藏**
 *   （参考版 `renderGroup` 的「本组及其后代都没有命中就整组隐藏」）；空分组在搜索态下也会隐藏。
 *   ⚠️ 与参考版一致：只匹配**标题与正文**，不匹配标签。
 * @param collapsedKeys 收起的分组键；收起时只跳过它的子树，不跳过它自己。
 *   ⚠️ 必须带 visited 保护：父级由用户自由指定，老存档里可能带环，没有保护会**无限递归**。
 */
internal fun buildWorldTreeRows(
    groups: List<WorldGroup>,
    documents: List<WorldDocument>,
    collapsedKeys: Set<String>,
    query: String = "",
): List<WorldTreeRow> {
    val needle = query.trim().lowercase()
    val searching = needle.isNotEmpty()
    fun matches(doc: WorldDocument): Boolean =
        !searching || doc.title.lowercase().contains(needle) || doc.content.lowercase().contains(needle)

    val rows = mutableListOf<WorldTreeRow>()
    val emitted = mutableSetOf<String>()

    fun ownDocs(groupId: String?): List<WorldDocument> = documents.filter { it.groupId == groupId }
    fun childrenOf(groupId: String): List<WorldGroup> = groups.filter { it.parentId == groupId }

    /** 子树里（含本组文档）有没有命中项。 */
    fun subtreeHits(group: WorldGroup): Boolean =
        ownDocs(group.id).any(::matches) || childrenOf(group.id).any { subtreeHits(it) }

    /**
     * 结构上「能从顶层走下来」的分组集合。
     *
     * ⚠️ 必须与「有没有渲染过」分开算：收起一个分组时它的子树不会被渲染，
     *   如果拿「没渲染过」当「孤儿」，被折叠的子分组会被下面的孤儿补偿重新当成顶层露出一遍，
     *   折叠就等于没折。
     */
    val reachable = mutableSetOf<String>()
    fun markReachable(group: WorldGroup) {
        if (!reachable.add(group.id)) return
        childrenOf(group.id).forEach { markReachable(it) }
    }
    groups.filter { it.parentId == null }.forEach { markReachable(it) }

    fun walk(group: WorldGroup, depth: Int) {
        if (!emitted.add(group.id)) return // 环保护
        val docs = ownDocs(group.id).filter(::matches)
        val children = childrenOf(group.id)
        if (searching && docs.isEmpty() && children.none { subtreeHits(it) }) return

        rows += WorldTreeRow.GroupNode(group, depth, documentCountInGroup(groups, documents, group.id))
        if (group.id in collapsedKeys) return // 收起：跳过子树，但保留分组自己
        children.forEach { walk(it, depth + 1) }
        docs.forEach { rows += WorldTreeRow.DocNode(it, depth + 1) }
    }

    groups.filter { it.parentId == null }.forEach { walk(it, 0) }
    // 真正不可达的分组（父级不存在，或成环走不到顶层）也必须露出来，否则用户永远看不到、也删不掉。
    groups.filterNot { it.id in reachable }.forEach { walk(it, 0) }

    val ungrouped = ownDocs(null).filter(::matches)
    if (ungrouped.isNotEmpty()) {
        rows += WorldTreeRow.GroupNode(null, 0, ungrouped.size)
        if (UNGROUPED_WORLD_KEY !in collapsedKeys) {
            ungrouped.forEach { rows += WorldTreeRow.DocNode(it, 1) }
        }
    }
    return rows
}

/** 某分组子树（含自身）内的文档数量，用于分组行右侧的计数。 */
internal fun documentCountInGroup(
    groups: List<WorldGroup>,
    documents: List<WorldDocument>,
    groupId: String,
): Int {
    val ids = selfAndDescendantWorldGroupIds(groups, groupId)
    return documents.count { it.groupId != null && it.groupId in ids }
}

// ────────────────────────────────── 左栏：分组行 / 文档行

/** 一行分组：折叠箭头 + 色点 + 名字 + 文档数 + 行内操作。 */
@Composable
private fun WorldGroupRow(
    row: WorldTreeRow.GroupNode,
    collapsed: Boolean,
    windowScope: Boolean,
    groupOn: Boolean,
    onToggleGroup: () -> Unit,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onPickParent: () -> Unit,
    onAddDocument: () -> Unit,
) {
    val group = row.group
    var editing by remember(group?.id) { mutableStateOf(false) }
    var draft by remember(group?.id) { mutableStateOf(group?.name.orEmpty()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 14).dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = WwSize.listRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
            contentDescription = if (collapsed) "展开" else "折叠",
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        if (group == null) {
            Icon(
                TablerIcons.Star,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Spacer(Modifier.width(6.dp))

        if (editing && group != null) {
            WwTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
            )
                        WwTag(
                text = "保存",
                onClick = { onRename(draft); editing = false },
                background = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            WwTag(
                text = "取消",
                onClick = { draft = group.name; editing = false },
                background = WwSurfaces.inset,
            )
        } else {
            Text(
                text = group?.name?.ifBlank { group.id } ?: "未分组",
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.docCount.toString(),
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 窗口形态的整组开关（参考版 `:481-489`：14dp、无边框、排在删除按钮之前）。
            // 「未分组」伪分组没有分组实体，参考版也没给它开关。
            if (windowScope && group != null) {
                WorldSessionToggle(
                    on = groupOn,
                    size = 14.dp,
                    description = if (groupOn) "整组已在本窗口注入（点击整组移出）"
                    else "整组未注入（点击整组注入）",
                    onClick = onToggleGroup,
                )
            }
            // 「未分组」伪分组没有分组实体，改名/改上级/删除都不适用。
            if (group != null) {
                IconButton(onClick = onAddDocument, modifier = Modifier.size(24.dp)) {
                    Icon(
                        TablerIcons.Plus,
                        contentDescription = "在「${group.name}」中新建文档",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        TablerIcons.Edit,
                        contentDescription = "重命名分组",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPickParent, modifier = Modifier.size(24.dp)) {
                    Icon(
                        TablerIcons.Globe,
                        contentDescription = "改上级分组",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        TablerIcons.Trash,
                        contentDescription = "删除分组",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 一行文档：点一下 = 选中（详情出现在右栏）；窗口形态下开头多一个「是否注入本窗口」开关。 */
@Composable
private fun WorldDocumentRow(
    doc: WorldDocument,
    depth: Int,
    selected: Boolean,
    windowScope: Boolean,
    injected: Boolean,
    onToggleInjected: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .padding(horizontal = 6.dp, vertical = WwSize.compactFrameVerticalPadding)
            .background(
                color = if (selected) WwSurfaces.cardSelected else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = WwSize.listRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 参考版 `:390-395`：开关在行首、位于文件图标之前。
        // （参考版要 `e.stopPropagation()` 才不会连带选中该行；Compose 里内层 `clickable`
        //   自己吃掉事件，外层行选中不会触发，不需要额外处理。）
        if (windowScope) {
            WorldSessionToggle(
                on = injected,
                size = 16.dp,
                description = if (injected) "本窗口已注入（点击移出）" else "本窗口未注入（点击加入）",
                onClick = onToggleInjected,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            TablerIcons.FileText,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        val titleColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        Text(
            text = doc.title.ifBlank { doc.id },
            fontSize = WwType.body,
            lineHeight = WwSize.compactTextLineHeight,
            // 参考版 `:397`：窗口形态下未注入的文档标题弱化到 55%（库形态不弱化）。
            color = if (windowScope && !injected) titleColor.copy(alpha = 0.55f) else titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 窗口形态的「注入本窗口」开关（参考版 `renderToggle`，`DocumentModal.tsx:349-365`）：
 * **无边框**、`p-0.5` + 10dp 圆角（圆角统一档，2026-10-12），选中为 `accentSoft` 底 + 主色图标，未选为透明底 + 次级色图标。
 *
 * ⚠️ 与正则那边的开关**样式不同**（`RegexModal` 的开关带描边与凹槽底）—— 两处参考版就不一样，
 *    别顺手「统一」。
 */
@Composable
private fun WorldSessionToggle(
    on: Boolean,
    size: Dp,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (on) WwSurfaces.accentSoft else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Icon(
            imageVector = if (on) TablerIcons.ToggleRight else TablerIcons.ToggleLeft,
            contentDescription = description,
            modifier = Modifier.size(size),
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ────────────────────────────────── 右栏：只读详情

@Composable
private fun WorldDocumentDetail(
    doc: WorldDocument,
    groups: List<WorldGroup>,
    groupName: String?,
    onMove: (String?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var copied by remember(doc.id) { mutableStateOf(false) }
    var moveMenuOpen by remember(doc.id) { mutableStateOf(false) }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.title.ifBlank { doc.id },
                    fontSize = WwType.title,
                    lineHeight = WwType.titleLineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                WwTag(text = groupName?.ifBlank { "未命名分组" } ?: "未分组")
            }
            IconButton(onClick = {
                copyTextToClipboard(doc.content)
                copied = true
            }) {
                Icon(
                    imageVector = if (copied) TablerIcons.Check else TablerIcons.Copy,
                    contentDescription = if (copied) "已复制全文" else "复制全文",
                    modifier = Modifier.size(15.dp),
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 参考版用拖拽改分组；桌面端这里给一个等价的下拉（能力不丢）。
            Box {
                IconButton(onClick = { moveMenuOpen = true }) {
                    Icon(
                        TablerIcons.FolderPlus,
                        contentDescription = "移动到分组",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = moveMenuOpen, onDismissRequest = { moveMenuOpen = false }) {
                    if (doc.groupId != null) {
                        DropdownMenuItem(
                            text = { Text("移到未分组") },
                            onClick = { onMove(null); moveMenuOpen = false },
                        )
                    }
                    groups.filterNot { it.id == doc.groupId }.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name.ifBlank { group.id }) },
                            onClick = { onMove(group.id); moveMenuOpen = false },
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    TablerIcons.Edit,
                    contentDescription = "编辑文档",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    TablerIcons.Trash,
                    contentDescription = "删除文档",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SelectionContainer {
            Text(
                text = doc.content.ifBlank { "（这篇文档还没有正文）" },
                fontSize = WwType.body,
                fontFamily = FontFamily.Monospace,
                lineHeight = WwType.bodyLineHeight,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = WwPad.card),
            )
        }
    }
}

// ────────────────────────────────── 叠层表单

/** 新建 / 编辑文档（参考版 `z-[80]` 那层）。 */
@Composable
private fun WorldDocumentFormDialog(
    existing: WorldDocument?,
    groups: List<WorldGroup>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, groupId: String?, content: String) -> Unit,
) {
    // ⚠️ 用 `existing?.id` 当 key：切换选中文档时必须重建草稿，
    //    否则会拿着上一篇文档的内容去覆盖这一篇（`remember` 不带 key 就不会重置）。
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var groupId by remember(existing?.id) { mutableStateOf(existing?.groupId) }
    var content by remember(existing?.id) { mutableStateOf(existing?.content.orEmpty()) }

    FeatureDialog(
        title = if (existing == null) "新建文档" else "编辑：${existing.title.orEmpty()}",
        onDismiss = onDismiss,
        maxWidth = 672.dp,
        heightFraction = 0.9f,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                WwSecondaryButton("取消", onClick = onDismiss)
                WwPrimaryButton(
                    text = "保存文档",
                    onClick = { onSubmit(title.trim(), groupId, content) },
                    enabled = title.isNotBlank(),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WwTextField(
                value = title,
                onValueChange = { title = it },
                label = "标题 *",
                placeholder = "如：世界观总纲、某某人物心理档案",
            )
            GroupPicker(groups, "所属分组", groupId) { groupId = it }
            WwTextField(
                value = content,
                onValueChange = { content = it },
                label = "正文（Markdown）",
                placeholder = "在此输入完整设定...",
                singleLine = false,
                maxLines = 16,
                minHeight = 220.dp,
                fontSize = WwType.hint,
                monospace = true,
            )
        }
    }
}

/** 新建分组（参考版 `z-[80]` 那层，`max-w-md`）。 */
@Composable
private fun WorldGroupFormDialog(
    groups: List<WorldGroup>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, parentId: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }

    FeatureDialog(
        title = "新建分组",
        onDismiss = onDismiss,
        maxWidth = 448.dp,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                WwSecondaryButton("取消", onClick = onDismiss)
                WwPrimaryButton(
                    text = "创建分组",
                    onClick = { onSubmit(name.trim(), parentId) },
                    enabled = name.isNotBlank(),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WwTextField(
                value = name,
                onValueChange = { name = it },
                label = "分组名称 *",
                placeholder = "大组就是一个世界观",
            )
            GroupPicker(groups, "上级分组", parentId) { parentId = it }
        }
    }
}

/** 通用的「选择一个分组（可空 = 未分组）」下拉（紧凑规格，见 `WwFormControls`）。 */
@Composable
private fun GroupPicker(
    groups: List<WorldGroup>,
    label: String,
    currentId: String?,
    onPick: (String?) -> Unit,
) {
    val currentName = currentId?.let { id -> groups.firstOrNull { it.id == id }?.name } ?: "未分组"
    WwSelectField(
        current = currentName,
        options = buildList {
            add("未分组")
            groups.forEach { add(it.name.ifBlank { it.id }) }
        },
        onSelect = { picked ->
            onPick(groups.firstOrNull { it.name.ifBlank { it.id } == picked }?.id)
        },
        label = label,
    )
}

/**
 * 改父级的弹层：**只列出合法候选**（排除目标自己及它的后代，防止成环），带搜索。
 * 与参考版 `banned = selfAndDescendantGroupIds(...)` 同一判据。
 */
@Composable
private fun GroupParentDialog(
    target: WorldGroup,
    groups: List<WorldGroup>,
    onCancel: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val banned = remember(target.id, groups) { selfAndDescendantWorldGroupIds(groups, target.id) }
    val candidates = groups.filterNot { it.id in banned }
    var query by remember { mutableStateOf("") }
    val filtered = candidates.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = WwSurfaces.dialogBase,
            border = BorderStroke(1.dp, WwSurfaces.panelBorder),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = WwPad.card).widthIn(max = 420.dp)) {
                Text(
                    "设置「${target.name}」的上级分组",
                    fontSize = WwType.title,
                    fontWeight = WwType.titleWeight,
                )
                Spacer(Modifier.height(8.dp))
                WwTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索分组...",
                )
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    WwDropdownItem(
                        text = "移到顶层（作为独立世界观）",
                        icon = TablerIcons.Star,
                        onClick = { onPick(null) },
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            "没有匹配的分组",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else {
                        filtered.forEach { group ->
                            WwDropdownItem(
                                text = group.name.ifBlank { group.id },
                                icon = TablerIcons.FolderPlus,
                                dividerAbove = true,
                                onClick = { onPick(group.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
