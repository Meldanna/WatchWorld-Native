package com.watchworld.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ModelFavorite
import com.watchworld.model.TagDisplayMode
import com.watchworld.model.TimelineBranch
import com.watchworld.ui.components.WwIconButton
import com.watchworld.ui.image.decodeDataUrlImage
import com.watchworld.ui.image.rememberMessageImages
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsLeftRight
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.Book
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Eye
import compose.icons.tablericons.FileUpload
import compose.icons.tablericons.GitBranch
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Search
import compose.icons.tablericons.Send
import compose.icons.tablericons.Server
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Square
import compose.icons.tablericons.Star
import compose.icons.tablericons.Tree
import compose.icons.tablericons.World
import compose.icons.tablericons.X

/**
 * 聊天输入栏。前置控件只负责展示当前窗口状态和转发点击事件；草稿、发送与停止逻辑保持独立。
 *
 * ### 布局（自上而下，参考项目 `ChatInput.tsx:272-624`）
 * 1. **时间线分支标签行**（仅开启记忆整理且已有分支时）：
 *    `[字母/文字切换] [分支药丸…横向滚动]` —— 点某个分支即把**后续消息**归到它名下。
 *    上一条用户消息所在的分支会被自动挪到最前面（参考版 `orderedTimelines`）。
 * 2. **Agent 行**：`[Agent 胶囊] [窗口功能] [可见性] [时间线树] [提示词]`
 * 3. **输入框**：文字区 + 底部功能区 `[图片] [文件] [联网] …… [发送/停止]`
 *
 * ⚠️ 整栏左右各留 12dp：少了这层留白，输入框会贴死窗口边缘（参考版是 `px-3 sm:px-6`）。
 * ⚠️ 底部四个图标是**透明底 + 染色图案**（[TransparentIconButton]），不是实心色块。
 */
