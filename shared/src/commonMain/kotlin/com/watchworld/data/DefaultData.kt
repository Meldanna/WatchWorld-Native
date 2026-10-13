package com.watchworld.data

import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.AppPreferences
import com.watchworld.model.BubbleStyle
import com.watchworld.model.DefaultModelConfig
import com.watchworld.model.DefaultSessionConfig
import com.watchworld.model.LaunchMode
import com.watchworld.model.MessageDisplaySettings

/**
 * `src/lib/defaultData.ts` 中 T14/T15 共用的默认设置。
 *
 * 此处只迁移当前任务实际依赖的显示设置与应用偏好。字段和值逐项保持 Web 版语义，
 * 尤其 [DEFAULT_APP_PREFERENCES] 的联网/流式默认开启，以及工具步数 0=无限。
 */
val DEFAULT_DISPLAY_SETTINGS: MessageDisplaySettings = MessageDisplaySettings(
    showModelName = true,
    showMessageTime = true,
    showLatency = true,
    showSessionTokens = true,
    showMessageTokens = true,
    showTimelineTag = true,
    bubbleStyle = BubbleStyle.SMALL,
    // 头像那一组是原生新增（Web 版没有），默认沿用模型默认值：大小 32px、显示开关开。
    avatarSize = 32,
    showAvatars = true,
    bubbleSpacing = 8,
)

/**
 * `defaultData.ts:211 DEFAULT_SUMMARY_PROMPT`（逐字）。
 * 总结面板的提示词留空时用它。⚠️ 改这里等于改「全新安装 + 老存档缺字段」两处的行为。
 */
const val DEFAULT_SUMMARY_PROMPT: String = "请对以下对话片段做一份可长期复用的结构化总结。\n\n" +
    "要求：\n" +
    "1. 只记录已确认发生的事实，不推测、不补充未出现的信息；\n" +
    "2. 保留关键因果：谁做了什么、出于什么动机、导致了什么结果；\n" +
    "3. 记录人物状态变化与关系进展（立场、态度、已知情范围）；\n" +
    "4. 若涉及时间线分支，标明事件归属哪个分支；\n" +
    "5. 用条目呈现，语言精炼，去掉寒暄与过程性描述；\n" +
    "6. 不复述原文，只提炼结论与当前状态。"

val DEFAULT_APP_PREFERENCES: AppPreferences = AppPreferences(
    chatFontSize = 15.0,
    defaultModels = DefaultModelConfig(),
    defaultSession = DefaultSessionConfig(
        aiContextVisibility = AiContextVisibilityFilter.ALL,
        uiRenderLimit = 0,
        toolCallStepLimit = 0,
    ),
    launchMode = LaunchMode.LAST,
    modelGroupOrder = emptyList(),
    onlineSearchEnabled = true,
    streamingEnabled = true,
)
