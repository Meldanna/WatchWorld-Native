package com.watchworld.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watchworld.domain.messageContentForAi
import com.watchworld.model.ChatGroup
import com.watchworld.model.ChatSession
import com.watchworld.model.UiMode
import com.watchworld.ui.components.GroupColorPickerDialog
import com.watchworld.ui.components.GroupEditDialog
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.components.randomGroupColor
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.Book
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Code
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.GitBranch
import compose.icons.tablericons.Globe
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Pin
import compose.icons.tablericons.Plug
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Search
import compose.icons.tablericons.Server
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Sun
import compose.icons.tablericons.X

/*
 * 侧边栏（对标 `SidebarDrawer.tsx`，587 行）。
 *
 * ### 结构（自上而下，与参考版逐段对应）
 * ```
 * ┌────────────────────────────────┐
 * │ 💬 观界 / 多API移动工作台  ☀ ✕ │ ← 顶部标题栏（p-3，32dp 主色方标）
 * │────────────────────────────────│
 * │ [＋ 新建分组] [⇧ 导入对话]      │ ← 功能按钮区（主色实心 + 描边）
 * │────────────────────────────────│
 * │ 🔍 搜索窗口或消息…              │ ← 搜索框（px-3 pt-2.5 pb-1.5）
 * │────────────────────────────────│
 * │ ▾ ● 分组名 (2)            ＋    │ ← 分组头（px-2 py-1）
 * │   💬 会话标题              ⋯   │ ← 会话行（px-2.5 py-2）
 * │ …                              │ ← 列表滚动区
 * │────────────────────────────────│
 * │ [MCP][Skill][知识库][世界观]    │ ← 底部导航 4×2 网格（p-2，gap-1，图标 14dp）
 * │ [时间线][API][正则库][设置]     │
 * └────────────────────────────────┘
 * ```
 *
 * ⚠️ 尺寸逐项取自参考版：容器上限 **320dp**、图标 14dp、标签 10sp、
 *    分组折叠箭头 13dp、会话行 `px-2.5 py-2`、分组头 `px-2 py-1`。
 * ⚠️ 底部导航**不含「备份」**（用户要求删除）；第 7 格改挂**正则库**（`SidebarDrawer.tsx` 里有备份，
 *    但用户定案换成 `regexRules` 入口）。图标一律走 TablerIcons，配色照抄参考版的 Tailwind 500 档。
 */

/** 参考版底部导航的图标配色（Tailwind 500 档，逐项照抄）。 */
private object NavColors {
    val mcp = Color(0xFF10B981)
    val skill = Color(0xFFF59E0B)
    val knowledge = Color(0xFF3B82F6)
    val world = Color(0xFF14B8A6)
    val timeline = Color(0xFF8B5CF6)
    val api = Color(0xFFEF4444)
    val regex = Color(0xFF06B6D4)
    val settings = Color(0xFF64748B)
}

