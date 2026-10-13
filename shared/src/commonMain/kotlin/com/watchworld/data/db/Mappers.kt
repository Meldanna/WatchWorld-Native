package com.watchworld.data.db

import com.watchworld.model.Agent
import com.watchworld.model.AgentSkill
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ChatGroup
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.CustomTheme
import com.watchworld.model.KnowledgeItem
import com.watchworld.model.McpServerConfig
import com.watchworld.model.ModelFavorite
import com.watchworld.model.PromptPreset
import com.watchworld.model.RegexRule
import com.watchworld.model.SessionSummary
import com.watchworld.model.TimelineBranch
import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup

/*
 * 实体 ↔ 模型 的映射，集中放一处。
 *
 * ### 为什么映射不写在实体里
 *
 * 实体是**存储形状**、模型是**存档形状**，两者必须能独立演进：将来某次迁移改了列，
 * 只该动本文件，不该动 `model/` 那一层。把 `toModel()` 塞进实体会让两件事纠缠在一起。
 *
 * ### 关于 `sortIndex`
 *
 * 入参 `index` 一律由调用方按**数组下标**给（见 `ConfigEntities.kt` 的说明）。
 * 保持「模型里的顺序 == 库里的顺序」是往返测试能过的前提。
 */

// ---------------- 配置类 ----------------

internal fun ProviderEntity.toModel() = ApiProviderConfig(
    id = id,
    name = name,
    type = type,
    baseUrl = baseUrl,
    apiKey = apiKey,
    defaultModel = defaultModel,
    models = modelsJson,
    isCustom = isCustom,
    enabled = enabled,
    isSystemDefault = isSystemDefault,
    customHeaders = customHeadersJson,
    defaultTemperature = defaultTemperature,
    defaultTopP = defaultTopP,
    defaultTopK = defaultTopK,
    defaultFrequencyPenalty = defaultFrequencyPenalty,
    defaultPresencePenalty = defaultPresencePenalty,
    defaultMaxTokens = defaultMaxTokens,
)

internal fun ApiProviderConfig.toEntity(index: Int) = ProviderEntity(
    id = id,
    sortIndex = index,
    name = name,
    type = type,
    baseUrl = baseUrl,
    apiKey = apiKey,
    defaultModel = defaultModel,
    modelsJson = models,
    isCustom = isCustom,
    enabled = enabled,
    isSystemDefault = isSystemDefault,
    customHeadersJson = customHeaders,
    defaultTemperature = defaultTemperature,
    defaultTopP = defaultTopP,
    defaultTopK = defaultTopK,
    defaultFrequencyPenalty = defaultFrequencyPenalty,
    defaultPresencePenalty = defaultPresencePenalty,
    defaultMaxTokens = defaultMaxTokens,
)

internal fun AgentEntity.toModel() = Agent(
    id = id,
    name = name,
    avatar = avatar,
    description = description,
    systemPrompt = systemPrompt,
    temperature = temperature,
    topP = topP,
    contextMessageLimit = contextMessageLimit,
    isDefault = isDefault,
    tags = tagsJson,
    isBuiltin = isBuiltin,
    model = model,
    memory = memory,
    mcpServerIds = mcpServerIdsJson,
    skillIds = skillIdsJson,
    fileAccess = fileAccess,
)

internal fun Agent.toEntity(index: Int) = AgentEntity(
    id = id,
    sortIndex = index,
    name = name,
    avatar = avatar,
    description = description,
    systemPrompt = systemPrompt,
    temperature = temperature,
    topP = topP,
    contextMessageLimit = contextMessageLimit,
    isDefault = isDefault,
    tagsJson = tags,
    isBuiltin = isBuiltin,
    model = model,
    memory = memory,
    mcpServerIdsJson = mcpServerIds,
    skillIdsJson = skillIds,
    fileAccess = fileAccess,
)

internal fun PromptEntity.toModel() = PromptPreset(
    id = id,
    title = title,
    category = category,
    content = content,
    description = description,
    tags = tagsJson,
)

internal fun PromptPreset.toEntity(index: Int) = PromptEntity(
    id = id,
    sortIndex = index,
    title = title,
    category = category,
    content = content,
    description = description,
    tagsJson = tags,
)

