package com.watchworld.ui.summary

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.watchworld.domain.summaryRange
import com.watchworld.model.ChatSession
import com.watchworld.model.SessionSummary
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwSelectField
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.Check
import compose.icons.tablericons.Feather
import compose.icons.tablericons.FileText
import compose.icons.tablericons.ListCheck
import compose.icons.tablericons.Loader
import compose.icons.tablericons.Trash

/**
 * 前文总结弹窗（对标 `SummaryModal.tsx`，356 行；规格见仓库文档
 * `tasks/T27-窗口功能弹窗按Web版校正.md` §二）。
 *
 * 三段式与参考版逐段对应：顶栏 109-140 / 滚动区 142-307 / 底栏 323-352。
 * 参考版把**错误条**放在滚动区**之外**、底栏之上（309-321），所以这里放进 `footer` 的第一项，
 * 避免它随内容滚走。
 *
 * ⚠️ 参考版 `SummaryModal` 里**没有**「角色关系分析」：分析由「窗口功能 → 角色心理分析」触发
 *    （`WindowFunctionPanel.tsx:154-171`，点完即收起面板）。旧版面板底部那个分析按钮是
 *    Native 特有，T27 按参考版删除 —— 别再照着旧版加回来。
 */
@Composable
fun SummaryPanel(
    session: ChatSession?,
    summarizing: Boolean,
    summaryError: String?,
    /** 可用模型：当前 Provider 的 `models`（`App.tsx:2616`）。 */
    models: List<String>,
    /** 当前窗口生效模型：`session.model || provider.defaultModel`（`App.tsx:2617`）。 */
    currentModel: String,
    onDismiss: () -> Unit,
    onSaveConfig: (prompt: String?, model: String?) -> Unit,
    onGenerate: (prompt: String?, model: String?) -> Unit,
    onDeleteSummary: (String) -> Unit,
    onClearSummaries: () -> Unit,
) {
    val current = session
    if (current == null) {
        FeatureDialog(title = "前文总结", icon = TablerIcons.FileText, onDismiss = onDismiss, maxWidth = 672.dp) {
            Text("没有可用的会话。", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val range = summaryRange(current)
    val summaries = current.summaries.orEmpty()
    // 参考版 56-61：弹窗打开时用会话里已保存的配置初始化表单。
    var prompt by remember(current.id, current.summaryPrompt) { mutableStateOf(current.summaryPrompt.orEmpty()) }
    var model by remember(current.id, current.summaryModel) { mutableStateOf(current.summaryModel.orEmpty()) }

    // `<select>` 的首项（参考版 226）：值仍是空串，只是显示成「跟随本窗口当前模型（X）」。
    val followCurrent = "跟随本窗口当前模型（${currentModel.ifBlank { "未指定" }}）"
    val modelOptions = remember(followCurrent, models) { listOf(followCurrent) + models }
    val lastSummarized = current.lastSummarizedFloor ?: 0
    val canGenerate = !summarizing && range.pendingCount > 0

    FeatureDialog(
        title = "前文总结",
        subtitle = "总结结果按顺序推入系统提示词 · 只处理未总结过的内容",
        icon = TablerIcons.FileText,
        onDismiss = onDismiss,
        maxWidth = 672.dp,
        heightFraction = 0.9f,
        footer = {
            summaryError?.let { SummaryErrorBox(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "总结以系统提示词身份发送，不占用聊天消息",
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WwSecondaryButton(text = "关闭", onClick = onDismiss)
                    GenerateButton(
                        summarizing = summarizing,
                        enabled = canGenerate,
                        onClick = {
                            // 参考版 88-92：保存配置与生成一起做，避免「改了提示词却忘了保存」。
                            // 提示词留空时传 null —— 默认提示词的兜底在 ChatViewModel 里。
                            val trimmedPrompt = prompt.trim().ifBlank { null }
                            val trimmedModel = model.trim().ifBlank { null }
                            onSaveConfig(trimmedPrompt, trimmedModel)
                            onGenerate(trimmedPrompt, trimmedModel)
                        },
                    )
                }
            }
        },
    ) {
        // ── ① 进度卡（参考版 144-171）──
        PanelCard {
            Row {
                Icon(
                    imageVector = if (range.pendingCount > 0) TablerIcons.ListCheck else TablerIcons.AlertCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    if (range.pendingCount > 0) {
                        Text(
                            buildAnnotatedString {
                                append("将总结 ")
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append("#${range.fromFloor} – #${range.maxFloor}")
                                }
                                append(" 共 ${range.pendingCount} 条消息")
                            },
                            fontSize = WwType.body,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "已总结至 #$lastSummarized，不会重复处理。",
                            fontSize = WwType.hint,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("没有新的内容需要总结", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "已总结至 #$lastSummarized（当前共 ${range.maxFloor} 楼）。",
                            fontSize = WwType.hint,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── ② 省钱行（参考版 174-185）：已总结的原文不再送往模型 ──
        PanelCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已总结、不再发送给 AI 的原文",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${range.hiddenCount} 条",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    fontFamily = FontFamily.Monospace,
                    color = if (range.hiddenCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── ③ 总结提示词（参考版 188-211）──
        FieldLabel(
            label = "总结提示词",
            hint = "留空即用内置默认：只提炼已确认事实与当前状态，不复述原文。",
            trailing = {
                WwTag(
                    text = "恢复默认",
                    onClick = { prompt = "" },
                    background = WwSurfaces.inset,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
        )
        WwTextField(
            value = prompt,
            onValueChange = { prompt = it },
            placeholder = "留空则使用内置默认提示词（只记录已确认事实、保留因果、记录人物状态变化…）",
            singleLine = false,
            maxLines = 6,
            // 参考版 `rows={3}` + `p-2.5`：三行 11sp 正文 + 上下 10dp ≈ 66dp 起，可继续长高。
            minHeight = 66.dp,
            fontSize = WwType.hint,
            monospace = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── ④ 总结使用的模型（参考版 213-239）──
        FieldLabel(label = "总结使用的模型", hint = "可用模型来自全局 Provider 配置；总结不写入聊天记录。")
        WwSelectField(
            current = if (model.isBlank()) followCurrent else model,
            options = modelOptions,
            onSelect = { picked -> model = if (picked == followCurrent) "" else picked },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── ⑤ 已推入的总结（参考版 241-306）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已推入的总结（${summaries.size}）", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurface)
            if (summaries.isNotEmpty()) {
                WwTag(
                    text = "清空全部",
                    onClick = onClearSummaries,
                    background = WwSurfaces.inset,
                    contentColor = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(WwSize.cardListGap))
        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WwSurfaces.inset, RoundedCornerShape(10.dp))
                    .border(1.dp, WwSurfaces.cardBorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = WwPad.card),
            ) {
                Text(
                    "暂无总结。点击下方「总结前文」后会按顺序出现在这里，并自动作为系统提示词发送。",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                summaries.forEachIndexed { index, summary ->
                    SummaryItem(index = index, summary = summary, onDelete = onDeleteSummary)
                }
            }
        }
    }
}

/** 参考版 `rounded-xl border p-3` 的浅底卡片（bg `--surface-1` → [WwSurfaces.inset]）。 */
@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.inset,
        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card)) { content() }
    }
}

/**
 * 字段标签行：`label + InfoHint`（左）与可选操作（右）。
 *
 * 尺寸随 [WwTextField] 内置的 label（[WwType.hint] 12sp，框上方 4dp）。
 */
@Composable
private fun FieldLabel(
    label: String,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Spacer(Modifier.width(6.dp))
                InfoHint(hint)
            }
        }
        trailing?.invoke()
    }
    Spacer(Modifier.height(WwSize.cardListGap))
}

/** 单条总结：`#{序号} · 覆盖 #a–#b · 模型` + 删除；正文限高 128dp 内部滚动（参考版 `max-h-32`）。 */
@Composable
private fun SummaryItem(index: Int, summary: SessionSummary, onDelete: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.inset,
        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${index + 1} · 覆盖 #${summary.fromFloor}–#${summary.toFloor}" +
                        (summary.model?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TablerIcons.Trash,
                    contentDescription = "删除这条总结",
                    modifier = Modifier
                        .clickable { onDelete(summary.id) }
                        .padding(4.dp)
                        .size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(WwSize.cardListGap))
            Box(Modifier.fillMaxWidth().heightIn(max = 128.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = summary.content,
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 错误条（参考版 309-321）：滚动区之外、底栏之上。
 *
 * 参考版写死 rose-500（`#f43f5e`）；本项目 error 系保留 M3 默认（Web 版未主题化错误色），
 * 所以用 [MaterialTheme.colorScheme.error] 及其浅色变体代替写死值。
 */
@Composable
private fun SummaryErrorBox(message: String) {
    val error = MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = WwPad.content),
        shape = RoundedCornerShape(10.dp),
        color = error.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, error.copy(alpha = 0.35f)),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "总结失败：$message",
            fontSize = WwType.hint,
            lineHeight = WwSize.compactTextLineHeight,
            color = error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = WwSize.boxVerticalPadding),
        )
    }
}

/**
 * 「总结前文 / 总结中…」主按钮。
 *
 * ⚠️ 没走 `WwPrimaryButton`：参考版按钮**带图标**（Sparkles / 进行中换旋转的 Loader2），
 *    而 `WwPrimaryButton` 没有图标位。这里按 `ProviderPanel` 里「主色实底 + 图标 + 文字」的
 *    既有写法自绘，尺寸与 `WwPrimaryButton` 一致（`px-4 py-1.5` ≈ 14×7dp、12sp）。
 */
@Composable
private fun GenerateButton(summarizing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val angle = if (summarizing) rememberSpinAngle() else 0f
    val content = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.4f)
    Row(
        modifier = Modifier
            .background(
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = WwSize.buttonVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (summarizing) TablerIcons.Loader else TablerIcons.Feather,
            contentDescription = null,
            modifier = Modifier.size(13.dp).rotate(angle),
            tint = content,
        )
        Text(if (summarizing) "总结中..." else "总结前文", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = content)
    }
}

/** 只在「总结中」存在的无限旋转角（`animate-spin`）。 */
@Composable
private fun rememberSpinAngle(): Float {
    val transition = rememberInfiniteTransition(label = "summary-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "summary-spin-angle",
    )
    return angle
}
