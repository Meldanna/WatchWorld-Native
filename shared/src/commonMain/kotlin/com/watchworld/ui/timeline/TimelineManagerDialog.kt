package com.watchworld.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowRight
import compose.icons.tablericons.Book
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Edit
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Filter
import compose.icons.tablericons.GitBranch
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Stars
import compose.icons.tablericons.Trash
import compose.icons.tablericons.X
import com.watchworld.domain.formatTimelineParentLabel
import com.watchworld.domain.resolveActiveTimeline
import com.watchworld.domain.timelineDisplayTitle
import com.watchworld.model.ChatSession
import com.watchworld.model.TimelineBranch
import com.watchworld.model.TimelineDescriptionTagRequest
import com.watchworld.model.TimelineDescriptionTagRequestStatus
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwModalScrim
import com.watchworld.ui.components.WwSwitch
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType

/**
 * 记忆整理 · 时间线消息树（对标 `TimelineModal.tsx`，945 行）。
 *
 * ### 三段固定 + 一段滚动
 * ```
 * ┌──────────────────────────────────────────────┐
 * │ [树] 记忆整理 · 时间线消息树  [已开启]    ✕  │ ← 固定标题区
 * │      自动识别时间线并打标签归类…             │
 * │──────────────────────────────────────────────│
 * │ [开关] 记忆整理开关  [AI 操作说明][树(N)][新建]│ ← 固定控制栏
 * │──────────────────────────────────────────────│
 * │ ▲ AI 描述标签申请                            │
 * │ ▲ AI 可见性提示 / 全选 / 全不选              │ ← 中部独立滚动
 * │ ▲ 当前激活分支                               │
 * │ ▲ 分支卡片 × N                               │
 * │──────────────────────────────────────────────│
 * │ 💡 智能识别触发示例：输入「这是一个时间线…」 │ ← 固定底部提示栏
 * └──────────────────────────────────────────────┘
 * ```
 *
 * ### 层级纪律（需求 §三、§七）
 * - 本弹窗可由**聊天界面或侧边栏**打开，关闭后返回直接上级界面。
 * - 编辑子弹窗与 AI 说明弹窗叠在本弹窗之上，关闭后**恢复本弹窗**（本弹窗的滚动位置
 *   与选中项都还在，因为它是独立窗口、期间没有被销毁）。
 * - 遮罩只作用于自己这一层：点击本弹窗遮罩 = 返回直接上级；子弹窗存在时由它独占输入。
 *
 * ⚠️ 宽 672dp 对齐参考项目的 `max-w-2xl`。这是被明确要求的「大型」管理器弹窗，
 *    不适用普通功能弹窗的 600dp 上限。
 */
@Composable
fun TimelineManagerDialog(
    session: ChatSession,
    onDismiss: () -> Unit,
    onToggleMemory: (Boolean) -> Unit,
    onSelectTimeline: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onSetAllVisibility: (Boolean) -> Unit,
    onCreateBranch: (parentId: String?) -> Unit,
    onEditTimeline: (String) -> Unit,
    onDeleteTimeline: (String) -> Unit,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onOpenAiGuide: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：全应用统一款式（毛玻璃雾面，不压黑），并登记自己让主内容去模糊。
            WwModalScrim(onDismiss = onDismiss)

            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = WwPad.shell),
                contentAlignment = Alignment.Center,
            ) {
                TimelineManagerContent(
                    session = session,
                    modifier = Modifier
                        .widthIn(max = 672.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    onDismiss = onDismiss,
                    onToggleMemory = onToggleMemory,
                    onSelectTimeline = onSelectTimeline,
                    onToggleVisibility = onToggleVisibility,
                    onSetAllVisibility = onSetAllVisibility,
                    onCreateBranch = onCreateBranch,
                    onEditTimeline = onEditTimeline,
                    onDeleteTimeline = onDeleteTimeline,
                    onApproveRequest = onApproveRequest,
                    onRejectRequest = onRejectRequest,
                    onOpenAiGuide = onOpenAiGuide,
                )
            }
        }
    }
}