internal fun RegexRuleEntity.toModel() = RegexRule(
    id = id,
    name = name,
    pattern = pattern,
    flags = flags,
    replacement = replacement,
    scope = scope,
    enabled = enabled,
    description = description,
    group = group,
)

internal fun RegexRule.toEntity(index: Int) = RegexRuleEntity(
    id = id,
    sortIndex = index,
    name = name,
    pattern = pattern,
    flags = flags,
    replacement = replacement,
    scope = scope,
    enabled = enabled,
    description = description,
    group = group,
)

internal fun KnowledgeEntity.toModel() = KnowledgeItem(
    id = id,
    title = title,
    content = content,
    tags = tagsJson,
    enabled = enabled,
    updatedAt = updatedAt,
)

internal fun KnowledgeItem.toEntity(index: Int) = KnowledgeEntity(
    id = id,
    sortIndex = index,
    title = title,
    content = content,
    tagsJson = tags,
    enabled = enabled,
    updatedAt = updatedAt,
)

internal fun McpServerEntity.toModel() = McpServerConfig(
    id = id,
    name = name,
    type = type,
    url = url,
    authToken = authToken,
    headers = headersJson,
    status = status,
    tools = toolsJson,
    description = description,
    lastTestedAt = lastTestedAt,
    lastLatencyMs = lastLatencyMs,
    lastError = lastError,
    serverName = serverName,
    serverVersion = serverVersion,
)

internal fun McpServerConfig.toEntity(index: Int) = McpServerEntity(
    id = id,
    sortIndex = index,
    name = name,
    type = type,
    url = url,
    authToken = authToken,
    headersJson = headers,
    status = status,
    toolsJson = tools,
    description = description,
    lastTestedAt = lastTestedAt,
    lastLatencyMs = lastLatencyMs,
    lastError = lastError,
    serverName = serverName,
    serverVersion = serverVersion,
)

internal fun SkillEntity.toModel() = AgentSkill(
    id = id,
    name = name,
    icon = icon,
    description = description,
    triggerKeywords = triggerKeywordsJson,
    systemInstructionInjection = systemInstructionInjection,
    systemInstruction = systemInstruction,
    toolsNeeded = toolsNeededJson,
    isBuiltin = isBuiltin,
)

internal fun AgentSkill.toEntity(index: Int) = SkillEntity(
    id = id,
    sortIndex = index,
    name = name,
    icon = icon,
    description = description,
    triggerKeywordsJson = triggerKeywords,
    systemInstructionInjection = systemInstructionInjection,
    systemInstruction = systemInstruction,
    toolsNeededJson = toolsNeeded,
    isBuiltin = isBuiltin,
)

internal fun WorldGroupEntity.toModel() = WorldGroup(
    id = id,
    name = name,
    parentId = parentId,
)

internal fun WorldGroup.toEntity(index: Int) = WorldGroupEntity(
    id = id,
    sortIndex = index,
    name = name,
    parentId = parentId,
)

internal fun WorldDocumentEntity.toModel() = WorldDocument(
    id = id,
    title = title,
    groupId = groupId,
    legacyGroup = legacyGroup,
    content = content,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tags = tagsJson,
)

internal fun WorldDocument.toEntity(index: Int) = WorldDocumentEntity(
    id = id,
    sortIndex = index,
    title = title,
    groupId = groupId,
    // ⚠️ **必须原样带上老 `group`**：迁移是「读的时候解析」（`ConfigRepository.worldDocuments()`），
    //    而导入走的是 `worldDocuments → 内存 → 写库`，这里丢掉它就等于迁移永远看不到它。
    //    Web 版的对应点：`importAllData` 把原始 JSON 整体塞进 localStorage（`group` 还在），
    //    之后的 `getWorldDocuments()` 才做解构迁移。
    //    写回时 `legacyGroup` 已经被解析成 `null`（迁移是纯函数、返回清理过的新对象），所以它不会复活。
    legacyGroup = legacyGroup,
    content = content,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tagsJson = tags,
)

internal fun CustomThemeEntity.toModel() = CustomTheme(
    id = id,
    name = name,
    m1 = m1,
    m2 = m2,
    createdAt = createdAt,
)

