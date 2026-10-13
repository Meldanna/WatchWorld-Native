package com.watchworld.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watchworld.data.DEFAULT_DISPLAY_SETTINGS
import com.watchworld.ui.components.WwTextField
import com.watchworld.model.Agent
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.AppPreferences
import com.watchworld.model.BubbleStyle
import com.watchworld.model.DefaultModelConfig
import com.watchworld.model.DefaultSessionConfig
import com.watchworld.model.LaunchMode
import com.watchworld.model.MessageDisplaySettings
import com.watchworld.platform.AvatarOwner
import com.watchworld.platform.defaultAvatarStore
import com.watchworld.platform.pickImageFiles
import com.watchworld.ui.components.AvatarImage
import com.watchworld.ui.components.AvatarSource
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwRangeSlider
import com.watchworld.ui.components.WwSwitch
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.theme.UiFontFamily
import com.watchworld.ui.theme.WwFont
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Coin
import compose.icons.tablericons.DeviceDesktop
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Filter
import compose.icons.tablericons.Globe
import compose.icons.tablericons.Hash
import compose.icons.tablericons.Maximize
import compose.icons.tablericons.Message
import compose.icons.tablericons.Power
import compose.icons.tablericons.Repeat
import compose.icons.tablericons.Rocket
import compose.icons.tablericons.RotateClockwise
import compose.icons.tablericons.Stack
import compose.icons.tablericons.Temperature
import compose.icons.tablericons.TrendingUp
import compose.icons.tablericons.Typography
import compose.icons.tablericons.Upload
import compose.icons.tablericons.User
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* =============================================================================
 * 设置 → 默认与偏好（对标 Web 版 `PreferencesModal.tsx`）
 * 设置 → 文本设置（对标 Web 版 `SettingsModal.tsx`）
 * ========================================================================== */

/** 一个全局 API 采样参数的读写规格（对标 `PreferencesModal` 里那张 `as const` 表）。 */
private data class GlobalParamSpec(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val read: (DefaultSessionConfig) -> Double?,
    val write: (DefaultSessionConfig, Double?) -> DefaultSessionConfig,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val fallback: Float,
    val integer: Boolean,
)

private val GLOBAL_PARAM_SPECS: List<GlobalParamSpec> = listOf(
    GlobalParamSpec(
        "发散度 (Temperature)", "越低越严谨收敛，越高越发散。范围 0 – 2。", TablerIcons.Temperature,
        { it.temperature }, { d, v -> d.copy(temperature = v) }, 0f..2f, 39, 0.7f, false,
    ),
    GlobalParamSpec(
        "核采样 (Top P)", "控制候选词累积概率阈值。范围 0 – 1，通常与温度二选一调节。", TablerIcons.Stack,
        { it.topP }, { d, v -> d.copy(topP = v) }, 0f..1f, 19, 0.95f, false,
    ),
    GlobalParamSpec(
        "候选词上限 (Top K)", "保留前 K 个高概率词。范围 1 – 200，需模型支持。", TablerIcons.Hash,
        { it.topK?.toDouble() }, { d, v -> d.copy(topK = v?.roundToInt()) }, 1f..200f, 198, 40f, true,
    ),
    GlobalParamSpec(
        "重复惩罚 (Frequency)", "正值抑制重复用词，负值鼓励重复。范围 -2 – 2，需模型支持。", TablerIcons.Repeat,
        { it.frequencyPenalty }, { d, v -> d.copy(frequencyPenalty = v) }, -2f..2f, 39, 0f, false,
    ),
    GlobalParamSpec(
        "话题惩罚 (Presence)", "正值鼓励新话题，负值紧扣当前话题。范围 -2 – 2，需模型支持。", TablerIcons.TrendingUp,
        { it.presencePenalty }, { d, v -> d.copy(presencePenalty = v) }, -2f..2f, 39, 0f, false,
    ),
    GlobalParamSpec(
        "最大输出 (Max Tokens)", "限制单次回复长度。范围 0 – 20000；留空不设置全局覆盖。", TablerIcons.Filter,
        { it.maxTokens?.toDouble() }, { d, v -> d.copy(maxTokens = v?.roundToInt()) }, 0f..20000f, 77, 2048f, true,
    ),
)

