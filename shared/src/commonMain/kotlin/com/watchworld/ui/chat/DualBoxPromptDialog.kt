package com.watchworld.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
import compose.icons.tablericons.ArrowDown
import compose.icons.tablericons.Book
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.DeviceFloppy
import compose.icons.tablericons.Stack
import compose.icons.tablericons.Upload
import com.watchworld.model.ChatSession
import com.watchworld.model.PromptPreset
import com.watchworld.model.Role
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType

/**
 * Dual-box prompt area dialog.
 *
 * Replicates reference `PromptDualBoxModal.tsx` (416 lines).
 * Title bar uses FeatureDialog's built-in header with icon + title + close button.
 * "Injection guide" badge sits to the right of the title.
 */
@Composable
fun DualBoxPromptDialog(
    session: ChatSession,
    prompts: List<PromptPreset> = emptyList(),
    onSave: (fixedPrompt: String, commonPrompt: String, isCommonPushed: Boolean) -> Unit,
    onPushCommon: () -> Unit,
    onRevertCommon: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showGuide by remember { mutableStateOf(false) }

    FeatureDialog(
        title = "",
        onDismiss = onDismiss,
        scrollable = false,
        // Web: max-w-2xl (672dp) + max-h-[92vh]
        maxWidth = 672.dp,
        heightFraction = 0.92f,
        // 工具条要通栏（Web 是 border-b 整条），内容自己管左右留白。
        contentPadding = 0.dp,
        titleContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(TablerIcons.Stack, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("双框提示词区域", fontSize = WwType.title, fontWeight = WwType.titleWeight, modifier = Modifier.weight(1f))
                WwTag(
                    text = "提示词讲解",
                    shape = RoundedCornerShape(100.dp),
                    pill = true,
                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    leadingIcon = TablerIcons.Book,
                    onClick = { showGuide = true },
                )
            }
        },
    ) {
        DualBoxPromptBody(
            session = session,
            prompts = prompts,
            onSave = onSave,
            onPushCommon = onPushCommon,
            onRevertCommon = onRevertCommon,
            onDismiss = onDismiss,
        )
    }

    if (showGuide) {
        PromptInjectionGuideDialog(onDismiss = { showGuide = false })
    }
}