internal fun CustomTheme.toEntity(index: Int) = CustomThemeEntity(
    id = id,
    sortIndex = index,
    name = name,
    m1 = m1,
    m2 = m2,
    createdAt = createdAt,
)

internal fun ModelFavoriteEntity.toModel() = ModelFavorite(
    providerId = providerId,
    modelName = modelName,
    addedAt = addedAt,
)

internal fun ModelFavorite.toEntity(index: Int) = ModelFavoriteEntity(
    providerId = providerId,
    modelName = modelName,
    sortIndex = index,
    addedAt = addedAt,
)

internal fun ChatGroupEntity.toModel() = ChatGroup(
    id = id,
    name = name,
    color = color,
    icon = icon,
    order = order,
)

internal fun ChatGroup.toEntity(index: Int) = ChatGroupEntity(
    id = id,
    sortIndex = index,
    name = name,
    color = color,
    icon = icon,
    order = order,
)

// ---------------- 核心：会话树 ----------------

/**
 * 会话头 + 配置 blob → [ChatSession]。
 *
 * `messages` / `timelines` / `summaries` 由调用方查好传进来（在同一个事务里）——
 * 一个会话拆成四张表，映射函数拿不到库，只能靠传。
 *
 * ⚠️ `timelines` / `summaries` 的**空**由 blob 里的两个布尔决定，不是由「列表是不是空」决定：
 * 空数组与 `null` 在存档里是两回事（`SessionConfigBlob` 有完整说明）。
 */
internal fun SessionEntity.toModel(
    messages: List<ChatMessage>,
    timelines: List<TimelineBranch>,
    summaries: List<SessionSummary>,
): ChatSession {
    val blob = configJson
    return ChatSession(
        id = id,
        title = title,
        groupId = groupId,
        agentId = agentId,
        providerId = providerId,
        model = model,
        temperature = blob.temperature,
        topP = blob.topP,
        topK = blob.topK,
        frequencyPenalty = blob.frequencyPenalty,
        presencePenalty = blob.presencePenalty,
        maxTokens = blob.maxTokens,
        toolCallStepLimit = blob.toolCallStepLimit,
        systemPromptOverride = blob.systemPromptOverride,
        systemPromptFixed = blob.systemPromptFixed,
        systemPromptInjectedCommon = blob.systemPromptInjectedCommon,
        isCommonPushed = blob.isCommonPushed,
        tagDisplayMode = blob.tagDisplayMode,
        aiContextVisibility = blob.aiContextVisibility,
        aiContextCustomLimit = blob.aiContextCustomLimit,
        uiRenderLimit = blob.uiRenderLimit,
        connectedKnowledgeIds = blob.connectedKnowledgeIds,
        connectedWorldDocIds = blob.connectedWorldDocIds,
        regexRuleIds = blob.regexRuleIds,
        messages = messages,
        timelineMemoryEnabled = blob.timelineMemoryEnabled,
        activeTimelineId = blob.activeTimelineId,
        timelines = if (blob.timelinesNull) null else timelines,
        timelineDescriptionTagRequests = blob.timelineDescriptionTagRequests,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned,
        summaryPrompt = blob.summaryPrompt,
        summaryModel = blob.summaryModel,
        summaries = if (blob.summariesNull) null else summaries,
        lastSummarizedFloor = blob.lastSummarizedFloor,
        imageContextMode = blob.imageContextMode,
        webSearchEnabled = blob.webSearchEnabled,
        streamingEnabled = blob.streamingEnabled,
        aiAvatarFileName = blob.aiAvatarFileName,
        userAvatarFileName = blob.userAvatarFileName,
    )
}