@Composable
fun SidebarSheet(
    sessions: List<ChatSession>,
    groups: List<ChatGroup>,
    activeSessionId: String?,
    onSelect: (String) -> Unit,
    onNewSession: (String?) -> Unit,
    onRename: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    /** 新建分组：`(名字, 颜色 #rrggbb)` —— 颜色来自创建行最前面的圆点选色（用户 2026-10-13）。 */
    onCreateGroup: (String, String) -> Unit,
    /** 编辑分组：`(id, 名字, 颜色)` —— 分组头 ⋯ 菜单的「编辑分组」（用户 2026-10-13）。 */
    onEditGroup: (String, String, String) -> Unit = { _, _, _ -> },
    onImportSession: () -> Unit = {},
    onToggleUiMode: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenMcp: () -> Unit = {},
    onOpenSkill: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenWorld: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    onOpenProvider: () -> Unit = {},
    onOpenRegex: () -> Unit = {},
    activeMcpCount: Int = 0,
    connectedKnowledgeCount: Int = 0,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var creatingGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    // 待创建分组的颜色：进入创建态时随机取一个预设色（对标 Web `GroupModal.tsx:56`），
    // 之后可在创建行最前面的圆点上点开色环取色板改（用户 2026-10-13）。
    var newGroupColor by remember { mutableStateOf(randomGroupColor()) }
    var colorPickerOpen by remember { mutableStateOf(false) }
    // 正在编辑的分组（非空 = 编辑弹窗开着）。
    var editingGroup by remember { mutableStateOf<ChatGroup?>(null) }

    val needle = query.trim().lowercase()
    val filtered = remember(sessions, needle) {
        if (needle.isEmpty()) {
            sessions
        } else {
            sessions.filter { session ->
                session.title.lowercase().contains(needle) ||
                    session.messages.any { messageContentForAi(it).lowercase().contains(needle) }
            }
        }
    }

    Surface(
        modifier = modifier.widthIn(max = 320.dp).fillMaxHeight(),
        color = WwSurfaces.sheetBase,
        border = androidx.compose.foundation.BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SidebarHeader(onToggleUiMode = onToggleUiMode, onClose = onDismiss)
            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)

            SidebarActions(
                creatingGroup = creatingGroup,
                newGroupName = newGroupName,
                newGroupColor = newGroupColor,
                onNewGroupNameChange = { newGroupName = it },
                onStartCreateGroup = {
                    newGroupColor = randomGroupColor()
                    creatingGroup = true
                },
                onPickColor = { colorPickerOpen = true },
                onConfirmCreateGroup = {
                    val name = newGroupName.trim()
                    if (name.isNotEmpty()) onCreateGroup(name, newGroupColor)
                    newGroupName = ""
                    creatingGroup = false
                },
                onCancelCreateGroup = {
                    newGroupName = ""
                    creatingGroup = false
                },
                onImportSession = onImportSession,
            )
            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)

            SidebarSearchBar(query = query, onQueryChange = { query = it })

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 参考版：会话之间 `space-y-0.5`(2dp)、分组之间 `space-y-3`(12dp)。
                // 扁平 LazyColumn 只有一个间距，所以分组之间靠表头的 top padding 补足：2 + 10 = 12。
                groups.sortedBy { it.order }.forEachIndexed { groupIndex, group ->
                    item(key = "group-${group.id}") {
                        GroupHeader(
                            group = group,
                            onAddSession = { onNewSession(group.id) },
                            onEditGroup = { editingGroup = group },
                            modifier = Modifier.padding(top = if (groupIndex == 0) 0.dp else 10.dp),
                        )
                    }
                    val inGroup = filtered
                        .filter { it.groupId == group.id }
                        .sortedWith(
                            compareByDescending<ChatSession> { it.pinned == true }
                                .thenByDescending { it.updatedAt },
                        )
                    items(count = inGroup.size, key = { inGroup[it].id }) { index ->
                        val session = inGroup[index]
                        SessionRow(
                            session = session,
                            active = session.id == activeSessionId,
                            onSelect = { onSelect(session.id) },
                            onRename = { title -> onRename(session.id, title) },
                            onTogglePinned = { onTogglePinned(session.id, session.pinned != true) },
                            onClear = { onClear(session.id) },
                            onDelete = { onDelete(session.id) },
                            onMove = { groupId -> onMove(session.id, groupId) },
                            groups = groups,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)
            SidebarNavGrid(
                activeMcpCount = activeMcpCount,
                connectedKnowledgeCount = connectedKnowledgeCount,
                onOpenMcp = onOpenMcp,
                onOpenSkill = onOpenSkill,
                onOpenKnowledge = onOpenKnowledge,
                onOpenWorld = onOpenWorld,
                onOpenTimeline = onOpenTimeline,
                onOpenProvider = onOpenProvider,
                onOpenRegex = onOpenRegex,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    // 分组颜色取色板（色环）：点创建行最前面的圆点弹出，选完「确定」写回待创建的颜色。
    if (colorPickerOpen) {
        GroupColorPickerDialog(
            initialHex = newGroupColor,
            onConfirm = {
                newGroupColor = it
                colorPickerOpen = false
            },
            onDismiss = { colorPickerOpen = false },
        )
    }

    // 分组编辑弹窗（名字 + 颜色），由分组头 ⋯ 菜单打开。
    editingGroup?.let { target ->
        GroupEditDialog(
            initialName = target.name,
            initialColor = target.color,
            onConfirm = { name, color ->
                onEditGroup(target.id, name, color)
                editingGroup = null
            },
            onDismiss = { editingGroup = null },
        )
    }
}

// ---------------------------------------------------------------------- 顶部标题栏（p-3）

@Composable
private fun SidebarHeader(onToggleUiMode: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = WwTheme.colors.primaryBg,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💬", fontSize = WwType.body)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "观界",
                    fontSize = WwType.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "多API移动工作台",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleUiMode, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (WwTheme.colors.uiMode == UiMode.DARK) TablerIcons.Moon
                    else TablerIcons.Sun,
                    contentDescription = "切换明暗",
                    modifier = Modifier.size(16.dp),
                    tint = if (WwTheme.colors.uiMode == UiMode.DARK) Color(0xFFC0C0C0)
                    else Color(0xFFFBBF24),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = "关闭侧边栏",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- 功能按钮区（p-3 gap-2）

@Composable
private fun SidebarActions(
    creatingGroup: Boolean,
    newGroupName: String,
    /** 待创建分组的颜色（创建行最前面圆点的填色）。 */
    newGroupColor: String,
    onNewGroupNameChange: (String) -> Unit,
    onStartCreateGroup: () -> Unit,
    /** 点圆点 → 打开色环取色板。 */
    onPickColor: () -> Unit,
    onConfirmCreateGroup: () -> Unit,
    onCancelCreateGroup: () -> Unit,
    onImportSession: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        if (creatingGroup) {
            // 参考版是模态输入；原生侧就地展开，少一层窗口、也不遮挡会话列表。
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 颜色圆点（用户 2026-10-13）：点击弹出色环取色板，位置在名字框之前。
                Box(
                    modifier = Modifier.size(20.dp).clickable(onClick = onPickColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(parseGroupColor(newGroupColor), CircleShape)
                            .border(1.dp, WwSurfaces.cardBorder, CircleShape),
                    )
                }
                Spacer(Modifier.width(6.dp))
                WwTextField(
                    value = newGroupName,
                    onValueChange = onNewGroupNameChange,
                    placeholder = "输入分组名称",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                WwTag(
                    text = "取消",
                    onClick = onCancelCreateGroup,
                    background = WwSurfaces.inset,
                )
                Spacer(Modifier.width(6.dp))
                WwTag(
                    text = "创建",
                    onClick = onConfirmCreateGroup,
                    enabled = newGroupName.isNotBlank(),
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    icon = TablerIcons.Plus,
                    label = "新建分组",
                    filled = true,
                    onClick = onStartCreateGroup,
                )
                ActionButton(
                    icon = TablerIcons.FileImport,
                    label = "导入对话",
                    filled = false,
                    onClick = onImportSession,
                )
            }
        }
    }
}

/** 参考版按钮：`py-2 px-3`、`rounded-xl`、图标 15dp。 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (filled) WwTheme.colors.primaryBg else WwSurfaces.card,
        border = if (filled) null
        else androidx.compose.foundation.BorderStroke(1.dp, WwSurfaces.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = WwSize.boxVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (filled) Color.White else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------- 搜索框（px-3 pt-2.5 pb-1.5）

/**
 * 参考版是一行 `flex`：容器 `px-2.5 py-1.5` 内联放大镜 + 原生 `<input>`。
 *
 * ⚠️ 这里**没有**用 `OutlinedTextField`：M3 的 outline 容器强制
 * `defaultMinSize(minHeight = 56.dp)`，`contentPadding` 压不到参考版的 ~30dp 行高，
 * 铺出来会比参考版高近一倍。改成自绘 `Surface` + `BasicTextField` 才能逐项对上
 * `px-2.5 py-1.5` 与 `rounded-xl`。
 */
@Composable
private fun SidebarSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
            .background(WwSurfaces.inset, shape)
            .border(1.dp, WwSurfaces.cardBorderSubtle, shape)
            .padding(horizontal = 10.dp, vertical = WwSize.searchFieldVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索窗口或消息...",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = "清除搜索",
                modifier = Modifier.size(13.dp).clickable { onQueryChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------- 分组头（px-2 py-1）

@Composable
private fun GroupHeader(
    group: ChatGroup,
    onAddSession: () -> Unit,
    onEditGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by remember(group.id) { mutableStateOf(false) }
    var menuOpen by remember(group.id) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed }
                .padding(horizontal = 8.dp, vertical = WwSize.compactFrameVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (collapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(parseGroupColor(group.color), CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = group.name,
                fontSize = WwType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 条数不再写（用户 2026-10-13：「已有分组不用写它里面有几个对话」）。
            IconButton(onClick = onAddSession, modifier = Modifier.size(22.dp)) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = "在「${group.name}」中新建窗口",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 分组操作：图标与会话行一致（竖排三点）。
            Icon(
                imageVector = TablerIcons.DotsVertical,
                contentDescription = "分组操作：编辑",
                modifier = Modifier
                    .clickable { menuOpen = !menuOpen }
                    .padding(horizontal = 6.dp, vertical = WwSize.compactFrameVerticalPadding)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (menuOpen) {
            ActionListPanel {
                SessionActionRow("编辑分组") {
                    onEditGroup()
                    menuOpen = false
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- 会话行（px-2.5 py-2）

@Composable
private fun SessionRow(
    session: ChatSession,
    active: Boolean,
    onSelect: () -> Unit,
    onRename: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMove: (String) -> Unit,
    groups: List<ChatGroup>,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember(session.id) { mutableStateOf(false) }
    var editing by remember(session.id) { mutableStateOf(false) }
    var title by remember(session.title) { mutableStateOf(session.title) }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (active) WwSurfaces.cardSelected else Color.Transparent,
                shape = shape,
            )
            .let { if (active) it.border(1.dp, WwSurfaces.cardBorder, shape) else it }
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = WwSize.boxVerticalPadding),
    ) {
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WwTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                WwTag(
                    text = "保存",
                    onClick = {
                        onRename(title)
                        editing = false
                    },
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            // 标题前**不再画 Agent emoji**（用户 2026-10-13：认不出是哪个 Agent、
            // 单一 Agent 时整列都是同一个字符，等于噪声）。Web 版有这一格，属有意分歧。
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.pinned == true) {
                    Icon(
                        imageVector = TablerIcons.Pin,
                        contentDescription = "已置顶",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = session.title.ifBlank { "未命名对话" },
                    fontSize = WwType.body,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TablerIcons.DotsVertical,
                    contentDescription = "窗口操作：重命名、置顶、清空、移动、删除",
                    modifier = Modifier
                        .clickable { menuOpen = !menuOpen }
                        .padding(horizontal = 6.dp, vertical = WwSize.compactFrameVerticalPadding)
                        .size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (menuOpen) {
            ActionListPanel {
                SessionActionRow("重命名") { editing = true; menuOpen = false }
                SessionActionRow(if (session.pinned == true) "取消置顶" else "置顶") {
                    onTogglePinned(); menuOpen = false
                }
                SessionActionRow("清空消息") { onClear(); menuOpen = false }
                groups.filterNot { it.id == session.groupId }.take(3).forEach { target ->
                    SessionActionRow("移动到 ${target.name}") { onMove(target.id); menuOpen = false }
                }
                SessionActionRow("删除", danger = true) { onDelete(); menuOpen = false }
            }
        }
    }
}

/**
 * 行内操作列表（会话 ⋯ / 分组 ⋯ 共用）：**列表款式 + 一根模块2 框线**。
 *
 * 用户 2026-10-13：「侧边栏的会话的编辑，是列表款式吧，增加框线」。
 */
@Composable
private fun ActionListPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, WwSurfaces.cardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp, vertical = WwSize.boxVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun SessionActionRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = WwType.body,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = WwSize.compactFrameVerticalPadding, horizontal = 4.dp),
    )
}

// ---------------------------------------------------------------------- 底部导航（p-2，grid-cols-4）

/**
 * 底部功能导航：4 列 × 2 行（对标参考版 `grid-cols-4 gap-1 py-1.5`，外壳 `p-2`）。
 *
 * ⚠️ **不含「备份」**：用户要求删除；第 7 格改挂**正则库**。
 */
@Composable
private fun SidebarNavGrid(
    activeMcpCount: Int,
    connectedKnowledgeCount: Int,
    onOpenMcp: () -> Unit,
    onOpenSkill: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenWorld: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenProvider: () -> Unit,
    onOpenRegex: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WwSurfaces.card)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NavGridItem(Modifier.weight(1f), TablerIcons.Server, NavColors.mcp, "MCP", activeMcpCount, onOpenMcp)
            NavGridItem(Modifier.weight(1f), TablerIcons.Bolt, NavColors.skill, "Skill", 0, onOpenSkill)
            NavGridItem(
                Modifier.weight(1f), TablerIcons.Book, NavColors.knowledge,
                "知识库", connectedKnowledgeCount, onOpenKnowledge,
            )
            NavGridItem(Modifier.weight(1f), TablerIcons.Globe, NavColors.world, "世界观", 0, onOpenWorld)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NavGridItem(Modifier.weight(1f), TablerIcons.GitBranch, NavColors.timeline, "时间线", 0, onOpenTimeline)
            NavGridItem(Modifier.weight(1f), TablerIcons.Plug, NavColors.api, "API", 0, onOpenProvider)
            NavGridItem(Modifier.weight(1f), TablerIcons.Code, NavColors.regex, "正则库", 0, onOpenRegex)
            NavGridItem(Modifier.weight(1f), TablerIcons.Settings, NavColors.settings, "设置", 0, onOpenSettings)
        }
    }
}

@Composable
private fun NavGridItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    color: Color,
    label: String,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = WwSize.navCellVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                // 图标与格里的标签 1:1、一起随「界面字体大小」变（用户 2026-10-13）。
                modifier = Modifier.size(WwSize.navIconSize),
                tint = color,
            )
            if (badgeCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                    shape = CircleShape,
                    color = color,
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = WwType.hint * (8f / 12f),
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = WwType.hint,
            lineHeight = WwSize.compactTextLineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------- 工具

/** `group.color` 在存档里是 `#rrggbb` 字符串；解析失败退回中性灰，不抛。 */
private fun parseGroupColor(value: String): Color {
    val hex = value.removePrefix("#")
    if (hex.length != 6) return NavColors.settings
    val rgb = hex.toLongOrNull(16) ?: return NavColors.settings
    return Color(0xFF000000L or rgb)
}