@Composable
fun ChatInputBar(
    streaming: Boolean,
    onSend: (String, List<String>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 输入框正文（**受 ViewModel 持有**，逐键落 kv）。不要在这里自己 `remember`。
     * 传默认空串时就是一个不受控的空输入框（截图 / 预览用）。
     */
    draft: String = "",
    /** 正文变化：调用方写回 ViewModel（[com.watchworld.ui.chat.ChatViewModel.setDraft]）。 */
    onDraftChange: (String) -> Unit = {},
    timelineEnabled: Boolean = false,
    /** 本窗口的全部分支（用于标签行）。 */
    timelines: List<TimelineBranch> = emptyList(),
    activeTimelineId: String? = null,
    tagDisplayMode: TagDisplayMode = TagDisplayMode.DESC,
    /** 上一条用户消息所属分支：会被自动排到标签行最前（参考版 `orderedTimelines`）。 */
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
    webSearchEnabled: Boolean = false,
    onToggleWebSearch: () -> Unit = {},
    /** 全部供应商配置（模型下拉的候选来源；只用有 apiKey 且有模型的）。 */
    providers: List<ApiProviderConfig> = emptyList(),
    /** 本窗口当前生效的供应商 ID。 */
    activeProviderId: String = "",
    /** 本窗口当前生效的模型名。 */
    activeModel: String = "",
    /** 切换模型（同 Provider 只改模型；跨 Provider 连带改会话 providerId 与全局 activeProviderId）。 */
    onSelectProviderAndModel: (String, String) -> Unit = { _, _ -> },
    /** 已收藏的模型（`(providerId, modelName)` 对），下拉里打星标。 */
    favorites: List<ModelFavorite> = emptyList(),
    /** 收藏 / 取消收藏。 */
    onToggleFavorite: (String, String) -> Unit = { _, _ -> },
    /** 打开完整模型选择器（搜索 / 只看收藏）。 */
    onOpenModelSelector: () -> Unit = {},
    /** 挑图片：回调参数是落盘后的图片 id，由输入栏记进待发送列表。 */
    onImportImage: ((List<String>) -> Unit) -> Unit = {},
    /** 挑文本文件：回调参数是要追加到草稿的正文片段。 */
    onImportFile: ((String) -> Unit) -> Unit = {},
) {
    // 只保留「待发送图片」在本地；正文由外部持有（草稿要保持到下次打开窗口）。
    var pendingImageIds by remember { mutableStateOf<List<String>>(emptyList()) }

    fun submit() {
        if (streaming) return
        val text = draft.trim()
        if (text.isEmpty() && pendingImageIds.isEmpty()) return
        onSend(text, pendingImageIds)
        // 正文不在这里清：它由 ViewModel 在消息真正落库后清（避免发送被拒时把草稿吃了）。
        pendingImageIds = emptyList()
    }

    val hasContent = draft.isNotBlank() || pendingImageIds.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // ── 1. 时间线分支标签行 ──
        if (timelineEnabled && timelines.isNotEmpty()) {
            TimelineBranchRow(
                timelines = timelines,
                activeTimelineId = activeTimelineId,
                tagDisplayMode = tagDisplayMode,
                lastUserMessageTimelineId = lastUserMessageTimelineId,
                onSelectTimeline = onSelectTimeline,
                onToggleTagDisplayMode = onToggleTagDisplayMode,
            )
            Spacer(Modifier.height(WwSize.cardListGap))
        }

        // ── 2. Agent 行 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.clickable(onClick = onOpenAgent),
                shape = RoundedCornerShape(10.dp),
                color = WwSurfaces.card,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = WwSize.boxVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (agentAvatar.isNotBlank()) {
                        Text(agentAvatar, fontSize = WwType.body)
                    } else {
                        Icon(
                            imageVector = TablerIcons.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = agentName.ifBlank { "未配置 Agent" },
                        fontSize = WwType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 名字过长时优先压缩自己，别把右边四个入口挤出屏幕。
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            }

            InputFeatureButton(TablerIcons.Settings, "窗口功能", onOpenWindowSettings)
            InputFeatureButton(TablerIcons.Eye, "当前窗口可见性与渲染", onOpenVisibility)
            InputFeatureButton(TablerIcons.Tree, "记忆树（时间线分支管理）", onOpenTimeline)
            InputFeatureButton(TablerIcons.Book, "提示词", onOpenPrompts)
        }

        Spacer(Modifier.height(WwSize.cardListGap))

        // ── 3. 输入框容器：文字区 + 底部功能区 ──
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = WwSurfaces.windowBase,
            border = BorderStroke(1.dp, WwSurfaces.cardBorder),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 输入框的框内留白：每侧 2sp（原先 12dp，底部图标离框边快到半个字高）。
            Column(modifier = Modifier.fillMaxWidth().padding(WwSize.boxVerticalPadding)) {
                if (pendingImageIds.isNotEmpty()) {
                    PendingImageRow(
                        imageIds = pendingImageIds,
                        onRemove = { id -> pendingImageIds = pendingImageIds - id },
                    )
                    Spacer(Modifier.height(WwSize.cardListGap))
                }

                // 文字输入区（textarea：自动增高、封顶后内滚）
                basicMessageInput(
                    value = draft,
                    onValueChange = onDraftChange,
                    enabled = !streaming,
                    onCtrlEnter = ::submit,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = WwSize.cardListGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransparentIconButton(
                        icon = TablerIcons.Photo,
                        contentDescription = "添加图片（也可直接粘贴）",
                        onClick = {
                            if (!streaming) {
                                onImportImage { ids -> pendingImageIds = (pendingImageIds + ids).distinct() }
                            }
                        },
                        tint = MaterialTheme.colorScheme.primary,
                        enabled = !streaming,
                    )
                    TransparentIconButton(
                        icon = TablerIcons.FileUpload,
                        contentDescription = "上传文本文件并添加到输入框",
                        onClick = {
                            if (!streaming) {
                                onImportFile { content ->
                                    onDraftChange(if (draft.isBlank()) content else "$draft\n\n$content")
                                }
                            }
                        },
                        tint = MaterialTheme.colorScheme.primary,
                        enabled = !streaming,
                    )
                    // 联网开关：紧跟在两个导入后面（用户 2026-10-10 定案），图标用地球。
                    TransparentIconButton(
                        icon = TablerIcons.World,
                        contentDescription = if (webSearchEnabled) "关闭本窗口联网权限" else "开启本窗口联网权限",
                        onClick = onToggleWebSearch,
                        tint = if (webSearchEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 模型切换药丸（用户 2026-10-11 加）：参考版顺序 图片→文件→联网→模型→spacer→发送。
                    ModelSwitchPill(
                        providers = providers,
                        activeProviderId = activeProviderId,
                        activeModel = activeModel,
                        favorites = favorites,
                        onSelect = onSelectProviderAndModel,
                        onToggleFavorite = onToggleFavorite,
                        onOpenFullSelector = onOpenModelSelector,
                    )
                    Spacer(Modifier.weight(1f))
                    SendIconButton(
                        streaming = streaming,
                        hasContent = hasContent,
                        onSend = ::submit,
                        onStop = onStop,
                    )
                }
            }
        
    }
}
}

/**
 * 时间线分支标签行（参考版 `ChatInput.tsx:326-385`）。
 *
 * - 最左是**字母/文字切换**：`DESC` 显示 `A1·学园线`，`CODE` 只显示 `A1`。
 * - 分支药丸横向滚动（分支多时不会把行撑爆）；选中项用该分支自己的色板打底。
 * - 上一条用户消息所在分支**自动排到最前**（参考版 `orderedTimelines` 的 useMemo）。
 * - 隐藏分支（`visible == false`）在药丸里补一个 `(隐)` 角标。
 */
@Composable
private fun TimelineBranchRow(
    timelines: List<TimelineBranch>,
    activeTimelineId: String?,
    tagDisplayMode: TagDisplayMode,
    lastUserMessageTimelineId: String?,
    onSelectTimeline: (String) -> Unit,
    onToggleTagDisplayMode: () -> Unit,
) {
    val ordered = remember(timelines, lastUserMessageTimelineId) {
        val lastIdx = timelines.indexOfFirst { it.id == lastUserMessageTimelineId }
        if (lastIdx > 0) listOf(timelines[lastIdx]) + timelines.filterIndexed { i, _ -> i != lastIdx }
        else timelines
    }
    val effectiveActiveId = activeTimelineId?.ifEmpty { null } ?: timelines.firstOrNull()?.id

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 标签展示模式切换：字母数字+文字 ⇄ 仅字母数字
        Surface(
            modifier = Modifier.clickable(onClick = onToggleTagDisplayMode),
            shape = RoundedCornerShape(10.dp),
            color = WwSurfaces.inset,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = WwSize.boxVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = TablerIcons.ArrowsLeftRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (tagDisplayMode == TagDisplayMode.CODE) "字母" else "文字",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ordered.forEach { branch ->
                val isSelected = branch.id == effectiveActiveId
                val code = branch.codeTag?.ifEmpty { null } ?: branch.tag.ifEmpty { null } ?: "通用"
                val desc = branch.descriptionTag?.ifEmpty { null } ?: branch.name.ifEmpty { null } ?: code
                val label = if (tagDisplayMode == TagDisplayMode.CODE) code else "$code·$desc"
                TimelineBranchChip(
                    label = label,
                    selected = isSelected,
                    hidden = branch.visible == false,
                    onClick = { onSelectTimeline(branch.id) },
                )
            }
        }
    }
}