@Composable
fun AppPreferencesPanel(
    ap: AppPreferences,
    providers: List<ApiProviderConfig>,
    agents: List<Agent>,
    onSaveAp: (AppPreferences) -> Unit,
) {
    val patchModels = { p: DefaultModelConfig -> onSaveAp(ap.copy(defaultModels = p)) }
    val patchDefaults = { p: DefaultSessionConfig -> onSaveAp(ap.copy(defaultSession = p)) }
    val ds = ap.defaultSession

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. 默认模型
        SectionTitle(TablerIcons.DeviceDesktop, "默认模型（留空则回退到窗口 / Provider 默认）")
        Spacer(Modifier.height(WwSize.cardListGap))
        ModelPickerField("聊天主模型", ap.defaultModels.chat, providers) { patchModels(ap.defaultModels.copy(chat = it)) }
        ModelPickerField("会话标题生成", ap.defaultModels.title, providers) { patchModels(ap.defaultModels.copy(title = it)) }
        ModelPickerField("前文总结", ap.defaultModels.summary, providers) { patchModels(ap.defaultModels.copy(summary = it)) }
        ModelPickerField("角色关系分析", ap.defaultModels.analysis, providers) { patchModels(ap.defaultModels.copy(analysis = it)) }

        Divider()

        // 2. 全局 API 参数
        SectionTitle(TablerIcons.DeviceDesktop, "全局 API 参数", "窗口未单独设定时跟随此处偏好；采样参数留空表示不设置全局覆盖。")
        Spacer(Modifier.height(WwSize.cardListGap))
        GLOBAL_PARAM_SPECS.forEach { spec ->
            val current = spec.read(ds)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(spec.icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(spec.label, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight)
                Spacer(Modifier.width(6.dp))
                InfoHint(spec.hint)
                Spacer(Modifier.weight(1f))
                Text(
                    text = current?.let { if (spec.integer) it.roundToInt().toString() else "%.2f".format(it) } ?: "未设置",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                                                if (current != null) {
                    WwTag(
                        text = "清除",
                        onClick = { patchDefaults(spec.write(ds, null)) },
                    )
                }
            }
            // 组内：标签与滑杆之间 = cardListGap；组与组之间只剩滑杆盒子的半高（≈ 8dp），
            // 组内 4dp < 组间 8dp，一组才读得出来（用户 2026-10-12：每组之间距离太大）。
            Spacer(Modifier.height(WwSize.cardListGap))
            // 走公共的 `WwRangeSlider`（贴紧内容 16dp、轨道上没有刻度点，等价 Web 的 `input[type=range]`）。
            WwRangeSlider(
                value = (current ?: spec.fallback.toDouble()).toFloat(),
                range = spec.range,
                step = spec.range.stepOf(spec.steps),
                onValueChange = { v -> patchDefaults(spec.write(ds, v.toDouble())) },
            )
            if (spec.label.startsWith("最大输出")) {
                NumberInputField(
                    label = "精确 Token 数",
                    value = ds.maxTokens,
                    placeholder = "留空 = 不设置",
                    min = 0,
                    max = 20000,
                    suffix = "tokens",
                    onChange = { patchDefaults(ds.copy(maxTokens = it)) },
                )
            }
        }
        NumberInputField(
            label = "连续工具轮数",
            hint = "0 为无限，正整数 N 为最多 N 轮",
            value = ds.toolCallStepLimit,
            placeholder = "留空 = 默认无限",
            min = 0,
            max = Int.MAX_VALUE,
            onChange = { patchDefaults(ds.copy(toolCallStepLimit = it)) },
        )

        Divider()

        // 3. 默认新窗口配置
        SectionTitle(TablerIcons.Rocket, "默认新窗口配置")
        Spacer(Modifier.height(WwSize.cardListGap))
        PrefPickerField(
            label = "顾问 / Agent",
            value = ds.agentId,
            options = agents.map { it.id to "${it.avatar} ${it.name}".trim() },
            placeholder = "跟随当前",
            noneLabel = "跟随当前",
            onSelect = { patchDefaults(ds.copy(agentId = it)) },
        )
        PrefPickerField(
            label = "Provider",
            value = ds.providerId,
            options = providers.map { it.id to it.name },
            placeholder = "跟随当前",
            noneLabel = "跟随当前",
            onSelect = { patchDefaults(ds.copy(providerId = it)) },
        )
        ModelPickerField("默认模型", ds.model, providers) { patchDefaults(ds.copy(model = it)) }
        PrefPickerField(
            label = "AI 视野",
            value = (ds.aiContextVisibility ?: AiContextVisibilityFilter.ALL).name,
            options = listOf(
                AiContextVisibilityFilter.ALL.name to "全记忆",
                AiContextVisibilityFilter.LATEST_CUSTOM.name to "仅最新 N 条回答",
                AiContextVisibilityFilter.HIDE_ALL.name to "无历史回答记忆",
            ),
            placeholder = "全记忆",
            noneLabel = null,
            onSelect = { patchDefaults(ds.copy(aiContextVisibility = it?.let { name -> AiContextVisibilityFilter.entries.first { e -> e.name == name } })) },
        )
        NumberInputField(
            label = "AI 最新条数 N",
            value = ds.aiContextCustomLimit,
            placeholder = "如 3",
            min = 1,
            max = 999,
            onChange = { patchDefaults(ds.copy(aiContextCustomLimit = it)) },
        )
        NumberInputField(
            label = "屏幕渲染条数",
            hint = "0 = 全部渲染，只影响显示性能",
            value = ds.uiRenderLimit,
            placeholder = "0 = 全部渲染",
            min = 0,
            max = Int.MAX_VALUE,
            onChange = { patchDefaults(ds.copy(uiRenderLimit = it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = ds.timelineMemoryEnabled == true,
                onCheckedChange = { patchDefaults(ds.copy(timelineMemoryEnabled = it)) },
            )
            Text("默认开启时间线记忆整理", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
        }

        Divider()

        // 4. 新建窗口的默认开关
        SectionTitle(
            TablerIcons.Globe, "新建窗口的默认开关",
            "只决定「新建窗口」时的初始状态，不会改动已经存在的窗口。",
        )
        Spacer(Modifier.height(WwSize.cardListGap))
        SegmentedField(
            label = "联网",
            options = listOf(true to "默认允许联网", false to "默认禁止联网"),
            selected = ap.onlineSearchEnabled != false,
            onSelect = { onSaveAp(ap.copy(onlineSearchEnabled = it)) },
        )
        SegmentedField(
            label = "流式输出",
            options = listOf(true to "默认流式", false to "默认非流式"),
            selected = ap.streamingEnabled != false,
            onSelect = { onSaveAp(ap.copy(streamingEnabled = it)) },
        )

        Divider()

        // 5. 启动行为
        SectionTitle(TablerIcons.Power, "启动应用时")
        Spacer(Modifier.height(WwSize.cardListGap))
        SegmentedField(
            label = "启动行为",
            options = listOf(true to "进入上次窗口", false to "打开新窗口"),
            selected = ap.launchMode == LaunchMode.LAST,
            onSelect = { onSaveAp(ap.copy(launchMode = if (it) LaunchMode.LAST else LaunchMode.NEW)) },
        )

                Divider()
        WwTag(
            text = "清空默认项",
            onClick = {
                onSaveAp(ap.copy(defaultModels = DefaultModelConfig(), defaultSession = DefaultSessionConfig()))
            },
            background = WwSurfaces.inset,
            leadingIcon = TablerIcons.RotateClockwise,
        )
    }
}

@Composable
fun DisplaySettingsPanel(
    ds: MessageDisplaySettings,
    ap: AppPreferences,
    uiFont: UiFontFamily,
    onSaveDs: (MessageDisplaySettings) -> Unit,
    onSaveAp: (AppPreferences) -> Unit,
    onSetUiFont: (UiFontFamily) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 聊天区消息样式 ──
        SectionTitle(TablerIcons.Message, "聊天区消息样式")
        Spacer(Modifier.height(WwSize.cardGap))
        val currentBubble = ds.bubbleStyle ?: BubbleStyle.SMALL
        // 卡片间距由容器负责（cardGap 6dp），卡片自身不加垂直外边距。
        Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardGap)) {
        listOf(
            BubbleStyle.SMALL to ("小气泡" to "微信 / QQ 式，随文字宽度自适应（现有）"),
            BubbleStyle.LARGE to ("大气泡" to "无论文字多少，气泡占满聊天区宽度"),
        ).forEach { (style, meta) ->
            val active = currentBubble == style
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (active) WwSurfaces.cardSelected else WwSurfaces.card,
                // 非胶囊的框补一根模块2 框线（用户 2026-10-13）。
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                modifier = Modifier.fillMaxWidth().clickable {
                    onSaveDs(ds.copy(bubbleStyle = style))
                },
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (style == BubbleStyle.LARGE) TablerIcons.Maximize else TablerIcons.Message,
                            null,
                            Modifier.size(14.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(meta.first, fontSize = WwType.body)
                        Spacer(Modifier.width(6.dp))
                        InfoHint(meta.second)
                    }
                    Spacer(Modifier.height(WwSize.cardListGap))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                            .background(WwSurfaces.card, RoundedCornerShape(10.dp))
                            .border(1.dp, WwSurfaces.cardBorder, RoundedCornerShape(10.dp))
                            .padding(6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (style == BubbleStyle.LARGE) 1f else 0.5f)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.height(WwSize.cardListGap))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (style == BubbleStyle.LARGE) 1f else 0.66f)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
        }
        }

        Divider()

        // ── 界面字体大小 ──
        SectionTitle(TablerIcons.Typography, "界面字体大小")
        Spacer(Modifier.height(WwSize.cardListGap))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("A", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WwRangeSlider(
                value = ap.chatFontSize.toFloat(),
                range = 12f..22f,
                step = (12f..22f).stepOf(9),
                onValueChange = { v -> onSaveAp(ap.copy(chatFontSize = v.toDouble())) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("A", fontSize = WwType.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                "${ap.chatFontSize.roundToInt()}px",
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Divider()

        // ── 界面字体（黑体 / 宋体）──
        SectionTitle(TablerIcons.Typography, "界面字体")
        Spacer(Modifier.height(WwSize.cardListGap))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UiFontFamily.entries.forEach { option ->
                val active = option == uiFont
                WwTag(
                    text = option.label,
                    onClick = { onSetUiFont(option) },
                    background = if (active) MaterialTheme.colorScheme.secondaryContainer else WwSurfaces.inset,
                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                )
            }
        }

        Divider()

        // ── 用户头像（全局一个，消息区里「自己」那一侧）──
        SectionTitle(TablerIcons.User, "用户头像", "消息区里你自己那一侧的头像，所有窗口共用")
        Spacer(Modifier.height(WwSize.cardListGap))
        val avatarScope = rememberCoroutineScope()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(
                source = ap.userAvatarFileName?.let { AvatarSource.User(it) },
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WwSecondaryButton(
                    text = "上传头像",
                    leadingIcon = TablerIcons.Upload,
                    onClick = {
                        // 文件框是模态的：在点击回调（UI 线程）里同步挑完，再交给协程落盘。
                        val picked = pickImageFiles().firstOrNull()
                        if (picked != null) {
                            avatarScope.launch {
                                val name = withContext(Dispatchers.IO) {
                                    defaultAvatarStore.save(AvatarOwner.User, "", picked.bytes, picked.mime)
                                }
                                onSaveAp(ap.copy(userAvatarFileName = name))
                            }
                        }
                    },
                )
                if (ap.userAvatarFileName != null) {
                    WwSecondaryButton(
                        text = "移除",
                        onClick = {
                            avatarScope.launch {
                                withContext(Dispatchers.IO) { defaultAvatarStore.remove(AvatarOwner.User, "") }
                                onSaveAp(ap.copy(userAvatarFileName = null))
                            }
                        },
                    )
                }
            }
        }

        Divider()

        // ── 消息元数据显示开关 ──
        SectionTitle(TablerIcons.Message, "消息元数据显示开关")
        Spacer(Modifier.height(WwSize.cardGap))
        // 卡片间距由容器负责（cardGap 6dp），卡片自身不加垂直外边距。
        Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardGap)) {
            ToggleField("显示消息头像", "在每条消息上方显示头像（用户头像 / 窗口 AI 头像，窗口功能 →「头像设置」里换图与调大小）", "32px", TablerIcons.User, ds.showAvatars) {
                onSaveDs(ds.copy(showAvatars = it))
            }
            ToggleField("显示模型名", "在 AI 回复标题或气泡底部显示对应调用的模型名称（如 gemini-3.8-flash、gpt-4o）", "gemini-3.8-flash", TablerIcons.DeviceDesktop, ds.showModelName) {
                onSaveDs(ds.copy(showModelName = it))
            }
            ToggleField("显示消息发出时间", "在每条提问与回答下方显示具体时分秒（如 14:28:09）", "14:32:05", TablerIcons.Clock, ds.showMessageTime) {
                onSaveDs(ds.copy(showMessageTime = it))
            }
            ToggleField("显示响应耗时", "在 AI 回答下方展示思考与流式生成消耗的时间（如 820ms、1.4s）", "820ms", TablerIcons.Bolt, ds.showLatency) {
                onSaveDs(ds.copy(showLatency = it))
            }
            ToggleField("显示本条消息 Token", "预估并展示当前单条消息内容消耗的 Token 数量（如 128 tok）", "128 tok", TablerIcons.FileText, ds.showMessageTokens) {
                onSaveDs(ds.copy(showMessageTokens = it))
            }
            ToggleField("显示本窗口总 Token", "在窗口顶部显示该对话累计交互消耗的预估上下文 Token 总和", "2,450 tok", TablerIcons.Coin, ds.showSessionTokens) {
                onSaveDs(ds.copy(showSessionTokens = it))
            }
        }

    }
}

