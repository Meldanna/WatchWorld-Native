package com.watchworld.ui.chat

import com.watchworld.data.DEFAULT_SUMMARY_PROMPT
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.data.repository.imageIdsOf
import com.watchworld.domain.ChatRequestAssembler
import com.watchworld.domain.COMMON_CODE_TAG
import com.watchworld.domain.ContextBuilder
import com.watchworld.domain.ContextRequest
import com.watchworld.domain.DEFAULT_MAIN_TIMELINE_ID
import com.watchworld.domain.buildSystemInstruction
import com.watchworld.domain.dropSummarizedFloors
import com.watchworld.domain.getAvailableMcpToolNames
import com.watchworld.domain.selectActiveMcpServers
import com.watchworld.domain.selectActiveRegexRules
import com.watchworld.domain.selectActiveSkills
import com.watchworld.domain.selectActiveWorldDocuments
import com.watchworld.domain.ROLE_ANALYSIS_CODE_TAG
import com.watchworld.domain.ROLE_ANALYSIS_DESCRIPTION_TAG
import com.watchworld.domain.ROLE_ANALYSIS_GROUP_ID
import com.watchworld.domain.ROLE_ANALYSIS_PLACEHOLDER
import com.watchworld.domain.ROLE_ANALYSIS_SYSTEM_INSTRUCTION
import com.watchworld.domain.SUMMARY_SYSTEM_INSTRUCTION
import com.watchworld.domain.TimelineDraft
import com.watchworld.domain.applyTimelineDraft
import com.watchworld.domain.createDefaultMainTimeline
import com.watchworld.domain.buildAnalysisDocTitle
import com.watchworld.domain.buildRoleAnalysisContext
import com.watchworld.domain.buildRoleAnalysisFallback
import com.watchworld.domain.buildRoleAnalysisUserPrompt
import com.watchworld.domain.buildSummaryRequest
import com.watchworld.domain.buildSummaryTranscript
import com.watchworld.domain.currentBranchInfo
import com.watchworld.domain.deleteTimelineBranch
import com.watchworld.domain.dropHiddenAnalysisMessages
import com.watchworld.domain.resolveAnalysisModel
import com.watchworld.domain.resolveSummaryModel
import com.watchworld.domain.selectConnectedKnowledge
import com.watchworld.domain.setActiveTimelineBranch
import com.watchworld.domain.setAllTimelineBranchesVisibility
import com.watchworld.domain.setTimelineBranchVisibility
import com.watchworld.domain.summaryRange
import com.watchworld.domain.unsummarizedMessages
import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.ImageContextMode
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import com.watchworld.model.SessionSummary
import com.watchworld.model.TagDisplayMode
import com.watchworld.model.WorldDocument
import com.watchworld.net.ChatStreamClient
import com.watchworld.net.McpClient
import com.watchworld.net.ToolChatListeners
import com.watchworld.net.ToolChatMessage
import com.watchworld.net.ToolChatParams
import com.watchworld.net.WebSearchClient
import com.watchworld.net.formatSearchContext
import com.watchworld.net.formatSkillDirectoryContext
import com.watchworld.net.isSkillSearchIntent
import com.watchworld.net.runToolChat
import com.watchworld.net.shouldUseWebSearch
import com.watchworld.platform.ImageStore
import com.watchworld.platform.currentTimeMillis
import com.watchworld.platform.defaultImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** T14 正式聊天状态。 */
data class ChatUiState(
    val session: ChatSession? = null,
    val streamingMessageId: String? = null,
    val error: String? = null,
    /** T20：前文总结生成中。 */
    val summarizing: Boolean = false,
    /** T20：最近一次总结失败原因（不落消息）。 */
    val summaryError: String? = null,
    /** T20：角色关系分析生成中。 */
    val analyzing: Boolean = false,
    /**
     * 本窗口输入框的**草稿**（逐键落 kv，见 [ChatViewModel.setDraft]）。
     * 由 ViewModel 持有而不是输入栏内部 `remember` —— 切窗口 / 重启应用才能接着写。
     */
    val draft: String = "",
) {
    val streaming: Boolean get() = streamingMessageId != null
}

