package com.watchworld.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ChatMessage
import com.watchworld.model.Role
import com.watchworld.domain.estimateTokens
import com.watchworld.domain.formatTokenCount
import com.watchworld.platform.ImageStore
import com.watchworld.platform.defaultImageStore
import com.watchworld.ui.components.AvatarImage
import com.watchworld.ui.components.AvatarSource
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.image.MessageImageAttachments
import com.watchworld.ui.image.rememberMessageImages
import com.watchworld.ui.theme.CssColors
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import com.watchworld.platform.copyTextToClipboard
import com.watchworld.ui.components.WwIconButton
import com.watchworld.ui.markdown.StreamingMarkdown
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronLeft
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Trash
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 单条消息气泡（T02 版；T12 起颜色走 [WwTheme.colors] 角色层）。
 *
 * 用户消息靠右、助手消息靠左；助手消息正文走 [StreamingMarkdown],
 * 因此「块级缓存 + 光标闪烁」由 `ui/markdown` 统一负责，本文件不碰渲染细节。
 *
 * 角色对应（theme.ts 类名 → WwColors 字段）：
 * - 用户气泡 = `userBubble`（bg-m1-N + text-white + shadow-sm）
 * - 助手气泡 = `--m1-bubble`（预设主题固定白/深灰，自定义主题随主色极淡/极浓档）
 * - 助手正文色沿用 `colorScheme.onSurface`（= token text-primary,双轨①已喂主题）
 */
@Composable
fun MessageBubble(message: UiMessage, modifier: Modifier = Modifier) {
    MessageBubbleContent(
        id = message.id, isUser = message.role == UiRole.User,
        content = message.content, streaming = message.streaming,
        versionCount = 0, currentVersionIndex = 0, imageIds = null,
        onSwitchVersion = null, onRegenerate = null, onEdit = null, onSaveEdit = null, onDelete = null,
        analysisBadge = null, onToggleVisibility = null, onArchive = null,
        imageStore = defaultImageStore,
        modifier = modifier,
    )
}

/** T14 正式模型重载（T16 扩展：版本指示器 + 操作菜单）。 */
@Composable
fun MessageBubble(
    message: ChatMessage, streaming: Boolean, modifier: Modifier = Modifier,
    onSwitchVersion: ((String, Int) -> Unit)? = null,
    onRegenerate: ((String) -> Unit)? = null,
    onEdit: ((String, String) -> Unit)? = null,
    /** 只保存编辑、**不重发**（用户 2026-10-12：编辑框里的「保存」）。 */
    onSaveEdit: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onToggleAnalysisVisibility: ((String) -> Unit)? = null,
    onArchiveAnalysis: ((String) -> Unit)? = null,
    imageStore: ImageStore = defaultImageStore,
    // 新增参数：头像和显示设置
    /** 用户头像来源；`null` = 不显示。 */
    userAvatar: AvatarSource? = null,
    /** AI 头像来源：窗口覆盖优先、其次 Agent 默认，顺序由调用方给。 */
    aiAvatar: AvatarSource? = null,
    avatarSize: Int = 32,
    /** 消息区是否显示头像（`MessageDisplaySettings.showAvatars`）。 */
    showAvatars: Boolean = true,
    showModelName: Boolean = false,
    showMessageTime: Boolean = false,
    showLatency: Boolean = false,
    showMessageTokens: Boolean = false,
    /** 是否显示本窗口总 Token（仅 assistant 气泡，对标 Web `MessageItem.tsx:453`）。 */
    showSessionTokens: Boolean = false,
    /** 本窗口累计 Token（`estimateSessionTokens`）。 */
    sessionTotalTokens: Int = 0,
) {
    val content = if (message.role == Role.ASSISTANT) {
        message.versions.getOrNull(message.currentVersionIndex)?.content
            ?: message.versions.firstOrNull()?.content
            ?: message.content
    } else { message.content }

    // 提取元数据（优先从 versions，回退到顶层）
    val version = if (message.role == Role.ASSISTANT) {
        message.versions.getOrNull(message.currentVersionIndex)
    } else null

    val modelName = version?.model ?: version?.providerName
    val timestamp = version?.timestamp ?: message.timestamp
    val latencyMs = version?.latencyMs ?: message.latencyMs
    // Token 回退逻辑（对标 Web 版 MessageItem.tsx:158-160）：
    // assistant 优先取版本的 tokens；user 取顶层 tokens；
    // 二者均为空时，用 estimateTokens(content) 估算。
    val tokens = (version?.tokens ?: message.tokens) ?: estimateTokens(content)

    MessageBubbleContent(
        id = message.id, isUser = message.role == Role.USER,
        content = content, streaming = streaming,
        versionCount = message.versions.size,
        currentVersionIndex = message.currentVersionIndex,
        imageIds = message.imageIds,
        onSwitchVersion = if (message.versions.size > 1 && onSwitchVersion != null) {
            { idx -> onSwitchVersion(message.id, idx) } } else null,
        onRegenerate = if (message.role == Role.ASSISTANT && onRegenerate != null) {
            { onRegenerate(message.id) } } else null,
        onEdit = if (message.role == Role.USER && onEdit != null) {
            { txt -> onEdit(message.id, txt) } } else null,
        onSaveEdit = if (message.role == Role.USER && onSaveEdit != null) {
            { txt -> onSaveEdit(message.id, txt) } } else null,
        onDelete = if (onDelete != null) { { onDelete(message.id) } } else null,
        analysisBadge = if (message.isAnalysis == true) {
            if (message.analysisVisibility == AnalysisVisibility.HIDDEN) "🔒 仅自己可见（不占AI上下文）" else "📊 角色关系分析（AI可见）"
        } else null,
        onToggleVisibility = if (message.isAnalysis == true && onToggleAnalysisVisibility != null) {
            { onToggleAnalysisVisibility(message.id) } } else null,
        onArchive = if (message.isAnalysis == true && onArchiveAnalysis != null) {
            { onArchiveAnalysis(message.id) } } else null,
        imageStore = imageStore,
        modifier = modifier,
        // 传递头像和元数据参数
        avatarSource = when {
            !showAvatars -> null
            message.role == Role.USER -> userAvatar
            else -> aiAvatar
        },
        avatarSize = avatarSize,
        showAvatars = showAvatars,
        modelName = modelName,
        timestamp = timestamp,
        latencyMs = latencyMs,
        tokens = tokens,
        showModelName = showModelName,
        showMessageTime = showMessageTime,
        showLatency = showLatency,
        showMessageTokens = showMessageTokens,
        showSessionTokens = showSessionTokens,
        sessionTotalTokens = sessionTotalTokens,
    )
}