/**
 * 「文本设置」弹窗的固定底栏（用户 2026-10-12：这个弹窗少了保存分区）。
 *
 * 面板本身是**改一项立刻写回**，所以「保存」只负责关窗；「恢复默认」回到
 * [DEFAULT_DISPLAY_SETTINGS]。底栏常驻、正文上滚（与 AgentPanel / ProviderPanel /
 * PromptLibraryDialog 同一种 `FeatureDialog(footer = …)` 结构）。
 */
@Composable
fun DisplaySettingsFooter(
    ds: MessageDisplaySettings,
    onSave: () -> Unit,
    onSaveDs: (MessageDisplaySettings) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.weight(1f))
        WwSecondaryButton(
            text = "恢复默认",
            onClick = { onSaveDs(DEFAULT_DISPLAY_SETTINGS) },
            leadingIcon = TablerIcons.RotateClockwise,
        )
        WwPrimaryButton(text = "保存", onClick = onSave)
    }
}

/* =============================================================================
 * 复用小组件
 * ========================================================================== */

/** 滑杆的吸附步长，等价 Web `input[type=range]` 的 `step`（吸附由 `WwRangeSlider` 自己做）。 */
private fun ClosedFloatingPointRange<Float>.stepOf(steps: Int): Float = (endInclusive - start) / (steps + 1)

