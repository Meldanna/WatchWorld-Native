package com.watchworld.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ChatSession
import com.watchworld.model.ImageContextMode
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwRangeSlider
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.AntennaBars5
import compose.icons.tablericons.Filter
import compose.icons.tablericons.Hash
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Repeat
import compose.icons.tablericons.RotateClockwise
import compose.icons.tablericons.Stack
import compose.icons.tablericons.Temperature
import compose.icons.tablericons.TrendingUp
import kotlin.math.roundToInt

/**
 * 窗口 API 参数（对标 `WindowApiParamsModal.tsx`，581 行；逐段规格见仓库文档
 * `tasks/T27-窗口功能弹窗按Web版校正.md` §三）。
 *
 * 所有采样字段 `null` = 继承全局/Agent 默认值；未覆盖时滑杆停在参考版写死的 fallback 上
 * （温度/Top P 停在继承值，Top K 停在 40，两个惩罚与最大 Token 停在 0），值文案写「(跟随默认)」。
 * 只影响当前窗口：密钥 / Base URL / 模型清单属于全局 Provider 配置，不在此处编辑。
 *
 * ⚠️ 参考版整卡的正文基准是 `preferences.chatFontSize`（`chat-text-*` 类）；
 *    原生暂用固定字号（标签 14sp、小字 10/11sp、输入 12sp）—— 这是**全项目**尚未接的缩放，
 *    不是本弹窗独有的偏差，见文档「要点」。
 *
 * @param inheritedToolCallStepLimit 窗口未覆盖时继承的全局连续工具轮数，0 = 无限。
 */