/** [ChatSession] → 会话头行（含配置 blob）。子表由各自的 mapper 单独产出。 */
internal fun ChatSession.toEntity(index: Int) = SessionEntity(
    id = id,
    sortIndex = index,
    title = title,
    groupId = groupId,
    agentId = agentId,
    providerId = providerId,
    model = model,
    pinned = pinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    configJson = SessionConfigBlob(
        temperature = temperature,
        topP = topP,
        topK = topK,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        maxTokens = maxTokens,
        toolCallStepLimit = toolCallStepLimit,
        systemPromptOverride = systemPromptOverride,
        systemPromptFixed = systemPromptFixed,
        systemPromptInjectedCommon = systemPromptInjectedCommon,
        isCommonPushed = isCommonPushed,
        tagDisplayMode = tagDisplayMode,
        aiContextVisibility = aiContextVisibility,
        aiContextCustomLimit = aiContextCustomLimit,
        uiRenderLimit = uiRenderLimit,
        connectedKnowledgeIds = connectedKnowledgeIds,
        connectedWorldDocIds = connectedWorldDocIds,
        regexRuleIds = regexRuleIds,
        timelineMemoryEnabled = timelineMemoryEnabled,
        activeTimelineId = activeTimelineId,
        timelineDescriptionTagRequests = timelineDescriptionTagRequests,
        summaryPrompt = summaryPrompt,
        summaryModel = summaryModel,
        lastSummarizedFloor = lastSummarizedFloor,
        imageContextMode = imageContextMode,
        webSearchEnabled = webSearchEnabled,
        streamingEnabled = streamingEnabled,
        aiAvatarFileName = aiAvatarFileName,
        userAvatarFileName = userAvatarFileName,
        timelinesNull = timelines == null,
        summariesNull = summaries == null,
    ),
)

internal fun MessageEntity.toModel() = ChatMessage(
    id = id,
    role = role,
    versions = versionsJson,
    currentVersionIndex = currentVersionIndex,
    content = content,
    timestamp = timestamp,
    latencyMs = latencyMs,
    tokens = tokens,
    timelineId = timelineId,
    timelineTag = timelineTag,
    floorNumber = floorNumber,
    codeTag = codeTag,
    descriptionTag = descriptionTag,
    isCommon = isCommon,
    isAnalysis = isAnalysis,
    analysisVisibility = analysisVisibility,
    imageIds = imageIdsJson,
    analysisBranchCode = analysisBranchCode,
)

internal fun ChatMessage.toEntity(sessionId: String, index: Int) = MessageEntity(
    id = id,
    sessionId = sessionId,
    sortIndex = index,
    role = role,
    versionsJson = versions,
    currentVersionIndex = currentVersionIndex,
    content = content,
    timestamp = timestamp,
    latencyMs = latencyMs,
    tokens = tokens,
    timelineId = timelineId,
    timelineTag = timelineTag,
    floorNumber = floorNumber,
    codeTag = codeTag,
    descriptionTag = descriptionTag,
    isCommon = isCommon,
    isAnalysis = isAnalysis,
    analysisVisibility = analysisVisibility,
    imageIdsJson = imageIds,
    analysisBranchCode = analysisBranchCode,
)

internal fun TimelineEntity.toModel() = TimelineBranch(
    id = id,
    name = name,
    tag = tag,
    codeTag = codeTag,
    descriptionTag = descriptionTag,
    visible = visible,
    color = color,
    description = description,
    plotSummary = plotSummary,
    keyMilestones = keyMilestonesJson,
    messageIds = messageIdsJson,
    parentId = parentId,
    forkMessageId = forkMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun TimelineBranch.toEntity(sessionId: String, index: Int) = TimelineEntity(
    id = id,
    sessionId = sessionId,
    sortIndex = index,
    name = name,
    tag = tag,
    codeTag = codeTag,
    descriptionTag = descriptionTag,
    visible = visible,
    color = color,
    description = description,
    plotSummary = plotSummary,
    keyMilestonesJson = keyMilestones,
    messageIdsJson = messageIds,
    parentId = parentId,
    forkMessageId = forkMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SummaryEntity.toModel() = SessionSummary(
    id = id,
    content = content,
    fromFloor = fromFloor,
    toFloor = toFloor,
    model = model,
    createdAt = createdAt,
)

internal fun SessionSummary.toEntity(sessionId: String, index: Int) = SummaryEntity(
    id = id,
    sessionId = sessionId,
    sortIndex = index,
    content = content,
    fromFloor = fromFloor,
    toFloor = toFloor,
    model = model,
    createdAt = createdAt,
)