@Composable
private fun SectionTitle(icon: ImageVector, text: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = WwType.title)
        if (hint != null) {
            Spacer(Modifier.width(6.dp))
            InfoHint(hint)
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = WwPad.content))
}

/** 文本输入的数字框：只提交合法整数（对标 `ApiParameterInput` 的草稿约定）。 */
@Composable
private fun NumberInputField(
    label: String?,
    value: Int?,
    placeholder: String,
    min: Int,
    max: Int,
    onChange: (Int?) -> Unit,
    suffix: String? = null,
    hint: String? = null,
) {
    var draft by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = WwPad.fieldGap)) {
        if (label != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 标签里原来内嵌的括号说明，收成 ⓘ（用户 2026-10-12）。
                if (hint != null) {
                    Spacer(Modifier.width(6.dp))
                    InfoHint(hint)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WwTextField(
                value = draft,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(9)
                    draft = digits
                    val parsed = digits.toLongOrNull()
                    when {
                        digits.isEmpty() -> onChange(null)
                        parsed != null && parsed in min.toLong()..max.toLong() -> onChange(parsed.toInt())
                    }
                },
                singleLine = true,
                placeholder = placeholder,
                modifier = Modifier.weight(1f).widthIn(max = 280.dp),
            )
            if (suffix != null) {
                Text(suffix, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 通用下拉选择（对标 `pickerField`）：触发器 + 下拉栏（不再弹二级 Dialog）。 */
@Composable
private fun PrefPickerField(
    label: String,
    value: String?,
    options: List<Pair<String, String>>,
    placeholder: String,
    noneLabel: String?,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = WwPad.fieldGap)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            DropdownTrigger(
                text = options.firstOrNull { it.first == value }?.second ?: placeholder,
                onClick = { open = true },
            )
        }
        PreferenceDropdown(expanded = open, onDismiss = { open = false }, width = 300.dp) {
            if (noneLabel != null) {
                DropdownRow(noneLabel, value == null) { onSelect(null); open = false }
            }
            options.forEach { (id, text) ->
                DropdownRow(text, value == id) { onSelect(id); open = false }
            }
        }
    }
}

/** 模型选择（对标 `modelField`）：只列已配置 API Key 且有模型的 Provider，按 Provider 分组。 */
@Composable
private fun ModelPickerField(
    label: String,
    value: String?,
    providers: List<ApiProviderConfig>,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val groups = providers.filter { !it.apiKey.isNullOrBlank() && it.models.isNotEmpty() }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = WwPad.fieldGap)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            DropdownTrigger(
                text = value ?: "跟随当前 / Provider 默认模型",
                onClick = { open = true },
            )
        }
        PreferenceDropdown(expanded = open, onDismiss = { open = false }, width = 320.dp) {
            DropdownRow("跟随当前 / Provider 默认模型", value == null) { onSelect(null); open = false }
            if (groups.isEmpty()) {
                Text(
                    "暂无已配置 API Key 的可用模型",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding),
                )
            }
            groups.forEach { provider ->
                // 供应商分组标题（与输入栏「模型切换」下拉同款：名字 + 模型数）。
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwSize.listRowVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = provider.name,
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = provider.models.size.toString(),
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                provider.models.forEach { model ->
                    DropdownRow(model, value == model, monospace = true, indent = 16.dp) { onSelect(model); open = false }
                }
            }
        }
    }
}

