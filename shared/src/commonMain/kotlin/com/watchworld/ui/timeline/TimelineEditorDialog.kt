package com.watchworld.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.GitBranch
import compose.icons.tablericons.X
import com.watchworld.domain.TIMELINE_COLORS
import com.watchworld.domain.TimelineDraft
import com.watchworld.domain.formatTimelineParentLabel
import com.watchworld.model.TimelineBranch
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwModalScrim
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType

/**
 * Timeline branch create/edit form.
 *
 * 1:1 replication of TimelineModal.tsx:717-893.
 * TS puts all fields in a single scrollable body with save/cancel at the bottom of the scroll.
 * No pinned footer -- save button scrolls with the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEditorDialog(
    initial: TimelineDraft,
    timelines: List<TimelineBranch>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (TimelineDraft) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var codeTag by remember(initial) { mutableStateOf(initial.codeTag) }
    var descriptionTag by remember(initial) { mutableStateOf(initial.descriptionTag) }
    var colorId by remember(initial) { mutableStateOf(initial.color) }
    var parentId by remember(initial) { mutableStateOf(initial.parentId.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var plotSummary by remember(initial) { mutableStateOf(initial.plotSummary) }
    var parentMenuOpen by remember { mutableStateOf(false) }

    val isEditing = initial.editingId != null
    val canSave = name.isNotBlank() && codeTag.isNotBlank() && !saving

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // TS line 717: fixed inset-0 z-[80]
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：全应用统一款式（毛玻璃雾面，不压黑），并登记自己让主内容去模糊。
            WwModalScrim(onDismiss = onDismiss)

            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = WwPad.shell),
                contentAlignment = Alignment.Center,
            ) {
                // TS line 723: max-w-2xl max-h-[90vh] flex flex-col
                Surface(
                    modifier = Modifier
                        .widthIn(max = 672.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.90f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = WwSurfaces.dialogBase,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                ) {
                    Column {
                        // TS lines 724-736: Header
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = WwPad.content),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                TablerIcons.GitBranch, null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (isEditing) "编辑时间线分支信息"
                                else "创建新时间线分支 (消息树节点)",
                                fontSize = WwType.title,
                                fontWeight = WwType.titleWeight,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(TablerIcons.X, "关闭")
                            }
                        }
                        HorizontalDivider(color = WwSurfaces.cardBorderSubtle)

                        // TS line 738: flex-1 overflow-y-auto p-4 space-y-4
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = WwPad.content),
                            verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
                        ) {
                            // TS lines 740-753: code tag
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Text("标签编号 *", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                CompactField(
                                    value = codeTag,
                                    onValueChange = { codeTag = it.uppercase() },
                                    placeholder = "例如：A12",
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = WwType.hint,
                                        lineHeight = WwSize.compactTextLineHeight,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // TS lines 755-778: composed display name
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Text("标签全称 *", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                                    color = WwSurfaces.inset,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(color = WwSurfaces.card, shape = RoundedCornerShape(10.dp)) {
                                            Text(
                                                text = codeTag.uppercase().ifBlank { "A1" },
                                                style = TextStyle(
                                                    fontSize = WwType.hint,
                                                    lineHeight = WwSize.compactTextLineHeight,
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = WwSize.formFieldVerticalPadding,
                                                ),
                                            )
                                        }
                                        // Name part uses a plain BasicTextField — no outline, no padding overhead
                                        val nameInteraction = remember { MutableInteractionSource() }
                                        BasicTextField(
                                            value = name,
                                            onValueChange = { name = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = WwType.hint,
                                                lineHeight = WwSize.compactTextLineHeight,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            interactionSource = nameInteraction,
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                Box(
                                                    Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = WwSize.formFieldVerticalPadding,
                                                    ),
                                                ) {
                                                    if (name.isEmpty()) {
                                                        Text(
                                                            "例如：学园线·结识好友",
                                                            fontSize = WwType.hint,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        )
                                                    }
                                                    inner()
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            // TS lines 780-793: description tag
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Text("描述标签", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                CompactField(
                                    value = descriptionTag,
                                    onValueChange = { descriptionTag = it },
                                    placeholder = "例如：学园时期、首次结识好友、未加入组织",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // TS lines 795-817: color picker
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Text("分支代表色彩", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    TIMELINE_COLORS.forEach { option ->
                                        ColorSwatch(
                                            label = option.name,
                                            accent = timelineAccent(option.id),
                                            selected = option.id == colorId,
                                            onClick = { colorId = option.id },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }

                            // TS lines 819-838: parent select
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("上级分支", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                    Spacer(Modifier.width(6.dp))
                                    InfoHint("用于构建记忆树因果链")
                                }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable { parentMenuOpen = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = WwSurfaces.inset,
                                        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(
                                                horizontal = 12.dp,
                                                vertical = WwSize.formFieldVerticalPadding,
                                            ),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = timelines.firstOrNull { it.id == parentId }?.let { formatTimelineParentLabel(it) } ?: "作为独立根分支",
                                                fontSize = WwType.hint,
                                                lineHeight = WwSize.compactTextLineHeight,
                                            )
                                            Icon(TablerIcons.ChevronDown, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    DropdownMenu(expanded = parentMenuOpen, onDismissRequest = { parentMenuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("作为独立根分支") },
                                            onClick = { parentId = ""; parentMenuOpen = false },
                                        )
                                        timelines.filter { it.id != initial.editingId }.forEach { candidate ->
                                            DropdownMenuItem(
                                                text = { Text(formatTimelineParentLabel(candidate)) },
                                                onClick = { parentId = candidate.id; parentMenuOpen = false },
                                            )
                                        }
                                    }
                                }
                            }

                            // TS lines 840-853: description (rows=3)
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Text("核心区分信息 / 世界观与设定特征 *", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                CompactField(
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = "例如：这是离开团队5年后的平行分支，主角已成为要塞指挥官...",
                                    singleLine = false,
                                    minLines = 3,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // TS lines 855-867: plot summary (rows=2)
                            Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("剧情脉络与推进记录", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                                    Spacer(Modifier.width(6.dp))
                                    InfoHint("随会话持续自动丰富")
                                }
                                CompactField(
                                    value = plotSummary,
                                    onValueChange = { plotSummary = it },
                                    placeholder = "分支初始状态，随后续聊天剧情发展，AI系统会自动追加更多脉络记录...",
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            error?.let {
                                Text("保存失败：$it",
                                    fontSize = WwType.hint,
                                    lineHeight = WwSize.compactTextLineHeight,
                                    color = MaterialTheme.colorScheme.error)
                            }

                            // TS lines 869-890: save & cancel (inside scroll)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = WwSize.cardListGap),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WwSecondaryButton(text = "取消", onClick = onDismiss)
                                Spacer(Modifier.size(8.dp))
                                WwPrimaryButton(
                                    text = if (isEditing) "保存修改" else "确认创建分支",
                                    enabled = canSave,
                                    onClick = {
                                        onSave(initial.copy(
                                            codeTag = codeTag, name = name,
                                            descriptionTag = descriptionTag, color = colorId,
                                            parentId = parentId.ifEmpty { null },
                                            description = description, plotSummary = plotSummary,
                                        ))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- 紧凑输入框

/**
 * TS 的 `<input className="px-3 py-2 rounded-xl bg-slate-100 border ...">` 对应物。
 *
 * Material3 的 `OutlinedTextField` 默认 contentPadding 是 16dp（上下各 16），
 * 比 TS 的 `py-2`（8dp）大一倍，导致输入框整体偏高。
 * 这里用 `BasicTextField` + `OutlinedTextFieldDefaults.DecorationBox` 拿到
 * 外观一致的 outline + focus 描边，同时把 contentPadding 压到
 * `12dp × WwSize.formFieldVerticalPadding`（每侧固定 1dp），文字用 `WwSize.compactTextLineHeight`
 * —— 框高只跟随文字行高变化，字号调大时留白不跟着放大。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle? = null,
) {
    val mergedStyle = textStyle ?: TextStyle(
        fontSize = WwType.hint,
        lineHeight = WwSize.compactTextLineHeight,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedContainerColor = WwSurfaces.inset,
        focusedContainerColor = WwSurfaces.inset,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = mergedStyle,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = @Composable { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = WwSize.formFieldVerticalPadding,
                ),
                placeholder = placeholder?.let {
                    { Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                },
                container = {
                    @Suppress("DEPRECATION")
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = RoundedCornerShape(12.dp),
                    )
                },
            )
        },
    )
}

// ---------------------------------------------------------------------- 颜色色块

@Composable
private fun ColorSwatch(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TS: selected = "border-white shadow-sm ring-2 ring-indigo-500/50"
    // Compose approximation: outer border as ring, inner Surface with white border
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                // TODO: 聚焦状态配色暂时注释
                // if (selected) Modifier.border(
                //     width = 2.dp,
                //     color = Color(0xFF6366F1).copy(alpha = 0.5f), // indigo-500/50
                //     shape = RoundedCornerShape(100.dp),
                // ).padding(WwSize.compactFrameVerticalPadding)
                // else
                Modifier
            ),
        shape = RoundedCornerShape(100.dp),
        color = if (selected) accent else accent.copy(alpha = 0.12f),
        // border = if (selected) BorderStroke(2.dp, Color.White) else null,
        border = null, // TODO: 聚焦状态边框暂时注释
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.compactFrameVerticalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(TablerIcons.Check, null, Modifier.size(12.dp), tint = Color.White)
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text = label,
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = if (selected) Color.White else accent,
            )
        }
    }
}

internal fun timelineAccent(colorId: String): Color = when (colorId) {
    "indigo" -> Color(0xFF4F46E5)
    "emerald" -> Color(0xFF009966)
    "amber" -> Color(0xFFD97706)
    "rose" -> Color(0xFFE11D48)
    "cyan" -> Color(0xFF0891B2)
    "purple" -> Color(0xFF9333EA)
    "teal" -> Color(0xFF0D9488)
    else -> Color(0xFF4F46E5)
}
