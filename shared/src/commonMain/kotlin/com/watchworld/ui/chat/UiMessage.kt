package com.watchworld.ui.chat

/** 消息角色（T02 只需区分用户 / 助手）。 */
enum class UiRole { User, Assistant }

/**
 * T02 临时 UI 消息模型 —— **只为「最小聊天界面 + 假流式」服务**。
 *
 * ⚠️ 不要在这里加字段去贴近存档：真正的消息模型是 T05 按 PLAN §4 从 `src/types.ts`
 * 迁移出的 `ChatMessage`（19 字段）。届时本类**整体替换**，不是被扩展。
 *
 * 之所以不放进 `model/`：`model/` 是 §4 的正式产物，放临时模型会误导后来者。
 */
data class UiMessage(
    val id: Long,
    val role: UiRole,
    val content: String,
    /** 该条是否正在流式输出（决定是否显示闪烁光标）。 */
    val streaming: Boolean = false,
)
