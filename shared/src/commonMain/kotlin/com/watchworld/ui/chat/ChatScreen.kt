package com.watchworld.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ChatMessage
import com.watchworld.model.ModelFavorite
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.model.TagDisplayMode
import com.watchworld.model.TimelineBranch
import com.watchworld.domain.formatTokenCount
import com.watchworld.ui.components.AvatarSource
import com.watchworld.ui.theme.CssColors
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowDown
import compose.icons.tablericons.ArrowUp

/**
 * 聊天消息列表（T14 起吃正式模型 [ChatMessage]；T16 加消息级操作回调）。
 *
 * T02/T03 的临时 `UiMessage` 版界面已由本文件 + [ChatViewModel] 取代：
 * 消息来自 `ChatSession.messages`（存档契约形状），正文按角色取值 ——
 * assistant 在 `versions[currentVersionIndex]`，user 在顶层 `content`
 * （见 `model/Message.kt` 的角色不对称说明）。
 */
@Composable
fun ChatScreen(
    session: ChatSession?,
    streamingMessageId: String?,
    onSend: (String, List<String>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** 本窗口输入框草稿（由 ViewModel 持有并逐键落 kv）。 */
    draft: String = "",
    onDraftChange: (String) -> Unit = {},
    onSwitchVersion: ((messageId: String, newIndex: Int) -> Unit)? = null,
    onRegenerate: ((messageId: String) -> Unit)? = null,
    onEdit: ((messageId: String, newContent: String) -> Unit)? = null,
    /** 只保存编辑、不重发（气泡编辑框里的「保存」）。 */
    onSaveEdit: ((messageId: String, newContent: String) -> Unit)? = null,
    onDeleteMessage: ((messageId: String) -> Unit)? = null,
    onToggleAnalysisVisibility: ((messageId: String) -> Unit)? = null,
    onArchiveAnalysis: ((messageId: String) -> Unit)? = null,
    timelineEnabled: Boolean = false,
    /** 本窗口的全部分支（输入栏标签行）。 */
    timelines: List<TimelineBranch> = emptyList(),
    activeTimelineId: String? = null,
    tagDisplayMode: TagDisplayMode = TagDisplayMode.DESC,
    lastUserMessageTimelineId: String? = null,
    onSelectTimeline: (String) -> Unit = {},
    onToggleTagDisplayMode: () -> Unit = {},
    agentAvatar: String = "",
    agentName: String = "未配置 Agent",
    onOpenAgent: () -> Unit = {},
    onOpenWindowSettings: () -> Unit = {},
    onOpenVisibility: () -> Unit = {},
    onOpenPrompts: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    /** 本窗口是否允许联网（`ChatSession.webSearchEnabled`）。 */
    webSearchEnabled: Boolean = false,
    /** 切换本窗口联网授权。 */
    onToggleWebSearch: () -> Unit = {},
    /** 模型下拉：全部供应商（只用有 apiKey 且有模型的）。 */
    providers: List<ApiProviderConfig> = emptyList(),
    activeProviderId: String = "",
    activeModel: String = "",
    onSelectProviderAndModel: (String, String) -> Unit = { _, _ -> },
    /** 已收藏的模型（模型下拉与完整选择器里打星标）。 */
    favorites: List<ModelFavorite> = emptyList(),
    /** 收藏 / 取消收藏一个模型。 */
    onToggleFavorite: (String, String) -> Unit = { _, _ -> },
    /** 打开完整模型选择器（搜索 / 只看收藏）。 */
    onOpenModelSelector: () -> Unit = {},
    /** 导入图片文件（输入栏图标按钮）。 */
    onImportImage: (((List<String>) -> Unit) -> Unit) = {},
    /** 导入文本/文件到输入框（输入栏图标按钮）。 */
    onImportFile: (((String) -> Unit) -> Unit) = {},
    /** 用户头像来源（全局设置）。 */
    userAvatar: AvatarSource? = null,
    /** AI 头像来源（窗口级覆盖优先于 Agent 默认，顺序由调用方给）。 */
    aiAvatar: AvatarSource? = null,
    /** 头像大小（24-48px）。 */
    avatarSize: Int = 32,
    /** 消息区是否显示头像。 */
    showAvatars: Boolean = true,
    /** 相邻气泡之间的垂直间距（px，0–256，默认 8）。 */
    bubbleSpacing: Int = 8,
    /** 是否显示模型名。 */
    showModelName: Boolean = false,
    /** 是否显示消息时间。 */
    showMessageTime: Boolean = false,
    /** 是否显示耗时。 */
    showLatency: Boolean = false,
    /** 是否显示消息 Token 数。 */
    showMessageTokens: Boolean = false,
    /** 是否显示本窗口总 Token（列表顶徽章 + 气泡内一条）。 */
    showSessionTokens: Boolean = false,
    /** 本窗口累计 Token（`estimateSessionTokens`）。 */
    sessionTotalTokens: Int = 0,
) {
    val messages = session?.messages.orEmpty()
    val isStreaming = streamingMessageId != null
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lastIndex = messages.lastIndex
    val lastLength = messages.lastOrNull()?.let { previewOf(it).length } ?: 0

    // 回底回顶按钮的显示逻辑（对标 Web 版 ChatMessageList.tsx）：
    // - 回到顶部按钮：当 firstVisibleItemIndex > 0 时显示（页面不在顶部）
    // - 回到底部按钮：当 canScrollForward == true 时显示（页面不在底部）
    var showScrollTop by remember { mutableStateOf(false) }
    var showScrollBottom by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            val canScrollForward = listState.canScrollForward
            Triple(firstIndex, firstOffset, canScrollForward)
        }.map { (firstIndex, firstOffset, canScrollForward) ->
            // 回到顶部按钮：当 firstVisibleItemIndex > 0，或者滚动偏移超过阈值
            val scrolledDown = firstIndex > 0 || firstOffset > 0
            // 回到底部按钮：当还能向前滚动（说明未到底部）
            val notAtBottom = canScrollForward
            scrolledDown to notAtBottom
        }.distinctUntilChanged().collect { (top, bottom) ->
            showScrollTop = top
            showScrollBottom = bottom
        }
    }

    LaunchedEffect(session?.id, lastIndex, lastLength) {
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                Text(
                    text = "发送一条消息，看真实模型回复逐字出现。",
                    fontSize = WwType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                // ⚠️ 空列表时没有 `weight(1f)` 的列表撑开，输入栏会贴到提示语下面（不在底部）。
                //    用等权 Spacer 把输入栏压回窗口底部。
                Spacer(Modifier.weight(1f))
            } else {
                // 本窗口总 Token 徽章（对标 Web `ChatMessageList.tsx:200`）。
                if (showSessionTokens && sessionTotalTokens > 0) {
                    Text(
                        text = "本窗口总 Token：~${formatTokenCount(sessionTotalTokens)} tok（${messages.size} 条消息）",
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwSize.cardListGap),
                    )
                }
                // 消息区用 Box 包裹，让悬浮按钮只在消息区 overlay，不入侵输入框分区。
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(bubbleSpacing.dp),
                    ) {
                        items(items = messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                streaming = message.id == streamingMessageId,
                                onSwitchVersion = if (!isStreaming) onSwitchVersion else null,
                                onRegenerate = if (!isStreaming) onRegenerate else null,
                                onEdit = if (!isStreaming) onEdit else null,
                                onSaveEdit = if (!isStreaming) onSaveEdit else null,
                                onDelete = if (!isStreaming) onDeleteMessage else null,
                                onToggleAnalysisVisibility = if (!isStreaming) onToggleAnalysisVisibility else null,
                                onArchiveAnalysis = if (!isStreaming) onArchiveAnalysis else null,
                                // 头像和显示设置
                                userAvatar = userAvatar,
                                aiAvatar = aiAvatar,
                                avatarSize = avatarSize,
                                showAvatars = showAvatars,
                                showModelName = showModelName,
                                showMessageTime = showMessageTime,
                                showLatency = showLatency,
                                showMessageTokens = showMessageTokens,
                                showSessionTokens = showSessionTokens,
                                sessionTotalTokens = sessionTotalTokens,
                            )
                        }
                    }

                    // ── 回顶按钮（靠右，靠上）──
                    // 仅当页面不在顶部（向下滚动）时显示；到达顶部后自动隐藏。
                    if (showScrollTop) {
                        ScrollFloatingButton(
                            icon = TablerIcons.ArrowUp,
                            contentDescription = "回到顶部",
                            onClick = { coroutineScope.launch { listState.scrollToItem(0) } },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 24.dp, end = 8.dp),
                        )
                    }

                    // ── 回底按钮（靠右，靠下）──
                    // 仅当页面不在底部（向上滑动）时显示；到达底部后自动隐藏。
                    if (showScrollBottom) {
                        ScrollFloatingButton(
                            icon = TablerIcons.ArrowDown,
                            contentDescription = "回到底部",
                            onClick = { coroutineScope.launch { listState.scrollToItem(lastIndex) } },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = WwSize.cardListGap, end = 8.dp),
                        )
                    }
                }
            }

            ChatInputBar(
                streaming = isStreaming,
                onSend = onSend,
                onStop = onStop,
                draft = draft,
                onDraftChange = onDraftChange,
                timelineEnabled = timelineEnabled,
                timelines = timelines,
                activeTimelineId = activeTimelineId,
                tagDisplayMode = tagDisplayMode,
                lastUserMessageTimelineId = lastUserMessageTimelineId,
                onSelectTimeline = onSelectTimeline,
                onToggleTagDisplayMode = onToggleTagDisplayMode,
                agentAvatar = agentAvatar,
                agentName = agentName,
                onOpenAgent = onOpenAgent,
                onOpenWindowSettings = onOpenWindowSettings,
                onOpenVisibility = onOpenVisibility,
                onOpenPrompts = onOpenPrompts,
                onOpenTimeline = onOpenTimeline,
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = onToggleWebSearch,
                providers = providers,
                activeProviderId = activeProviderId,
                activeModel = activeModel,
                onSelectProviderAndModel = onSelectProviderAndModel,
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onOpenModelSelector = onOpenModelSelector,
                onImportImage = onImportImage,
                onImportFile = onImportFile,
            )
        }
    }
}