/** 单个分支药丸：12dp 分支图标 + 标签（超长省略），选中时主色实底白字。 */
@Composable
private fun TimelineBranchChip(
    label: String,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else WwSurfaces.inset,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = WwSize.compactFrameVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = TablerIcons.GitBranch,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                fontFamily = FontFamily.Monospace,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            if (hidden) {
                Text(
                    text = "(隐)",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 待发送图片预览：56dp 缩略图 + 右上角移除角标。
 *
 * 没有这一行，挑完图片在界面上完全没有反馈，用户会以为「导入没生效」。
 */
@Composable
private fun PendingImageRow(imageIds: List<String>, onRemove: (String) -> Unit) {
    val images = rememberMessageImages(imageIds)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        images.forEach { image ->
            val bitmap = remember(image.id) { decodeDataUrlImage(image.dataUrl) }
            Box {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = image.name ?: "待发送图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WwSurfaces.inset),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.Photo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), CircleShape)
                        .clickable { onRemove(image.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = "移除这张图片",
                        modifier = Modifier.size(11.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 文字输入区（对标参考版 textarea：chat-text-sm resize-none）。
 *
 * 用 BasicTextField 的 minLines/maxLines 实现「自动增高、封顶后内滚」。
 */
private const val MAX_INPUT_LINES = 8

@Composable
private fun basicMessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onCtrlEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(
            fontSize = WwType.body,
            lineHeight = WwType.bodyLineHeight,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        minLines = 1,
        maxLines = MAX_INPUT_LINES,
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                    onCtrlEnter()
                    true
                } else {
                    false
                }
            },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = "输入消息…",
                        fontSize = WwType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * 透明背景按钮：32dp 点击区，图标染色（tint 控制），背景全透明。
 * 点击时通过 Material3 IconButton 默认的 ripple 反馈。
 */
@Composable
private fun TransparentIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    // 图标按钮统一走全局组件（用户 2026-10-12）：24dp 紧凑点击区 + 16dp 图标 + 透明底。
    WwIconButton(
        icon = icon,
        contentDescription = contentDescription.orEmpty(),
        onClick = onClick,
        tint = tint,
        background = Color.Transparent,
        iconSize = 16.dp,
        enabled = enabled,
    )
}

/** 与 SidebarSheet 功能入口一致：18dp primary 图标、紧凑点击区、分层主题底色。 */
@Composable
private fun InputFeatureButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.card,
        tonalElevation = 0.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 发送 / 停止二态按钮（透明底 + 染色图案）。
 *
 * ⚠️ **永远可点**（用户 2026-10-10 定案）：草稿为空时也允许点击 ——
 *    此时若模型正在输出就当作「停止」；既没内容也没在输出则什么都不做。
 */
@Composable
private fun SendIconButton(
    streaming: Boolean,
    hasContent: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val stopRed = Color(0xFFEF4444)
    val iconTint: Color
    val icon: ImageVector
    val description: String
    when {
        streaming -> {
            iconTint = stopRed
            icon = TablerIcons.Square
            description = "停止生成"
        }
        hasContent -> {
            iconTint = MaterialTheme.colorScheme.primary
            icon = TablerIcons.Send
            description = "发送消息"
        }
        else -> {
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            icon = TablerIcons.Send
            description = "发送消息（当前无可发送内容）"
        }
    }
    IconButton(
        onClick = { if (streaming) onStop() else onSend() },
        modifier = Modifier.size(24.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = iconTint,
        )
    }
}

/**
 * 模型切换药丸 + 供应商分组下拉（参考版 `ChatInput.tsx:603-782`）。
 *
 * - 药丸：`[Server 图标] 模型名 [ChevronDown]`，展开时主色实底白字，高 32dp 对齐功能区其它按钮。
 * - 下拉**向上弹出**（输入区贴在窗口底部，Compose 的 DropdownMenu 自动上翻）。
 * - 只列**有 apiKey 且有模型**的供应商（参考版 `ChatInput.tsx:143-145` 的过滤）。
 * - 每组可折叠；点模型即切换，当前项打 ✓。
 *
 * 与参考版的差异（有意）：不做供应商拖拽排序（Native 侧没有可设 `modelGroupOrder` 的 UI）。
 */
@Composable
private fun ModelSwitchPill(
    providers: List<ApiProviderConfig>,
    activeProviderId: String,
    activeModel: String,
    favorites: List<ModelFavorite>,
    onSelect: (String, String) -> Unit,
    onToggleFavorite: (String, String) -> Unit,
    onOpenFullSelector: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val groups = remember(providers) {
        providers.filter { !it.apiKey.isNullOrBlank() && it.models.isNotEmpty() }
    }
    // 命中集合：参考版用 `favorites.some(...)` 线性扫，这里摊平成 Set 做 O(1) 判断。
    val favoriteKeys = remember(favorites) {
        favorites.map { it.providerId + ' ' + it.modelName }.toSet()
    }

    Box {
        Surface(
            modifier = Modifier.clickable { expanded = !expanded },
            shape = RoundedCornerShape(10.dp),
            color = if (expanded) MaterialTheme.colorScheme.primary else WwSurfaces.inset,
            tonalElevation = 0.dp,
        ) {
            Row(
                // 胶囊每侧 2sp（框总高 = 文字行 + 4）；同排的图标按钮是**透明** 32dp 点击区
                // （图标只有 16dp），所以胶囊矮一截也不会跟它们「高低不齐」。
                modifier = Modifier.padding(horizontal = 10.dp, vertical = WwSize.boxVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = TablerIcons.Server,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = activeModel.ifBlank { "选择模型" },
                    fontSize = WwType.hint,
                    // 显式紧凑行高：否则会继承 Material 的 20sp 行高，把胶囊撑高。
                    lineHeight = WwSize.compactTextLineHeight,
                    // 模型名全项目都走等宽（设置里的可用模型列表、侧边栏模型行……），这里漏了。
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = WwSurfaces.dialogBase,
            border = BorderStroke(1.dp, WwSurfaces.cardBorder),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (groups.isEmpty()) {
                    Text(
                        text = "暂无可用模型",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding),
                        textAlign = TextAlign.Center,
                    )
                }
                groups.forEach { provider ->
                    val isCurrent = provider.id == activeProviderId
                    val isCollapsed = provider.id in collapsed
                    // 供应商标题行：展开箭头 + 名字 + (当前) + 模型数
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                collapsed = if (isCollapsed) collapsed - provider.id else collapsed + provider.id
                            }
                            .padding(horizontal = 10.dp, vertical = WwSize.listRowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (isCollapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = provider.name,
                                fontSize = WwType.body,
                                lineHeight = WwSize.compactTextLineHeight,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isCurrent) {
                                Text(
                                    text = "(当前)",
                                    fontSize = WwType.hint,
                                    lineHeight = WwSize.compactTextLineHeight,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = provider.models.size.toString(),
                            fontSize = WwType.hint,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isCollapsed) {
                        provider.models.forEach { model ->
                            val isActive = isCurrent && model == activeModel
                            val isFavorite = (provider.id + ' ' + model) in favoriteKeys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(provider.id, model)
                                        expanded = false
                                    }
                                    .padding(start = 26.dp, end = 4.dp, top = WwSize.listRowVerticalPadding, bottom = WwSize.listRowVerticalPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isActive) "✓ $model" else model,
                                    fontSize = WwType.body,
                                    lineHeight = WwSize.compactTextLineHeight,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                // 星标：独立点击区，点它只切收藏、不选中该模型。
                                Icon(
                                    imageVector = TablerIcons.Star,
                                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleFavorite(provider.id, model) }
                                        .padding(5.dp),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }

            // 底部入口（参考版 `ChatInput.tsx:762-777`）：完整选择器可搜索、可只看收藏。
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).background(WwSurfaces.cardBorderSubtle),
            )
            Text(
                text = "打开完整模型选择器（搜索 / 收藏）",
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = false
                        onOpenFullSelector()
                    }
                    .padding(vertical = WwSize.boxVerticalPadding),
            )
        }
    }
}
