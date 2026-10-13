package com.watchworld.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.PromptPreset
import com.watchworld.platform.currentTimeMillis
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.theme.WwType
import com.watchworld.ui.components.SearchField
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwSelectField
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import compose.icons.TablerIcons
import compose.icons.tablericons.Edit
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Wand
import compose.icons.tablericons.Trash
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 提示词库弹窗（对标参考版 `PromptModal.tsx`，585 行）——设置总入口的**独立弹窗**。
 *
 * ### 结构（行号指向参考版）
 * - 顶栏（281-303）：32dp 徽章（Sparkles，emerald 系）+ `提示词库与快捷切换` +
 *   副标题 `可填入输入框，或直接设为当前会话窗口的主控提示词` + ✕
 * - 工具条（435-486）：搜索框 + `从当前内容存入` + `新建` + 分类分段筛选
 * - 列表（487-575）：两列卡片（`md:grid-cols-2`），每张 = 标题 + 标签 + 说明 +
 *   内容预览 + 编辑 / 删除 + （宿主给了能力时）`设为窗口提示词` / `填入输入框`
 * - 表单层（306-433）：又一个独立弹窗（`z-[80]`）——标题 / 说明 / 分类 / 内容 + 取消 / 保存
 *
 * ⚠️ **设置入口不传 `onInsertToInput` / `onApplyAsSystemPrompt`**：
 *    参考版 `App.tsx:2456-2465` 只传 CRUD 三件套，卡片底部那两个按钮因此不渲染
 *    （参考版 L547-573 的 `(onApplyAsSystemPrompt || onInsertToInput) &&` 守卫）。
 *    「填入输入框」「设为窗口提示词」是**输入栏那个入口**才有的能力。
 */
@Composable
fun PromptLibraryDialog(
    prompts: List<PromptPreset>,
    onSavePrompt: (PromptPreset) -> Unit,
    onDeletePrompt: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 输入栏入口才传：把内容填进输入框。 */
    onInsertToInput: ((String) -> Unit)? = null,
    /** 输入栏入口才传：设为当前窗口的系统提示词。 */
    onApplyAsSystemPrompt: ((content: String, title: String) -> Unit)? = null,
    /** 当前窗口正在生效的提示词内容（用于卡片上的「当前窗口生效中」态）。 */
    activeSystemPrompt: String? = null,
) {
    FeatureDialog(
        title = "提示词库与快捷切换",
        subtitle = "可填入输入框，或直接设为当前会话窗口的主控提示词",
        icon = TablerIcons.Wand,
        onDismiss = onDismiss,
        maxWidth = 768.dp,
        heightFraction = 0.92f,
    ) {
        PromptLibraryContent(
            prompts = prompts,
            onSavePrompt = onSavePrompt,
            onDeletePrompt = onDeletePrompt,
            onInsertToInput = onInsertToInput,
            onApplyAsSystemPrompt = onApplyAsSystemPrompt,
            activeSystemPrompt = activeSystemPrompt,
            onApplied = onDismiss,
        )
    }
}

/** 六个分类（参考版 `PromptModal.tsx:89-96` 的 `categories`）。 */
private val PROMPT_CATEGORIES = listOf(
    "productivity" to "效率分析",
    "coding" to "编程开发",
    "writing" to "写作润色",
    "reasoning" to "深度思考",
    "custom" to "自定义",
)

