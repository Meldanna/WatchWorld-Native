package com.watchworld.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Filter
import compose.icons.tablericons.Stack
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType

/**
 * AI 上下文视野 & 界面渲染优化弹窗。
 *
 * 对标参考项目 `AiVisibilityModal.tsx`（219 行）：
 * - Section 1：AI 助手消息显隐 — 三个 radio-style 卡片（ALL / HIDE_ALL / LATEST_CUSTOM）
 * - Section 2：屏幕渲染条数 — 5 个选项按钮（0/5/10/20/50）
 * - 所有变更即时生效，无显式保存按钮
 *
 * 状态来源：`ChatSession.aiContextVisibility` / `aiContextCustomLimit` / `uiRenderLimit`
 * 写回路径：`ChatViewModel.setAiContextMode` / `setAiContextCustomLimit` / `setUiRenderLimit`
 */
@Composable
fun AiVisibilityDialog(
    currentMode: AiContextVisibilityFilter,
    customLimit: Int,
    uiRenderLimit: Int,
    onChangeMode: (AiContextVisibilityFilter) -> Unit,
    onChangeCustomLimit: (Int) -> Unit,
    onChangeUiRenderLimit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    FeatureDialog(
        title = "AI 上下文视野 & 界面渲染",
        icon = TablerIcons.Eye,
        onDismiss = onDismiss,
        // Web `AiVisibilityModal`: max-w-lg (512dp) + max-h-[92vh]
        maxWidth = 512.dp,
        heightFraction = 0.92f,
    ) {
        AiVisibilityContent(
            currentMode = currentMode,
            customLimit = customLimit,
            uiRenderLimit = uiRenderLimit,
            onChangeMode = onChangeMode,
            onChangeCustomLimit = onChangeCustomLimit,
            onChangeUiRenderLimit = onChangeUiRenderLimit,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun AiVisibilityContent(
    currentMode: AiContextVisibilityFilter,
    customLimit: Int,
    uiRenderLimit: Int,
    onChangeMode: (AiContextVisibilityFilter) -> Unit,
    onChangeCustomLimit: (Int) -> Unit,
    onChangeUiRenderLimit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // ── 副标题 ──
    Text(
        text = "精细控制 AI 所见上下文，以及屏幕渲染条数",
        fontSize = WwType.hint,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(WwSize.cardListGap))

    // ═══════════════════════════════════════════════════════════
    // Section 1: AI 助手消息显隐
    // ═══════════════════════════════════════════════════════════
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = CircleShape,
            color = Color(0xFFFBBF24), // amber-400
        ) {}
        Spacer(Modifier.width(6.dp))
        Text(
            text = "1. AI 助手消息显隐",
            fontSize = WwType.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(WwSize.cardListGap))

    val options = remember {
        listOf(
            AiContextOption(
                mode = AiContextVisibilityFilter.ALL,
                title = "全部历史回复",
                description = "AI 能看到完整的前序助手回答。",
                icon = TablerIcons.Eye,
                iconTint = null, // 用主题色
            ),
            AiContextOption(
                mode = AiContextVisibilityFilter.HIDE_ALL,
                title = "隐藏所有助手消息",
                description = "只传用户提问，不传以前的 AI 回答。",
                icon = TablerIcons.EyeOff,
                iconTint = Color(0xFFFBBF24), // amber-400
                tag = "防幻觉",
            ),
            AiContextOption(
                mode = AiContextVisibilityFilter.LATEST_CUSTOM,
                title = "仅让 AI 看到最新 N 条回答",
                description = "手动设置保留最新几条助手回答。",
                icon = TablerIcons.Filter,
                iconTint = Color(0xFF60A5FA), // blue-400
            ),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        options.forEach { opt ->
            AiContextOptionCard(
                option = opt,
                selected = currentMode == opt.mode,
                onClick = { onChangeMode(opt.mode) },
            )
            // 当 latest_custom 被选中时展示数量输入
            if (opt.mode == AiContextVisibilityFilter.LATEST_CUSTOM && currentMode == opt.mode) {
                CustomLimitInput(
                    value = customLimit,
                    onValueChange = onChangeCustomLimit,
                )
            }
        }
    }

    Spacer(Modifier.height(WwSize.cardListGap))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(WwSize.cardListGap))

    // ═══════════════════════════════════════════════════════════
    // Section 2: 屏幕渲染条数
    // ═══════════════════════════════════════════════════════════
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = TablerIcons.Stack,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "2. 屏幕渲染条数",
            fontSize = WwType.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        // 说明收成浮层角标（用户 2026-10-11：框外注释一律收起）。
        InfoHint("渲染最新 N 条消息，提升长会话流畅度。")
    }
    Spacer(Modifier.height(WwSize.cardListGap))

    val renderOptions = remember {
        listOf(
            0 to "全部渲染",
            5 to "最新 5 条",
            10 to "最新 10 条",
            20 to "最新 20 条",
            50 to "最新 50 条",
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        renderOptions.forEach { (limit, label) ->
            val selected = uiRenderLimit == limit
            Surface(
                modifier = Modifier.weight(1f).clickable { onChangeUiRenderLimit(limit) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else WwSurfaces.card,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = label,
                    fontSize = WwType.hint,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = WwSize.boxVerticalPadding, horizontal = 2.dp)
                        .fillMaxWidth(),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    Spacer(Modifier.height(WwSize.cardListGap))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(WwSize.cardListGap))

    // ── 底部提示 + 完成按钮 ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "设置将自动作用于当前会话窗口",
            fontSize = WwType.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WwSecondaryButton(text = "完成", onClick = onDismiss)
    }
}

// ── 内部数据 ──

private data class AiContextOption(
    val mode: AiContextVisibilityFilter,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color?, // null = 用主题 primary
    val tag: String? = null,
)

@Composable
private fun AiContextOptionCard(
    option: AiContextOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected)
        MaterialTheme.colorScheme.outline
    else
        MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) WwSurfaces.cardSelected else WwSurfaces.card,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 图标
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp).padding(top = 2.dp),
                tint = option.iconTint ?: MaterialTheme.colorScheme.primary,
            )
            // 标题 + 描述
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.title,
                        fontSize = WwType.body,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    // 说明收成浮层角标（用户 2026-10-11：框外注释一律收起）。
                    Spacer(Modifier.width(6.dp))
                    InfoHint(option.description)
                    if (option.tag != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = option.tag,
                            fontSize = WwType.hint,
                            color = Color(0xFFFBBF24), // amber-400
                        )
                    }
                }
            }
            // 选中勾
            if (selected) {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = "已选择",
                        modifier = Modifier.size(13.dp).padding(3.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomLimitInput(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.padding(start = 36.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WwTextField(
            value = text,
            onValueChange = { input ->
                text = input
                val parsed = input.toIntOrNull()
                if (parsed != null && parsed in 1..999) {
                    onValueChange(parsed)
                }
            },
            modifier = Modifier.widthIn(max = 80.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(
            text = "条助手回答",
            fontSize = WwType.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
