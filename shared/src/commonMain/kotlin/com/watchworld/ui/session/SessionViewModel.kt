package com.watchworld.ui.session

import com.watchworld.data.DEFAULT_APP_PREFERENCES
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.data.repository.imageIdsOf
import com.watchworld.model.AiContextVisibilityFilter
import com.watchworld.model.ChatGroup
import com.watchworld.model.ChatSession
import com.watchworld.platform.ImageStore
import com.watchworld.ui.components.randomGroupColor
import com.watchworld.platform.currentTimeMillis
import com.watchworld.platform.defaultImageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** T14 会话树状态。UI 只依赖 Repository，不触碰 DAO。 */
data class SessionUiState(
    val loading: Boolean = true,
    val sessions: List<ChatSession> = emptyList(),
    val groups: List<ChatGroup> = emptyList(),
    val activeSessionId: String? = null,
    val error: String? = null,
) {
    val activeSession: ChatSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }
}

class SessionViewModel(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val config: ConfigRepository,
    private val now: () -> Long = ::currentTimeMillis,
    /** T21：会话被清空/删除后清理不再被引用的图片。 */
    private val imageStore: ImageStore = defaultImageStore,
) {
    private val mutableState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    suspend fun load() {
        runCatching {
            val loadedGroups = config.groups().ifEmpty { listOf(defaultGroup()) }
            val loadedSessions = sessions.all()
            val selected = settings.activeSessionId()
            val active = selected.takeIf { id -> loadedSessions.any { it.id == id } }
                ?: loadedSessions.firstOrNull()?.id
            mutableState.value = SessionUiState(
                loading = false,
                sessions = loadedSessions,
                groups = loadedGroups.sortedBy { it.order },
                activeSessionId = active,
            )
            active?.let { settings.setActiveSessionId(it) }
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                loading = false,
                error = error.message ?: error.toString(),
            )
        }
    }

    suspend fun newSession(groupId: String? = null): ChatSession {
        val snapshot = mutableState.value
        val preferences = settings.appPreferences()
        val providers = config.providers()
        val agents = config.agents()
        val defaults = preferences.defaultSession
        val providerId = defaults.providerId
            ?: settings.activeProviderId().takeIf { id -> providers.any { it.id == id } }
            ?: providers.firstOrNull()?.id
            ?: "provider-gemini"
        val provider = providers.firstOrNull { it.id == providerId }
        val stamp = now()
        val created = ChatSession(
            id = "session-$stamp",
            title = "新对话",
            groupId = groupId
                ?: snapshot.groups.firstOrNull()?.id
                ?: "group-default",
            agentId = defaults.agentId
                ?: agents.firstOrNull { it.isDefault == true }?.id
                ?: agents.firstOrNull()?.id
                ?: "agent-omni",
            providerId = providerId,
            model = defaults.model
                ?: preferences.defaultModels.chat
                ?: provider?.defaultModel
                ?: "gemini-2.0-flash",
            aiContextVisibility = defaults.aiContextVisibility
                ?: DEFAULT_APP_PREFERENCES.defaultSession.aiContextVisibility
                ?: AiContextVisibilityFilter.ALL,
            aiContextCustomLimit = defaults.aiContextCustomLimit,
            uiRenderLimit = defaults.uiRenderLimit ?: 0,
            timelineMemoryEnabled = defaults.timelineMemoryEnabled ?: false,
            temperature = defaults.temperature,
            topP = defaults.topP,
            topK = defaults.topK,
            frequencyPenalty = defaults.frequencyPenalty,
            presencePenalty = defaults.presencePenalty,
            maxTokens = defaults.maxTokens,
            toolCallStepLimit = defaults.toolCallStepLimit,
            webSearchEnabled = preferences.onlineSearchEnabled != false,
            streamingEnabled = preferences.streamingEnabled != false,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val stored = sessions.create(created)
        val next = listOf(stored) + snapshot.sessions.filterNot { it.id == stored.id }
        mutableState.value = snapshot.copy(sessions = next, activeSessionId = stored.id, error = null)
        settings.setActiveSessionId(stored.id)
        return stored
    }

    /**
     * 从轻量导入文件（MD / JSON）创建一个新窗口并切到它。
     * 偏好字段与 [newSession] 完全同一套默认值回退。
     */
    suspend fun importConversation(parsed: com.watchworld.domain.ParsedConversation): ChatSession {
        val snapshot = mutableState.value
        val preferences = settings.appPreferences()
        val providers = config.providers()
        val agents = config.agents()
        val defaults = preferences.defaultSession
        val providerId = defaults.providerId
            ?: settings.activeProviderId().takeIf { id -> providers.any { it.id == id } }
            ?: providers.firstOrNull()?.id
            ?: "provider-gemini"
        val provider = providers.firstOrNull { it.id == providerId }
        val stamp = now()
        val created = ChatSession(
            id = "session-$stamp",
            title = parsed.title.ifBlank { "导入的对话" },
            groupId = snapshot.groups.firstOrNull()?.id ?: "group-default",
            agentId = defaults.agentId
                ?: agents.firstOrNull { it.isDefault == true }?.id
                ?: agents.firstOrNull()?.id
                ?: "agent-omni",
            providerId = providerId,
            model = defaults.model
                ?: preferences.defaultModels.chat
                ?: provider?.defaultModel
                ?: "gemini-2.0-flash",
            aiContextVisibility = defaults.aiContextVisibility
                ?: DEFAULT_APP_PREFERENCES.defaultSession.aiContextVisibility
                ?: AiContextVisibilityFilter.ALL,
            aiContextCustomLimit = defaults.aiContextCustomLimit,
            uiRenderLimit = defaults.uiRenderLimit ?: 0,
            timelineMemoryEnabled = defaults.timelineMemoryEnabled ?: false,
            temperature = defaults.temperature,
            topP = defaults.topP,
            topK = defaults.topK,
            frequencyPenalty = defaults.frequencyPenalty,
            presencePenalty = defaults.presencePenalty,
            maxTokens = defaults.maxTokens,
            toolCallStepLimit = defaults.toolCallStepLimit,
            webSearchEnabled = preferences.onlineSearchEnabled != false,
            streamingEnabled = preferences.streamingEnabled != false,
            createdAt = stamp,
            updatedAt = stamp,
            messages = parsed.messages,
        )
        val stored = sessions.create(created)
        val next = listOf(stored) + snapshot.sessions.filterNot { it.id == stored.id }
        mutableState.value = snapshot.copy(sessions = next, activeSessionId = stored.id, error = null)
        settings.setActiveSessionId(stored.id)
        return stored
    }

    suspend fun select(id: String) {
        if (mutableState.value.sessions.none { it.id == id }) return
        mutableState.value = mutableState.value.copy(activeSessionId = id)
        settings.setActiveSessionId(id)
    }

    suspend fun rename(id: String, title: String) {
        val value = title.trim()
        if (value.isEmpty()) return
        val stamp = sessions.rename(id, value)
        updateSession(id) { it.copy(title = value, updatedAt = stamp) }
    }

    suspend fun clear(id: String) {
        val current = mutableState.value.sessions.firstOrNull { it.id == id } ?: return
        replace(sessions.save(current.copy(messages = emptyList())))
        pruneOrphanImages()
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val stamp = sessions.setPinned(id, pinned)
        updateSession(id) { it.copy(pinned = pinned, updatedAt = stamp) }
    }

    suspend fun moveToGroup(id: String, groupId: String) {
        if (mutableState.value.groups.none { it.id == groupId }) return
        val stamp = sessions.moveToGroup(id, groupId)
        updateSession(id) { it.copy(groupId = groupId, updatedAt = stamp) }
    }

    suspend fun delete(id: String) {
        sessions.delete(id)
        settings.clearInputDraft(id)
        val remaining = mutableState.value.sessions.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            mutableState.value = mutableState.value.copy(sessions = emptyList(), activeSessionId = null)
            newSession()
            pruneOrphanImages()
            return
        }
        val active = if (mutableState.value.activeSessionId == id) remaining.first().id
        else mutableState.value.activeSessionId
        mutableState.value = mutableState.value.copy(sessions = remaining, activeSessionId = active)
        active?.let { settings.setActiveSessionId(it) }
        pruneOrphanImages()
    }

    /** 清理没有任何消息引用的图片（`App.tsx:1571`）。按剩余窗口统计，删除失败不影响主流程。 */
    private suspend fun pruneOrphanImages() {
        runCatching { imageStore.pruneOrphans(imageIdsOf(mutableState.value.sessions)) }
    }

    /**
     * 新建分组。
     *
     * ⚠️ 颜色**不再写死石板灰**（用户 2026-10-13 报「圆点灰暗、Web 版是彩色」）：
     * 缺省随机取一个预设色（对标 Web `GroupModal.tsx:56`），侧边栏创建行里点的色环取色板
     * 通过 [color] 传入。
     */
    suspend fun createGroup(name: String, color: String = randomGroupColor()): ChatGroup? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = mutableState.value.groups
        val group = ChatGroup(
            id = "group-${now()}",
            name = trimmed,
            color = color,
            icon = "Folder",
            order = current.size,
        )
        config.setGroups(current + group)
        mutableState.value = mutableState.value.copy(groups = current + group)
        return group
    }

    /** 改分组名字 / 颜色（用户 2026-10-13：侧边栏分组头的「编辑」）。名字空着不改。 */
    suspend fun updateGroup(groupId: String, name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = mutableState.value.groups
        val next = current.map { if (it.id == groupId) it.copy(name = trimmed, color = color) else it }
        config.setGroups(next)
        mutableState.value = mutableState.value.copy(groups = next)
    }

    /** ChatViewModel 完成消息写回后，用 Repository 返回的权威对象同步侧边栏状态。 */
    fun replace(session: ChatSession) {
        updateSession(session.id) { session }
    }

    private fun updateSession(id: String, transform: (ChatSession) -> ChatSession) {
        mutableState.value = mutableState.value.copy(
            sessions = mutableState.value.sessions.map { if (it.id == id) transform(it) else it },
        )
    }

    private fun defaultGroup(): ChatGroup = ChatGroup(
        id = "group-default",
        name = "默认分组",
        color = "#64748b",
        icon = "Folder",
        order = 0,
    )
}