@Composable
internal fun PromptLibraryContent(
    prompts: List<PromptPreset>,
    onSavePrompt: (PromptPreset) -> Unit,
    onDeletePrompt: (String) -> Unit,
    onInsertToInput: ((String) -> Unit)?,
    onApplyAsSystemPrompt: ((content: String, title: String) -> Unit)?,
    activeSystemPrompt: String?,
    onApplied: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("all") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PromptPreset?>(null) }
    var confirmingDelete by remember { mutableStateOf<PromptPreset?>(null) }

    // ── 工具条（参考版 437-486）──
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索提示词名称、内容或标签...",
            modifier = Modifier.weight(1f),
        )
        WwPrimaryButton("新建", onClick = { creating = true; editing = null })
    }

    // 分类分段筛选（参考版 466-484）。
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CategoryChip("全部", category == "all") { category = "all" }
        PROMPT_CATEGORIES.forEach { (id, label) ->
            CategoryChip(label, category == id) { category = id }
        }
    }
    Spacer(Modifier.height(WwSize.cardListGap))

    val needle = query.trim().lowercase()
    val filtered = remember(prompts, needle, category) {
        prompts.filter { preset ->
            val matchCategory = category == "all" || preset.category == category
            val matchSearch = needle.isEmpty() ||
                preset.title.lowercase().contains(needle) ||
                preset.content.lowercase().contains(needle) ||
                preset.category.lowercase().contains(needle) ||
                preset.description.orEmpty().lowercase().contains(needle) ||
                preset.tags.orEmpty().any { it.lowercase().contains(needle) }
            matchCategory && matchSearch
        }
    }

    if (filtered.isEmpty()) {
        Text(
            text = if (prompts.isEmpty()) "还没有提示词，点击「新建」添加一个。" else "没有找到匹配的提示词",
            fontSize = WwType.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding).wrapContentWidth(Alignment.CenterHorizontally),
        )
    }

    // ── 两列卡片（参考版 `md:grid-cols-2 gap-2.5`）──
    filtered.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = WwSize.cardListGap),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            pair.forEach { preset ->
                PromptCard(
                    preset = preset,
                    isActiveSystemPrompt = activeSystemPrompt != null && activeSystemPrompt == preset.content,
                    onEdit = { editing = preset; creating = false },
                    onDelete = { confirmingDelete = preset },
                    onApply = onApplyAsSystemPrompt?.let { cb ->
                        { cb(preset.content, preset.title); onApplied() }
                    },
                    onInsert = onInsertToInput?.let { cb -> { cb(preset.content); onApplied() } },
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    // ── 表单层（参考版 306-433，又一个独立弹窗）──
    if (creating || editing != null) {
        PromptEditorDialog(
            initial = editing,
            creating = creating,
            prompts = prompts,
            onSave = { onSavePrompt(it); creating = false; editing = null },
            onDismiss = { creating = false; editing = null },
        )
    }

    // ── 删除确认（参考版是 `confirm()`，原生统一走 AlertDialog）──
    confirmingDelete?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除提示词") },
            text = { Text("确认删除提示词「${target.title.ifBlank { target.id }}」？该操作不可撤销。") },
            confirmButton = {
                WwPrimaryButton("删除", onClick = {
                    onDeletePrompt(target.id)
                    confirmingDelete = null
                })
            },
            dismissButton = {
                WwSecondaryButton("取消", onClick = { confirmingDelete = null })
            },
        )
    }
}

/** 分类筛选小药丸（参考版 `px-2.5 py-1 rounded-lg chat-text-xs font-medium`）。 */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    WwTag(
        text = label,
        shape = RoundedCornerShape(10.dp),
        background = if (selected) WwSurfaces.cardSelected else WwSurfaces.card,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) WwSurfaces.cardBorder else WwSurfaces.cardBorderSubtle),
        onClick = onClick,
    )
}

/** 提示词卡片（参考版 `PromptModal.tsx:490-575`）。 */
@Composable
private fun PromptCard(
    preset: PromptPreset,
    isActiveSystemPrompt: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApply: (() -> Unit)?,
    onInsert: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.card,
        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preset.title.ifBlank { preset.id },
                            fontSize = WwType.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        preset.tags.orEmpty().forEach { tag ->
                            Text(
                                " · $tag",
                                fontSize = WwType.hint,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        preset.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Spacer(Modifier.width(6.dp))
                            // 卡片说明收成浮层角标（用户 2026-10-11：框外注释一律收起）。
                            InfoHint(desc)
                        }
                    }
                }
                Icon(
                    TablerIcons.Edit, "编辑",
                    Modifier.size(13.dp).clickable(onClick = onEdit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    TablerIcons.Trash, "删除",
                    Modifier.size(13.dp).clickable(onClick = onDelete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            // 内容预览（参考版 `bg-slate-50 rounded-lg p-2 font-mono line-clamp-2`）。
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WwSurfaces.inset,
                border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    preset.content,
                    fontSize = WwType.hint,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }

            // 两个动作按钮只在宿主给了能力时出现（参考版 L547-573 的守卫）。
            if (onApply != null || onInsert != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onApply?.let {
                        WwSecondaryButton(
                            text = if (isActiveSystemPrompt) "当前窗口生效中" else "设为窗口提示词",
                            onClick = it,
                        )
                    }
                    onInsert?.let {
                        WwPrimaryButton("填入输入框", onClick = it)
                    }
                }
            }
        }
    }
}

