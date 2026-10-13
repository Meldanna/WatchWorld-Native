package com.watchworld.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ChatSession
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Book
import compose.icons.tablericons.Download
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Message
import compose.icons.tablericons.Repeat
import compose.icons.tablericons.Stack
import compose.icons.tablericons.Temperature
import compose.icons.tablericons.TrendingUp
import compose.icons.tablericons.Adjustments
import kotlin.math.roundToInt

/**
 * 当前窗口的**派生配置**只读总览（原生补充面板，参考版没有独立组件）。
 *
 * 为什么做这一块：设置里的「默认与偏好 / 文本设置」管的是**全局默认**，
 * 而一个已经打开的窗口往往有自己的一套覆盖值（窗口 API 参数、本窗口模型、
 * 本窗口正则 / 知识库 / 世界观 / 时间线可见性 …）。
 * 用户看不到「这个窗口现在到底在用什么」，只能一个个弹窗翻。
 * 这里把它们**只读**列出来，编辑仍然回各自的入口。
 *
 * ⚠️ 只读、不改任何状态。别在这里加输入控件 —— 那会与窗口功能弹层形成两个改同一份
 *    会话字段的入口（污染「一个窗口配置只有一处可改」的边界）。
 */
@Composable
fun CurrentWindowOverview(
    session: ChatSession?,
    providerName: String,
    modelName: String,
    agentName: String,
    regexEnabledCount: Int?,
    knowledgeCount: Int,
    worldDocCount: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        if (session == null) {
            Text(
                "当前没有打开的窗口。",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        SectionLabel(TablerIcons.Adjustments, "当前窗口的生效配置（只读）")

        OverviewRow("窗口", session.title.ifBlank { "未命名对话" })
        OverviewRow(
            "服务商 / 模型",
            "${providerName.ifBlank { "未选择服务商" }} · ${modelName.ifBlank { "未选择模型" }}",
        )
        OverviewRow("顾问", agentName.ifBlank { "未配置 Agent" })
        OverviewRow(
            "窗口 API 参数",
            windowParamSummary(session),
            icon = TablerIcons.Temperature,
        )
        OverviewRow(
            "本窗口正则",
            when (regexEnabledCount) {
                null -> "跟随全局（全部已启用）"
                else -> "已启用 $regexEnabledCount 条"
            },
        )
        OverviewRow("本窗口知识库", "$knowledgeCount 篇已连接")
        OverviewRow(
            "本窗口世界观",
            when (worldDocCount) {
                null -> "跟随全局（全部启用文档）"
                else -> "$worldDocCount 篇已注入"
            },
        )
        OverviewRow(
            "时间线记忆整理",
            if (session.timelineMemoryEnabled == true) {
                "已开启 · ${session.timelines.orEmpty().count { it.visible != false }} 条可见分支"
            } else {
                "未开启"
            },
        )
        OverviewRow(
            "流式输出",
            when (session.streamingEnabled) {
                true -> "流式"
                false -> "非流式"
                null -> "跟随默认（流式）"
            },
        )
        OverviewRow(
            "图片上下文",
            when (session.imageContextMode?.name?.lowercase()) {
                "history" -> "随历史保留"
                else -> "只发一轮"
            },
        )
        OverviewRow(
            "AI 视野",
            when (session.aiContextVisibility.name.lowercase()) {
                "latest_custom" -> "仅最新 ${session.aiContextCustomLimit ?: 3} 条回答"
                "hide_all" -> "无历史回答记忆"
                else -> "全记忆"
            },
        )
        OverviewRow(
            "屏幕渲染条数",
            if (session.uiRenderLimit <= 0) "全部渲染" else "${session.uiRenderLimit} 条",
        )

        if (session.systemPromptOverride.orEmpty().isNotBlank()) {
            OverviewRow("窗口提示词覆盖", "已设置（${session.systemPromptOverride.orEmpty().length} 字）")
        }
    }
}

/** 窗口 API 参数一行摘要：只列**被本窗口覆盖**的那几项，没覆盖就不列（避免一屏 `跟随默认`）。 */
private fun windowParamSummary(session: ChatSession): String {
    val parts = buildList {
        session.temperature?.let { add("发散 ${trimNum(it)}") }
        session.topP?.let { add("TopP ${trimNum(it)}") }
        session.topK?.let { add("TopK $it") }
        session.frequencyPenalty?.let { add("重复 ${trimNum(it)}") }
        session.presencePenalty?.let { add("话题 ${trimNum(it)}") }
        session.maxTokens?.let { add("最大 $it tok") }
        session.toolCallStepLimit?.let { add(if (it == 0) "工具轮数不限" else "工具 $it 轮") }
    }
    return if (parts.isEmpty()) "全部跟随默认（未覆盖）" else parts.joinToString(" · ")
}

/** 去掉多余小数位：`0.70` → `0.7`（与 Agent 发散度那处的显示口径保持一致）。 */
private fun trimNum(value: Double): String {
    val scaled = (value * 100).roundToInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = WwType.body, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 一行 `标签 —— 值`；值过长时省略号截断（只读总览不换行堆高）。 */
@Composable
private fun OverviewRow(label: String, value: String, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.inset,
        // 非胶囊的框补一根模块2 框线（用户 2026-10-13）。
        border = androidx.compose.foundation.BorderStroke(1.dp, WwSurfaces.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwSize.boxVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(
                value,
                fontSize = WwType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
