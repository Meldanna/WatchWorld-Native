package com.watchworld.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ChatMessage
import com.watchworld.model.Role
import com.watchworld.model.TimelineBranch
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowRight
import compose.icons.tablericons.Search
import compose.icons.tablericons.User
import compose.icons.tablericons.World
import compose.icons.tablericons.X

/**
 * 全文搜索面板（对标 Web 版 `SearchModal.tsx`）。
 *
 * ### 与源码逐项对齐的点（上一版缺的都在这里）
 * - 搜索框占位文案：`搜索当前窗口消息（显示楼号与标签，点击直达）…`
 * - **信息条**：左侧 `请输入搜索关键词` / `找到 N 条相关消息`，右侧 `当前窗口共 M 条消息`
 * - 结果卡片**两行堆叠**：第一行 `#楼号` + 时间线色标 + 角色（AI / 我）+ 右侧「跳转 →」；
 *   第二行是正文预览（最多两行）。上一版把它压成了一行横排，比例完全不同。
 * - 两种空态文案：`未找到包含“…”的消息` / `输入关键词，按楼号和时间线分支快速检索`
 *
 * ⚠️ 列表用普通 `Column` 而不是 `LazyColumn`：本面板挂在 `FeatureDialog` 的
 *    `verticalScroll` 内部，再套一层懒滚动会触发同方向嵌套滚动异常。
 */
@Composable
fun SearchPanel(
    messages: List<ChatMessage>,
    timelines: List<TimelineBranch>,
    onJumpToMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val timelineMap = remember(timelines) { timelines.associateBy { it.id } }

    val results = remember(messages, timelines, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) emptyList()
        else messages.filter { searchTextOf(it).lowercase().contains(needle) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 搜索框（Web: p-3.5 顶栏内的无边框输入 + 前置放大镜） ──
        WwTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索当前窗口消息（显示楼号与标签，点击直达）…",
            leadingIcon = TablerIcons.Search,
            trailing = {
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = "清除搜索",
                        modifier = Modifier.size(15.dp).clickable { query = "" },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── 信息条（Web: px-4 py-2 + bg-slate-50，左右各一条小字） ──
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = WwSurfaces.inset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (query.isBlank()) "请输入搜索关键词" else "找到 ${results.size} 条相关消息",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "当前窗口共 ${messages.size} 条消息",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── 结果列表 ──
        when {
            results.isNotEmpty() -> results.forEach { message ->
                val timeline = message.timelineId?.let { timelineMap[it] }
                SearchResultCard(
                    message = message,
                    timelineCodeTag = timeline?.codeTag,
                    timelineTag = timeline?.tag,
                    timelineColorId = timeline?.color,
                    onClick = {
                        onJumpToMessage(message.id)
                        onDismiss()
                    },
                )
            }

            query.isNotBlank() -> EmptyHint("未找到包含“$query”的消息")

            else -> EmptyHint("输入关键词，按楼号和时间线分支快速检索")
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = WwType.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultCard(
    message: ChatMessage,
    timelineCodeTag: String?,
    timelineTag: String?,
    timelineColorId: String?,
    onClick: () -> Unit,
) {
    val isAssistant = message.role == Role.ASSISTANT
    val floorNumber = message.floorNumber ?: 1
    // Web: codeTag ? `${codeTag}${descriptionTag ? `·${descriptionTag}` : ''}` : (timeline?.tag || '通用')
    val tagDisplay = message.descriptionTag
        ?.takeIf { message.codeTag != null }
        ?.let { "·$it" }
        ?.let { "${message.codeTag}$it" }
        ?: message.codeTag
        ?: timelineTag
        ?: "通用"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WwSurfaces.card,
        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
        modifier = Modifier.fillMaxWidth().padding(bottom = WwSize.cardListGap).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card)) {
            // ── 第一行：楼号 + 色标 + 角色 …… 跳转 ──
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                WwTag(
                    text = "#$floorNumber",
                    shape = RoundedCornerShape(10.dp),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    monospace = true,
                )
                Spacer(Modifier.width(6.dp))
                WwTag(
                    text = tagDisplay,
                    shape = RoundedCornerShape(100.dp),
                    pill = true,
                    background = timelineBadgeColor(timelineColorId),
                    contentColor = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isAssistant) TablerIcons.World else TablerIcons.User,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isAssistant) Color(0xFF818CF8) else Color(0xFF34D399),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isAssistant) "AI" else "我",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "跳转",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    TablerIcons.ArrowRight,
                    contentDescription = "跳转",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(WwSize.cardListGap))
            // ── 第二行：正文预览（最多两行） ──
            Text(
                text = searchTextOf(message),
                fontSize = WwType.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 时间线色标底色。Web 用 Tailwind 的 `bg-*-600 text-white`；
 * 这里按 `getTimelineColorConfig` 的 7 个 id 取对应的 600 档色值。
 */
private fun timelineBadgeColor(colorId: String?): Color = when (colorId) {
    "emerald" -> Color(0xFF059669)
    "amber" -> Color(0xFFD97706)
    "rose" -> Color(0xFFE11D48)
    "cyan" -> Color(0xFF0891B2)
    "purple" -> Color(0xFF9333EA)
    "teal" -> Color(0xFF0D9488)
    else -> Color(0xFF4F46E5) // indigo（默认，见 TIMELINE_COLORS[0]）
}

/** 提取消息文本（assistant 取当前版本）。 */
private fun searchTextOf(m: ChatMessage): String {
    if (m.role == Role.ASSISTANT) {
        val version = m.versions.getOrNull(m.currentVersionIndex)
            ?: m.versions.firstOrNull()
        if (version != null) return version.content
    }
    return m.content
}