/** 新建 / 编辑表单（参考版 306-433）。 */
@Composable
private fun PromptEditorDialog(
    initial: PromptPreset?,
    creating: Boolean,
    prompts: List<PromptPreset>,
    onSave: (PromptPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    // 新建默认落在「自定义」（参考版 `startCreate`）。
    var category by remember(initial?.id) { mutableStateOf(initial?.category?.ifBlank { null } ?: "custom") }
    var tagsStr by remember(initial?.id) {
        mutableStateOf(initial?.tags.orEmpty().joinToString(", ").ifBlank { if (creating) "自建" else "" })
    }
    var notice by remember { mutableStateOf<String?>(null) }

    FeatureDialog(
        title = if (creating) "新建提示词" else "编辑：${initial?.title.orEmpty()}",
        onDismiss = onDismiss,
        maxWidth = 672.dp,
        heightFraction = 0.9f,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                WwSecondaryButton("取消", onClick = onDismiss)
                WwPrimaryButton(
                    text = "保存提示词",
                    enabled = title.isNotBlank() && content.isNotBlank(),
                    onClick = {
                        onSave(
                            PromptPreset(
                                id = initial?.id ?: "prompt-${currentTimeMillis()}",
                                title = title.trim(),
                                description = description.trim().ifBlank { null },
                                content = content.trim(),
                                category = category,
                                // 逗号（中英）、空格都算分隔符；空则回退「自建」（参考版 L263-266）。
                                tags = parsePromptTags(tagsStr).ifEmpty { listOf("自建") },
                            ),
                        )
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
            notice?.let {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WwSurfaces.inset,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = WwPad.card),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(TablerIcons.InfoCircle, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(it, fontSize = WwType.hint, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            WwTextField(
                value = title,
                onValueChange = { title = it },
                label = "提示词标题 *",
                placeholder = "例如：论文结构润色、深度反问...",
            )
            WwTextField(
                value = description,
                onValueChange = { description = it },
                label = "简要说明",
                placeholder = "描述该提示词的主要效果...",
            )
            WwSelectField(
                current = PROMPT_CATEGORIES.firstOrNull { it.first == category }?.second ?: "自定义",
                options = PROMPT_CATEGORIES.map { it.second },
                onSelect = { picked ->
                    PROMPT_CATEGORIES.firstOrNull { it.second == picked }?.let { category = it.first }
                },
                label = "分类",
            )
            WwTextField(
                value = content,
                onValueChange = { content = it },
                label = "提示词具体内容 *",
                placeholder = "在此编写详细的指令模版...",
                singleLine = false,
                maxLines = 12,
                minHeight = 180.dp,
                fontSize = WwType.hint,
                monospace = true,
            )
            WwTextField(
                value = tagsStr,
                onValueChange = { tagsStr = it },
                label = "标签",
                placeholder = "逗号分隔，例如：写作, 润色",
                hint = if (notice == null && prompts.isNotEmpty()) "同名标题会并存（参考版的重名处理在「从当前内容存入」里，设置入口用不到）。" else null,
            )
        }
    }
}

/** 标签串 → 列表：逗号（中英）与空格都算分隔符（参考版 `split(/[,，\s]+/)`）。 */
private fun parsePromptTags(raw: String): List<String> =
    raw.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
