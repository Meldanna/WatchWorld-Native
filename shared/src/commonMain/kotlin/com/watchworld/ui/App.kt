package com.watchworld.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Adjustments
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.Book
import compose.icons.tablericons.Code
import compose.icons.tablericons.GitBranch
import compose.icons.tablericons.Globe
import compose.icons.tablericons.Menu2
import compose.icons.tablericons.Message
import compose.icons.tablericons.UserCheck
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Plug
import compose.icons.tablericons.Server
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Sun
import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ArchiveStore
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.data.repository.createSyncState
import com.watchworld.data.repository.isSyncConfigured
import com.watchworld.data.repository.pullChanges
import com.watchworld.data.repository.pushChanges
import com.watchworld.domain.buildTimelineTreePromptContext
import com.watchworld.domain.createDefaultMainTimeline
import com.watchworld.domain.editTimelineDraft
import com.watchworld.domain.estimateSessionTokens
import com.watchworld.domain.newTimelineDraft
import com.watchworld.domain.resolveActiveTimeline
import com.watchworld.domain.selectActiveMcpServers
import com.watchworld.domain.selectConnectedKnowledge
import com.watchworld.domain.parseConversationFile
import com.watchworld.model.Role
import com.watchworld.model.TagDisplayMode
import com.watchworld.model.UiMode
import com.watchworld.net.ChatStreamClient
import com.watchworld.net.HttpClientFactory
import com.watchworld.net.McpClient
import com.watchworld.net.ProviderApi
import com.watchworld.net.WebDavClient
import com.watchworld.net.WebSearchClient
import com.watchworld.platform.AvatarOwner
import com.watchworld.platform.defaultAvatarStore
import com.watchworld.platform.defaultImageStore
import com.watchworld.platform.pickImageFiles
import com.watchworld.platform.saveTextFile
import com.watchworld.platform.pickTextFiles
import com.watchworld.ui.chat.AiVisibilityDialog
import com.watchworld.ui.chat.AvatarSettingsDialog
import com.watchworld.ui.chat.ChatScreen
import com.watchworld.ui.chat.ChatViewModel
import com.watchworld.ui.chat.DualBoxPromptDialog
import com.watchworld.ui.chat.SearchPanel
import com.watchworld.ui.chat.SessionActionsPanel
import com.watchworld.ui.chat.WindowApiParamsPanel
import com.watchworld.ui.chat.WindowFunctionSheet
import com.watchworld.ui.chat.ModelSelectorDialog
import com.watchworld.ui.components.AvatarSource
import com.watchworld.ui.components.ModalScrimCount
import com.watchworld.ui.components.WwModalScrim
import com.watchworld.ui.session.SessionViewModel
import com.watchworld.model.McpServerStatus
import com.watchworld.domain.backupFileName
import com.watchworld.platform.currentTimeMillis
import com.watchworld.platform.ensureAppDataDir
import com.watchworld.ui.settings.AgentPanel
import com.watchworld.ui.settings.AppPreferencesPanel
import com.watchworld.ui.settings.DisplaySettingsFooter
import com.watchworld.ui.settings.DisplaySettingsPanel
import com.watchworld.ui.settings.PromptLibraryDialog
import com.watchworld.ui.settings.SettingsDestination
import com.watchworld.ui.settings.ThemeDialog
import com.watchworld.ui.settings.BackupSyncDialog
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.settings.KnowledgePanel
import com.watchworld.ui.settings.McpPanel
import com.watchworld.ui.settings.ProviderPanel
import com.watchworld.ui.settings.RegexPanel
import com.watchworld.ui.settings.SettingsHub
import com.watchworld.ui.settings.SettingsViewModel
import com.watchworld.ui.settings.SkillPanel
import com.watchworld.ui.sidebar.SidebarLayout
import com.watchworld.ui.sidebar.SidebarSheet
import com.watchworld.ui.sidebar.defaultSidebarLayout
import com.watchworld.ui.sidebar.startsExpanded
import com.watchworld.ui.summary.SummaryPanel
import com.watchworld.ui.theme.UiFontFamily
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import com.watchworld.ui.timeline.TimelineEditorDialog
import com.watchworld.ui.timeline.TimelineGuideDialog
import com.watchworld.ui.timeline.TimelineLibraryDialog
import com.watchworld.ui.timeline.TimelineManagerDialog
import com.watchworld.ui.world.WorldPanel
import com.watchworld.ui.world.WorldViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 侧边栏宽度：停靠列与抽屉面板共用（`SidebarSheet` 内部还有一层 `widthIn(max = 320.dp)` 兜底）。
 */
private val SidebarWidth = 320.dp