/**
 * 管理器的主体（不含 `Dialog` 外壳）。
 *
 * 拆出来的唯一原因：`Dialog` 在 `ImageComposeScene` 里**不渲染**（另开平台窗口），
 * 而 T24 需要给这块面板留离屏截图回归（见 `TimelinePanelScreenshotTest`）。
 * 生产路径只在 [TimelineManagerDialog] 内部调用它。
 */
@Composable
fun TimelineManagerContent(
    session: ChatSession,
    onDismiss: () -> Unit,
    onToggleMemory: (Boolean) -> Unit,
    onSelectTimeline: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onSetAllVisibility: (Boolean) -> Unit,
    onCreateBranch: (parentId: String?) -> Unit,
    onEditTimeline: (String) -> Unit,
    onDeleteTimeline: (String) -> Unit,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onOpenAiGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    val timelines = session.timelines.orEmpty()
    val enabled = session.timelineMemoryEnabled == true
    val activeTimeline = resolveActiveTimeline(session)
    val visibleCount = timelines.count { it.visible != false }
    val requests = session.timelineDescriptionTagRequests.orEmpty()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = WwSurfaces.dialogBase,
        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
    ) {
        Column {
            ManagerHeader(enabled = enabled, onDismiss = onDismiss)
            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)
            ManagerControlBar(
                enabled = enabled,
                timelineCount = timelines.size,
                onToggleMemory = onToggleMemory,
                onOpenAiGuide = onOpenAiGuide,
                onCreateBranch = { onCreateBranch(activeTimeline?.id) },
            )
            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = WwPad.content),
                verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
            ) {
                DescriptionTagRequestSection(
                    requests = requests,
                    timelines = timelines,
                    enabled = enabled,
                    onApproveRequest = onApproveRequest,
                    onRejectRequest = onRejectRequest,
                    onOpenAiGuide = onOpenAiGuide,
                )
                VisibilityHintRow(
                    visibleCount = visibleCount,
                    totalCount = timelines.size,
                    onSetAllVisibility = onSetAllVisibility,
                )
                activeTimeline?.let { ActiveBranchCard(it) }
                if (timelines.isEmpty()) {
                    Text(
                        text = "当前窗口还没有时间线分支。开启记忆整理后输入「这是一个时间线：五年后…」可以自动建档，也可以直接新建分支。",
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                timelines.forEach { branch ->
                    TimelineBranchCard(
                        branch = branch,
                        isActive = branch.id == activeTimeline?.id,
                        parent = timelines.firstOrNull { it.id == branch.parentId },
                        messageCount = session.messages.count { it.timelineId == branch.id },
                        canDelete = timelines.size > 1,
                        confirmingDelete = confirmDeleteId == branch.id,
                        onSelect = { onSelectTimeline(branch.id) },
                        onToggleVisibility = { onToggleVisibility(branch.id) },
                        onEdit = { onEditTimeline(branch.id) },
                        onDeleteRequest = { confirmDeleteId = branch.id },
                        onDeleteConfirm = {
                            confirmDeleteId = null
                            onDeleteTimeline(branch.id)
                        },
                        onDeleteCancel = { confirmDeleteId = null },
                        onCreateChild = { onCreateBranch(branch.id) },
                    )
                }
            }

            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 16.dp,
                    vertical = WwSize.compactFrameVerticalPadding,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = TablerIcons.Book,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "智能识别触发示例：输入「这是一个时间线：五年后...」",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- 固定标题区

@Composable
private fun ManagerHeader(enabled: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = 16.dp,
            top = WwSize.compactFrameVerticalPadding,
            end = 8.dp,
            bottom = WwSize.compactFrameVerticalPadding,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TablerIcons.GitBranch,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("记忆整理 · 时间线消息树", fontSize = WwType.title, fontWeight = WwType.titleWeight)
                Spacer(Modifier.size(6.dp))
                // 副标题收成 ⓘ（用户 2026-10-12：弹窗顶栏只留一行标题）。
                InfoHint("自动识别时间线并打标签归类，构建后台消息树，随着剧情写入区分信息")
                Spacer(Modifier.size(8.dp))
                StateBadge(enabled = enabled)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(TablerIcons.X, contentDescription = "关闭")
        }
    }
}

