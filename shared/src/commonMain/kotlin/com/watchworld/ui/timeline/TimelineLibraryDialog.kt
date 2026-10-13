package com.watchworld.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ChatSession
import com.watchworld.model.TimelineBranch
import com.watchworld.ui.components.SearchField
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.GitBranch
import compose.icons.tablericons.Message

/*
 * 时间线库（对标 `TimelineLibraryModal.tsx`，274 行）。
 *
 * **只读浏览**：列出所有「已经建立时间线」的窗口，以及每个窗口下的分支。
 * 刻意不做开启/关闭开关 —— 分支可见性与记忆整理开关属于窗口级操作，
 * 在「窗口功能 → 时间线管理」里改（那边是 [TimelineManagerDialog]）。
 *
 * 点某条分支 = 切到那个窗口并聚焦到该分支（`onJumpToTimeline`），随后关闭本弹窗。
 */

@Composable
fun TimelineLibraryDialog(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    onJumpToTimeline: (sessionId: String, timelineId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 只保留真正有时间线的窗口（参考版 L44-50）。
    val groups = remember(sessions) { timelineLibraryGroups(sessions) }
    // 顶栏副标题统计的是**全部**分支，不受搜索影响（参考版 L92）。
    val totalTimelines = groups.sumOf { it.timelines.size }

    FeatureDialog(
        title = "时间线库",
        subtitle = "$totalTimelines 条分支 • ${groups.size} 次会话",
        // 数据型副标题（分支数 / 会话数）要一眼看到，不收成 ⓘ。
        subtitleAsHint = false,
        icon = TablerIcons.GitBranch,
        onDismiss = onDismiss,
        maxWidth = 672.dp,
        heightFraction = 0.9f,
    ) {
        TimelineLibraryBody(
            groups = groups,
            activeSessionId = activeSessionId,
            onJump = { sessionId, timelineId ->
                onJumpToTimeline(sessionId, timelineId)
                onDismiss()
            },
        )
    }
}

/** 有时间线的窗口 + 它的分支。 */
internal data class TimelineLibraryGroup(
    val session: ChatSession,
    val timelines: List<TimelineBranch>,
)

/** `(s.timelines || []).length > 0` 的窗口才有条目（参考版 L44-50）。 */
internal fun timelineLibraryGroups(sessions: List<ChatSession>): List<TimelineLibraryGroup> =
    sessions.map { TimelineLibraryGroup(it, it.timelines.orEmpty()) }.filter { it.timelines.isNotEmpty() }

/**
 * 弹窗正文（不含 `FeatureDialog` 外壳）。
 *
 * ⚠️ 单独拆出来是为了**离屏截图验收**：`Dialog` 在 `ImageComposeScene` 里不渲染
 *    （见 `TimelinePanelScreenshotTest` 的类注释），整窗截图拿不到东西。
 */
@Composable
internal fun TimelineLibraryBody(
    groups: List<TimelineLibraryGroup>,
    activeSessionId: String?,
    onJump: (sessionId: String, timelineId: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var collapsedIds by remember { mutableStateOf(emptySet<String>()) }

    val needle = query.trim().lowercase()
    val filtered = remember(groups, needle) {
        if (needle.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val sessionHit = group.session.title.lowercase().contains(needle)
                val hits = if (sessionHit) {
                    group.timelines
                } else {
                    group.timelines.filter { t ->
                        t.name.lowercase().contains(needle) ||
                            t.descriptionTag.orEmpty().lowercase().contains(needle) ||
                            t.codeTag.orEmpty().lowercase().contains(needle) ||
                            t.tag.lowercase().contains(needle)
                    }
                }
                if (hits.isEmpty()) null else group.copy(timelines = hits)
            }
        }
    }
    val allCollapsed = filtered.isNotEmpty() && filtered.all { it.session.id in collapsedIds }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        // ── 工具条：搜索 + 全部折叠/展开 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索窗口或时间线...",
                modifier = Modifier.weight(1f),
            )
            WwTag(
                text = if (allCollapsed) "展开全部" else "折叠全部",
                shape = RoundedCornerShape(10.dp),
                background = WwSurfaces.card,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                leadingIcon = if (allCollapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                onClick = {
                    collapsedIds = if (allCollapsed) {
                        emptySet()
                    } else {
                        filtered.map { it.session.id }.toSet()
                    }
                },
            )
        }

        if (filtered.isEmpty()) {
            Text(
                text = if (groups.isEmpty()) {
                    "还没有任何窗口建立时间线。在窗口里开启「记忆整理」后，时间线会出现在这里。"
                } else {
                    "没有匹配的窗口或时间线"
                },
                fontSize = WwType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding),
            )
        }

        filtered.forEach { group ->
            SessionTimelineCard(
                session = group.session,
                timelines = group.timelines,
                isCurrentSession = group.session.id == activeSessionId,
                collapsed = group.session.id in collapsedIds,
                onToggleCollapse = {
                    collapsedIds = if (group.session.id in collapsedIds) {
                        collapsedIds - group.session.id
                    } else {
                        collapsedIds + group.session.id
                    }
                },
                onJump = { timelineId -> onJump(group.session.id, timelineId) },
            )
        }
    }
}

@Composable
private fun SessionTimelineCard(
    session: ChatSession,
    timelines: List<TimelineBranch>,
    isCurrentSession: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onJump: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.card,
        border = BorderStroke(
            width = if (isCurrentSession) 2.dp else 1.dp,
            color = if (isCurrentSession) accent else WwSurfaces.cardBorder,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = WwPad.card)) {
            // ── 窗口标题行 ──
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(horizontal = 12.dp, vertical = WwSize.listRowVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (collapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                    contentDescription = if (collapsed) "展开" else "折叠",
                    modifier = Modifier.size(14.dp),
                    tint = if (isCurrentSession) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = TablerIcons.Message,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (isCurrentSession) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = session.title.ifBlank { "未命名对话" },
                    fontSize = WwType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSession) accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timelines.size.toString(),
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isCurrentSession) {
                    Spacer(Modifier.width(6.dp))
                    WwTag(
                        text = "当前窗口",
                        shape = RoundedCornerShape(10.dp),
                        background = WwSurfaces.accentSoft,
                        contentColor = accent,
                    )
                }
            }

            if (!collapsed) {
                timelines.forEach { branch ->
                    TimelineLibraryRow(
                        branch = branch,
                        focused = isCurrentSession && session.activeTimelineId == branch.id,
                        onClick = { onJump(branch.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineLibraryRow(
    branch: TimelineBranch,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val accent = timelineAccent(branch.color)
    // 参考版 L218-220：有描述标签时优先展示「编号·描述标签」。
    val label = branch.descriptionTag?.ifEmpty { null }?.let { descriptionTag ->
        branch.codeTag?.ifEmpty { null }?.let { "$it·$descriptionTag" } ?: descriptionTag
    } ?: branch.name

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = WwSize.compactFrameVerticalPadding)
            .background(if (focused) WwSurfaces.accentSoft else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = WwSize.listRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WwTag(
            text = branch.codeTag?.ifEmpty { null } ?: branch.tag,
            shape = RoundedCornerShape(100.dp),
            pill = true,
            background = accent,
            contentColor = Color.White,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = WwType.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (branch.visible == false) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "AI 不可见",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (focused) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = "当前聚焦分支",
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