/** 只用于滚动跟随的长度探针:与气泡显示的文本同源。 */
private fun previewOf(message: ChatMessage): String = if (message.role == Role.ASSISTANT) {
    message.versions.getOrNull(message.currentVersionIndex)?.content
        ?: message.versions.firstOrNull()?.content
        ?: message.content
} else {
    message.content
}

/**
 * 圆形悬浮滚动按钮(对标 Web 版 `fixed bottom-36/bottom-24 right-5`)。
 *
 * 样式:圆形(CircleShape)、模块2 色阶背景、白色图标、细描边。
 * ⚠️ **连接主题色模块2**:背景取 `vars.m2[6]`(对标 Material `secondary = m2[6]`),
 *     描边取 `vars.m2[5]`,图标 tint 用 `onUserBubble`(白色)——
 *     主题辅助色变化时按钮一起变化。
 */
@Composable
private fun ScrollFloatingButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WwTheme.colors
    // 模块2 色阶:背景取 600 档(与 Material secondary 同源),描边取 500 档。
    val m2Bg = colors.vars.m2.getOrNull(6)?.let { CssColors.parse(it) } ?: colors.primaryBg
    val m2Border = colors.vars.m2.getOrNull(5)?.let { CssColors.parse(it) } ?: colors.primaryHover
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(m2Bg)
            .border(1.dp, m2Border.copy(alpha = 0.3f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = colors.onUserBubble, // 连接主题色 —— 用户气泡文字色(通常为白色)
        )
    }
}