/**
 * 应用根组合（双端共享）。
 *
 * **布局对标 Web 版**：垂直三段式（TopBar → ChatScreen → 输入栏），
 * 侧边栏和设置以覆盖层弹出，不替换聊天区。
 *
 * ⚠️ UI **只拿 Repository**，不碰 DAO（CONSTRAINTS §5）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val database = remember { WwDatabase.open(ensureAppDataDir() + "/" + WwDatabase.FILE_NAME) }
    val http = remember { HttpClientFactory.create() }
    val chatClient = remember { ChatStreamClient(http) }
    val searchClient = remember { WebSearchClient(http) }
    val mcpClient = remember { McpClient(http) }
    val sessionRepository = remember { SessionRepository(database) }
    val settingsRepository = remember { SettingsRepository(database) }
    val configRepository = remember { ConfigRepository(database) }
    val archiveStore = remember { ArchiveStore(database) }
    val webDavClient = remember { WebDavClient(http) }
    val providerApi = remember { ProviderApi(http) }
    // 输入栏「添加图片」用：落盘后的 id 随消息写进 `imageIds`（与 ChatViewModel 里的同一实例语义）。
    val imageStore = defaultImageStore
    // 头像文件（用户 / 窗口 / Agent 三处，见 `AvatarStore`）。
    val avatarStore = defaultAvatarStore
    // T19 增量同步状态（清单 + 已建目录标记）；整个应用生命周期内只一份。
    val syncState = remember { createSyncState() }

    val scope = rememberCoroutineScope()
    val sessionViewModel = remember {
        SessionViewModel(sessionRepository, settingsRepository, configRepository)
    }
    val chatViewModel = remember {
        ChatViewModel(
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository,
            configRepository = configRepository,
            chatClient = chatClient,
            searchClient = searchClient,
            mcpClient = mcpClient,
            // 每条消息写回后同步侧边栏（标题 / updatedAt / 顺序），否则列表显示的是旧数据。
            onSessionChanged = { session -> sessionViewModel.replace(session) },
        )
    }
    val settingsViewModel = remember {
        SettingsViewModel(configRepository, settingsRepository, sessionRepository, archiveStore, webDavClient, providerApi)
    }
    val worldViewModel = remember { WorldViewModel(configRepository) }
    DisposableEffect(Unit) {
        onDispose {
            chatClient.close()
            database.close()
        }
    }

    val sessionState by sessionViewModel.state.collectAsState()
    val chatState by chatViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val worldState by worldViewModel.state.collectAsState()
    // 侧边栏形态由平台定（用户 2026-10-13）：桌面**停靠**、Android **抽屉**。
    // `sidebarOpen` 在两种形态下含义不同 —— 停靠 = 这一列显不显示，抽屉 = 浮层开没开 ——
    // 所以初始值也跟着形态走：桌面默认展开（「windows版本侧边栏固定展开」），移动端默认收起。
    val sidebarLayout = defaultSidebarLayout
    val sidebarDocked = sidebarLayout == SidebarLayout.DOCKED
    var sidebarOpen by remember { mutableStateOf(sidebarLayout.startsExpanded) }
    // 头像换图（上传 / 移除）后 +1：头像弹窗的预览等地方拿它当重新读盘的开关。
    var avatarRevision by remember { mutableStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var mcpOpen by remember { mutableStateOf(false) }
    var skillOpen by remember { mutableStateOf(false) }
    var knowledgeOpen by remember { mutableStateOf(false) }
    var worldOpen by remember { mutableStateOf(false) }
    var providerOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    // T24 时间线：管理器从聊天打开；编辑器与 AI 说明叠在管理器之上。父子关系用两个状态表达，
    // 关闭子层只清 [timelineChild]，管理器不受影响（需求 §三 第 4 条）。
    var timelineOpen by remember { mutableStateOf(false) }
    var timelineChild by remember { mutableStateOf<TimelineDestination?>(null) }
    // 侧边栏「时间线」格开的是**跨窗口只读**的时间线库（参考版 `TimelineLibraryModal`），
    // 窗口级的时间线管理仍走 [timelineOpen]（输入栏与「窗口功能」里的入口）。
    var timelineLibraryOpen by remember { mutableStateOf(false) }
    // 时间线库跳转时要聚焦的分支，记的是 (窗口 id, 分支 id) **一对**：
    // 切窗口是异步的（由 [LaunchedEffect] 里的 `chatViewModel.open` 完成加载），
    // 不能在同一协程里紧跟着 `setActiveTimeline`，否则会和「重新加载会话」抢同一份状态；
    // 而只记分支 id 的话，用户在加载完成前又切走窗口，这个分支就会落到别的会话头上。
    var pendingTimelineFocus by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 侧边栏底部「正则库」格：独立弹窗（**不在**设置里，与参考版的设置卡片是有意分歧）。
    var regexOpen by remember { mutableStateOf(false) }
    // 时间线编辑器保存失败的提示。⚠️ 只有**真失败**才有值：领域层对「名字为空」
    // 直接返回同一实例（不落库），这种情况连弹窗都不该关。
    var timelineSaveError by remember { mutableStateOf<String?>(null) }
    // 侧边栏「导入对话」的失败提示（顶栏展示；成功就切到新窗口，无需提示）。
    var importError by remember { mutableStateOf<String?>(null) }
    // T24 窗口功能底部弹层。子页用**一个目的地枚举**表达，而不是继续堆布尔——
    // 依据是需求 §三：子弹窗关闭后必须回到**发起它的那一层**，一堆互相独立的 Boolean
    // 一旦同时为真，就再也说不清该回哪儿。
    var windowFunctionOpen by remember { mutableStateOf(false) }
    var windowChild by remember { mutableStateOf<WindowFunctionDestination?>(null) }
    // 完整模型选择器（参考版 `ModelSelectorModal`）：跨供应商搜索 + 只看收藏。
    var modelSelectorOpen by remember { mutableStateOf(false) }
    // 输入区其余一级入口的挂载点。提示词连接现有面板；窗口功能齿轮是底部弹层。
    // 可见性与渲染先保留明确空壳，等待后续参考图补全最终内容。
    var windowVisibilityOpen by remember { mutableStateOf(false) }
    // 设置里的每一张卡点开的都是**独立弹窗**（不是设置的内部子页）：
    // 参考版 `SettingsHubModal.tsx:43-47` 的 `handle()` 是先 `onClose()` 关掉设置总入口、
    // 再开目标弹窗（注释原文「避免多层级弹窗叠压」），关闭时由 `closeWithReturn`
    // （`App.tsx:280-287`）重新打开设置总入口。
    // 输入栏的 Agent 按钮复用同一个 Agent 弹窗，只是不置「回设置」标志 —— 关闭回聊天界面。
    var settingsChild by remember { mutableStateOf<SettingsDestination?>(null) }
    var settingsChildReturnsToSettings by remember { mutableStateOf(false) }

    // 启动：读会话树；库里一个会话都没有时建一个，保证界面永远有可用的活动会话。
    LaunchedEffect(Unit) {
        settingsViewModel.load()
        sessionViewModel.load()
        if (sessionViewModel.state.value.sessions.isEmpty()) {
            sessionViewModel.newSession()
            sessionViewModel.load()
        }
        // T19 启动拉取：远端比本地新的窗口合并回库。用 replaceAll（不盖 updatedAt），
        // 否则本地会把远端的时间戳改成本机时间，下一轮又反向推送。
        val config = settingsViewModel.state.value.webDavConfig
        if (config != null && isSyncConfigured(config)) {
            runCatching {
                val local = sessionRepository.all()
                val (pulled, _) = pullChanges(config, local, syncState, webDavClient)
                if (pulled.isNotEmpty()) {
                    val merged = local.associateBy { it.id }.toMutableMap()
                    pulled.forEach { merged[it.id] = it }
                    sessionRepository.replaceAll(merged.values.toList())
                    sessionViewModel.load()
                }
            }
        }
        chatViewModel.open(sessionViewModel.state.value.activeSession)
    }

    // T19 变动后防抖推送：只上传 updatedAt 有变化的窗口（3s 防抖，与 Web 版一致）。
    LaunchedEffect(sessionState.sessions, settingsState.webDavConfig) {
        val config = settingsState.webDavConfig ?: return@LaunchedEffect
        if (!isSyncConfigured(config) || !config.autoSync) return@LaunchedEffect
        delay(3000)
        runCatching { pushChanges(config, sessionState.sessions, syncState, webDavClient) }
    }

    // 会话切换：把新会话交给 ChatViewModel（正在流式时它自己先停）。
    LaunchedEffect(sessionState.activeSessionId, sessionState.sessions) {
        val active = sessionState.activeSession
        if (active != null && active.id != chatState.session?.id) chatViewModel.open(active)
    }

    // 时间线库跳转：`select` 之后会话要经上面的 effect 异步加载完才谈得上聚焦分支，
    // 所以在这里等「已加载的会话 == 这次跳转的目标窗口」成立再把分支落下去。
    LaunchedEffect(pendingTimelineFocus, chatState.session?.id) {
        val (targetSessionId, targetTimelineId) = pendingTimelineFocus ?: return@LaunchedEffect
        if (chatState.session?.id != targetSessionId) return@LaunchedEffect
        chatViewModel.setActiveTimeline(targetTimelineId)
        pendingTimelineFocus = null
    }

    // T15: 主题/明暗从库读取
    val resolvedUiMode = when (settingsState.uiModeRaw) {
        "light" -> UiMode.LIGHT
        "dark" -> UiMode.DARK
        else -> null
    }
    val isDark = resolvedUiMode == UiMode.DARK || (resolvedUiMode == null)

    WwTheme(
        palette = settingsState.themePalette,
        customThemes = settingsState.customThemes,
        chatFontSize = settingsState.appPreferences.chatFontSize,
        uiFontFamily = UiFontFamily.fromStorage(settingsState.uiFontFamilyRaw),
        uiMode = resolvedUiMode,
    ) {
        // ── 侧边栏面板：停靠与抽屉两种形态共用同一份参数 ──
        // 抽成一个 lambda 是因为参数有二十多个、调用点有两处（布局列 / 浮层）：
        // 各拼一份迟早会漏接某个回调（SidebarSheet 的参数都带默认值，漏了只是静默变成死键）。
        val sidebarPanel: @Composable (Modifier) -> Unit = { panelModifier ->
            SidebarSheet(
                sessions = sessionState.sessions,
                groups = sessionState.groups,
                activeSessionId = sessionState.activeSessionId,
                onSelect = { id ->
                    scope.launch { sessionViewModel.select(id) }
                    sidebarOpen = false
                },
                onNewSession = { groupId ->
                    scope.launch {
                        sessionViewModel.newSession(groupId)
                        chatViewModel.open(sessionViewModel.state.value.activeSession)
                    }
                    sidebarOpen = false
                },
                onRename = { id, title -> scope.launch { sessionViewModel.rename(id, title) } },
                onClear = { id ->
                    scope.launch {
                        sessionViewModel.clear(id)
                        if (id == chatState.session?.id) {
                            chatViewModel.open(sessionViewModel.state.value.activeSession)
                        }
                    }
                },
                onDelete = { id ->
                    scope.launch {
                        sessionViewModel.delete(id)
                        chatViewModel.open(sessionViewModel.state.value.activeSession)
                    }
                },
                onMove = { id, groupId -> scope.launch { sessionViewModel.moveToGroup(id, groupId) } },
                onTogglePinned = { id, pinned ->
                    scope.launch { sessionViewModel.setPinned(id, pinned) }
                },
                onCreateGroup = { name, color ->
                    scope.launch { sessionViewModel.createGroup(name, color) }
                },
                onEditGroup = { id, name, color ->
                    scope.launch { sessionViewModel.updateGroup(id, name, color) }
                },
                // ⚠️ 这四个原本没接：SidebarSheet 的参数都带默认值（`{}` / `""`），
                // 调用点漏传不会编译报错，只会让按钮静默变成死键。
                onToggleUiMode = {
                    scope.launch { settingsViewModel.setUiMode(if (isDark) UiMode.LIGHT else UiMode.DARK) }
                },
                onDismiss = { sidebarOpen = false },
                onOpenSettings = {
                    settingsOpen = true
                    scope.launch { settingsViewModel.load() }
                },
                onOpenMcp = {
                    mcpOpen = true
                    scope.launch { settingsViewModel.load() }
                },
                onOpenSkill = {
                    skillOpen = true
                    scope.launch { settingsViewModel.load() }
                },
                onOpenKnowledge = {
                    knowledgeOpen = true
                    scope.launch { settingsViewModel.load() }
                },
                onOpenWorld = {
                    worldOpen = true
                    scope.launch { worldViewModel.load() }
                },
                onOpenTimeline = { timelineLibraryOpen = true },
                onOpenProvider = {
                    providerOpen = true
                    scope.launch { settingsViewModel.load() }
                },
                onOpenRegex = {
                    regexOpen = true
                    scope.launch { settingsViewModel.load() }
                },
                // 侧边栏「导入对话」：挑一个 MD / JSON 文件 → 解析 → 建新窗口并切过去。
                // ⚠️ `pickTextFiles` 弹模态文件框，必须在 UI 线程（点击回调）里调。
                onImportSession = {
                    val picked = pickTextFiles(listOf("md", "txt", "json")).firstOrNull()
                    if (picked != null) {
                        scope.launch {
                            runCatching {
                                chatViewModel.open(
                                    sessionViewModel.importConversation(parseConversationFile(picked.name, picked.content)),
                                )
                            }.onFailure { error ->
                                importError = error.message ?: error.toString()
                            }
                        }
                        sidebarOpen = false
                    }
                },
                // 徽章口径照抄参考版：MCP 只算「本窗口 Agent 绑定且真实可用」的，
                // 知识库算「全局 enabled ∩ 本窗口勾选」。两个判定都走 domain 里的唯一实现，
                // 不复用就会和真正注入 system 的能力清单对不上。
                activeMcpCount = selectActiveMcpServers(
                    settingsState.mcpServers,
                    settingsState.agents.firstOrNull { it.id == sessionState.activeSession?.agentId },
                ).size,
                connectedKnowledgeCount = selectConnectedKnowledge(
                    settingsState.knowledgeBase,
                    sessionState.activeSession,
                ).size,
                modifier = panelModifier,
            )
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            // 毛玻璃：只要有一个模态遮罩在组合里，主内容就整体模糊（遮罩自己只负责压雾、不压黑）。
            // 遮罩画在**独立窗口**里、糊不到下层像素，所以「玻璃」的模糊只能由主窗口这一层做：
            // 于是弹窗后面看到的是糊掉的界面，而不是抹平的一块灰。
            val modalScrims = ModalScrimCount.value
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (modalScrims > 0) Modifier.blur(WwSurfaces.scrimBlurRadius) else Modifier),
            ) {
                // ── TopBar：对标 Web 版 Header ──
                TopAppBar(
                    title = {
                        Text(
                            text = chatState.session?.title?.ifBlank { "未命名对话" }
                                ?: "WatchWorld Native",
                            fontSize = WwType.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        // 停靠形态下这个按钮就是「收起 / 展开左边这一列」；抽屉形态下是「打开浮层」。
                        IconButton(onClick = { sidebarOpen = !sidebarOpen }) {
                            Icon(
                                TablerIcons.Menu2,
                                contentDescription = if (sidebarOpen) "收起侧边栏" else "打开侧边栏",
                            )
                        }
                    },
                    actions = {
                        chatState.error?.let { message ->
                            Text(
                                text = "⚠️ $message",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = WwType.hint,
                                lineHeight = WwSize.compactTextLineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp),
                            )
                        } ?: importError?.let { message ->
                            Text(
                                text = "导入失败：$message",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = WwType.hint,
                                lineHeight = WwSize.compactTextLineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp),
                            )
                        }
                        IconButton(onClick = {
                            val next = if (isDark) UiMode.LIGHT else UiMode.DARK
                            scope.launch { settingsViewModel.setUiMode(next) }
                        }) {
                            Icon(
                                imageVector = if (isDark) TablerIcons.Moon
                                    else TablerIcons.Sun,
                                contentDescription = if (isDark) "切换到浅色" else "切换到深色",
                                tint = if (isDark) Color(0xFFC0C0C0) else Color(0xFFFBBF24),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WwSurfaces.windowBase,
                    ),
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                // ── 主内容区：ChatScreen 始终可见（停靠形态下侧边栏就在它左边）──
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // 停靠形态：侧边栏是布局里的一列 —— 内容区被挤窄，而不是被遮住。
                    if (sidebarDocked && sidebarOpen) {
                        sidebarPanel(Modifier.fillMaxHeight().width(SidebarWidth))
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ChatScreen(
                            session = chatState.session,
                            streamingMessageId = chatState.streamingMessageId,
                            onSend = { text, imageIds ->
                                val job = scope.launch { chatViewModel.send(text, imageIds) }
                                chatViewModel.bindJob(job)
                            },
                            onStop = { chatViewModel.stop() },
                            modifier = Modifier.fillMaxSize(),
                            // 输入草稿：切窗口 / 重启应用都接着写（逐键落 kv，见 ChatViewModel.setDraft）。
                            draft = chatState.draft,
                            onDraftChange = { text -> scope.launch { chatViewModel.setDraft(text) } },
                            onSwitchVersion = { id, idx ->
                                scope.launch { chatViewModel.switchVersion(id, idx) }
                            },
                            onRegenerate = { id ->
                                val job = scope.launch { chatViewModel.regenerate(id) }
                                chatViewModel.bindJob(job)
                            },
                            onEdit = { id, text ->
                                val job = scope.launch { chatViewModel.editAndResend(id, text) }
                                chatViewModel.bindJob(job)
                            },
                            // 只改正文、不重发（用户 2026-10-12）。
                            onSaveEdit = { id, text ->
                                scope.launch { chatViewModel.editMessage(id, text) }
                            },
                            onDeleteMessage = { id ->
                                scope.launch { chatViewModel.deleteMessage(id) }
                            },
                            onToggleAnalysisVisibility = { id ->
                                scope.launch { chatViewModel.toggleAnalysisVisibility(id) }
                            },
                            onArchiveAnalysis = { id ->
                                scope.launch { chatViewModel.archiveAnalysisToDocument(id) }
                            },
                            timelineEnabled = chatState.session?.timelineMemoryEnabled == true,
                            // 输入栏标签行：全部分支 + 当前聚焦 + 展示模式 + 上一条用户消息的归属。
                            timelines = chatState.session?.timelines.orEmpty(),
                            activeTimelineId = chatState.session?.activeTimelineId,
                            tagDisplayMode = chatState.session?.tagDisplayMode ?: TagDisplayMode.DESC,
                            lastUserMessageTimelineId = chatState.session?.messages
                                .orEmpty()
                                .lastOrNull { it.role == Role.USER }
                                ?.timelineId,
                            onSelectTimeline = { id ->
                                scope.launch { chatViewModel.setActiveTimeline(id) }
                            },
                            onToggleTagDisplayMode = {
                                scope.launch { chatViewModel.toggleTagDisplayMode() }
                            },
                            agentAvatar = settingsState.agents
                                .firstOrNull { it.id == chatState.session?.agentId }
                                ?.avatar
                                .orEmpty(),
                            agentName = settingsState.agents
                                .firstOrNull { it.id == chatState.session?.agentId }
                                ?.name
                                ?.ifBlank { null }
                                ?: chatState.session?.agentId?.ifBlank { null }
                                ?: "未配置 Agent",
                            onOpenAgent = {
                                // 输入栏入口：关闭回聊天界面（不置「回设置」标志）。
                                settingsChildReturnsToSettings = false
                                settingsChild = SettingsDestination.AGENT
                                scope.launch { settingsViewModel.load() }
                            },
                            onOpenWindowSettings = { windowFunctionOpen = true },
                            onOpenVisibility = { windowVisibilityOpen = true },
                            onOpenPrompts = {
                                // Input bar prompt button opens dual-box prompt (same as WindowFunction entry)
                                windowChild = WindowFunctionDestination.DualBoxPrompt
                            },
                            onOpenTimeline = { timelineOpen = true },
                            webSearchEnabled = chatState.session?.webSearchEnabled == true,
                            onToggleWebSearch = {
                                scope.launch { chatViewModel.toggleWebSearchEnabled() }
                            },
                            providers = settingsState.providers,
                            activeProviderId = chatState.session?.providerId.orEmpty(),
                            activeModel = chatState.session?.model?.takeIf { it.isNotBlank() }
                                ?: settingsState.providers
                                    .firstOrNull { it.id == chatState.session?.providerId }
                                    ?.defaultModel
                                    .orEmpty(),
                            onSelectProviderAndModel = { providerId, model ->
                                scope.launch { chatViewModel.selectProviderAndModel(providerId, model) }
                            },
                            favorites = settingsState.modelFavorites,
                            onToggleFavorite = { providerId, modelName ->
                                scope.launch { settingsViewModel.toggleModelFavorite(providerId, modelName) }
                            },
                            onOpenModelSelector = { modelSelectorOpen = true },
                            onImportImage = { register ->
                                // 文件框是模态的，必须在 UI 线程（点击回调）里同步挑完再交给协程落盘。
                                val picked = pickImageFiles()
                                if (picked.isNotEmpty()) {
                                    scope.launch {
                                        val ids = picked.mapNotNull { file ->
                                            runCatching {
                                                imageStore.saveFile(file.bytes, file.mime, file.name).id
                                            }.getOrNull()
                                        }
                                        if (ids.isNotEmpty()) register(ids)
                                    }
                                }
                            },
                            onImportFile = { append ->
                                val picked = pickTextFiles(
                                    listOf(
                                        "txt", "md", "json", "csv", "log", "yaml", "yml", "xml",
                                        "html", "css", "js", "ts", "py", "java", "c", "cpp", "sql", "sh", "ps1",
                                    ),
                                )
                                if (picked.isNotEmpty()) {
                                    // 与参考版 `handleFilePick` 同一格式：`【文件：<名>】\n```\n<正文>\n``` `
                                    append(
                                        picked.joinToString("\n\n") { file ->
                                            "【文件：${file.name}】\n```\n${file.content}\n```"
                                        },
                                    )
                                }
                            },
                            // 头像和显示设置
                            // 窗口用户头像优先，没有则用全局用户头像
                            userAvatar = chatState.session?.let { session ->
                                // 窗口用户头像读 `SessionUser`（`sessions/<id>/user/`），与 AI 头像分目录。
                                session.userAvatarFileName?.let { AvatarSource.SessionUser(session.id, it) }
                            } ?: settingsState.appPreferences.userAvatarFileName
                                ?.let { AvatarSource.User(it) },
                            // 窗口 AI 头像优先，没有则用该窗口所用 Agent 的默认头像（有图用图、没图用 emoji）。
                            aiAvatar = chatState.session?.let { session ->
                                session.aiAvatarFileName?.let { AvatarSource.Session(session.id, it) }
                                    ?: settingsState.agents
                                        .firstOrNull { it.id == session.agentId }
                                        ?.let { AvatarSource.Agent(it.id, it.avatar) }
                            },
                            avatarSize = settingsState.displaySettings.avatarSize,
                            showAvatars = settingsState.displaySettings.showAvatars,
                            bubbleSpacing = settingsState.displaySettings.bubbleSpacing,
                            showModelName = settingsState.displaySettings.showModelName,
                            showMessageTime = settingsState.displaySettings.showMessageTime,
                            showLatency = settingsState.displaySettings.showLatency,
                            showMessageTokens = settingsState.displaySettings.showMessageTokens,
                            showSessionTokens = settingsState.displaySettings.showSessionTokens,
                            // 会话总 Token 只随消息列表变化重算（对标 Web 的 useMemo），避免流式刷新时重复全量估算。
                            sessionTotalTokens = remember(chatState.session?.messages) {
                                chatState.session?.messages?.let { estimateSessionTokens(it) } ?: 0
                            },
                        )
                    }
                }
            }


            // ── 抽屉形态的覆盖层：毛玻璃遮罩 + 左侧面板 ──
            // 停靠形态（桌面）下不渲染它 —— 侧边栏此时是内容左边的一列，不盖住任何东西。
            if (!sidebarDocked && sidebarOpen) {
                Dialog(
                    onDismissRequest = { sidebarOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 遮罩：全应用统一款式（毛玻璃雾面，不压黑），并登记自己让主内容去模糊。
                        WwModalScrim(onDismiss = { sidebarOpen = false })
                        sidebarPanel(
                            Modifier.fillMaxHeight().width(SidebarWidth).align(Alignment.CenterStart),
                        )
                    }
                }
            }

            // ── 功能弹窗：FeatureDialog 系列 ──
            if (mcpOpen) {
                FeatureDialog(
                    title = "MCP (Model Context Protocol) 服务管理",
                    subtitle = "连接外部 MCP 工具服务器（SSE / Stdio / API），赋能 AI 真实环境操作",
                    icon = TablerIcons.Server,
                    onDismiss = { mcpOpen = false },
                    maxWidth = 576.dp,
                    heightFraction = 0.92f,
                ) {
                    McpPanel(
                        items = settingsState.mcpServers,
                        onSave = { scope.launch { settingsViewModel.saveMcpServer(it) } },
                        onDelete = { scope.launch { settingsViewModel.deleteMcpServer(it) } },
                        // 测试结果**落回配置**：面板的状态行与「已发现工具」读的就是这些持久化字段
                        // （参考版 `App.tsx` 的 handleTestMcpServer 同样写回 server 再保存）。
                        // 失败时保留上一次发现的工具列表，不要把已有信息清空。
                        onTestServer = { server ->
                            val result = mcpClient.testConnection(server)
                            settingsViewModel.saveMcpServer(
                                server.copy(
                                    status = if (result.success) McpServerStatus.CONNECTED
                                    else McpServerStatus.DISCONNECTED,
                                    tools = if (result.success) result.tools else server.tools,
                                    lastTestedAt = currentTimeMillis(),
                                    lastLatencyMs = result.latencyMs,
                                    lastError = result.error,
                                    serverName = result.serverInfo?.name ?: server.serverName,
                                    serverVersion = result.serverInfo?.version ?: server.serverVersion,
                                ),
                            )
                            result
                        },
                    )
                }
            }
            if (skillOpen) {
                FeatureDialog(
                    title = "Skill 技能库",
                    subtitle = "浏览、创建和维护技能；绑定关系在 Agent 设置中管理",
                    icon = TablerIcons.Bolt,
                    onDismiss = { skillOpen = false },
                    maxWidth = 576.dp,
                    heightFraction = 0.92f,
                ) {
                    SkillPanel(
                        items = settingsState.skills,
                        onSave = { scope.launch { settingsViewModel.saveSkill(it) } },
                        onDelete = { scope.launch { settingsViewModel.deleteSkill(it) } },
                    )
                }
            }
            if (knowledgeOpen) {
                // 参考版是 `h-[92vh]` 固定高（不是 max-h）——要让列表区自己滚、顶栏定住。
                FeatureDialog(
                    title = "本地知识库与文档连接",
                    subtitle = "纯资料库：管理知识文档；本窗口连接在「窗口功能 → 本地知识库」里勾选",
                    icon = TablerIcons.Book,
                    onDismiss = { knowledgeOpen = false },
                    maxWidth = 768.dp,
                    heightFraction = 0.92f,
                    fixedHeight = true,
                ) {
                    // 库形态（侧边栏）：不传 session 参数 → 不显示连接开关，卡片也不带选中外观。
                    // 与世界观库 / 正则库同一套「一个组件、两种形态」（用户 2026-10-10 定案）。
                    KnowledgePanel(
                        items = settingsState.knowledgeBase,
                        onSave = { scope.launch { settingsViewModel.saveKnowledgeItem(it) } },
                        onDelete = { scope.launch { settingsViewModel.deleteKnowledgeItem(it) } },
                    )
                }
            }
            if (worldOpen) {
                // 参考版是「左树 + 右详情」双栏、各栏独立滚动，选中文档时 `h-[90vh]` 固定高。
                // 所以这里必须 `fixedHeight + scrollable = false`：WorldPanel 内部自己管两个滚动区。
                FeatureDialog(
                    title = "世界观库",
                    subtitle = "作品内部的既定事实（正典）",
                    icon = TablerIcons.Globe,
                    onDismiss = { worldOpen = false },
                    maxWidth = 896.dp,
                    heightFraction = 0.92f,
                    scrollable = false,
                    fixedHeight = true,
                ) {
                    WorldPanel(
                        state = worldState,
                        onCreateGroup = { name, parentId -> scope.launch { worldViewModel.createGroup(name, parentId) } },
                        onRenameGroup = { id, name -> scope.launch { worldViewModel.renameGroup(id, name) } },
                        onSetGroupParent = { id, parentId -> scope.launch { worldViewModel.setGroupParent(id, parentId) } },
                        onDeleteGroup = { id -> scope.launch { worldViewModel.deleteGroup(id) } },
                        onToggleCollapsed = worldViewModel::toggleCollapsed,
                        onSelectDocument = worldViewModel::select,
                        onSetQuery = worldViewModel::setQuery,
                        onCreateDocument = { title, groupId, content ->
                            scope.launch { worldViewModel.createDocument(title, groupId, content) }
                        },
                        onSaveDocument = { doc -> scope.launch { worldViewModel.saveDocument(doc) } },
                        onDeleteDocument = { id -> scope.launch { worldViewModel.deleteDocument(id) } },
                        onMoveDocumentToGroup = { id, groupId ->
                            scope.launch { worldViewModel.moveDocumentToGroup(id, groupId) }
                        },
                    )
                }
            }
            // ── 时间线库：跨窗口只读浏览（侧边栏「时间线」格）──
            // ⚠️ 与下面的 [TimelineManagerDialog] 是两件事：那个是**本窗口**的管理界面，
            //    入口在输入栏时间线按钮与「窗口功能」里；这里只浏览 + 跳转，不带开关。
            if (timelineLibraryOpen) {
                TimelineLibraryDialog(
                    sessions = sessionState.sessions,
                    activeSessionId = sessionState.activeSessionId,
                    onDismiss = { timelineLibraryOpen = false },
                    onJumpToTimeline = { sessionId, timelineId ->
                        if (sessionId == sessionState.activeSessionId) {
                            scope.launch { chatViewModel.setActiveTimeline(timelineId) }
                        } else {
                            pendingTimelineFocus = sessionId to timelineId
                            scope.launch { sessionViewModel.select(sessionId) }
                        }
                    },
                )
            }
            // ── 时间线管理器（记忆整理 · 时间线消息树） ──
            // ⚠️ 会话还没有分支时，用 `createDefaultMainTimeline` 在**内存里**补一条通用主线
            //    供界面展示 —— 它只是展示用，真正写库发生在用户开启总开关之后。
            val timelineSession = chatState.session?.let { session ->
                session.copy(
                    timelines = session.timelines?.takeIf { it.isNotEmpty() }
                        ?: listOf(createDefaultMainTimeline(session.createdAt)),
                )
            }
            if (timelineOpen && timelineSession != null) {
                TimelineManagerDialog(
                    session = timelineSession,
                    onDismiss = { timelineOpen = false },
                    onToggleMemory = { enabled ->
                        scope.launch { chatViewModel.setTimelineMemoryEnabled(enabled) }
                    },
                    onSelectTimeline = { id -> scope.launch { chatViewModel.setActiveTimeline(id) } },
                    onToggleVisibility = { id ->
                        val branch = timelineSession.timelines.orEmpty().firstOrNull { it.id == id }
                        scope.launch { chatViewModel.setTimelineVisible(id, branch?.visible == false) }
                    },
                    onSetAllVisibility = { visible ->
                        scope.launch { chatViewModel.setAllTimelinesVisible(visible) }
                    },
                    onCreateBranch = { parentId -> timelineChild = TimelineDestination.Editor(parentId) },
                    onEditTimeline = { id -> timelineChild = TimelineDestination.Editor(null, id) },
                    onDeleteTimeline = { id -> scope.launch { chatViewModel.deleteTimeline(id) } },
                    onApproveRequest = { id ->
                        scope.launch { chatViewModel.approveTimelineDescriptionTagRequest(id) }
                    },
                    onRejectRequest = { id ->
                        scope.launch { chatViewModel.rejectTimelineDescriptionTagRequest(id) }
                    },
                    onOpenAiGuide = { timelineChild = TimelineDestination.Guide },
                )
            }
            // 子弹窗叠在管理器之上；关闭后管理器仍在（父子状态分离，见需求 §三 第 4 条）。
            when (val child = timelineChild) {
                is TimelineDestination.Editor -> {
                    val timelines = timelineSession?.timelines.orEmpty()
                    val editing = child.timelineId?.let { id -> timelines.firstOrNull { it.id == id } }
                    TimelineEditorDialog(
                        initial = if (child.timelineId == null) {
                            newTimelineDraft(
                                timelines = timelines,
                                parentId = child.parentId,
                                activeTimelineId = timelineSession?.activeTimelineId,
                            )
                        } else {
                            editing?.let { editTimelineDraft(it) }
                                ?: newTimelineDraft(timelines, null, timelineSession?.activeTimelineId)
                        },
                        timelines = timelines,
                        saving = false,
                        error = timelineSaveError,
                        onDismiss = { timelineChild = null },
                        onSave = { draft ->
                            scope.launch {
                                val saved = chatViewModel.saveTimelineDraft(draft)
                                if (saved) {
                                    timelineSaveError = null
                                    timelineChild = null
                                } else {
                                    timelineSaveError = "分支名称不能为空，或写入本地库失败。请重试。"
                                }
                            }
                        },
                    )
                }

                TimelineDestination.Guide -> TimelineGuideDialog(
                    guide = if (timelineSession != null && timelineSession.timelineMemoryEnabled == true) {
                        resolveActiveTimeline(timelineSession)?.let { branch ->
                            buildTimelineTreePromptContext(
                                activeTimeline = branch,
                                allTimelines = timelineSession.timelines.orEmpty(),
                                allMessages = timelineSession.messages,
                            )
                        }.orEmpty()
                    } else {
                        ""
                    },
                    onDismiss = { timelineChild = null },
                )

                null -> Unit
            }
            if (providerOpen) {
                FeatureDialog(
                    title = "多 API 接口管理与兼容",
                    subtitle = "支持保存多种官方、开源或自建 OpenAI 兼容端点",
                    icon = TablerIcons.Plug,
                    onDismiss = { providerOpen = false },
                    maxWidth = 576.dp,
                ) {
                    ProviderPanel(
                        items = settingsState.providers,
                        onSave = { scope.launch { settingsViewModel.saveProvider(it) } },
                        onDelete = { scope.launch { settingsViewModel.deleteProvider(it) } },
                        onTestConnection = { settingsViewModel.testProvider(it) },
                        onFetchModels = { settingsViewModel.fetchProviderModels(it) },
                    )
                }
            }
            // ── 正则库：侧边栏独立入口（不在设置里）──
            // ⚠️ 与「窗口功能 → 本窗口正则」共用同一个 `RegexPanel`（参考版就是同一个 `RegexModal`
            //    的两种形态）：这个只管全局规则库，窗口级的「哪些规则在本窗口生效」由窗口形态的
            //    session 参数提供。
            if (regexOpen) {
                FeatureDialog(
                    title = "正则库",
                    subtitle = "维护全部正则规则，可按分组归档；具体窗口在「窗口功能 → 本窗口正则」里启用",
                    icon = TablerIcons.Code,
                    onDismiss = { regexOpen = false },
                    maxWidth = 576.dp,
                    heightFraction = 0.92f,
                ) {
                    RegexPanel(
                        rules = settingsState.regexRules,
                        onSave = { scope.launch { settingsViewModel.saveRegexRule(it) } },
                        onDelete = { scope.launch { settingsViewModel.deleteRegexRule(it) } },
                        onToggle = { scope.launch { settingsViewModel.toggleRegexRule(it) } },
                    )
                }
            }
            // ── 设置子弹窗（每张设置卡一个独立 FeatureDialog）──
            // 关闭 = 返回发起它的那一层：设置来的回设置总入口，输入栏来的回聊天界面。
            settingsChild?.let { destination ->
                val closeChild = {
                    settingsChild = null
                    if (destination == SettingsDestination.BACKUP) settingsViewModel.clearImportNotice()
                    if (settingsChildReturnsToSettings) {
                        settingsChildReturnsToSettings = false
                        settingsOpen = true
                    }
                }
                when (destination) {
                    SettingsDestination.AGENT -> FeatureDialog(
                        title = "Agent 顾问中心",
                        subtitle = "切换不同角色设定或创建专属顾问",
                        icon = TablerIcons.UserCheck,
                        onDismiss = closeChild,
                        maxWidth = 576.dp,
                    ) {
                        AgentPanel(
                            items = settingsState.agents,
                            onSave = { scope.launch { settingsViewModel.saveAgent(it) } },
                            onDelete = { scope.launch { settingsViewModel.deleteAgent(it) } },
                            mcpServers = settingsState.mcpServers,
                            skills = settingsState.skills,
                            activeAgentId = chatState.session?.agentId,
                            // 参考版 `AgentModal.tsx:489-492`：选中后 `onSelectAgent` + `onClose`。
                            onSelectAgent = { id -> scope.launch { chatViewModel.selectAgent(id) }; closeChild() },
                        )
                    }

                    SettingsDestination.PREFERENCES -> FeatureDialog(
                        title = "偏好 · 默认模型 · 全局 API 参数",
                        subtitle = "控制默认模型、全局 API 参数、新窗口默认项与启动行为",
                        icon = TablerIcons.Adjustments,
                        onDismiss = closeChild,
                        maxWidth = 672.dp,
                    ) {
                        AppPreferencesPanel(
                            ap = settingsState.appPreferences,
                            providers = settingsState.providers,
                            agents = settingsState.agents,
                            onSaveAp = { scope.launch { settingsViewModel.saveAppPreferences(it) } },
                        )
                    }

                    SettingsDestination.DISPLAY -> FeatureDialog(
                        title = "文本设置",
                        subtitle = "消息样式、界面字体与显示指标",
                        icon = TablerIcons.Message,
                        onDismiss = closeChild,
                        maxWidth = 576.dp,
                        // 底部保存分区（用户 2026-10-12）：保存 / 恢复默认常驻，正文上滚。
                        footer = {
                            DisplaySettingsFooter(
                                ds = settingsState.displaySettings,
                                onSave = closeChild,
                                onSaveDs = { scope.launch { settingsViewModel.saveDisplaySettings(it) } },
                            )
                        },
                    ) {
                        DisplaySettingsPanel(
                            ds = settingsState.displaySettings,
                            ap = settingsState.appPreferences,
                            uiFont = UiFontFamily.fromStorage(settingsState.uiFontFamilyRaw),
                            onSaveDs = { scope.launch { settingsViewModel.saveDisplaySettings(it) } },
                            onSaveAp = { scope.launch { settingsViewModel.saveAppPreferences(it) } },
                            onSetUiFont = { scope.launch { settingsViewModel.setUiFontFamily(it) } },
                        )
                    }

                    SettingsDestination.THEME -> ThemeDialog(
                        palette = settingsState.themePalette,
                        customThemes = settingsState.customThemes,
                        onSelectTheme = { scope.launch { settingsViewModel.setThemePalette(it) } },
                        onSaveCustomTheme = { scope.launch { settingsViewModel.saveCustomTheme(it) } },
                        onDeleteCustomTheme = { scope.launch { settingsViewModel.deleteCustomTheme(it) } },
                        onDismiss = closeChild,
                    )

                    SettingsDestination.PROMPT -> PromptLibraryDialog(
                        prompts = settingsState.prompts,
                        onSavePrompt = { scope.launch { settingsViewModel.savePrompt(it) } },
                        onDeletePrompt = { scope.launch { settingsViewModel.deletePrompt(it) } },
                        onDismiss = closeChild,
                    )

                    SettingsDestination.BACKUP -> BackupSyncDialog(
                        config = settingsState.webDavConfig,
                        webDavNotice = settingsState.webDavNotice,
                        busy = settingsState.webDavBusy,
                        importNotice = settingsState.importNotice,
                        onSaveConfig = { scope.launch { settingsViewModel.saveWebDavConfig(it) } },
                        onTestConnection = { scope.launch { settingsViewModel.testWebDav() } },
                        onPush = { scope.launch { settingsViewModel.pushBackupToWebDav() } },
                        onPull = { scope.launch { settingsViewModel.pullBackupFromWebDav() } },
                        onSaveConfigApplied = { scope.launch { settingsViewModel.markWebDavConfigSaved() } },
                        onExport = { includeApiKeys ->
                            // 参考版 `handleExportData`（App.tsx:1854-1863）：直接落一个 JSON 文件，
                            // 文件名 `watchworld-backup-YYYY-MM-DD.json`（UTC 日期）。
                            // 存档 JSON 要读库，只能异步生成；`scope` 在桌面端就跑在 UI 线程上，
                            // 因此系统保存框仍是在 UI 线程里弹的（同 `SessionActionsPanel` 的做法）。
                            scope.launch {
                                val json = settingsViewModel.exportData(includeApiKeys)
                                saveTextFile(backupFileName(currentTimeMillis()), json)
                            }
                        },
                        onImport = { json -> scope.launch { settingsViewModel.importData(json) } },
                        onDismiss = closeChild,
                    )
                }
            }

            // ── 窗口功能底部弹层（T24）──
            // 齿轮打开它；它带来的子功能一律叠在它之上，关闭后**恢复本弹层**（需求 §三 第 4 条）。
            if (windowFunctionOpen) {
                WindowFunctionSheet(
                    onDismiss = { windowFunctionOpen = false },
                    onOpenSummary = { windowChild = WindowFunctionDestination.Summary },
                    onOpenKnowledge = { windowChild = WindowFunctionDestination.Knowledge },
                    onOpenSearch = { windowChild = WindowFunctionDestination.Search },
                    onOpenWindowWorld = { windowChild = WindowFunctionDestination.WindowWorld },
                    onAnalyzeRole = {
                        // 参考项目在触发后直接收起弹层（`WindowFunctionPanel.tsx:159`）。
                        windowFunctionOpen = false
                        scope.launch { chatViewModel.analyzeRoleRelationships() }
                    },
                    onOpenWindowApi = { windowChild = WindowFunctionDestination.WindowApi },
                    onOpenSessionActions = { windowChild = WindowFunctionDestination.SessionActions },
                    onOpenWindowRegex = { windowChild = WindowFunctionDestination.WindowRegex },
                    onOpenAvatarSettings = { windowChild = WindowFunctionDestination.AvatarSettings },
                )
            }
            when (val destination = windowChild) {
                // 前文总结自带 FeatureDialog 外壳：底栏的「总结前文」要用到面板内的提示词/模型状态，
                // 外壳跟着面板走，状态才不用提到这里（参考版 `SummaryModal` 也是自闭的）。
                WindowFunctionDestination.Summary -> {
                    // 模型清单与回退链同 `ChatViewModel.resolveProvider`：会话指定的 Provider 优先，否则第一个。
                    val summaryProvider = settingsState.providers
                        .firstOrNull { it.id == chatState.session?.providerId }
                        ?: settingsState.providers.firstOrNull()
                    SummaryPanel(
                        session = chatState.session,
                        summarizing = chatState.summarizing,
                        summaryError = chatState.summaryError,
                        // 参考版 `App.tsx:2616-2617`：可用模型取当前 Provider，当前模型取窗口模型再回退 Provider 默认。
                        models = summaryProvider?.models.orEmpty(),
                        currentModel = chatState.session?.model?.takeIf { it.isNotBlank() }
                            ?: summaryProvider?.defaultModel.orEmpty(),
                        onDismiss = { windowChild = null },
                        onSaveConfig = { prompt, model ->
                            scope.launch { chatViewModel.saveSummaryConfig(prompt, model) }
                        },
                        onGenerate = { prompt, model ->
                            scope.launch { chatViewModel.generateSummary(prompt, model) }
                        },
                        onDeleteSummary = { id -> scope.launch { chatViewModel.deleteSummary(id) } },
                        onClearSummaries = { scope.launch { chatViewModel.clearSummaries() } },
                    )
                }

                WindowFunctionDestination.DualBoxPrompt -> {
                    val dualSession = chatState.session
                    if (dualSession != null) {
                        DualBoxPromptDialog(
                            session = dualSession,
                            prompts = settingsState.prompts,
                            onSave = { fixed, common, pushed ->
                                scope.launch { chatViewModel.saveDualBoxPrompt(fixed, common, pushed) }
                                windowChild = null
                            },
                            onPushCommon = { scope.launch { chatViewModel.pushCommonMessages() } },
                            onRevertCommon = { scope.launch { chatViewModel.revertCommonMessages() } },
                            onDismiss = { windowChild = null },
                        )
                    }
                }

                // ── 本窗口世界观（与侧边栏「世界观」库共用 WorldPanel，靠 session 参数切到窗口形态）──
                WindowFunctionDestination.WindowWorld -> {
                    LaunchedEffect(Unit) { worldViewModel.load() }
                    // 缺省（没保存过窗口选择）按「全部 enabled 文档」算 —— 与
                    // `selectSessionWorldDocuments`（`WorldLibrary.kt:118`）实际注入的集合一致。
                    // ⚠️ 参考版调用点是 `worldDocuments.map { it.id }`（全部文档，不看 enabled），
                    //    与它自己的领域语义不一致；这里按**领域语义**走，免得界面把模型收不到的
                    //    文档显示成「已注入」。
                    val defaultWorldDocIds = worldState.documents
                        .filter { it.enabled == true }
                        .map { it.id }
                    FeatureDialog(
                        title = "本窗口世界观",
                        subtitle = "仅影响当前窗口",
                        icon = TablerIcons.Globe,
                        onDismiss = { windowChild = null },
                        maxWidth = 896.dp,
                        heightFraction = 0.92f,
                        // 与「世界观库」同款：面板内部自己管「左树 + 右详情」两个滚动区，
                        // 所以这里必须钉死高度、关掉外壳滚动（否则 `weight(1f)` 拿不到有界高度）。
                        scrollable = false,
                        fixedHeight = true,
                    ) {
                        WorldPanel(
                            state = worldState,
                            onCreateGroup = { name, parentId -> scope.launch { worldViewModel.createGroup(name, parentId) } },
                            onRenameGroup = { id, name -> scope.launch { worldViewModel.renameGroup(id, name) } },
                            onSetGroupParent = { id, parentId -> scope.launch { worldViewModel.setGroupParent(id, parentId) } },
                            onDeleteGroup = { id -> scope.launch { worldViewModel.deleteGroup(id) } },
                            onToggleCollapsed = worldViewModel::toggleCollapsed,
                            onSelectDocument = worldViewModel::select,
                            onSetQuery = worldViewModel::setQuery,
                            onCreateDocument = { title, groupId, content ->
                                scope.launch { worldViewModel.createDocument(title, groupId, content) }
                            },
                            onSaveDocument = { doc -> scope.launch { worldViewModel.saveDocument(doc) } },
                            onDeleteDocument = { id -> scope.launch { worldViewModel.deleteDocument(id) } },
                            onMoveDocumentToGroup = { id, groupId ->
                                scope.launch { worldViewModel.moveDocumentToGroup(id, groupId) }
                            },
                            // 窗口形态：开关只写 `session.connectedWorldDocIds`，
                            // 绝不动文档/分组的全局状态（要改资料得走「编辑/新建/删除」）。
                            sessionDocIds = chatState.session?.connectedWorldDocIds ?: defaultWorldDocIds,
                            onToggleForSession = { docId ->
                                scope.launch {
                                    chatViewModel.toggleWindowWorldDoc(docId, defaultWorldDocIds)
                                }
                            },
                            onSetSessionDocs = { ids ->
                                scope.launch { chatViewModel.setWindowWorldDocs(ids) }
                            },
                        )
                    }
                }

                // ── 本窗口知识库（与侧边栏「知识库」共用 KnowledgePanel，靠 session 参数切到窗口形态）──
                WindowFunctionDestination.Knowledge -> {
                    FeatureDialog(
                        title = "本窗口知识库",
                        subtitle = "勾选即可连接至当前会话，AI 自动在提问时参考该知识",
                        icon = TablerIcons.Book,
                        onDismiss = { windowChild = null },
                        maxWidth = 768.dp,
                        heightFraction = 0.92f,
                        fixedHeight = true,
                    ) {
                        KnowledgePanel(
                            items = settingsState.knowledgeBase,
                            // 窗口形态：连接只写 `session.connectedKnowledgeIds`，
                            // 绝不动资料的全局状态（要改内容得走「编辑/新建/删除」）。
                            sessionIds = chatState.session?.connectedKnowledgeIds.orEmpty(),
                            onToggleForSession = { id -> scope.launch { chatViewModel.toggleKnowledgeConnected(id) } },
                            onSave = { scope.launch { settingsViewModel.saveKnowledgeItem(it) } },
                            onDelete = { scope.launch { settingsViewModel.deleteKnowledgeItem(it) } },
                        )
                    }
                }

                // ── 本窗口正则（与侧边栏「正则库」共用 RegexPanel，靠 session 参数切到窗口形态）──
                WindowFunctionDestination.WindowRegex -> {
                    val enabledRuleIds = settingsState.regexRules.filter { it.enabled }.map { it.id }
                    FeatureDialog(
                        title = "本窗口正则",
                        subtitle = "开头的开关即「本窗口是否生效」；分组标题上的开关作用于整组",
                        icon = TablerIcons.Code,
                        onDismiss = { windowChild = null },
                        maxWidth = 576.dp,
                        heightFraction = 0.92f,
                    ) {
                        RegexPanel(
                            rules = settingsState.regexRules,
                            onSave = { scope.launch { settingsViewModel.saveRegexRule(it) } },
                            onDelete = { scope.launch { settingsViewModel.deleteRegexRule(it) } },
                            onToggle = { scope.launch { settingsViewModel.toggleRegexRule(it) } },
                            // 参考版 `App.tsx:2362-2365`：未配置窗口级列表时按「全部已启用」显示，
                            // 第一次拨开关就落成显式列表（`ChatViewModel.toggleWindowRegexRule` 同语义）。
                            sessionRuleIds = chatState.session?.regexRuleIds ?: enabledRuleIds,
                            onToggleForSession = { ruleId ->
                                scope.launch { chatViewModel.toggleWindowRegexRule(ruleId, enabledRuleIds) }
                            },
                            onSetSessionRules = { ids ->
                                scope.launch { chatViewModel.setWindowRegexRules(ids) }
                            },
                        )
                    }
                }

                // ── 全文搜索 ──
                WindowFunctionDestination.Search -> FeatureDialog(
                    title = "全文搜索",
                    onDismiss = { windowChild = null },
                    maxWidth = 512.dp,
                    heightFraction = 0.85f,
                ) {
                    SearchPanel(
                        messages = chatState.session?.messages.orEmpty(),
                        timelines = chatState.session?.timelines.orEmpty(),
                        onJumpToMessage = { _ ->
                            // TODO: 接入 ChatScreen 的 scrollToMessage 能力
                        },
                        onDismiss = { windowChild = null },
                    )
                }

                // ── 窗口 API 参数 ──
                WindowFunctionDestination.WindowApi -> {
                    val apiSession = chatState.session
                    if (apiSession != null) {
                        val currentAgent = settingsState.agents.firstOrNull { it.id == apiSession.agentId }
                        val currentProvider = settingsState.providers.firstOrNull { it.id == apiSession.providerId }
                        FeatureDialog(
                            title = "窗口 API 参数",
                            subtitle = "仅影响当前窗口，不修改全局 API 配置",
                            icon = TablerIcons.Adjustments,
                            onDismiss = { windowChild = null },
                            maxWidth = 512.dp,
                        ) {
                            WindowApiParamsPanel(
                                session = apiSession,
                                providerName = currentProvider?.name.orEmpty(),
                                modelName = apiSession.model ?: currentProvider?.defaultModel.orEmpty(),
                                // 继承值 = 非窗口段的同一回退链（`ChatRequestAssembler.kt:72` /
                                // 参考版 `api.ts:393`）：全局默认 → 顾问 → 内置兜底。
                                inheritedTemperature = settingsState.appPreferences.defaultSession.temperature
                                    ?: currentAgent?.temperature ?: 0.7,
                                inheritedTopP = settingsState.appPreferences.defaultSession.topP
                                    ?: currentAgent?.topP ?: 0.95,
                                inheritedToolCallStepLimit = settingsState.appPreferences.defaultSession.toolCallStepLimit ?: 0,
                                onSave = { temp, tp, tk, fp, pp, mt, tl, im, se ->
                                    scope.launch {
                                        chatViewModel.saveWindowApiParams(
                                            temp, tp, tk, fp, pp, mt, tl, im, se,
                                        )
                                    }
                                },
                                onDismiss = { windowChild = null },
                            )
                        }
                    }
                }

                // ── 窗口导出删除 ──
                WindowFunctionDestination.SessionActions -> {
                    val actionsSession = chatState.session
                    if (actionsSession != null) {
                        FeatureDialog(
                            title = "窗口导出删除",
                            onDismiss = { windowChild = null },
                            maxWidth = 672.dp,
                        ) {
                            SessionActionsPanel(
                                session = actionsSession,
                                onDeleteSession = {
                                    scope.launch {
                                        sessionViewModel.delete(actionsSession.id)
                                        chatViewModel.open(sessionViewModel.state.value.activeSession)
                                    }
                                    windowChild = null
                                    windowFunctionOpen = false
                                },
                                onDismiss = { windowChild = null },
                            )
                        }
                    }
                }

                // ── 头像设置 ──
                WindowFunctionDestination.AvatarSettings -> {
                    val avatarSession = chatState.session
                    if (avatarSession != null) {
                        AvatarSettingsDialog(
                            sessionId = avatarSession.id,
                            currentUserAvatarFileName = avatarSession.userAvatarFileName,
                            currentAiAvatarFileName = avatarSession.aiAvatarFileName,
                            currentAvatarSize = settingsState.displaySettings.avatarSize,
                            currentShowAvatars = settingsState.displaySettings.showAvatars,
                            currentBubbleSpacing = settingsState.displaySettings.bubbleSpacing,
                            previewToken = avatarRevision,
                            // ⚠️ 窗口用户头像走 `SessionUser`（`sessions/<id>/user/`），**不能**和
                            //    AI 头像共用 `Session`：`AvatarStore.save` 会清掉目标目录里的上一张，
                            //    同目录会导致两个头像互相删掉（用户 2026-10-13 报）。
                            onUploadUserAvatar = {
                                val picked = pickImageFiles().firstOrNull()
                                if (picked != null) {
                                    scope.launch {
                                        val name = withContext(Dispatchers.IO) {
                                            avatarStore.save(
                                                AvatarOwner.SessionUser,
                                                avatarSession.id,
                                                picked.bytes,
                                                picked.mime,
                                            )
                                        }
                                        avatarRevision++
                                        chatViewModel.setSessionUserAvatar(name)
                                    }
                                }
                            },
                            onRemoveUserAvatar = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        avatarStore.remove(AvatarOwner.SessionUser, avatarSession.id)
                                    }
                                    avatarRevision++
                                    chatViewModel.setSessionUserAvatar(null)
                                }
                            },
                            onUploadAiAvatar = {
                                // 文件框是模态的：必须在点击回调（UI 线程）里同步挑完，再交给协程落盘。
                                val picked = pickImageFiles().firstOrNull()
                                if (picked != null) {
                                    scope.launch {
                                        val name = withContext(Dispatchers.IO) {
                                            avatarStore.save(
                                                AvatarOwner.Session,
                                                avatarSession.id,
                                                picked.bytes,
                                                picked.mime,
                                            )
                                        }
                                        avatarRevision++
                                        chatViewModel.setSessionAiAvatar(name)
                                    }
                                }
                            },
                            onRemoveAiAvatar = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        avatarStore.remove(AvatarOwner.Session, avatarSession.id)
                                    }
                                    avatarRevision++
                                    chatViewModel.setSessionAiAvatar(null)
                                }
                            },
                            onSave = { size, showAvatars, bubbleSpacing ->
                                scope.launch {
                                    settingsViewModel.saveDisplaySettings(
                                        settingsState.displaySettings.copy(
                                            avatarSize = size,
                                            showAvatars = showAvatars,
                                            bubbleSpacing = bubbleSpacing,
                                        )
                                    )
                                }
                                windowChild = null
                            },
                            onDismiss = { windowChild = null },
                        )
                    }
                }

                null -> Unit
            }

            // ── 输入区前置入口 ──
            if (windowVisibilityOpen) {
                val session = chatState.session
                AiVisibilityDialog(
                    currentMode = session?.aiContextVisibility
                        ?: com.watchworld.model.AiContextVisibilityFilter.ALL,
                    customLimit = session?.aiContextCustomLimit ?: 3,
                    uiRenderLimit = session?.uiRenderLimit ?: 0,
                    onChangeMode = { mode ->
                        scope.launch { chatViewModel.setAiContextMode(mode) }
                    },
                    onChangeCustomLimit = { limit ->
                        scope.launch { chatViewModel.setAiContextCustomLimit(limit) }
                    },
                    onChangeUiRenderLimit = { limit ->
                        scope.launch { chatViewModel.setUiRenderLimit(limit) }
                    },
                    onDismiss = { windowVisibilityOpen = false },
                )
            }
            // ── 完整模型选择器（参考版 `ModelSelectorModal`）：跨供应商搜索 + 只看收藏 ──
            if (modelSelectorOpen) {
                ModelSelectorDialog(
                    providers = settingsState.providers,
                    activeProviderId = chatState.session?.providerId.orEmpty(),
                    activeModel = chatState.session?.model?.takeIf { it.isNotBlank() }
                        ?: settingsState.providers
                            .firstOrNull { it.id == chatState.session?.providerId }
                            ?.defaultModel
                            .orEmpty(),
                    favorites = settingsState.modelFavorites,
                    onSelectModel = { providerId, model ->
                        scope.launch { chatViewModel.selectProviderAndModel(providerId, model) }
                    },
                    onToggleFavorite = { providerId, modelName ->
                        scope.launch { settingsViewModel.toggleModelFavorite(providerId, modelName) }
                    },
                    onDismiss = { modelSelectorOpen = false },
                )
            }
            // ── 设置：FeatureDialog 壳子 + SettingsHub 卡片列表 ──
            if (settingsOpen) {
                FeatureDialog(
                    title = "设置",
                    subtitle = "集中管理显示、主题、导入导出与全局规则",
                    icon = TablerIcons.Settings,
                    onDismiss = { settingsOpen = false },
                    maxWidth = 512.dp,
                    heightFraction = 0.92f,
                    // 功能框外面那圈框：功能框 + 4（用户 2026-10-11 定）。
                    contentPaddingVertical = WwPad.featureFrame,
                ) {
                    SettingsHub(
                        state = settingsState,
                        // 参考版 `SettingsHubModal.tsx:43-47`：先关设置总入口，再开目标弹窗
                        //（注释原文「避免多层级弹窗叠压」）；关闭目标弹窗后由 `closeWithReturn`
                        // 重新打开设置总入口（`App.tsx:280-287`）。
                        onOpen = { destination ->
                            settingsChildReturnsToSettings = true
                            settingsOpen = false
                            settingsChild = destination
                        },
                    )
                }
            }
        }
    }
}


/**
 * 窗口功能底部弹层的子页（T24）。
 *
 * ⚠️ 用一个枚举表达子页，而不是「每个子页一个 Boolean」：关闭子弹窗时必须知道
 *    **回到哪一层**（需求 §三 第 4 条）。一堆互不相干的布尔一旦同时为真，
 *    「关掉这个该回哪儿」就没有唯一答案了。
 */