@Composable
private fun StateBadge(enabled: Boolean) {
    WwTag(
        text = if (enabled) "已开启" else "已关闭",
        shape = RoundedCornerShape(100.dp),
        pill = true,
        background = if (enabled) WwSurfaces.accentSoft else WwSurfaces.inset,
        contentColor = if (enabled) WwTheme.colors.primaryText else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------- 固定控制栏

@Composable
private fun ManagerControlBar(
    enabled: Boolean,
    timelineCount: Int,
    onToggleMemory: (Boolean) -> Unit,
    onOpenAiGuide: () -> Unit,
    onCreateBranch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = 16.dp,
            vertical = WwSize.compactFrameVerticalPadding,
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: switch + label — TS: w-11 h-6 toggle + chat-text-xs label
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 紧凑开关（见 `WwSwitch`）：不再靠 `graphicsLayer` 缩图形 —— 那样布局高还是 48dp，
            // 行高不变（与设置里的开关行不一致）。
            WwSwitch(checked = enabled, onCheckedChange = onToggleMemory)
            Text(
                text = "记忆整理开关",
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
            )
        }
        // Right: AI guide + tree count + create
        // 三个入口统一走 WwTag，避免与 Material TextButton 的默认高度混排。
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WwTag(
                text = "AI 操作说明",
                shape = RoundedCornerShape(10.dp),
                background = WwSurfaces.inset,
                contentColor = MaterialTheme.colorScheme.onSurface,
                leadingIcon = TablerIcons.Book,
                onClick = onOpenAiGuide,
            )
            WwTag(
                text = "时间线树 ($timelineCount)",
                shape = RoundedCornerShape(10.dp),
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            WwTag(
                text = "新建分支",
                shape = RoundedCornerShape(10.dp),
                background = WwSurfaces.inset,
                contentColor = MaterialTheme.colorScheme.onSurface,
                leadingIcon = TablerIcons.Plus,
                onClick = onCreateBranch,
            )
        }
    }
}

// ---------------------------------------------------------------------- AI 描述标签申请