class ChatViewModel(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val configRepository: ConfigRepository,
    private val chatClient: ChatStreamClient,
    private val searchClient: WebSearchClient,
    private val mcpClient: McpClient,
    private val now: () -> Long = ::currentTimeMillis,
    private val onSessionChanged: (ChatSession) -> Unit = {},
    /** T21：图片本体从这里读（默认走应用数据目录，测试可注入）。 */
    private val imageStore: ImageStore = defaultImageStore,
) {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    private var generationJob: Job? = null

    fun bindJob(job: Job) {
        generationJob = job
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
    }

    /** 切换本窗口联网授权（参考版 `App.tsx:2227` 的 `onToggleWebSearch`）。 */
    suspend fun toggleWebSearchEnabled() {
        val session = mutableState.value.session ?: return
        mutateSession { it.copy(webSearchEnabled = it.webSearchEnabled != true) }
    }

    /**
     * 切换本窗口的模型（参考版 `ChatInput` 的模型下拉 → `App.tsx:2246-2256`）。
     *
     * 同 Provider 只改 `session.model`；跨 Provider 连带写会话的 `providerId` 与
     * 全局偏好的 `activeProviderId`（参考版 `if (providerId !== activeProviderId)` 那一步）——
     * 少了后者，用户在本窗口选了别的供应商，新建窗口又会跳回旧的。
     *
     * 三项都没变时**直接返回**：无谓地盖一次 `updatedAt`，会骗过同步引擎（T19）当成真改动。
     */
    suspend fun selectProviderAndModel(providerId: String, model: String) {
        val session = mutableState.value.session ?: return
        if (session.providerId == providerId && session.model == model) return
        if (session.providerId == providerId) {
            mutateSession { it.copy(model = model) }
        } else {
            val saved = sessionRepository.save(session.copy(providerId = providerId, model = model))
            publish(saved, null, null)
            settingsRepository.setActiveProviderId(providerId)
        }
    }

    suspend fun open(session: ChatSession?) {
        if (mutableState.value.streaming) stop()
        // 草稿跟着窗口走（`App.tsx:188-192` 的 useEffect）：打开窗口时把该窗口的草稿读回输入框。
        val draft = session?.id?.let { settingsRepository.inputDraft(it) }.orEmpty()
        mutableState.value = ChatUiState(session = session, draft = draft)
    }

    /**
     * 输入框草稿**即时保持**（用户 2026-10-12；对标 `App.tsx:194-199 handleDraftChange`）。
     *
     * 敲一下键就写一次 kv（空串 = 删行，见 [SettingsRepository.setInputDraft]），
     * 不做防抖 —— 参考版就是逐键同步写 localStorage，桌面端 kv 单行 upsert 足够便宜。
     */
    suspend fun setDraft(text: String) {
        val sessionId = mutableState.value.session?.id ?: return
        mutableState.value = mutableState.value.copy(draft = text)
        settingsRepository.setInputDraft(sessionId, text)
    }

    // ------------------------------------------------------------------ T16 消息级操作

    /**
     * 版本切换：更新 [ChatMessage.currentVersionIndex] 并持久化。
     * 同时镜像顶层 `latencyMs` / `tokens`（`api.ts:1412 handleSwitchVersion` 的语义）。
     */
    suspend fun switchVersion(messageId: String, newIndex: Int) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.streaming) return
        val updated = session.copy(
            messages = session.messages.map { msg ->
                if (msg.id != messageId) msg else {
                    val clamped = newIndex.coerceIn(0, msg.versions.lastIndex.coerceAtLeast(0))
                    val ver = msg.versions.getOrNull(clamped)
                    msg.copy(
                        currentVersionIndex = clamped,
                        latencyMs = ver?.latencyMs,
                        tokens = ver?.tokens,
                    )
                }
            },
        )
        val saved = sessionRepository.save(updated)
        publish(saved, null, null)
    }

    /** 删除单条消息并持久化。 */
    suspend fun deleteMessage(messageId: String) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.streaming) return
        val updated = session.copy(
            messages = session.messages.filter { it.id != messageId },
        )
        val saved = sessionRepository.save(updated)
        publish(saved, null, null)
        // 删后清理不再被引用的图片，避免图片目录无限增长（`App.tsx:1502` 同一时机）。
        pruneOrphanImages()
    }

    /**
     * 清理没有任何消息引用的图片（`App.tsx:1499 pruneOrphanImages`）。
     *
     * 按**全部窗口**统计引用（不是当前窗口）：图片可能被别的窗口共享，
     * 只看当前窗口会把别处还在用的图删掉。
     */
    suspend fun pruneOrphanImages(): Int {
        val used = runCatching { imageIdsOf(sessionRepository.all()) }.getOrElse { return 0 }
        return runCatching { imageStore.pruneOrphans(used) }.getOrElse { 0 }
    }

    /**
     * 编辑用户消息正文并重发（`api.ts:1427 handleEditContent`）。
     *
     * 语义：更新目标 user 消息的 `content`，**删除该消息之后的所有消息**，
     * 然后追加一条新的 assistant 消息并触发生成。
     */
    suspend fun editAndResend(messageId: String, newContent: String) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.streaming || newContent.isBlank()) return
        val idx = session.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val editedMessages = session.messages.subList(0, idx).toMutableList()
        val edited = session.messages[idx].copy(content = newContent, timestamp = now())
        editedMessages.add(edited)
        val updated = session.copy(messages = editedMessages)
        val saved = sessionRepository.save(updated)
        publish(saved, null, null)
        // 以编辑后的正文触发新一轮发送（与 send 相同链路）
        send(newContent)
    }

    /**
     * 编辑用户消息正文、**不重发**（用户 2026-10-12 加的「保存」）。
     *
     * 与 [editAndResend] 的区别：只把正文写回并持久化，**不删后续消息、不触发生成** ——
     * 适合改错别字、补一句说明。与重发一样只对 user 消息开放（assistant 的正文不是「用户输入」）。
     */
    suspend fun editMessage(messageId: String, newContent: String) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.streaming) return
        val target = session.messages.firstOrNull { it.id == messageId } ?: return
        if (target.role != Role.USER) return
        val updated = session.copy(
            messages = session.messages.map { msg ->
                // timestamp 保持原值：这不是「又发了一次」，不要把它顶到时间线末尾。
                if (msg.id == messageId) msg.copy(content = newContent) else msg
            },
        )
        val saved = sessionRepository.save(updated)
        publish(saved, null, null)
    }

    /**
     * 滚动重答（`api.ts:1187 handleRollMessage`）。
     *
     * 在目标 assistant 消息上新增一个空 [MessageVersion]，将 `currentVersionIndex` 指向它，
     * 然后以**上一条 user 消息**的内容重新发送。
     */
    suspend fun regenerate(messageId: String) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.streaming) return
        val msgIdx = session.messages.indexOfFirst { it.id == messageId }
        if (msgIdx < 0) return
        val target = session.messages[msgIdx]
        if (target.role != Role.ASSISTANT) return

        // 新增一个空版本
        val newVersions = target.versions + MessageVersion(timestamp = now())
        val newIndex = newVersions.lastIndex
        val updatedMessages = session.messages.toMutableList()
        updatedMessages[msgIdx] = target.copy(
            versions = newVersions,
            currentVersionIndex = newIndex,
        )
        val prepared = session.copy(messages = updatedMessages)
        val saved = sessionRepository.save(prepared)
        publish(saved, null, null)

        // 取上一条 user 消息的正文作为重发内容
        val userMsg = session.messages.subList(0, msgIdx).lastOrNull { it.role == Role.USER }
        val query = userMsg?.content ?: ""

        // 走与 send 相似的生成路径，但把内容写到已有的 assistant 消息的新版本上
        regenerateInto(saved, messageId, query)
    }

    suspend fun send(rawText: String, imageIds: List<String> = emptyList()) {
        val text = rawText.trim()
        val initial = mutableState.value.session ?: return
        if ((text.isEmpty() && imageIds.isEmpty()) || mutableState.value.streaming) return

        val timestamp = now()
        val maxFloor = initial.messages.maxOfOrNull { it.floorNumber ?: 0 } ?: 0
        val userId = "msg-u-$timestamp"
        val assistantId = "msg-a-${timestamp + 1}"

        // 本轮消息归属哪个分支：由输入栏选中的 `activeTimelineId` 决定（`App.tsx:889-890`）。
        // 未开启记忆整理时也要打上标签 —— 屏幕与导出都靠它显示归属。
        val branches = initial.timelines.orEmpty().ifEmpty { listOf(createDefaultMainTimeline(timestamp)) }
        val targetId = initial.activeTimelineId?.ifEmpty { null } ?: branches.first().id
        val branch = branches.firstOrNull { it.id == targetId } ?: branches.first()
        val codeTag = branch.codeTag?.ifEmpty { null } ?: branch.tag.ifEmpty { null } ?: "通用"
        val descriptionTag = branch.descriptionTag?.ifEmpty { null }
            ?: branch.name.ifEmpty { null } ?: "通用世界观"
        val isCommon = targetId == DEFAULT_MAIN_TIMELINE_ID ||
            codeTag == COMMON_CODE_TAG ||
            initial.timelineMemoryEnabled != true

        val user = ChatMessage(
            id = userId,
            role = Role.USER,
            content = text,
            timestamp = timestamp,
            floorNumber = maxFloor + 1,
            imageIds = imageIds.ifEmpty { null },
            timelineId = targetId,
            timelineTag = codeTag,
            codeTag = codeTag,
            descriptionTag = descriptionTag,
            isCommon = isCommon,
        )
        val assistant = ChatMessage(
            id = assistantId,
            role = Role.ASSISTANT,
            timestamp = timestamp,
            floorNumber = maxFloor + 2,
            timelineId = targetId,
            timelineTag = codeTag,
            codeTag = codeTag,
            descriptionTag = descriptionTag,
            isCommon = isCommon,
            versions = listOf(
                MessageVersion(
                    content = "",
                    timestamp = timestamp,
                    model = initial.model,
                ),
            ),
        )
        val title = if (initial.title == "新对话" && initial.messages.none { it.role == Role.USER }) {
            if (text.isNotBlank()) text.take(20) + if (text.length > 20) "..." else ""
            else "图片消息"
        } else initial.title
        var working = sessionRepository.save(
            initial.copy(title = title, messages = initial.messages + user + assistant),
        )
        publish(working, assistantId, null)
        // 消息已经落到会话里了 → 清掉本窗口的输入草稿（否则重启后又回到输入框）。
        settingsRepository.clearInputDraft(working.id)
        if (mutableState.value.session?.id == working.id) {
            mutableState.value = mutableState.value.copy(draft = "")
        }

        try {
            val providers = configRepository.providers()
            val agents = configRepository.agents()
            val provider = providers.firstOrNull { it.id == working.providerId }
                ?: providers.firstOrNull()
                ?: error("没有可用的 Provider")
            val agent = agents.firstOrNull { it.id == working.agentId }
                ?: agents.firstOrNull()
                ?: error("没有可用的 Agent")
            val knowledge = configRepository.knowledgeBase()
            val worlds = configRepository.worldDocuments()
            val regex = configRepository.regexRules()
            val servers = configRepository.mcpServers()
            val skills = configRepository.skills()
            val preferences = settingsRepository.appPreferences()

            val parts = buildSystemInstruction(working, agent)
            val history = dropSummarizedFloors(working.messages.dropLast(1), working)
            val activeMcp = selectActiveMcpServers(servers, agent)
            val toolNames = getAvailableMcpToolNames(activeMcp)
            val activeSkills = selectActiveSkills(skills, agent, toolNames)
            val firstPass = ContextBuilder.build(
                ContextRequest(
                    agent = agent,
                    messages = history,
                    systemInstruction = parts.head,
                    aiContextVisibility = working.aiContextVisibility,
                    aiContextCustomLimit = working.aiContextCustomLimit ?: 3,
                    connectedKnowledge = selectConnectedKnowledge(knowledge, working),
                    activeWorldDocuments = selectActiveWorldDocuments(worlds, working),
                    activeSkills = activeSkills,
                    activeMcpServers = activeMcp,
                    regexRules = selectActiveRegexRules(regex, working),
                    extraSystemTail = parts.tail,
                    allTimelines = working.timelines.orEmpty(),
                    activeTimelineId = working.activeTimelineId,
                    timelineMemoryEnabled = working.timelineMemoryEnabled == true,
                    imageContextMode = working.imageContextMode ?: ImageContextMode.ONCE,
                ),
            )
            val searchContext = searchContext(working, firstPass.latestUserQuery)
            val context = if (searchContext.isEmpty()) firstPass else ContextBuilder.build(
                ContextRequest(
                    agent = agent,
                    messages = history,
                    systemInstruction = parts.head,
                    aiContextVisibility = working.aiContextVisibility,
                    aiContextCustomLimit = working.aiContextCustomLimit ?: 3,
                    connectedKnowledge = selectConnectedKnowledge(knowledge, working),
                    activeWorldDocuments = selectActiveWorldDocuments(worlds, working),
                    activeSkills = activeSkills,
                    activeMcpServers = activeMcp,
                    regexRules = selectActiveRegexRules(regex, working),
                    extraSystemTail = parts.tail,
                    allTimelines = working.timelines.orEmpty(),
                    activeTimelineId = working.activeTimelineId,
                    timelineMemoryEnabled = working.timelineMemoryEnabled == true,
                    imageContextMode = working.imageContextMode ?: ImageContextMode.ONCE,
                    webSearchContext = searchContext,
                ),
            )

            var visible = ""
            var reasoning = ""
            val params = ChatRequestAssembler.assemble(
                session = working,
                agent = agent,
                provider = provider,
                preferences = preferences,
                context = context,
                listeners = ToolChatListeners(
                    onChunk = { accumulated ->
                        visible = accumulated
                        working = updateAssistant(working, assistantId, visible, reasoning)
                        publish(working, assistantId, null)
                    },
                    onReasoningChunk = { accumulated ->
                        reasoning = accumulated
                        working = updateAssistant(working, assistantId, visible, reasoning)
                        publish(working, assistantId, null)
                    },
                ),
            )
            // T21：把 imageIds 解析成图片本体（TS 在进入 native 请求前做同一件事）。
            // 记录本轮请求耗时（对标 Web 版 `requestStartTime`）。
            val requestStart = now()
            val result = chatClient.runToolChat(ChatRequestAssembler.loadImages(params, imageStore), mcpClient)
            visible = result.text
            working = updateAssistant(working, assistantId, visible, reasoning)
            working = applyLatency(working, assistantId, now() - requestStart)
            working = sessionRepository.save(working)
            publish(working, null, null)
        } catch (cancelled: CancellationException) {
            // 「停止」的落库必须在 NonCancellable 里做：协程已被取消，任何挂起点（SQLite 写）
            // 都会立刻再抛一次 CancellationException，部分正文就存不下来了。
            // 这正是 T09 gotcha「取消路径里补一句提示，反而又抛一次」的同一条纪律。
            withContext(NonCancellable) {
                val partial = assistantText(working, assistantId)
                val stopped = if (partial.isBlank()) "[已停止生成]" else "$partial\n\n[已停止生成]"
                working = updateAssistant(working, assistantId, stopped, assistantReasoning(working, assistantId))
                working = sessionRepository.save(working)
                publish(working, null, null)
            }
            throw cancelled
        } catch (error: Throwable) {
            val current = assistantText(working, assistantId)
            val note = "[请求失败]: ${error.message ?: error.toString()}"
            working = updateAssistant(
                working,
                assistantId,
                if (current.isBlank()) note else "$current\n\n$note",
                assistantReasoning(working, assistantId),
            )
            working = sessionRepository.save(working)
            publish(working, null, error.message ?: error.toString())
        } finally {
            generationJob = null
        }
    }

    // ------------------------------------------------------------------ T20 前文总结

    /** 保存总结配置（提示词 / 模型）：`App.tsx:476 handleSaveSummaryConfig`。 */
    suspend fun saveSummaryConfig(prompt: String?, model: String?) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(
            session.copy(summaryPrompt = prompt, summaryModel = model),
        )
        mutableState.value = mutableState.value.copy(session = saved)
        onSessionChanged(saved)
    }

    /** 删除单条总结（`App.tsx:480 handleDeleteSummary`）。 */
    suspend fun deleteSummary(id: String) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(
            session.copy(summaries = session.summaries.orEmpty().filterNot { it.id == id }),
        )
        mutableState.value = mutableState.value.copy(session = saved)
        onSessionChanged(saved)
    }

    /** 清空全部总结（`App.tsx:487 handleClearSummaries`）。⚠️ 不动 `lastSummarizedFloor`。 */
    suspend fun clearSummaries() {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(summaries = emptyList()))
        mutableState.value = mutableState.value.copy(session = saved)
        onSessionChanged(saved)
    }

    /**
     * 生成前文总结（`App.tsx:496 handleGenerateSummary`）。
     *
     * - 只处理 `lastSummarizedFloor` 之后的楼层；没有新内容直接返回；
     * - 失败写进 [ChatUiState.summaryError]（对应 Web 版的 `summaryError`），**不落消息**；
     * - 成功后把 `lastSummarizedFloor` 推进到最大楼号 —— `dropSummarizedFloors` 据此省钱。
     */
    suspend fun generateSummary(prompt: String?, model: String?) {
        val session = mutableState.value.session ?: return
        if (mutableState.value.summarizing) return
        val range = summaryRange(session)
        if (range.fromFloor > range.maxFloor) return

        val transcript = buildSummaryTranscript(unsummarizedMessages(session))
        mutableState.value = mutableState.value.copy(summarizing = true, summaryError = null)
        try {
            val provider = resolveProvider(session)
            val usedModel = resolveSummaryModel(model, session, settingsRepository.appPreferences(), provider)
            val text = oneShotText(
                provider = provider,
                model = usedModel,
                system = SUMMARY_SYSTEM_INSTRUCTION,
                user = buildSummaryRequest(
                    prompt?.takeIf { it.isNotBlank() } ?: DEFAULT_SUMMARY_PROMPT,
                    transcript,
                ),
                temperature = 0.3,
            ).trim()
            if (text.isEmpty()) {
                mutableState.value = mutableState.value.copy(
                    summarizing = false,
                    summaryError = "模型返回了空内容，请检查模型配置后重试。",
                )
                return
            }
            val saved = sessionRepository.save(
                session.copy(
                    summaries = session.summaries.orEmpty() + SessionSummary(
                        id = "summary-${now()}",
                        content = text,
                        fromFloor = range.fromFloor,
                        toFloor = range.maxFloor,
                        model = usedModel.takeIf { it.isNotBlank() },
                        createdAt = now(),
                    ),
                    lastSummarizedFloor = range.maxFloor,
                ),
            )
            mutableState.value = mutableState.value.copy(session = saved, summarizing = false, summaryError = null)
            onSessionChanged(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(
                summarizing = false,
                summaryError = error.message ?: error.toString(),
            )
        }
    }

    // ------------------------------------------------------------------ T20 角色关系分析

    /**
     * 触发心理动力学角色关系分析（`App.tsx:1924 handleTriggerRoleAnalysis`）。
     *
     * 先插入一条 `isAnalysis = true` 的占位 assistant 消息，拿到报告后回填正文；
     * 失败写 [buildRoleAnalysisFallback]（与 Web 版 `analyzeRoleRelationships` 内部 catch 一致）。
     */
    suspend fun analyzeRoleRelationships() {
        val session = mutableState.value.session ?: return
        if (mutableState.value.streaming || mutableState.value.analyzing) return

        val branch = currentBranchInfo(session)
        val started = now()
        val id = "analysis-$started"
        val maxFloor = session.messages.maxOfOrNull { it.floorNumber ?: 0 } ?: 0
        // 上下文在**插入占位消息之前**快照（对齐 App.tsx:1938）：占位正文不该进入分析上下文。
        // 并且「仅自己可见」的历史分析报告不参与本轮分析（T20）。
        val contextText = buildRoleAnalysisContext(dropHiddenAnalysisMessages(session.messages))
            .ifEmpty { "暂无详细历史记录。" }
        val placeholder = ChatMessage(
            id = id,
            role = Role.ASSISTANT,
            content = ROLE_ANALYSIS_PLACEHOLDER,
            timestamp = started,
            floorNumber = maxFloor + 1,
            timelineId = session.activeTimelineId,
            codeTag = ROLE_ANALYSIS_CODE_TAG,
            descriptionTag = ROLE_ANALYSIS_DESCRIPTION_TAG,
            isAnalysis = true,
            analysisVisibility = AnalysisVisibility.VISIBLE,
            analysisBranchCode = branch.code,
            versions = listOf(MessageVersion(content = ROLE_ANALYSIS_PLACEHOLDER, timestamp = started, model = session.model)),
            currentVersionIndex = 0,
        )
        var working = sessionRepository.save(session.copy(messages = session.messages + placeholder))
        mutableState.value = mutableState.value.copy(analyzing = true)
        publish(working, id, null)

        val report = try {
            val provider = resolveProvider(working)
            val usedModel = resolveAnalysisModel(working, settingsRepository.appPreferences(), provider)
            oneShotText(
                provider = provider,
                model = usedModel,
                system = ROLE_ANALYSIS_SYSTEM_INSTRUCTION,
                user = buildRoleAnalysisUserPrompt(branch, contextText),
                temperature = 0.5,
            ).trim().ifEmpty { buildRoleAnalysisFallback(branch) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            buildRoleAnalysisFallback(branch)
        }

        working = working.copy(
            messages = working.messages.map { message ->
                if (message.id != id) message
                else message.copy(
                    content = report,
                    versions = listOf(
                        MessageVersion(
                            content = report,
                            timestamp = now(),
                            model = working.model,
                            latencyMs = now() - started,
                        ),
                    ),
                )
            },
        )
        working = sessionRepository.save(working)
        mutableState.value = mutableState.value.copy(analyzing = false)
        publish(working, null, null)
    }

    /** 切换分析报告的可见性（`App.tsx:1883 handleToggleAnalysisVisibility`）。 */
    suspend fun toggleAnalysisVisibility(messageId: String) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(
            session.copy(
                messages = session.messages.map { message ->
                    if (message.id != messageId || message.isAnalysis != true) message
                    else message.copy(
                        analysisVisibility = if (
                            (message.analysisVisibility ?: AnalysisVisibility.VISIBLE) == AnalysisVisibility.HIDDEN
                        ) AnalysisVisibility.VISIBLE else AnalysisVisibility.HIDDEN,
                    )
                },
            ),
        )
        mutableState.value = mutableState.value.copy(session = saved)
        onSessionChanged(saved)
    }

    /** 把分析报告归档为世界观文档（`App.tsx:1899 handleSaveAnalysisToDoc`）。 */
    suspend fun archiveAnalysisToDocument(messageId: String) {
        val session = mutableState.value.session ?: return
        val message = session.messages.firstOrNull { it.id == messageId } ?: return
        val content = assistantText(session, messageId).ifEmpty { message.content }
        val doc = WorldDocument(
            id = "doc-analysis-${now()}",
            title = buildAnalysisDocTitle(message.floorNumber, message.codeTag),
            groupId = ROLE_ANALYSIS_GROUP_ID,
            content = content,
            createdAt = now(),
            updatedAt = now(),
            tags = listOf("角色分析", message.codeTag ?: ROLE_ANALYSIS_CODE_TAG),
        )
        configRepository.transaction {
            configRepository.setWorldDocuments(listOf(doc) + configRepository.worldDocuments())
        }
    }

    // ------------------------------------------------------------------ T24 时间线管理

    /**
     * 总开关。**真实持久化**，并且立即决定输入栏第一行是否出现
     * （`ChatInputBar` 读的就是 `session.timelineMemoryEnabled`）。
     */
    suspend fun setTimelineMemoryEnabled(enabled: Boolean) {
        // 与领域层同名 → 写全限定名，避免被本方法遮蔽。
        mutateSession { com.watchworld.domain.setTimelineMemoryEnabled(it, enabled, now()) }
    }

    /** 切换当前聚焦分支（`TimelineModal.tsx:102`）。 */
    suspend fun setActiveTimeline(timelineId: String) {
        mutateSession { setActiveTimelineBranch(it, timelineId, now()) }
    }

    /** 切换输入栏标签展示模式（参考版 `App.tsx:2219` 的 `onToggleTagDisplayMode`）。 */
    suspend fun toggleTagDisplayMode() {
        mutateSession {
            it.copy(
                tagDisplayMode = if (it.tagDisplayMode == TagDisplayMode.CODE) TagDisplayMode.DESC
                else TagDisplayMode.CODE,
            )
        }
    }

    /** 单个分支的 AI 可见性（`TimelineModal.tsx:110`）。只影响发给 AI 的上下文。 */
    suspend fun setTimelineVisible(timelineId: String, visible: Boolean) {
        mutateSession { setTimelineBranchVisibility(it, timelineId, visible, now()) }
    }

    /** 全选 / 全不选（`TimelineModal.tsx:126`）。**不改** 当前聚焦分支。 */
    suspend fun setAllTimelinesVisible(visible: Boolean) {
        mutateSession { setAllTimelineBranchesVisibility(it, visible, now()) }
    }

    /**
     * 新建或更新一个分支（`TimelineModal.tsx:175 handleSaveTimeline`）。
     *
     * ⚠️ 清单里把新建与更新列成两个方法，这里合并成一个 —— TS 的 `handleSaveTimeline` 本身就
     *    按 `editingTimelineId` 内部分支，而编辑弹窗只有一个「保存」按钮，合成一个入口才能让
     *    「保存失败保留用户输入」有唯一的成功/失败返回点。
     *
     * @return `false` = 名字为空**或落库失败**；调用方据此保留编辑内容并显示错误。
     */
    suspend fun saveTimelineDraft(draft: TimelineDraft): Boolean {
        var session = mutableState.value.session ?: return false
        // If session has no timelines yet, materialize the default main timeline first
        // so the new branch doesn't replace it (App.kt only adds it in-memory for display).
        if (session.timelines.orEmpty().isEmpty()) {
            session = session.copy(
                timelines = listOf(createDefaultMainTimeline(now())),
            )
        }
        val updated = applyTimelineDraft(session, draft, now())
        if (updated === session) return false
        val saved = runCatching { sessionRepository.save(updated) }.getOrNull() ?: return false
        publish(saved, null, null)
        return true
    }

    /** 删除分支；只剩一个分支时**不做任何事**（领域层守卫）。 */
    suspend fun deleteTimeline(timelineId: String) {
        mutateSession { deleteTimelineBranch(it, timelineId, now()) }
    }

    /** 批准一条描述标签申请（`App.tsx:1384`）。拒绝写入时整体不动。 */
    suspend fun approveTimelineDescriptionTagRequest(requestId: String) {
        val session = mutableState.value.session ?: return
        // 与领域层同名 → 必须写全限定名，否则会被本方法自己遮蔽成无限递归。
        val result = com.watchworld.domain.approveTimelineDescriptionTagRequest(
            requests = session.timelineDescriptionTagRequests.orEmpty(),
            timelines = session.timelines.orEmpty(),
            messages = session.messages,
            requestId = requestId,
            timelineMemoryEnabled = session.timelineMemoryEnabled == true,
            now = now(),
        )
        if (!result.resolved) return
        val saved = sessionRepository.save(
            session.copy(
                timelineDescriptionTagRequests = result.requests,
                timelines = result.timelines,
                messages = result.messages,
            ),
        )
        publish(saved, null, null)
    }

    /** 拒绝一条描述标签申请（`App.tsx:1404`）。⚠️ 与批准不同，拒绝**不看**总开关。 */
    suspend fun rejectTimelineDescriptionTagRequest(requestId: String) {
        val session = mutableState.value.session ?: return
        val result = com.watchworld.domain.rejectTimelineDescriptionTagRequest(
            requests = session.timelineDescriptionTagRequests.orEmpty(),
            timelines = session.timelines.orEmpty(),
            messages = session.messages,
            requestId = requestId,
            now = now(),
        )
        if (!result.resolved) return
        val saved = sessionRepository.save(
            session.copy(timelineDescriptionTagRequests = result.requests),
        )
        publish(saved, null, null)
    }

    /**
     * 会话级写操作的统一出口：`读当前会话 → 纯变换 → 落库 → 回流`。
     *
     * ⚠️ 领域变换在「无事可做」时返回**同一个实例**（如只剩一个分支还要删），
     *    这里据此跳过写库 —— 否则会白白盖一次 `updatedAt`，把同步引擎（T19）骗成「有新改动」。
     */
    private suspend fun mutateSession(transform: (ChatSession) -> ChatSession) {
        val session = mutableState.value.session ?: return
        val updated = transform(session)
        if (updated === session) return
        val saved = sessionRepository.save(updated)
        publish(saved, null, null)
    }

    /** 当前 Provider（`session.providerId` 优先，否则第一个）。没有则抛（与 [send] 一致）。 */
    private suspend fun resolveProvider(session: ChatSession): ApiProviderConfig {
        val providers = configRepository.providers()
        return providers.firstOrNull { it.id == session.providerId }
            ?: providers.firstOrNull()
            ?: error("没有可用的 Provider")
    }

    /**
     * 一次性非流式对话（无工具）。总结与分析共用。
     *
     * 复用 T09 的 [runToolChat]：把工具/技能留空、`stream = false`，就退化成一次普通请求，
     * 且三家 Provider 的 wire 形状由工具循环统一处理，不必重写一遍。
     *
     * ⚠️ [runToolChat] 对失败/空回复是**写 note 正文**而不是抛异常。一次性调用若把这句
     * note 当正文，总结会存成「[模型未返回最终正文]」、分析会把它当报告。因此这里把 known
     * 失败 note 与空串**转成异常**抛给调用方 —— 正好对齐 Web 版 `sendChatMessage` 抛错的语义。
     *
     * @throws IllegalStateException 上游失败、空回复或无完成标记。
     */
    private suspend fun oneShotText(
        provider: ApiProviderConfig?,
        model: String,
        system: String,
        user: String,
        temperature: Double,
    ): String {
        val resolved = provider ?: error("没有可用的 Provider")
        val params = ToolChatParams(
            provider = resolved,
            model = model.takeIf { it.isNotBlank() } ?: resolved.defaultModel.ifBlank { "gemini-2.0-flash" },
            messages = listOf(ToolChatMessage(role = "user", content = user)),
            systemInstruction = system,
            temperature = temperature,
            stream = false,
        )
        val text = chatClient.runToolChat(params, mcpClient).text
        if (isOneShotFailure(text)) throw IllegalStateException(text.ifBlank { "模型未返回文本内容" })
        return text
    }

    /** 判断一次性调用的返回值是不是工具循环写的失败 note（或空串）。 */
    private fun isOneShotFailure(text: String): Boolean =
        text.isBlank() ||
            text.startsWith("[模型未返回最终正文]") ||
            text.startsWith("[响应意外中断") ||
            text.startsWith("[模型停止：") ||
            text.startsWith("[请求失败：")

    private suspend fun searchContext(session: ChatSession, query: String): String {
        if (session.webSearchEnabled != true || query.isBlank() || !shouldUseWebSearch(query)) return ""
        return try {
            if (isSkillSearchIntent(query)) {
                val result = searchClient.searchAgentSkills(query, pageSize = 8)
                formatSkillDirectoryContext(query, result).ifEmpty {
                    "# 【Agent Skill 目录搜索结果】\n目录中没有匹配的 Skill。不得捏造结果，请如实告知用户未检索到相关 Skill。"
                }
            } else {
                formatSearchContext(query, searchClient.searchWeb(query, 6), false)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            "# 【联网搜索状态】\n本轮搜索失败：${error.message ?: error.toString()}\n不得假装已经取得联网资料；请明确告知用户搜索失败。"
        }
    }

    private fun updateAssistant(
        session: ChatSession,
        id: String,
        content: String,
        reasoning: String,
    ): ChatSession = session.copy(
        messages = session.messages.map { message ->
            if (message.id != id) message else {
                val versions = message.versions.ifEmpty { listOf(MessageVersion(timestamp = now())) }.toMutableList()
                val index = message.currentVersionIndex.coerceIn(0, versions.lastIndex)
                versions[index] = versions[index].copy(
                    content = content,
                    reasoning = reasoning.takeIf { it.isNotEmpty() },
                )
                message.copy(versions = versions)
            }
        },
    )

    private fun assistantText(session: ChatSession, id: String): String {
        val message = session.messages.firstOrNull { it.id == id } ?: return ""
        return message.versions.getOrNull(message.currentVersionIndex)?.content
            ?: message.versions.firstOrNull()?.content
            ?: message.content
    }

    private fun assistantReasoning(session: ChatSession, id: String): String {
        val message = session.messages.firstOrNull { it.id == id } ?: return ""
        return message.versions.getOrNull(message.currentVersionIndex)?.reasoning
            ?: message.versions.firstOrNull()?.reasoning
            ?: ""
    }

    /**
     * 把本轮请求耗时写进该 assistant 消息的**当前版本**，并镜像顶层 `latencyMs`
     * （对标 Web 版在生成完成时同时写到 version 与 message）。
     * 没写这一步时气泡的「耗时」永远不显示（`latencyMs` 恒为 null）。
     */
    private fun applyLatency(session: ChatSession, id: String, latencyMs: Long): ChatSession =
        session.copy(
            messages = session.messages.map { message ->
                if (message.id != id || message.versions.isEmpty()) message
                else {
                    val versions = message.versions.toMutableList()
                    val index = message.currentVersionIndex.coerceIn(0, versions.lastIndex)
                    versions[index] = versions[index].copy(latencyMs = latencyMs)
                    message.copy(versions = versions, latencyMs = latencyMs)
                }
            },
        )

    /**
     * 滚动重答的生成路径：把内容写到 [assistantId] 消息的当前版本上。
     * 与 [send] 的后半段（装配→工具循环→落库）结构一致，但不新建消息。
     */
    private suspend fun regenerateInto(
        initial: ChatSession, assistantId: String, query: String,
    ) {
        var working = initial
        publish(working, assistantId, null)
        try {
            val providers = configRepository.providers()
            val agents = configRepository.agents()
            val provider = providers.firstOrNull { it.id == working.providerId }
                ?: providers.firstOrNull()
                ?: error("没有可用的 Provider")
            val agent = agents.firstOrNull { it.id == working.agentId }
                ?: agents.firstOrNull()
                ?: error("没有可用的 Agent")
            val knowledge = configRepository.knowledgeBase()
            val worlds = configRepository.worldDocuments()
            val regex = configRepository.regexRules()
            val servers = configRepository.mcpServers()
            val skills = configRepository.skills()
            val preferences = settingsRepository.appPreferences()
            val parts = buildSystemInstruction(working, agent)
            val history = dropSummarizedFloors(working.messages.dropLast(1), working)
            val activeMcp = selectActiveMcpServers(servers, agent)
            val toolNames = getAvailableMcpToolNames(activeMcp)
            val activeSkills = selectActiveSkills(skills, agent, toolNames)
            val firstPass = ContextBuilder.build(
                ContextRequest(
                    agent = agent, messages = history, systemInstruction = parts.head,
                    aiContextVisibility = working.aiContextVisibility,
                    aiContextCustomLimit = working.aiContextCustomLimit ?: 3,
                    connectedKnowledge = selectConnectedKnowledge(knowledge, working),
                    activeWorldDocuments = selectActiveWorldDocuments(worlds, working),
                    activeSkills = activeSkills, activeMcpServers = activeMcp,
                    regexRules = selectActiveRegexRules(regex, working),
                    extraSystemTail = parts.tail,
                    allTimelines = working.timelines.orEmpty(),
                    activeTimelineId = working.activeTimelineId,
                    timelineMemoryEnabled = working.timelineMemoryEnabled == true,
                    imageContextMode = working.imageContextMode ?: ImageContextMode.ONCE,
                ),
            )
            val searchCtx = searchContext(working, firstPass.latestUserQuery)
            val context = if (searchCtx.isEmpty()) firstPass else ContextBuilder.build(
                ContextRequest(
                    agent = agent, messages = history, systemInstruction = parts.head,
                    aiContextVisibility = working.aiContextVisibility,
                    aiContextCustomLimit = working.aiContextCustomLimit ?: 3,
                    connectedKnowledge = selectConnectedKnowledge(knowledge, working),
                    activeWorldDocuments = selectActiveWorldDocuments(worlds, working),
                    activeSkills = activeSkills, activeMcpServers = activeMcp,
                    regexRules = selectActiveRegexRules(regex, working),
                    extraSystemTail = parts.tail,
                    allTimelines = working.timelines.orEmpty(),
                    activeTimelineId = working.activeTimelineId,
                    timelineMemoryEnabled = working.timelineMemoryEnabled == true,
                    imageContextMode = working.imageContextMode ?: ImageContextMode.ONCE,
                    webSearchContext = searchCtx,
                ),
            )
            var visible = ""
            var reasoning = ""
            val params = ChatRequestAssembler.assemble(
                session = working, agent = agent, provider = provider,
                preferences = preferences, context = context,
                listeners = ToolChatListeners(
                    onChunk = { accumulated ->
                        visible = accumulated
                        working = updateAssistant(working, assistantId, visible, reasoning)
                        publish(working, assistantId, null)
                    },
                    onReasoningChunk = { accumulated ->
                        reasoning = accumulated
                        working = updateAssistant(working, assistantId, visible, reasoning)
                        publish(working, assistantId, null)
                    },
                ),
            )
            // T21：把 imageIds 解析成图片本体（TS 在进入 native 请求前做同一件事）。
            // 记录本轮请求耗时（对标 Web 版 `requestStartTime`）。
            val requestStart = now()
            val result = chatClient.runToolChat(ChatRequestAssembler.loadImages(params, imageStore), mcpClient)
            visible = result.text
            working = updateAssistant(working, assistantId, visible, reasoning)
            working = applyLatency(working, assistantId, now() - requestStart)
            working = sessionRepository.save(working)
            publish(working, null, null)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val partial = assistantText(working, assistantId)
                val stopped = if (partial.isBlank()) "[已停止生成]" else "$partial\n\n[已停止生成]"
                working = updateAssistant(working, assistantId, stopped, assistantReasoning(working, assistantId))
                working = sessionRepository.save(working)
                publish(working, null, null)
            }
            throw cancelled
        } catch (error: Throwable) {
            val current = assistantText(working, assistantId)
            val note = "[请求失败]: ${error.message ?: error.toString()}"
            working = updateAssistant(working, assistantId,
                if (current.isBlank()) note else "$current\n\n$note",
                assistantReasoning(working, assistantId))
            working = sessionRepository.save(working)
            publish(working, null, error.message ?: error.toString())
        } finally {
            generationJob = null
        }
    }

    // ────────────────────────────────── AI 可见性 & 渲染限制 & 提示词覆盖

    suspend fun setAiContextMode(mode: com.watchworld.model.AiContextVisibilityFilter) {
        val session = mutableState.value.session ?: return
        val limit = if (mode == com.watchworld.model.AiContextVisibilityFilter.LATEST_CUSTOM
            && session.aiContextCustomLimit == null) 3 else session.aiContextCustomLimit
        val saved = sessionRepository.save(session.copy(aiContextVisibility = mode, aiContextCustomLimit = limit))
        publish(saved, null, null)
    }

    suspend fun setAiContextCustomLimit(limit: Int) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(aiContextCustomLimit = limit.coerceIn(1, 999)))
        publish(saved, null, null)
    }

    suspend fun setUiRenderLimit(limit: Int) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(uiRenderLimit = limit.coerceAtLeast(0)))
        publish(saved, null, null)
    }

    /** 设置本窗口的 AI 头像文件名（`null` = 撤掉窗口头像、回落到 Agent 默认）。 */
    suspend fun setSessionAiAvatar(fileName: String?) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(aiAvatarFileName = fileName))
        publish(saved, null, null)
    }

    /** 设置本窗口的用户头像文件名（`null` = 撤掉窗口头像、回落到全局用户头像）。 */
    suspend fun setSessionUserAvatar(fileName: String?) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(userAvatarFileName = fileName))
        publish(saved, null, null)
    }

    suspend fun applySystemPromptOverride(content: String) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(systemPromptOverride = content.ifBlank { null }))
        publish(saved, null, null)
    }

    /** Select the Agent for the current chat window. */
    suspend fun selectAgent(agentId: String) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(agentId = agentId))
        publish(saved, null, null)
    }

    /** Save dual-box prompt. Matches App.tsx `handleSaveDualBoxPrompt`. */
    suspend fun saveDualBoxPrompt(fixedPrompt: String, commonPrompt: String, isCommonPushed: Boolean) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(
            systemPromptFixed = fixedPrompt,
            systemPromptInjectedCommon = commonPrompt.ifBlank { null },
            systemPromptOverride = fixedPrompt.ifBlank { null },
            isCommonPushed = isCommonPushed,
        ))
        publish(saved, null, null)
    }

    /** Push common messages into injected common. Matches App.tsx `handlePushCommonMessages`. */
    suspend fun pushCommonMessages() {
        val session = mutableState.value.session ?: return
        val common = session.messages.filter { m ->
            m.isCommon == true || m.timelineId == "timeline-main" || m.codeTag == "通用"
        }
        val generated = common.joinToString("\n\n") { m ->
            val floor = m.floorNumber?.let { "[来自 #$it]" }.orEmpty()
            val label = if (m.role == Role.USER) "用户设定" else "基础世界观"
            val text = if (m.role == Role.ASSISTANT) {
                m.versions.getOrNull(m.currentVersionIndex)?.content
                    ?: m.versions.firstOrNull()?.content ?: m.content
            } else m.content
            "$floor $label：${text.trim()}"
        }
        val saved = sessionRepository.save(session.copy(
            systemPromptInjectedCommon = generated,
            isCommonPushed = true,
        ))
        publish(saved, null, null)
    }

    /** Revert common messages. Matches App.tsx `handleRevertCommonMessages`. */
    suspend fun revertCommonMessages() {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(
            systemPromptInjectedCommon = null,
            isCommonPushed = false,
        ))
        publish(saved, null, null)
    }

    // ────────────────────────────────── 窗口 API 参数

    /** 一次性保存窗口级 API 采样参数覆盖（对标 Web `WindowApiParamsModal` 的 onSave）。 */
    suspend fun saveWindowApiParams(
        temperature: Double?, topP: Double?, topK: Int?,
        frequencyPenalty: Double?, presencePenalty: Double?,
        maxTokens: Int?, toolCallStepLimit: Int?,
        imageContextMode: com.watchworld.model.ImageContextMode?,
        streamingEnabled: Boolean?,
    ) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(
            temperature = temperature, topP = topP, topK = topK,
            frequencyPenalty = frequencyPenalty, presencePenalty = presencePenalty,
            maxTokens = maxTokens, toolCallStepLimit = toolCallStepLimit,
            imageContextMode = imageContextMode, streamingEnabled = streamingEnabled,
        ))
        publish(saved, null, null)
    }

    // ────────────────────────────────── 本窗口世界观

    /**
     * 切换单个世界观文档在当前窗口的注入状态。
     *
     * ⚠️ `connectedWorldDocIds == null`（没保存过窗口选择）时，基准**不是空列表**，而是
     * [defaultIds] —— 与 `selectSessionWorldDocuments`（`WorldLibrary.kt:118`）「缺省 = 全部
     * `enabled` 文档」的语义、以及参考版 `App.tsx:1611-1613` 的 `base` 一致。
     * 旧实现从空列表起步，会让「界面上没勾」与「AI 实际收到全部」对不上。
     */
    suspend fun toggleWindowWorldDoc(docId: String, defaultIds: List<String>) {
        val session = mutableState.value.session ?: return
        val current = session.connectedWorldDocIds ?: defaultIds
        val updated = if (docId in current) current - docId else current + docId
        val saved = sessionRepository.save(session.copy(connectedWorldDocIds = updated))
        publish(saved, null, null)
    }

    /** 批量设置当前窗口启用的世界观文档 ID。 */
    suspend fun setWindowWorldDocs(ids: List<String>) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(connectedWorldDocIds = ids))
        publish(saved, null, null)
    }

    // ────────────────────────────────── 本窗口知识库

    /**
     * 切换一条知识库资料在当前窗口的「连接」状态（参考版 `onToggleConnectToSession`）。
     *
     * ⚠️ 只改本窗口的 `connectedKnowledgeIds`，**不动**资料的全局 `enabled`：
     *    一条资料要真正参与注入，得「全局启用 ∩ 本窗口连接」同时成立
     *    （见 `domain/selectConnectedKnowledge`，侧边栏知识库徽章也走这个口径）。
     */
    suspend fun toggleKnowledgeConnected(itemId: String) {
        val session = mutableState.value.session ?: return
        val current = session.connectedKnowledgeIds
        val updated = if (itemId in current) current - itemId else current + itemId
        val saved = sessionRepository.save(session.copy(connectedKnowledgeIds = updated))
        publish(saved, null, null)
    }

    // ────────────────────────────────── 本窗口正则

    /**
     * 切换单条正则规则在当前窗口的启用状态。
     *
     * 当 `regexRuleIds == null`（未配置窗口级规则）时，以 [allEnabledIds] 作为基准初始化，
     * 然后再对目标规则做 add/remove。
     */
    suspend fun toggleWindowRegexRule(ruleId: String, allEnabledIds: List<String>) {
        val session = mutableState.value.session ?: return
        val current = session.regexRuleIds ?: allEnabledIds
        val updated = if (ruleId in current) current - ruleId else current + ruleId
        val saved = sessionRepository.save(session.copy(regexRuleIds = updated))
        publish(saved, null, null)
    }

    /** 批量设置当前窗口启用的正则规则 ID。 */
    suspend fun setWindowRegexRules(ids: List<String>) {
        val session = mutableState.value.session ?: return
        val saved = sessionRepository.save(session.copy(regexRuleIds = ids))
        publish(saved, null, null)
    }

    /**
     * 会话回流到 UI。用 `copy` 而不是新建 [ChatUiState]，保留 [ChatUiState.summarizing] /
     * [ChatUiState.summaryError] / [ChatUiState.analyzing] 这些与单次消息流无关的临时标记
     * （否则一次流式回调就会把它们清掉）。
     */
    private fun publish(session: ChatSession, streamingId: String?, error: String?) {
        mutableState.value = mutableState.value.copy(
            session = session,
            streamingMessageId = streamingId,
            error = error,
        )
        onSessionChanged(session)
    }
}
