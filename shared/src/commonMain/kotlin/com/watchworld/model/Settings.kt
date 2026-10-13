package com.watchworld.model

import kotlinx.serialization.Serializable

/**
 * `src/types.ts:143 MessageDisplaySettings`。
 *
 * ⚠️ 前 6 个布尔字段在 TS 里是**必填**，但老存档可能整个对象缺失
 *    （storage.ts `getDisplaySettings` 用 `{...DEFAULT_DISPLAY_SETTINGS, ...saved}` 合并）。
 *    这里只能给「关」作默认值，真正的兜底默认值在 `DefaultData`（T07/T06 引入默认数据时补）。
 */
@Serializable
data class MessageDisplaySettings(
    /** 是否显示模型名。 */
    val showModelName: Boolean = false,
    /** 是否显示消息发出时间。 */
    val showMessageTime: Boolean = false,
    /** 是否显示耗时。 */
    val showLatency: Boolean = false,
    /** 是否显示本窗口 Token 统计。 */
    val showSessionTokens: Boolean = false,
    /** 是否显示本消息 Token。 */
    val showMessageTokens: Boolean = false,
    /** 是否显示时间线分支标签。 */
    val showTimelineTag: Boolean? = null,
    /** 聊天气泡样式：small=小气泡（微信/QQ 式）；large=大气泡（占满聊天区宽度）。 */
    val bubbleStyle: BubbleStyle? = null,
    /** 头像大小（px），范围 24-96（用户 2026-10-13 把上限 48 → 96），默认 32。 */
    val avatarSize: Int = 32,
    /**
     * 消息区是否显示头像（窗口功能 →「头像设置」里的开关）。
     * 默认开：没设过头像的窗口本来就什么也画不出来。
     */
    val showAvatars: Boolean = true,
    /**
     * 相邻气泡之间的垂直间距（px，0–256，默认 8）。
     * 窗口功能 →「头像设置」里用滑杆调；影响聊天区消息列表的分隔。
     */
    val bubbleSpacing: Int = 8,
)

/** `src/types.ts:160 DefaultModelConfig`（各用途的默认模型，留空则回退到当前 Provider/窗口模型）。 */
@Serializable
data class DefaultModelConfig(
    val chat: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val analysis: String? = null,
)

/** `src/types.ts:168 DefaultSessionConfig`（新建窗口时的默认配置）。 */
@Serializable
data class DefaultSessionConfig(
    val agentId: String? = null,
    val providerId: String? = null,
    val model: String? = null,
    val aiContextVisibility: AiContextVisibilityFilter? = null,
    val aiContextCustomLimit: Int? = null,
    val uiRenderLimit: Int? = null,
    val timelineMemoryEnabled: Boolean? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val maxTokens: Int? = null,
    /**
     * 连续工具调用轮数上限：0 = 无限制。
     * ⚠️ 三态：`null` = 继承全局；`0` = 无限制。读老存档时先按 JSON 数值归一化再赋值
     *    （见 `MIGRATION_CONSTRAINTS.md` §2 与 `AppPreferences.toolCallStepLimit`）。
     */
    val toolCallStepLimit: Int? = null,
)

/** `src/types.ts:190 AppPreferences`（全局偏好设置）。 */
@Serializable
data class AppPreferences(
    /** 界面字体大小（px），消息正文与各弹窗均跟随。 */
    val chatFontSize: Double = 15.0,
    val defaultModels: DefaultModelConfig = DefaultModelConfig(),
    val defaultSession: DefaultSessionConfig = DefaultSessionConfig(),
    /** 启动行为：进入上次窗口 / 打开新窗口。 */
    val launchMode: LaunchMode = LaunchMode.LAST,
    /** 输入框模型下拉里供应商分组的显示顺序（存 Provider ID）。 */
    val modelGroupOrder: List<String>? = null,
    /** 新建窗口时的默认联网授权；窗口建立后由 [ChatSession.webSearchEnabled] 独立控制。 */
    val onlineSearchEnabled: Boolean? = null,
    /**
     * 新建窗口时的默认流式开关。**只影响之后新建的窗口**，不改变已存在窗口的显示方式。
     * ⚠️ 保持 `Boolean?` 三态，不要把 `null` 归一化成 `false`。
     */
    val streamingEnabled: Boolean? = null,
    /** 用户头像文件名（全局统一，存储在 avatars/ 目录）。 */
    val userAvatarFileName: String? = null,
)