@Composable
fun WindowApiParamsPanel(
    session: ChatSession,
    providerName: String,
    modelName: String,
    inheritedTemperature: Double,
    inheritedTopP: Double,
    inheritedToolCallStepLimit: Int = 0,
    onSave: (
        temperature: Double?, topP: Double?, topK: Int?,
        frequencyPenalty: Double?, presencePenalty: Double?,
        maxTokens: Int?, toolCallStepLimit: Int?,
        imageContextMode: ImageContextMode?, streamingEnabled: Boolean?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    // ── 本地编辑态（参考版 52-89：每次打开都从会话同步，避免上次的编辑残留）──
    var temperature by remember(session.id) { mutableStateOf(session.temperature) }
    var topP by remember(session.id) { mutableStateOf(session.topP) }
    var topK by remember(session.id) { mutableStateOf(session.topK) }
    var freqPenalty by remember(session.id) { mutableStateOf(session.frequencyPenalty) }
    var presPenalty by remember(session.id) { mutableStateOf(session.presencePenalty) }
    var maxTokens by remember(session.id) { mutableStateOf(session.maxTokens) }
    var maxTokensDraft by remember(session.id) { mutableStateOf(session.maxTokens?.toString().orEmpty()) }
    var toolLimit by remember(session.id) { mutableStateOf(session.toolCallStepLimit?.toString().orEmpty()) }
    var streaming by remember(session.id) { mutableStateOf(session.streamingEnabled) }
    var imageMode by remember(session.id) { mutableStateOf(session.imageContextMode) }

    // 空 = 继承全局；只接受 0..Int.MAX 的整数（输入已按位过滤，仅溢出时非法）。
    val parsedToolLimit = toolLimit.toIntOrNull()
    val validToolLimit = toolLimit.isEmpty() || (parsedToolLimit != null && parsedToolLimit >= 0)
    val inheritedToolLimitLabel = if (inheritedToolCallStepLimit == 0) "无限" else "$inheritedToolCallStepLimit 轮"

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 当前生效（参考版 182-193）：一整行 provider · model ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WwSurfaces.inset, RoundedCornerShape(10.dp))
                .border(1.dp, WwSurfaces.cardBorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = WwPad.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = TablerIcons.InfoCircle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "当前生效",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append(providerName.ifBlank { "未选择服务商" })
                    append(if (modelName.isNotBlank()) " · $modelName" else " · 未选择模型")
                },
                fontSize = WwType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ── 六个采样块（参考版 195-428；块与块之间没有额外间距）──
        ParamBlock(
            label = "发散度 (Temperature)",
            hint = "越低越严谨收敛，越高越发散。范围 0 – 1.5",
            icon = TablerIcons.Temperature,
            range = 0f..1.5f,
            step = 0.05f,
            sliderPosition = temperature?.toFloat() ?: inheritedTemperature.toFloat(),
            valueText = temperature?.let { "%.2f".format(it) }
                ?: "%.2f (跟随默认)".format(inheritedTemperature),
            clearLabel = if (temperature != null) "跟随默认" else null,
            onValueChange = { temperature = it.toDouble() },
            onClear = { temperature = null },
        )
        ParamBlock(
            label = "核采样 (Top P)",
            hint = "控制候选词的累积概率阈值，通常与温度二选一调节。",
            icon = TablerIcons.Stack,
            range = 0f..1f,
            step = 0.05f,
            sliderPosition = topP?.toFloat() ?: inheritedTopP.toFloat(),
            valueText = topP?.let { "%.2f".format(it) } ?: "%.2f (跟随默认)".format(inheritedTopP),
            clearLabel = if (topP != null) "跟随默认" else null,
            onValueChange = { topP = it.toDouble() },
            onClear = { topP = null },
        )
        ParamBlock(
            label = "候选词上限 (Top K)",
            hint = "每步只从概率最高的 K 个候选词中采样。范围 1 – 200，Claude / Gemini 支持。",
            icon = TablerIcons.Filter,
            range = 1f..200f,
            step = 1f,
            sliderPosition = topK?.toFloat() ?: 40f,
            valueText = topK?.toString() ?: "未设置",
            clearLabel = if (topK != null) "清除" else null,
            onValueChange = { topK = it.roundToInt() },
            onClear = { topK = null },
        )
        ParamBlock(
            label = "重复惩罚 (Frequency)",
            hint = "正值抑制重复用词，负值鼓励复读。0 为中性。仅 OpenAI 系服务商支持。",
            icon = TablerIcons.Repeat,
            range = -2f..2f,
            step = 0.1f,
            sliderPosition = freqPenalty?.toFloat() ?: 0f,
            valueText = freqPenalty?.let { "%.2f".format(it) } ?: "未设置",
            clearLabel = if (freqPenalty != null) "清除" else null,
            onValueChange = { freqPenalty = it.toDouble() },
            onClear = { freqPenalty = null },
        )
        ParamBlock(
            label = "话题惩罚 (Presence)",
            hint = "正值推动模型引入新话题，负值让它紧扣当前话题。仅 OpenAI 系服务商支持。",
            icon = TablerIcons.TrendingUp,
            range = -2f..2f,
            step = 0.1f,
            sliderPosition = presPenalty?.toFloat() ?: 0f,
            valueText = presPenalty?.let { "%.2f".format(it) } ?: "未设置",
            clearLabel = if (presPenalty != null) "清除" else null,
            onValueChange = { presPenalty = it.toDouble() },
            onClear = { presPenalty = null },
        )
        // 最大输出 Token：滑杆右侧没有值文案（参考版 375-393 只有「清空」药丸）。
        ParamBlock(
            label = "最大输出 Token",
            hint = "限制单次回复长度，留空表示不限制。",
            icon = TablerIcons.Hash,
            range = 0f..20000f,
            step = 256f,
            sliderPosition = maxTokens?.toFloat() ?: 0f,
            valueText = null,
            clearLabel = if (maxTokens != null) "清空" else null,
            onValueChange = { raw ->
                val v = raw.roundToInt()
                maxTokens = if (v == 0) null else v
                maxTokensDraft = if (v == 0) "" else v.toString()
            },
            onClear = { maxTokens = null; maxTokensDraft = "" },
            extra = {
                // 参考版 407-427：滑杆下方一行「数字输入 + tokens」。
                Spacer(Modifier.height(WwSize.cardListGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WwTextField(
                        value = maxTokensDraft,
                        onValueChange = { raw ->
                            val digits = raw.filter { c -> c.isDigit() }.take(5)
                            maxTokensDraft = digits
                            maxTokens = digits.toIntOrNull()?.coerceIn(0, 20000)?.takeIf { it > 0 }
                        },
                        placeholder = "留空 = 不限制",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "tokens",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        // ── 连续工具轮数（参考版 430-467：整块 py-3，无图标、无 InfoHint）──
        Spacer(Modifier.height(WwSize.cardListGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "连续工具轮数",
                    fontSize = WwType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                // 常驻帮助行收成浮层角标（用户 2026-10-11：框外注释一律收起）。
                InfoHint(
                    "留空继承全局（当前：$inheritedToolLimitLabel），0 为无限，正整数 N 为最多 N 轮。" +
                        "一个响应内多个工具算一步；完成 N 轮立即结束，不再请求模型。",
                )
            }
            if (toolLimit.isNotEmpty()) {
                PillButton("跟随全局") { toolLimit = "" }
            }
        }
        Spacer(Modifier.height(WwSize.cardListGap))
        WwTextField(
            value = toolLimit,
            onValueChange = { raw -> toolLimit = raw.filter { c -> c.isDigit() }.take(9) },
            placeholder = "留空 = 跟随全局（$inheritedToolLimitLabel）",
            singleLine = true,
            monospace = true,
            isError = !validToolLimit,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!validToolLimit) {
            Spacer(Modifier.height(WwSize.cardListGap))
            Text(
                "请输入 0 – 2147483647 的整数，或清空以继承全局。",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── 流式输出（参考版 469-502：py-3 border-t，默认按「流式」高亮）──
        HorizontalDivider()
        Spacer(Modifier.height(WwSize.cardListGap))
        SectionLabel(
            label = "流式输出",
            hint = "流式：边生成并显示，最后一个字后面有闪烁光标；非流式：等模型全部写完再一次性显示，" +
                "不出现光标。只影响本窗口的显示方式，不改变上下文与工具调用。",
            icon = TablerIcons.AntennaBars5,
        )
        Spacer(Modifier.height(WwSize.cardListGap))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 老存档没有该字段：undefined 按流式处理，因此默认高亮「流式」（参考版 483-484）。
            SegmentButton("流式", active = streaming != false) { streaming = true }
            SegmentButton("非流式", active = streaming == false) { streaming = false }
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── 图片是否留在上下文（参考版 504-543：缺省即「只发一轮」）──
        SectionLabel(
            label = "图片是否留在上下文",
            hint = "「只发一轮」：图片只在发送它的那一轮带给模型，之后不再重复计费，但 AI 后续不再看到该图。" +
                "「保留在历史中」：模型每轮都能看到，但图片会反复计入费用。",
            icon = TablerIcons.Photo,
        )
        Spacer(Modifier.height(WwSize.cardListGap))
        val effectiveImageMode = imageMode ?: ImageContextMode.ONCE
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SegmentButton("只发一轮（省 token）", active = effectiveImageMode == ImageContextMode.ONCE) {
                imageMode = ImageContextMode.ONCE
            }
            SegmentButton("保留在历史中", active = effectiveImageMode == ImageContextMode.HISTORY) {
                imageMode = ImageContextMode.HISTORY
            }
        }

        // ── 底栏（参考版 545-577：在卡片的滚动流里，不是固定底栏）──
        Spacer(Modifier.height(WwSize.cardListGap))
        HorizontalDivider()
        Spacer(Modifier.height(WwSize.cardListGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WwSecondaryButton(
                text = "全部跟随默认",
                leadingIcon = TablerIcons.RotateClockwise,
                onClick = {
                    // 参考版 117-126：**不**重置 imageContextMode（缺省即「只发一轮」，与本窗口显式选择等价）。
                    temperature = null; topP = null; topK = null
                    freqPenalty = null; presPenalty = null
                    maxTokens = null; maxTokensDraft = ""
                    toolLimit = ""; streaming = null
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WwSecondaryButton(text = "取消", onClick = onDismiss)
                WwPrimaryButton(
                    text = "保存",
                    enabled = validToolLimit,
                    onClick = {
                        onSave(
                            temperature, topP, topK,
                            freqPenalty, presPenalty, maxTokens,
                            parsedToolLimit, imageMode, streaming,
                        )
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * 一个采样块（参考版 195-428 的重复结构）：
 * 标签行（图标 + 标签 + InfoHint ／ 等宽值 + 可选药丸）+ 紧凑滑杆 + 可选补充内容。
 *
 * @param sliderPosition 未覆盖时滑杆停的位置（参考版的 `value ?? fallback`）。
 * @param valueText 右侧的等宽值文案；`null` = 不显示（最大 Token 如此）。
 * @param clearLabel 药丸文案（跟随默认 / 清除 / 清空）；`null` = 未覆盖，不显示药丸。
 */
@Composable
private fun ParamBlock(
    label: String,
    hint: String,
    icon: ImageVector,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    sliderPosition: Float,
    valueText: String?,
    clearLabel: String?,
    onValueChange: (Float) -> Unit,
    onClear: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = WwType.body, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                InfoHint(hint)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (valueText != null) {
                    Text(
                        valueText,
                        fontSize = WwType.body,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (clearLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    PillButton(clearLabel, onClear)
                }
            }
        }
        Spacer(Modifier.height(WwSize.cardListGap))
        WwRangeSlider(
            value = sliderPosition,
            range = range,
            step = step,
            onValueChange = onValueChange,
        )
        extra?.invoke()
    }
}

/** 小字标签行（流式输出 / 图片上下文）：13dp 主色图标 + 标签 + InfoHint。 */
@Composable
private fun SectionLabel(label: String, hint: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = WwType.body, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        InfoHint(hint)
    }
}

/** 小药丸（参考版 `px-1.5 py-0.5 rounded bg(--surface-2)`）—— 统一走 `WwTag`。 */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    WwTag(text = text, onClick = onClick)
}

/**
 * 分段按钮（流式输出 / 图片上下文）。
 * 选中：`bg --m2-soft` + 主色文字 + 主色描边；未选：`bg --surface-2` + 次级文字 + 默认描边。
 */
@Composable
private fun RowScope.SegmentButton(text: String, active: Boolean, onClick: () -> Unit) {
    // 与设置里的分段选择同一个规格：统一走 `WwTag`，不再自绘一版 12×8 的分段按钮。
    WwTag(
        text = text,
        onClick = onClick,
        modifier = Modifier.weight(1f),
        background = if (active) WwSurfaces.accentSoft else WwSurfaces.inset,
        contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else WwSurfaces.cardBorderSubtle),
    )
}