@Composable
private fun DescriptionTagRequestSection(
    requests: List<TimelineDescriptionTagRequest>,
    timelines: List<TimelineBranch>,
    enabled: Boolean,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onOpenAiGuide: () -> Unit,
) {
    val pendingCount = requests.count { it.status == TimelineDescriptionTagRequestStatus.PENDING }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WwSurfaces.card,
        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 12.dp,
                    vertical = WwSize.compactFrameVerticalPadding,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TablerIcons.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(6.dp))
                Text("AI 描述标签申请", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
                Spacer(Modifier.size(6.dp))
                WwTag(
                    text = "待审批 $pendingCount",
                    shape = RoundedCornerShape(100.dp),
                    pill = true,
                    background = WwSurfaces.inset,
                )
                Spacer(Modifier.weight(1f))
                WwTag(
                    text = "查看模型收到的规则",
                    onClick = onOpenAiGuide,
                    background = WwSurfaces.inset,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()

            if (!enabled) {
                Text(
                    text = "时间线功能已关闭。模型不会获得申请协议，现有待审批申请也不能批准写入。",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = WwSize.compactFrameVerticalPadding,
                    ),
                )
            }

            if (requests.isEmpty()) {
                Text(
                    text = "暂无申请。模型只能提出方案，只有你点击「同意并写入」后才会修改描述标签。",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = WwSize.compactFrameVerticalPadding,
                    ),
                )
            } else {
                requests.sortedByDescending { it.createdAt }.forEach { request ->
                    RequestRow(
                        request = request,
                        targetLabel = timelines
                            .firstOrNull { it.id == request.targetTimelineId }
                            ?.let { formatTimelineParentLabel(it) }
                            ?: "目标分支已不存在",
                        enabled = enabled,
                        onApprove = { onApproveRequest(request.id) },
                        onReject = { onRejectRequest(request.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    request: TimelineDescriptionTagRequest,
    targetLabel: String,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val isPending = request.status == TimelineDescriptionTagRequestStatus.PENDING
    val statusLabel = when (request.status) {
        TimelineDescriptionTagRequestStatus.PENDING -> "待审批"
        TimelineDescriptionTagRequestStatus.APPROVED -> "已同意"
        TimelineDescriptionTagRequestStatus.REJECTED -> "已拒绝"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = 12.dp,
            vertical = WwSize.compactFrameVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = targetLabel,
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                modifier = Modifier.weight(1f),
            )
            WwTag(
                text = statusLabel,
                shape = RoundedCornerShape(100.dp),
                pill = true,
                background = when (request.status) {
                    TimelineDescriptionTagRequestStatus.PENDING -> WwSurfaces.accentSoft
                    TimelineDescriptionTagRequestStatus.APPROVED -> WwSurfaces.cardSelected
                    TimelineDescriptionTagRequestStatus.REJECTED -> WwSurfaces.inset
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelBox(
                label = "当前标签",
                value = request.oldDescriptionTag.ifBlank { "（空）" },
                modifier = Modifier.weight(1f),
            )
            LabelBox(
                label = "建议标签",
                value = request.proposedDescriptionTag,
                highlight = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "理由：${request.reason}",
            fontSize = WwType.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isPending) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WwTag(
                    text = "拒绝",
                    onClick = onReject,
                    background = WwSurfaces.inset,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                )
                Spacer(Modifier.size(8.dp))
                WwTag(
                    text = "同意并写入",
                    onClick = if (enabled) onApprove else null,
                    background = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun LabelBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) WwSurfaces.accentSoft else WwSurfaces.inset,
        border = BorderStroke(1.dp, if (highlight) WwSurfaces.cardBorder else WwSurfaces.cardBorderSubtle),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = 8.dp,
                vertical = WwSize.compactFrameVerticalPadding,
            ),
        ) {
            Text(
                text = label,
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
        }
    }
}

// ---------------------------------------------------------------------- 可见性提示

@Composable
private fun VisibilityHintRow(
    visibleCount: Int,
    totalCount: Int,
    onSetAllVisibility: (Boolean) -> Unit,
) {
    // TS: p-2.5 rounded-xl bg-slate-100 border -- single flat card, no nesting
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = 10.dp,
            vertical = WwSize.compactFrameVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = TablerIcons.Filter,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "AI 可见分支",
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(6.dp))
            // 原来常驻的三行长说明，收成 ⓘ（用户 2026-10-12）。
            InfoHint(
                "可自由多选，仅影响发送给 AI 的上下文，屏幕始终展示全部消息；" +
                    "发送时按通用、浅层到深层排序，当前聚焦分支排在最后。" +
                    "编号标签用于定位分支，描述标签用于记录 AI 辨识所需的特征、事件、行为或代号。",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WwTag(
                text = "全选",
                modifier = Modifier.weight(1f),
                background = WwSurfaces.inset,
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                leadingIcon = TablerIcons.Eye,
                onClick = if (totalCount > 0) ({ onSetAllVisibility(true) }) else null,
            )
            WwTag(
                text = "全不选",
                modifier = Modifier.weight(1f),
                background = WwSurfaces.inset,
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                leadingIcon = TablerIcons.EyeOff,
                onClick = if (totalCount > 0) ({ onSetAllVisibility(false) }) else null,
            )
            WwTag(
                text = "$visibleCount/$totalCount",
                background = WwSurfaces.inset,
                border = null,
            )
            }
        }
    }

// ---------------------------------------------------------------------- 当前激活分支

@Composable
private fun ActiveBranchCard(branch: TimelineBranch) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WwSurfaces.accentSoft,
        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 12.dp,
                vertical = WwSize.compactFrameVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TablerIcons.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = WwTheme.colors.primaryText,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "当前激活时间线：${branch.name}",
                    fontSize = WwType.body,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = WwTheme.colors.primaryText,
                )
            }
            Text(
                text = branch.description,
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

// ---------------------------------------------------------------------- 分支卡片

@Composable
private fun TimelineBranchCard(
    branch: TimelineBranch,
    isActive: Boolean,
    parent: TimelineBranch?,
    messageCount: Int,
    canDelete: Boolean,
    confirmingDelete: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onCreateChild: () -> Unit,
) {
    val accent = timelineAccent(branch.color)
    val visible = branch.visible != false
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) WwSurfaces.cardSelected else WwSurfaces.cardStrong,
        border = BorderStroke(
            width = 1.dp, // TODO: 聚焦状态宽度暂时统一为 1.dp
            // color = if (isActive) accent else WwSurfaces.cardBorder,
            color = WwSurfaces.cardBorder, // TODO: 聚焦状态颜色暂时注释，统一接入全局
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 14.dp,
                vertical = WwSize.compactFrameVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
        ) {
            // ── 第一行：可见开关 / 编号 / 名称 / 聚焦 / 父分支 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onToggleVisibility)
                        .padding(vertical = WwSize.compactFrameVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (visible) TablerIcons.Eye else TablerIcons.EyeOff,
                        contentDescription = if (visible) "AI 可见，点击关闭" else "AI 不可见，点击开启",
                        modifier = Modifier.size(15.dp),
                        tint = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = if (visible) "AI 可见" else "AI 不可见",
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.size(8.dp))
                WwTag(
                    text = branch.codeTag?.ifEmpty { null } ?: branch.tag,
                    shape = RoundedCornerShape(100.dp),
                    pill = true,
                    background = accent,
                    contentColor = Color.White,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = timelineDisplayTitle(branch),
                    fontSize = WwType.body,
                    lineHeight = WwSize.compactTextLineHeight,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    WwTag(
                        text = "当前聚焦",
                        shape = RoundedCornerShape(10.dp),
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            parent?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TablerIcons.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "分化自: " +
                            (it.codeTag?.ifEmpty { null }?.let { code -> "[$code] " } ?: "") +
                            it.name,
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 操作行：切换 / 编辑 / 删除 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isActive) {
                    WwTag(
                        text = "切换至此",
                        onClick = onSelect,
                        background = WwSurfaces.inset,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(TablerIcons.Edit, contentDescription = "编辑时间线信息", modifier = Modifier.size(16.dp))
                }
                if (canDelete) {
                    IconButton(onClick = onDeleteRequest) {
                        Icon(
                            imageVector = TablerIcons.Trash,
                            contentDescription = "删除此分支",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (confirmingDelete) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = 10.dp,
                            vertical = WwSize.compactFrameVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
                    ) {
                        Text(
                            text = "确定删除此时间线分支吗？该分支的消息将被重新分配回第一条剩余分支。",
                            fontSize = WwType.hint,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            WwTag(
                                text = "取消",
                                onClick = onDeleteCancel,
                                background = WwSurfaces.inset,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.size(8.dp))
                            WwTag(
                                text = "删除",
                                onClick = onDeleteConfirm,
                                background = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                }
            }

            InfoBlock(
                title = "🔍 核心区分信息与设定特征：",
                trailing = "确保分类正确",
                body = branch.description.ifBlank { "暂无详细区分信息" },
            )

            branch.plotSummary.takeIf { it.isNotBlank() }?.let {
                InfoBlock(
                    title = "剧情脉络与沉淀演进 (自动更新)：",
                    icon = { Icon(TablerIcons.Clock, contentDescription = null, modifier = Modifier.size(12.dp)) },
                    body = it,
                )
            }

            val milestones = branch.keyMilestones.orEmpty()
            if (milestones.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    milestones.takeLast(3).forEach { milestone ->
                        WwTag(
                            text = milestone,
                            shape = RoundedCornerShape(10.dp),
                            background = WwSurfaces.inset,
                        )
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "包含 $messageCount 条对话",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                WwTag(
                    text = "创建子分支",
                    onClick = onCreateChild,
                    background = WwSurfaces.inset,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    leadingIcon = TablerIcons.Plus
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(
    title: String,
    body: String,
    trailing: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.inset,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 10.dp,
                vertical = WwSize.compactFrameVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    it()
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    text = title,
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                trailing?.let {
                    Text(
                        text = it,
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = body,
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
