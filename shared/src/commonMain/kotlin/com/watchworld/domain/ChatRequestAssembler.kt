package com.watchworld.domain

import com.watchworld.model.Agent
import com.watchworld.model.AppPreferences
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.net.ToolChatImage
import com.watchworld.net.ToolChatListeners
import com.watchworld.net.ToolChatMessage
import com.watchworld.net.ToolChatParams
import com.watchworld.platform.ImageStore

/** T14 请求装配的纯函数入口。网络搜索仍由调用方在 build 之前完成。 */
object ChatRequestAssembler {
    /**
     * 把 [ToolChatMessage.imageIds] 解析成图片本体（`api.ts:367` 的 `ImageStore.getMany`）。
     *
     * 为什么拆成独立的一步：读图要挂起 + 碰磁盘，而工具循环里的 `providerMessage`
     * 必须是纯函数（它还被回放路径复用）。在 `runToolChat` **之前**一次性补齐，
     * 循环内部就只需按协议拼装。
     *
     * 读不到的 id 会被默默丢弃（TS 的 `.filter(Boolean)` 同一语义）——
     * 此时该消息退回文本（纯图片消息补 `[图片]`），而不是整个请求失败。
     */
    suspend fun loadImages(params: ToolChatParams, store: ImageStore): ToolChatParams {
        if (params.messages.none { !it.imageIds.isNullOrEmpty() }) return params
        return params.copy(
            messages = params.messages.map { message ->
                val ids = message.imageIds
                if (ids.isNullOrEmpty()) {
                    message
                } else {
                    val map = store.getMany(ids)
                    message.copy(
                        images = ids.mapNotNull { id ->
                            map[id]?.let { ToolChatImage(dataUrl = it.dataUrl, mime = it.mime) }
                        },
                    )
                }
            },
        )
    }

    fun assemble(
        session: ChatSession,
        agent: Agent,
        provider: ApiProviderConfig,
        preferences: AppPreferences,
        context: AssembledContext,
        listeners: ToolChatListeners = ToolChatListeners(),
    ): ToolChatParams {
        val defaults = preferences.defaultSession
        return ToolChatParams(
            provider = provider,
            model = session.model?.takeIf { it.isNotBlank() }
                ?: provider.defaultModel.takeIf { it.isNotBlank() }
                ?: "gemini-2.0-flash",
            messages = context.messages.map { message ->
                ToolChatMessage(
                    role = when (message.role) {
                        Role.USER -> "user"
                        Role.ASSISTANT -> "assistant"
                        Role.SYSTEM -> "system"
                    },
                    content = message.content,
                    imageIds = message.imageIds,
                )
            },
            systemInstruction = context.systemInstruction,
            // api.ts native path: window ?? DEFAULT_APP_PREFERENCES.defaultSession ?? agent ?? literal.
            temperature = session.temperature ?: defaults.temperature ?: agent.temperature,
            topP = session.topP ?: defaults.topP ?: agent.topP ?: 0.95,
            topK = session.topK ?: defaults.topK,
            frequencyPenalty = session.frequencyPenalty ?: defaults.frequencyPenalty,
            presencePenalty = session.presencePenalty ?: defaults.presencePenalty,
            maxTokens = session.maxTokens ?: defaults.maxTokens,
            activeMcpServers = context.availableMcpServers,
            activeSkills = context.availableSkills,
            toolCallStepLimit = session.toolCallStepLimit ?: defaults.toolCallStepLimit ?: 0,
            stream = session.streamingEnabled != false,
            listeners = listeners,
        )
    }
}