/** 下拉栏的触发器：与 `WwSelectField` 同款（左文字 + 右箭头、10dp 圆角、1dp 描边）。 */
@Composable
private fun DropdownTrigger(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WwSurfaces.inset, shape)
            .border(1.dp, WwSurfaces.cardBorder, shape)
            .defaultMinSize(minHeight = WwSize.formFieldMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = WwSize.formFieldVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = WwType.hint,
            lineHeight = WwSize.compactTextLineHeight,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TablerIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 下拉栏浮层：与输入栏「模型切换」下拉同款（12dp 圆角 + 浮起底 + 1dp 描边 + 阴影），
 * 高度封顶后内部滚动。
 */
@Composable
private fun PreferenceDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    width: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(width),
        shape = RoundedCornerShape(12.dp),
        containerColor = WwSurfaces.dialogBase,
        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { content() }
    }
}

/** 下拉里的一行：当前项打 ✓；模型名走等宽（与「模型切换」下拉一致）。 */
@Composable
private fun DropdownRow(
    text: String,
    selected: Boolean,
    monospace: Boolean = false,
    indent: Dp = 12.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = indent, end = 12.dp, top = WwSize.listRowVerticalPadding, bottom = WwSize.listRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = WwType.body,
            lineHeight = WwSize.compactTextLineHeight,
            fontFamily = if (monospace) FontFamily.Monospace else WwFont.family,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 两段式选择（联网 / 流式 / 启动行为）。 */
@Composable
private fun SegmentedField(
    label: String,
    options: List<Pair<Boolean, String>>,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = WwPad.fieldGap)) {
        Text(label, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(WwSize.cardListGap))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, text) ->
                if (selected == value) {
                    WwTag(
                        text = text,
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        background = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    WwTag(
                        text = text,
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        background = WwSurfaces.inset,
                        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                    )
                }
            }
        }
    }
}

/** 带描述与预览的开关行（对标 SettingsModal 的开关卡片）。 */
@Composable
private fun ToggleField(
    title: String,
    description: String,
    preview: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
            Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (checked) WwSurfaces.cardSelected else WwSurfaces.card,
        // 非胶囊的框补一根模块2 框线（用户 2026-10-13）。
        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card)) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
                    Spacer(Modifier.width(6.dp))
                    // 描述收成浮层角标（用户 2026-10-11：框外注释一律收起）。
                    InfoHint(description)
                    Spacer(Modifier.weight(1f))
                    Text(
                        preview,
                        fontSize = WwType.hint,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            WwSwitch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