@Composable
private fun DualBoxPromptBody(
    session: ChatSession,
    prompts: List<PromptPreset>,
    onSave: (String, String, Boolean) -> Unit,
    onPushCommon: () -> Unit,
    onRevertCommon: () -> Unit,
    onDismiss: () -> Unit,
) {
    var fixedPrompt by remember(session.id) {
        mutableStateOf(session.systemPromptFixed ?: session.systemPromptOverride.orEmpty())
    }
    var commonPrompt by remember(session.id) {
        mutableStateOf(session.systemPromptInjectedCommon.orEmpty())
    }
    var isCommonPushed by remember(session.id) {
        mutableStateOf(session.isCommonPushed == true)
    }
    var commonExpanded by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var injectTarget by remember { mutableStateOf("fixed") }

    val commonMessages = session.messages.filter { m ->
        m.isCommon == true || m.timelineId == "timeline-main" || m.codeTag == "通用"
    }
    val summaries = session.summaries.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Action toolbar: push/revert + count | inject target + load local ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WwSurfaces.inset,
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = WwSize.boxVerticalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCommonPushed) {
                        WwTag(
                            text = "撤回推入通用",
                            shape = RoundedCornerShape(12.dp),
                            background = Color(0xFFF59E0B),
                            contentColor = Color.White,
                            leadingIcon = TablerIcons.ArrowBack,
                            onClick = {
                                onRevertCommon()
                                isCommonPushed = false
                                commonPrompt = ""
                            },
                        )
                    } else {
                        WwTag(
                            text = "推入通用",
                            shape = RoundedCornerShape(12.dp),
                            background = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIcon = TablerIcons.ArrowDown,
                            onClick = {
                                onPushCommon()
                                isCommonPushed = true
                                val snippets = commonMessages.map { m ->
                                    val floor = m.floorNumber?.let { "[来自 #$it]" }.orEmpty()
                                    val label = if (m.role == Role.USER) "用户设定" else "基础世界观"
                                    val text = if (m.role == Role.ASSISTANT) {
                                        m.versions.getOrNull(m.currentVersionIndex)?.content
                                            ?: m.versions.firstOrNull()?.content ?: m.content
                                    } else m.content
                                    "$floor $label：${text.trim()}"
                                }
                                commonPrompt = snippets.joinToString("\n\n")
                            },
                        )
                    }
                    Text("当前聊天中通用消息: ${commonMessages.size} 条",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WwTag(
                        text = if (injectTarget == "fixed") "注入至本身框" else "注入至通用框",
                        shape = RoundedCornerShape(10.dp),
                        background = WwSurfaces.inset,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        leadingIcon = TablerIcons.ChevronDown,
                        onClick = {
                            injectTarget = if (injectTarget == "fixed") "common" else "fixed"
                        },
                    )
                    WwTag(
                        text = "载入本地设定",
                        shape = RoundedCornerShape(10.dp),
                        background = WwSurfaces.inset,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        leadingIcon = TablerIcons.Upload,
                        onClick = { /* TODO: file picker */ },
                    )
                }
            }
        }
        // 工具条与正文之间的节奏走 cardListGap（原来是 4 + 8，行显得太“飘”）。
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── Scrollable body ──
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // BOX 1: Fixed prompt
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape,
                        color = Color(0xFF10B981)) {}
                    Spacer(Modifier.width(6.dp))
                    Text("【本身的提示词】",
                        fontSize = WwType.body)
                    Spacer(Modifier.width(6.dp))
                    InfoHint("始终展开·手写固定规则")
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WwTag(
                        text = "从库中获取",
                        shape = RoundedCornerShape(10.dp),
                        background = WwSurfaces.card,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIcon = TablerIcons.Book,
                        onClick = { showLibrary = !showLibrary },
                    )
                    Text("${fixedPrompt.length} 字",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(WwSize.cardListGap))
            WwTextField(
                value = fixedPrompt,
                onValueChange = { fixedPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = if (commonExpanded) 80.dp else 160.dp,
                    max = if (commonExpanded) 100.dp else 200.dp,
                ),
                placeholder = "在此填写固定的系统设定、世界观背景法则、AI说话口吻与行为准则...",
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                minHeight = if (commonExpanded) 80.dp else 160.dp,
            )

            // Library picker
            if (showLibrary) {
                Spacer(Modifier.height(WwSize.cardListGap))
                Surface(shape = RoundedCornerShape(12.dp), color = WwSurfaces.inset) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = WwPad.card),
                    ) {
                        if (prompts.isEmpty()) {
                            Text("提示词库为空，请到「设置 → 提示词库」新建提示词",
                                fontSize = WwType.hint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp))
                        } else {
                            prompts.forEach { p ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        fixedPrompt = if (fixedPrompt.isNotEmpty()) "$fixedPrompt\n\n${p.content}" else p.content
                                        showLibrary = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                ) {
                                    Column(Modifier.padding(horizontal = 8.dp, vertical = WwSize.listRowVerticalPadding)) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text(p.title,
                                                fontSize = WwType.body,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f))
                                            if (p.category.isNotBlank()) {
                                                Text(p.category,
                                                    fontSize = WwType.hint,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text((p.description ?: p.content).take(60),
                                            fontSize = WwType.hint,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(WwSize.cardListGap))

            // BOX 2: Injected common (collapsible)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(WwSize.cardListGap))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { commonExpanded = !commonExpanded },
                shape = RoundedCornerShape(12.dp),
                color = WwSurfaces.card,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (commonExpanded) TablerIcons.ChevronDown else TablerIcons.ChevronRight,
                            null, Modifier.size(16.dp),
                            tint = if (commonExpanded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("【▶ 注入的通用】",
                            fontSize = WwType.body)
                        InfoHint("包含已推入的通用消息，享受系统级 Prefix Prompt Caching")
                        if (isCommonPushed) {
                            WwTag(
                                text = "已激活推入缓存",
                                shape = RoundedCornerShape(10.dp),
                                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (commonPrompt.isNotEmpty()) "${commonPrompt.length} 字" else "暂无内容",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (commonExpanded) "折叠收起" else "展开编辑（大视窗）",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (commonExpanded) {
                Spacer(Modifier.height(WwSize.cardListGap))
                if (isCommonPushed) {
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        WwTag(
                            text = "清空通用框",
                            background = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.error,
                            onClick = { commonPrompt = "" },
                        )
                    }
                }
                WwTextField(
                    value = commonPrompt,
                    onValueChange = { commonPrompt = it },
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    placeholder = "点击上方【推入通用 ↓】或直接粘贴通用信息。推入后内容走系统提示词位置，享受API长文本缓存...",
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                    minHeight = 320.dp,
                )
            }
            Spacer(Modifier.height(WwSize.cardListGap))

            // BOX 3: Summary preview (always visible)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape,
                        color = Color(0xFFF59E0B)) {}
                    Spacer(Modifier.width(6.dp))
                    Text("【前文总结】",
                        fontSize = WwType.body)
                    Spacer(Modifier.width(6.dp))
                    InfoHint("自动推入·系统提示词身份")
                    Spacer(Modifier.width(4.dp))
                    InfoHint("总结按顺序拼接在通用缓存之后，以 system 身份发送；增删请到「窗口功能 → 前文总结」。")
                }
                Text("${summaries.size} 条 · 已总结至 #${session.lastSummarizedFloor ?: 0}",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(WwSize.cardListGap))
            Surface(shape = RoundedCornerShape(12.dp), color = WwSurfaces.inset) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 176.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = WwPad.card),
                ) {
                    if (summaries.isEmpty()) {
                        Text("暂无总结。在「窗口功能 → 前文总结」生成后会自动出现在这里，并随每次请求以系统提示词身份发送。",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        summaries.forEachIndexed { i, s ->
                            Text("#${i + 1} · 第 ${s.fromFloor}–${s.toFloor} 楼",
                                fontSize = WwType.hint,
                                color = MaterialTheme.colorScheme.primary)
                            Text(s.content,
                                fontSize = WwType.hint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = WwSize.cardListGap))
                        }
                    }
                }
            }
            Spacer(Modifier.height(WwSize.cardListGap))
        }

        // ── Footer (pinned bottom, no extra Surface wrapper) ──
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = WwSize.boxVerticalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 底栏那句说明收成 ⓘ（用户 2026-10-12：其它注释也收起来）。
            InfoHint("保存后立即应用到下一次向 AI 发送的请求")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WwSecondaryButton(text = "取消", onClick = onDismiss)
                WwTag(
                    text = "保存提示词配置",
                    shape = RoundedCornerShape(12.dp),
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    leadingIcon = TablerIcons.DeviceFloppy,
                    onClick = { onSave(fixedPrompt, commonPrompt, isCommonPushed) },
                )
            }
        }
    }
}