@Composable
private fun MessageBubbleContent(
    id: Any, isUser: Boolean, content: String, streaming: Boolean,
    versionCount: Int, currentVersionIndex: Int, imageIds: List<String>?,
    onSwitchVersion: ((Int) -> Unit)?, onRegenerate: (() -> Unit)?,
    onEdit: ((String) -> Unit)?, onSaveEdit: ((String) -> Unit)? = null, onDelete: (() -> Unit)?,
    analysisBadge: String?, onToggleVisibility: (() -> Unit)?, onArchive: (() -> Unit)?,
    imageStore: ImageStore,
    modifier: Modifier,
    // 新增参数：头像和元数据
    avatarSource: AvatarSource? = null,
    avatarSize: Int = 32,
    showAvatars: Boolean = true,
    modelName: String? = null,
    timestamp: Long? = null,
    latencyMs: Long? = null,
    tokens: Int? = null,
    showModelName: Boolean = false,
    showMessageTime: Boolean = false,
    showLatency: Boolean = false,
    showMessageTokens: Boolean = false,
    showSessionTokens: Boolean = false,
    sessionTotalTokens: Int = 0,
) {
    val colors = WwTheme.colors
    // T21：图片本体从存储读回；读不到时退回 T16 的 `📷 N 张图片` 占位。
    val storedImages = rememberMessageImages(imageIds, imageStore)
    val container = if (isUser) colors.userBubble else colors.aiBubble
    val onContainer = if (isUser) colors.onUserBubble else MaterialTheme.colorScheme.onSurface

    // 头像独立在气泡外面，横向排列：用户消息 [气泡 头像]、AI 消息 [头像 气泡]
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // AI 消息：先头像再气泡（靠左对齐时头像自然在左边）
        if (!isUser && avatarSource != null) {
            AvatarImage(source = avatarSource, size = avatarSize.dp)
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            color = container, shape = RoundedCornerShape(12.dp),
            shadowElevation = if (isUser) 1.dp else 0.dp,
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // 元数据行（不再含头像）
                MessageHeaderRow(
                    isUser = isUser,
                    modelName = modelName,
                    timestamp = timestamp,
                    latencyMs = latencyMs,
                    tokens = tokens,
                    showModelName = showModelName,
                    showMessageTime = showMessageTime,
                    showLatency = showLatency,
                    showMessageTokens = showMessageTokens,
                    showSessionTokens = showSessionTokens,
                    sessionTotalTokens = sessionTotalTokens,
                    metadataColor = onContainer.copy(alpha = 0.6f),
                )
                if (analysisBadge != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(analysisBadge, fontSize = WwType.hint,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = MaterialTheme.colorScheme.primary)
                        if (onToggleVisibility != null) {
                            WwTag(
                                text = "切换可见性",
                                onClick = onToggleVisibility,
                                background = WwSurfaces.inset,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onArchive != null) {
                            WwTag(
                                text = "归档到世界观",
                                onClick = onArchive,
                                background = WwSurfaces.inset,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (storedImages.isNotEmpty()) {
                    MessageImageAttachments(storedImages)
                } else if (!imageIds.isNullOrEmpty()) {
                    Text("\uD83D\uDCF7 ${imageIds.size} 张图片",
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = onContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                }
                if (isUser) {
                    // 多行正文用正文档行高（`bodyLineHeight` = 正文 +5），与助手气泡的 markdown 一致；
                    // 用 hint 档的紧凑行高会把 15sp 塞进 16sp 行框（1.07em），看着发挤。
                    Text(text = content, fontSize = WwType.body, lineHeight = WwType.bodyLineHeight, color = onContainer)
                } else {
                    StreamingMarkdown(content = content, streaming = streaming, resetKey = id)
                }
                if (!streaming) {
                    MessageActions(isUser, content, versionCount, currentVersionIndex,
                        onSwitchVersion, onRegenerate, onEdit, onSaveEdit, onDelete)
                }
            }
        }

        // 用户消息：气泡后面加头像（靠右对齐时头像自然在右边）
        if (isUser && avatarSource != null) {
            Spacer(Modifier.width(8.dp))
            AvatarImage(source = avatarSource, size = avatarSize.dp)
        }
    }
}

/**
 * 消息操作行（对标参考版 `MessageItem.tsx:504-600` 的 Action Toolbar）。
 *
 * - 图标一律走全局的 [WwIconButton] + Tabler 线条图标（用户 2026-10-12：
 *   不再用 emoji / `◀ ▶` 字符，和输入栏、侧边栏的图标同一套长相）；
 * - 行上方一条与顶部元信息同规格的**横线**（「气泡的底部横线也加上」）：上下各一个框间间距；
 * - 复制后用 ✓ 回馈 1.2s（参考版 `MessageItem.tsx:167-168`、`:545-551`）；
 * - 编辑只对用户气泡开放（[onEdit] / [onSaveEdit] 由 `MessageBubble` 按角色过滤）：
 *   「保存」= 只改正文不重发，「保存并发送」= 写回 + 删后续 + 重新生成。
 */
@Composable
private fun MessageActions(
    isUser: Boolean, content: String, versionCount: Int, currentVersionIndex: Int,
    onSwitchVersion: ((Int) -> Unit)?, onRegenerate: (() -> Unit)?,
    onEdit: ((String) -> Unit)?, onSaveEdit: ((String) -> Unit)?, onDelete: (() -> Unit)?,
) {
    if (onSwitchVersion == null && onRegenerate == null && onEdit == null && onDelete == null) return
    var editing by remember { mutableStateOf(false) }
    var editDraft by remember(content) { mutableStateOf(content) }
    var copied by remember { mutableStateOf(false) }
    val secondary = if (isUser) WwTheme.colors.onUserBubble.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val actionStyle = WwType.hint
    val actionLineHeight = WwSize.compactTextLineHeight

    // 复制反馈：✓ 显示 1.2s 后自行恢复。
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    Spacer(Modifier.height(WwSize.cardListGap))
    HorizontalDivider(color = bubbleDividerColor())

    if (editing && onEdit != null) {
        Column {
            Spacer(Modifier.height(WwSize.cardListGap))
            WwTextField(value = editDraft, onValueChange = { editDraft = it },
                modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 8, minHeight = 72.dp)
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WwTag(
                    text = "取消",
                    onClick = { editing = false },
                    background = WwSurfaces.inset,
                    contentColor = secondary,
                )
                if (onSaveEdit != null) {
                    Spacer(Modifier.width(8.dp))
                    // 保存：只把正文写回，不删后续、不重发（改错别字用）。
                    WwTag(
                        text = "保存",
                        onClick = { onSaveEdit(editDraft); editing = false },
                        background = WwSurfaces.inset,
                        contentColor = secondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                WwTag(
                    text = "保存并发送",
                    onClick = {
                        val t = editDraft.trim(); if (t.isNotEmpty()) { onEdit(t); editing = false }
                    },
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = WwSize.cardListGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSwitchVersion != null && versionCount > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WwIconButton(
                        icon = TablerIcons.ChevronLeft,
                        contentDescription = "查看上一分支",
                        onClick = { onSwitchVersion(currentVersionIndex - 1) },
                        tint = secondary,
                        enabled = currentVersionIndex > 0,
                        background = Color.Transparent,
                    )
                    Text(
                        "${currentVersionIndex + 1}/$versionCount",
                        fontSize = actionStyle,
                        lineHeight = actionLineHeight,
                        fontFamily = FontFamily.Monospace,
                        color = secondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    WwIconButton(
                        icon = TablerIcons.ChevronRight,
                        contentDescription = "查看下一分支",
                        onClick = { onSwitchVersion(currentVersionIndex + 1) },
                        tint = secondary,
                        enabled = currentVersionIndex < versionCount - 1,
                        background = Color.Transparent,
                    )
                }
            } else { Spacer(Modifier) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onRegenerate != null) {
                    WwIconButton(
                        icon = TablerIcons.Refresh,
                        contentDescription = "重新生成并保存为新分支",
                        onClick = onRegenerate,
                        tint = secondary,
                        background = Color.Transparent,
                    )
                }
                WwIconButton(
                    icon = if (copied) TablerIcons.Check else TablerIcons.Copy,
                    contentDescription = if (copied) "已复制" else "复制内容",
                    onClick = { copyTextToClipboard(content); copied = true },
                    tint = if (copied) MaterialTheme.colorScheme.primary else secondary,
                    background = Color.Transparent,
                )
                if (onEdit != null) {
                    WwIconButton(
                        icon = TablerIcons.Pencil,
                        contentDescription = "修改内容",
                        onClick = { editing = true },
                        tint = secondary,
                        background = Color.Transparent,
                    )
                }
                if (onDelete != null) {
                    WwIconButton(
                        icon = TablerIcons.Trash,
                        contentDescription = "删除此消息",
                        onClick = onDelete,
                        tint = MaterialTheme.colorScheme.error,
                        background = Color.Transparent,
                    )
                }
            }
        }
    }
}

/**
 * 气泡内分区线颜色（用户 2026-10-13：分区线连接主题「模块2」）。
 *
 * 取模块2 色阶 500 档，再降到低透明档 —— 气泡底有用户色 / 助手色两种，
 * 用固定的中性描边色会与主题不搭；连接主题辅助色后换主题时一起变。
 */
@Composable
private fun bubbleDividerColor(): Color {
    val colors = WwTheme.colors
    val m2 = colors.vars.m2.getOrNull(5)?.let { CssColors.parse(it) } ?: colors.primaryHover
    return m2.copy(alpha = 0.35f)
}

/** 消息元数据行（模型名、时间、耗时、Token），超宽自动换行。头像已移到气泡外。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageHeaderRow(
    isUser: Boolean,
    modelName: String?,
    timestamp: Long?,
    latencyMs: Long?,
    tokens: Int?,
    showModelName: Boolean,
    showMessageTime: Boolean,
    showLatency: Boolean,
    showMessageTokens: Boolean,
    showSessionTokens: Boolean,
    sessionTotalTokens: Int,
    metadataColor: androidx.compose.ui.graphics.Color,
) {
    // 如果没有任何要显示的内容，跳过整个头部
    if (!showModelName && !showMessageTime && !showLatency && !showMessageTokens && !showSessionTokens) {
        return
    }

    // 元数据走「框外提示」档（WwType.hint），随界面字体大小一起缩放。
    val metadataFontSize = WwType.hint
    val metadataParts = mutableListOf<String>()

    if (showModelName && modelName != null) {
        metadataParts.add(modelName)
    }
    if (showMessageTime && timestamp != null) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        metadataParts.add(timeStr)
    }
    if (showLatency && latencyMs != null) {
        val latencyStr = if (latencyMs < 1000) "${latencyMs}ms" else "%.1fs".format(latencyMs / 1000.0)
        metadataParts.add(latencyStr)
    }
    if (showMessageTokens && tokens != null) {
        metadataParts.add("${tokens} tok")
    }
    // 本窗口总 Token 只挂在 assistant 气泡上（对标 Web `MessageItem.tsx:453`）。
    if (showSessionTokens && !isUser && sessionTotalTokens > 0) {
        metadataParts.add("窗口 ${formatTokenCount(sessionTotalTokens)} tok")
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        metadataParts.forEachIndexed { index, part ->
            Text(
                text = part,
                fontSize = metadataFontSize,
                fontFamily = if (part.contains(Regex("\\d"))) FontFamily.Monospace else null,
                color = metadataColor,
            )
            if (index < metadataParts.size - 1) {
                Spacer(Modifier.size(6.dp))
                Text("·", fontSize = metadataFontSize, color = metadataColor)
                Spacer(Modifier.size(6.dp))
            }
        }
    }

    if (metadataParts.isNotEmpty()) {
        // 元信息与正文之间的一条横线（用户 2026-10-12）：上下各一个框间间距（cardListGap）。
        // 颜色连接主题「模块2」色阶（用户 2026-10-13）。
        Spacer(Modifier.height(WwSize.cardListGap))
        HorizontalDivider(color = bubbleDividerColor())
        Spacer(Modifier.height(WwSize.cardListGap))
    }
}