sealed interface WindowFunctionDestination {
    /** 前文总结：挂载现有的 `SummaryPanel`（应用内唯一入口）。 */
    data object Summary : WindowFunctionDestination
    /** 双框提示词。⚠️ **不能**接提示词库弹窗（`PromptLibraryDialog`）—— 那是库，不是窗口级双框。 */
    data object DualBoxPrompt : WindowFunctionDestination
    /** 全文搜索。 */
    data object Search : WindowFunctionDestination
    /** 本窗口世界观。⚠️ **不能**接侧边栏的全局 `WorldPanel`。 */
    data object WindowWorld : WindowFunctionDestination
    /** 本窗口知识库（窗口形态的 `KnowledgePanel`；T27 新增入口）。 */
    data object Knowledge : WindowFunctionDestination
    /** 窗口 API 参数。 */
    data object WindowApi : WindowFunctionDestination
    /** 窗口导出删除。 */
    data object SessionActions : WindowFunctionDestination
    /** 本窗口正则。⚠️ **不能**接全局正则库。 */
    data object WindowRegex : WindowFunctionDestination
    /** 头像设置：上传 AI 头像、调节头像大小。 */
    data object AvatarSettings : WindowFunctionDestination
}

/**
 * 时间线管理器的子层（T24）：编辑器与 AI 说明都叠在管理器之上，关闭后回到管理器。
 *
 * @param parentId 新建时的上级分支（`null` = 用当前聚焦分支）。
 * @param timelineId 编辑的目标分支；`null` = 新建。
 */
sealed interface TimelineDestination {
    data class Editor(val parentId: String? = null, val timelineId: String? = null) : TimelineDestination
    data object Guide : TimelineDestination
}
