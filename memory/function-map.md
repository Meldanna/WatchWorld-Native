# 函数地图（function-map.md）

> **用途**：要改某个功能时，先在这里定位「它由哪些函数实现、在哪个文件第几行」。
> **位置基准**：`E:\WatchWorld\WatchWorld\`（现有 React 版 = 规格书），行号采集于 2026-10-06。
> ⚠️ 行号会随文档冻结而保持有效；若 React 版被改动，以符号名搜索为准。
> **Kotlin 目标列**：迁移时逐格补上，不要等全做完。

## 怎么用

1. 想改「流式解析」→ 查 §C `nativeToolChat.ts`
2. 想改「存档字段」→ 查 §B `storage.ts`
3. 想改「某设置界面」→ 查 §I，再点进对应组件的 handler（§I 末）

---

## §A 数据模型

`src/types.ts`（24 个 interface/type）— 迁移目标 `shared/.../model/*.kt`

| 类型 | 行 | 说明 | Kotlin |
|---|---|---|---|
| `ApiProviderType` / `ProviderType` | 2 / 12 | provider 枚举 | `model/Enums.kt` |
| `ApiProviderConfig` | 14 | Provider 配置（含 apiKey/baseUrl/默认采样参数） | `model/Provider.kt` |
| `ModelFavorite` | 36 | 模型收藏 | `model/Provider.kt` |
| `Agent` | 43 | 智能体 | `model/Agent.kt` |
| `PromptPreset` | 67 | 提示词预设 | `model/Agent.kt` |
| `MessageVersion` | 77 | 消息版本（滚动重答） | `model/Message.kt` |
| `TimelineBranch` | 89 | 时间线分支 | `model/Timeline.kt` |
| `TimelineDescriptionTagRequestStatus` | 107 | 审批状态 | `model/Enums.kt` |
| `TimelineDescriptionTagRequest` | 109 | 描述标签审批请求 | `model/Timeline.kt` |
| `ChatMessage` | 121 | **消息（19 字段）** | `model/Message.kt` |
| `MessageDisplaySettings` | 143 | 显示设置 | `model/Settings.kt` |
| `AiContextVisibilityFilter` | 154 | 上下文可见性 | `model/Enums.kt` |
| `DefaultModelConfig` / `DefaultSessionConfig` | 160 / 168 | 默认模型/会话配置 | `model/Settings.kt` |
| `LaunchMode` | 187 | 启动模式 | `model/Enums.kt` |
| `AppPreferences` | 190 | 应用偏好 | `model/Settings.kt` |
| `ChatGroup` | 207 | 会话分组 | `model/Content.kt` |
| `RegexRule` | 216 | 正则规则 | `model/Content.kt` |
| `KnowledgeItem` | 230 | 知识库条目 | `model/Content.kt` |
| `McpTool` / `McpTransportType` / `McpServerConfig` | 240 / 246 / 248 | MCP | `model/Content.kt` + `model/Enums.kt` |
| `AgentSkill` | 270 | 技能 | `model/Content.kt` |
| `BuiltinThemePalette` / `ThemePalette` | 284 / 300 | 主题 | `model/Enums.kt`（`ThemePalette` = `String` typealias） |
| `CustomTheme` | 303 | 自定义主题 | `model/Content.kt` |
| `UiMode` | 314 | 明暗 | `model/Enums.kt` |
| `WebDavConfig` | 317 | WebDAV 配置 | `model/Content.kt` |
| `WorldGroup` / `WorldDocument` | 336 / 343 | 世界书 | `model/Content.kt` |
| **`ChatSession`** | **357** | **★ 上帝对象（~64 字段）** | **`model/Session.kt`** ★ 扁平形状 = 存档契约 |
| `SessionSummary` | 423 | 前文总结 | `model/Timeline.kt` |

> **T05 已落地**：以上 24 个类型已全部迁移到 `shared/src/commonMain/kotlin/com/watchworld/model/`，
> 由 `ArchiveV5ModelTest`（`shared/src/desktopTest/`）用 `fixtures/sse/archive-v5-{legacy,minimal}.json` 守住。
> ⚠️ 老存档的 6 条兼容逻辑**尚未实现**（归 T06）；`ChatSession` 在模型层保持扁平（= v5 存档契约），
> 拆 `messages`/`timelines`/`summaries` 由 T07 在 `data/` 层做。详见 [`../tasks/T05-数据模型.md`](../tasks/T05-数据模型.md)。

---

## §B 存储与存档

### `src/lib/storage.ts`（410 行）→ `data/repository/*` + `data/migration/`

| 函数 / 成员 | 行 | 职责 | Kotlin |
|---|---|---|---|
| `STORAGE_KEYS` | 38–60 | ★ 21 个存储键（`omnichat_*` / `guanjie_*`）**前缀不可改** | ✅ T07 `data/StorageKeys.kt`（键 → 表 / `kv` 落点） |
| `safeGet<T>` | 62 | 带兜底的读 | ✅ T07 `SettingsRepository`（读不抛错、没存过给默认值） |
| `safeSet<T>` | 73 | 带 try/catch 的写 | ✅ T07 Repository 的 `suspend` 写（事务内） |
| **`normalizeChatSession`** | **86** | ★★ **老存档兼容的 6 条逻辑，一条不能漏** | ✅ T06 `migration/SessionNormalizer.kt`：模型层 `normalizeChatSession`（②③④⑤）+ 解码前 `sanitizeSessionJson`（①⑥） |
| `normalizeMcpServer` | 114 | MCP 配置归一化（`endpoint`→`url`、`custom_api`/`stdio` 丢弃） | ✅ T06 `migration/McpServerNormalizer.kt` `normalizeMcpServerJson`（**必须在解码前做**） |
| `Storage` | 146 | 40+ 个 get/set（`getProviders`:147 `setSessions`:209 `getAppPreferences`:306 `getWorldDocuments`:360 `getModelFavorites`:387 …） | ✅ T07 `ConfigRepository` / `SessionRepository` / `SettingsRepository`（见 §N5） |
| `Storage.exportAllData` | **417** | ★★ 导出 v5 JSON；`redactSecrets` 时 apiKey/password → `''` | ✅ T06 `ArchiveRepository.exportArchive` / `redactSecrets` |
| `Storage.importAllData` | **453** | ★★ 导入兼容分支（version 判断） | ✅ T06 `ArchiveRepository.parseArchive` / `tryImportArchive`（归一化放在**入口**而非 read） |
| （无直接对应物） | — | 导出用的「顶层键铺齐 + 2 空格缩进」 | ✅ T06 `ArchiveRepository.exportJson` / `defaultsJson` |
| `getWorldDocuments` 的 `group`→`groupId` 迁移 | 365–373 | 旧数据「组名字符串 → 组 ID」 | ✅ T18 `domain/WorldLibrary.kt` `resolveWorldDocumentGroupIds`（在 `ConfigRepository.worldDocuments()` 里跑；老 `group` 经 Mapper 落库为 `legacyGroup` 才有得迁） |
| `getAppPreferences` 的 `defaultSession` 合并 | 317–338 | 非有限 `toolCallStepLimit` 兜底成 `0` | ⏳ T15 |

### 其它存储相关

| 文件 | 关键函数（行） | 职责 | Kotlin |
|---|---|---|---|
| `src/lib/imageStore.ts` | `openDb`:26 `runTx`:46 `dataUrlBytes`:62 `readAsDataURL`:68 `loadImage`:77 `compressImageFile`:90 `saveImageFile`:144 `ImageStore`:160 | IndexedDB 图片存储（**需换实现**） | `platform/ImageStore.kt` |
| `src/lib/appFolders.ts` | `FOLDER_LAYOUT`:22 `idb`:29 `idbGet`:47 `idbSet`:57 `idbDel`:67 `hasFsApi`:89 `isNativePlatform`:93 `getFolderCapability`:100 `pickRootDirectory`:118 `getStoredRootDirectory`:125 `clearRootDirectory`:133 `ensurePermission`:137 `ensureStructure`:149 `writeTextFile`:157 `exportAllToFolders`:188 `listStructure`:247 | 应用目录管理 ★桌面需要 | `platform/FileSystem.kt` |
| `src/lib/syncEngine.ts` | `createSyncState`:28 `isSyncConfigured`:32 `imageIdsOf`:36 `pushMissingImages`:40 `pushChanges`:59 `pullChanges`:101 | 增量同步引擎 | ✅ T19 `data/repository/SyncEngine.kt`（会话级；⚠️ `imageIdsOf`/`pushMissingImages` 及拉图循环归 T21） |
| `src/lib/configTextImporter.ts` | `normalizeText`:3 `stripMarkdown`:7 `valueOf`:15 `listOf`:30 `fileBaseName`:39 `parseSkillText`:43 `parseMcpText`:78 | 配置文本导入 | `domain/ConfigImporter.kt` |
| `src/lib/defaultData.ts` | `DEFAULT_DISPLAY_SETTINGS`:18 `DEFAULT_GROUPS`:28 `DEFAULT_PROVIDERS`:36 `DEFAULT_AGENTS`:84 `DEFAULT_PROMPTS`:185 `DEFAULT_APP_PREFERENCES`:198 `DEFAULT_SUMMARY_PROMPT`:211 `DEFAULT_REGEX_RULES`:221 `DEFAULT_KNOWLEDGE_BASE`:247 `DEFAULT_MCP_SERVERS`:251 `DEFAULT_SKILLS`:253 `DEFAULT_WEBDAV_CONFIG`:316 `DEFAULT_WORLD_GROUPS`:327 `DEFAULT_WORLD_DOCUMENTS`:336 `INITIAL_SESSION`:378 | 默认数据 | `data/DefaultData.kt` |

---

## §C 流式与协议（★ 迁移技术核心）

### `src/lib/nativeToolChat.ts`（480 行）→ `net/ChatStreamClient.kt` + `SseParser.kt` + `ToolCallLoop.kt`

| 函数 | 行 | 职责 | Kotlin | 备注 |
|---|---|---|---|---|
| `replayAsStream` | 22 | 回放伪装流式 | ❌ 废弃 | **迁移期间不要删**（设计好的降级路径） |
| **`sseLine`** | **73** | ★★ SSE 行解析状态机：空行派发 / `data:` 累积 / `[DONE]` / `message_stop` / 错误帧抛异常 | `net/SseParser.kt` | CONSTRAINTS §3.1 语义 |
| `readNativeEvents` | 96 | 从 Response 读事件 | 同上 | |
| `toPseudoEvents` | 128 | 非流式 → 伪事件 | ❌ 废弃 | |
| `emitPlainReply` | 176 | 纯文本回复发射 | | |
| **`sendNativeToolChat`** | **194** | ★★ 工具循环总入口 | ✅ T09 `net/ToolCallLoop.kt`（`runToolChat`） | |
| └ 工具名注册段 | 200–214 | 原始名优先，重名加 `_2/_3`，记 `originalName` | ✅ T09 `registerTools` | ⚠️ 必须与 `api.ts` 的 `mcpContext` 一致 |
| └ 组装 `messages`/`tools` | 241 | base 拼接、默认参数 | | |
| └ `append` / `note` / `pushReasoning` | 282–291 | 正文/提示/思维链三路输出 | | |
| └ `receive`（三家归一化） | 354–395 | ★ OpenAI / Claude / Gemini 事件 → 统一事件 | ✅ T09 `ToolCallLoop.kt` `Round.receive`（事件归一化复用 T04 adapter） | CONSTRAINTS §3.4 |

### `src/lib/api.ts`（520 行）→ `domain/ContextBuilder.kt` + `net/provider/*`

| 函数 | 行 | 职责 | Kotlin |
|---|---|---|---|
| **`sendChatMessage`** | **77** | ★★ 请求组装 + 上下文拼装总入口 | ✅ T08 `domain/ContextBuilder.kt`（拼装段 :154-373）；✅ T14 `domain/ChatRequestAssembler.kt`（采样回退链 + `ToolChatParams` 装配） |
| └ `messages.forEach` 过滤段 | 120 | `aiContextVisibility` 过滤历史 | |
| └ `filteredMessagesForAi.filter` | **145** | ★★ `pushedCommonFloors` 过滤（正则 `\[来自\s+#(\d+)\]`） | |
| └ MCP 能力清单拼装 | 189–214 | `mcpContext`，用**原始工具名** | ⚠️ 与工具注册同步 |
| └ `isCommonMessage` 三重判定 | 147–150 | `isCommon==true \|\| codeTag=="通用" \|\| timelineId=="timeline-main"` | |
| └ system 段落标题 | — | `# 【世界观·正典设定】` 等，**逐字一致** | |
| └ 文本格式化 | 281 | 消息 → 文本 | |
| └ 图片按模式保留 | 316 | `imageContextMode` | |
| `fetchModelsFromProvider` | 446 | 拉模型列表 | `net/ProviderApi.kt` |
| `testProviderConnection` | 488 | 连通性测试 | |
| `analyzeRoleRelationships` | 542 | 心理动力学角色关系分析 | ✅ T20 `domain/RoleAnalysis.kt` + `ui/chat/ChatViewModel.analyzeRoleRelationships` |

### 废弃模块（迁移期保留）

| 文件 | 函数（行） | 说明 |
|---|---|---|
| `src/lib/apiNative.ts` | `sendChatNative`:35 `callGeminiNative`:40 `callClaudeNative`:132 `callOpenAICompatibleNative`:231 `fetchModelsNative`:360 | Capacitor 专用 → ❌ 废弃 |
| `src/lib/apiBase.ts` | `normalizeBase`:26 `loadRuntimeApiBase`:31 `setRuntimeApiBase`:40 `getRuntimeApiBase`:51 `getApiBase`:56 `resolveApiUrl`:67 `apiAuthHeaders`:80 | Worker 中转地址解析 → 可降级为搜索回退开关 |

---

## §D 工具与 MCP

| 文件 | 函数（行） | 职责 | Kotlin |
|---|---|---|---|
| `src/lib/mcpApi.ts` | `getMcpRequestInit`:21 `callMcpToolNative`:29 `explainNativeMcpError`:58 `testMcpConnectionNative`:83 `testMcpConnection`:142 | MCP 调用与探测 | ✅ T10 `net/McpClient.kt`（从零实现） |
| `src/lib/mcpAvailability.ts` | `LOAD_SKILL_TOOL_NAME`:8 `isMcpAvailableToModel`:11 `getAvailableMcpToolNames`:15 `getMissingSkillTools`:24 `isSkillAvailableToModel`:31 `formatSkillManifest`:46 | 可用性判定 + Skill 清单 | ✅ T08 `domain/McpAvailability.kt` |
| `server.ts` | `validateMcpHttpUrl`:274 `mcpRequestInit`:285 `connectMcpClient`:291 | MCP 服务端探测（参考实现） | ✅ T10 归入 `McpClient` |

⚠️ `LOAD_SKILL_TOOL_NAME`（值 `load_skill`）必须与注入 system 的名字一致，不得改成别的。

---

## §E 上下文 / 时间线 / 正则

| 文件 | 函数（行） | 职责 | Kotlin |
|---|---|---|---|
| `src/lib/timelineMemory.ts` | `TIMELINE_COLORS`:3 `getTimelineColorConfig`:69 `createDefaultMainTimeline`:76 `allocateTimelineCodeTag`:98 **`rearrangeMessagesForAi`:146** `formatMessageWithFloorAndTag`:226 `detectTimelineIntent`:264 `enrichTimelineProgression`:347 `buildTimelineTreePromptContext`:396 | 时间线树 + 消息重排 | ✅ T08 `domain/TimelineMemory.kt`（`rearrangeMessagesForAi` / `formatMessageWithFloorAndTag`）；✅ T17 其余全部 |
| `src/lib/timelineDescriptionTagControl.ts` | 常量:7–11 `sanitizeText`:27 `stripTimelineDescriptionTagControl`:44 `parseTimelineDescriptionTagControl`:61 `createTimelineDescriptionTagRequest`:118 `approveTimelineDescriptionTagRequest`:168 `rejectTimelineDescriptionTagRequest`:220 | 描述标签审批流 | ✅ T17 `domain/TimelineTagControl.kt` |
| `src/lib/regexProcessor.ts` | `applyRegexRules`:3 `testRegexRule`:26 | 正则替换（渲染前作用于纯文本） | ✅ T08 `domain/RegexProcessor.kt`（`applyRegexRules`）；✅ T15 `testRegexRule` |
| `src/lib/tokenEstimator.ts` | `estimateTokens`:9 `formatTokenCount`:32 `formatLatency`:42 `formatTimestamp`:50 | 估算与格式化 | `domain/TokenEstimator.kt` |

---

## §F 联网搜索

| 文件 | 函数（行） | 职责 | Kotlin |
|---|---|---|---|
| `src/lib/webSearch.ts` | `decodeHtml`:21 `stripHtml`:27 `unwrapDuckDuckGoUrl`:31 `safeHostname`:43 **`parseDuckDuckGoHtml`:63** `normalizeResult`:105 `collectPayload`:117 `parseSearchResponse`:137 `isSkillSearchIntent`:173 `shouldUseWebSearch`:177 `fetchDuckDuckGoHtmlNative`:192 `searchWeb`:221 `searchWebViaProxy`:251 `formatSearchContext`:279 `normalizeSkillEntry`:333 `searchAgentSkills`:361 `parseSkillFrontmatter`:389 `previewSkillMd`:428 `formatSkillDirectoryContext`:478 | 搜索 + Skill 搜索 | ✅ T11 `net/WebSearchClient.kt`（只迁**原生直连**，代理不要） |
| `server.ts` | `decodeSearchHtml`:44 `stripSearchHtml`:55 `unwrapSearchUrl`:59 `parseDuckDuckGoResults`:70 `callSkillDirectoryRpc`:135 `normalizeSkillSearchItem`:175 | DDG HTML 解析（**纯字符串，1:1 直译**） | ✅ T11 归入 `WebSearchClient`（⚠️ `parseDuckDuckGoResults` 是**旧的有 bug 那版**，有意不采用） |

---

## §G WebDAV

`src/lib/webdavSync.ts`（259 行）→ ✅ T19 `net/WebDavClient.kt`

| 函数 | 行 | 职责 | Kotlin |
|---|---|---|---|
| `callWebDavProxy` | 14 | 经代理调用（原生版可去掉代理） | ✅ T19 直连（无代理） |
| `testWebDavConnection` | 43 | 连通性测试 | ✅ T19 `WebDavClient.testConnection` |
| `pullFromWebDav` | 87 | 拉取 | ✅ T19 `pullBackup` |
| `pushToWebDav` | 130 | 推送 | ✅ T19 `pushBackup`（成功文案去掉本地时间） |
| `emptyManifest` / `syncRoot` / `safeFileName` | 178 / 183 / 189 | manifest 与路径 | ✅ T19 `emptyManifest` / `syncRoot` / `safeFileName` |
| `putFile` / `getFile` | 193 / 209 | PUT / GET | ✅ T19（private） |
| `ensureCollections` | 220 | MKCOL | ✅ T19 `ensureCollections` |
| `fetchManifest` / `pushManifest` | 227 / 238 | manifest 读写 | ✅ T19 `fetchManifest` / `pushManifest` |
| `pushSessionFile` / `fetchSessionFile` | 248 / 260 | 会话文件 | ✅ T19 |
| `pushImageFile` / `fetchImageFile` | 270 / 282 | 图片文件 | ✅ T19 客户端已就位；⚠️ 调用点（ImageStore）归 T21 |

⚠️ 坚果云需用**应用密码**；`PROPFIND` 必须带 `Depth`（T19 测试连接用 `Depth: 0`，与 `webdavSync.ts` 一致）。
⚠️ 原生版**不走 `/api/webdav` 代理**：桌面/Android 是进程内应用、没有 CORS，直接对目标 URL 发 HTTP。

---

## §H 主题

| 文件 | 成员（行） | 职责 | Kotlin |
|---|---|---|---|
| `src/lib/theme.ts` | `SHADE_KEYS`:4 `makeTheme`:156 `buildThemes`:187 `THEMES`:205 `COLOR_SWATCHES`:211 `customThemeToConfig`:224 `resolveThemeById`:232 `hexToOklchChannels`:249 `buildScale`:268 **`resolveThemeVars`:284** | 主题解析（映射逻辑可直译） | `ui/theme/WwTheme.kt` |
| `src/index.css` | 288 行 | CSS 变量（**无对应物，需逐项映射**） | `ui/theme/WwColors.kt` |

⚠️ 工作量在"288 行 CSS 变量 → Compose 属性"的对应 + 视觉走查，光读代码做不完。

---

## §I UI 组件（34 个 tsx）

| 组件 | 行 | Compose 目标 | 难度 |
|---|---|---|---|
| `App.tsx` | 2369 | `ui/AppState.kt` + 多个 ViewModel | 🔴 87 hook 拆分 |
| `DocumentModal.tsx` | 980 | `DocumentScreen` | 🟠 |
| `TimelineModal.tsx` | 879 | `TimelineScreen` | 🔴 树形递归 |
| `ChatInput.tsx` | 760 | `ChatInputBar` | 🟠 |
| `ProviderModal.tsx` | 708 | `ProviderScreen` | 🟠 |
| `PreferencesModal.tsx` | 646 | `PreferencesScreen` | 🟢 |
| `RegexModal.tsx` | 619 | `RegexScreen` | 🟢 |
| `SkillModal.tsx` | 602 | `SkillScreen` | 🟠 |
| `MessageItem.tsx` | 559 | `MessageBubble` | 🔴 Markdown 流式 |
| `SidebarDrawer.tsx` | 555 | `SidebarSheet` | 🟢 |
| `WindowApiParamsModal.tsx` | 551 | `WindowParamsSheet` | 🟢 |
| `PromptModal.tsx` | 540 | `PromptScreen` | 🟢 |
| `AgentModal.tsx` | 538 | `AgentScreen` | 🟠 |
| `McpModal.tsx` | 474 | `McpScreen` | 🟠 |
| 其余 20 个 | — | — | 🟢 多数 |

其余组件清单（`src/components/`）：`AiVisibilityModal` `BackupSyncModal` `ChatMessageList` `ErrorBoundary` `GroupModal` `Header` `InfoHint` `KnowledgeBaseModal` `MarkdownRenderer` `MessageTimelineSelectorModal` `ModelSelectorModal` `PromptDualBoxModal` `PromptInjectionGuideModal` `SearchModal` `SessionTextModal` `SettingsHubModal` `SettingsModal` `SummaryModal` `ThemeModal` `TimelineLibraryModal` `WindowFunctionPanel`

### `src/App.tsx` 的 handler → ViewModel 归属

| handler（行） | 归属 |
|---|---|
| `handleSendMessage`:866 `handleStopGeneration`:1507 `handleRollMessage`:1187 `handleSwitchVersion`:1412 `handleEditContent`:1427 `handleDeleteMessage`:1498 | `ChatViewModel` |
| `buildSystemInstruction`:416 `dropSummarizedFloors`:465 `handleChangeAiContextVisibility`:1515 `handleChangeAiContextCustomLimit`:1524 `handleChangeUiRenderLimit`:1531 | `ContextViewModel` |
| `handleNewSession`:584 `handleRenameSession`:1538 `handleClearSession`:1544 `handleDeleteSession`:1550 `handleMoveSessionGroup`:1567 `handleImportSession`:624 | `SessionViewModel` |
| `handleGenerateSummary`:496 `handleSaveSummaryConfig`:476 `handleDeleteSummary`:480 `handleClearSummaries`:487 | `SummaryViewModel` |
| `handleApproveTimelineDescriptionTagRequest`:1375 `handleRejectTimelineDescriptionTagRequest`:1395 `handleChangeMessageTimeline`:1452 | `TimelineViewModel` |
| `handleSaveProvider`:1800 `handleDeleteProvider`:1812 `handleSaveAgent`:1749 `handleSavePrompt`:1765 `handleSaveRegexRule`:1703 `handleSaveKnowledgeItem`:1719 `handleSaveMcpServer`:1614 `handleSaveSkill`:1681 `handleSaveCustomTheme`:1782 `handleSaveGroup`:1816 | `SettingsViewModel` |
| `handleToggleConnectKnowledge`:1573 `handleToggleRegexForSession`:1583 `handleToggleWorldDocForSession`:1600 `handleSetSessionRegexRules`:1595 `handleSetSessionWorldDocs`:1610 `handleToggleRegexRule`:1697 | `BindingViewModel` |
| `handleExportData`:1845 `handleImportSessionMessages`:369 `handleSaveWindowApiParams`:343 | `DataViewModel` |

---

## §K 后端端点（`server.ts`，756 行 / 9 端点）

| 端点 | 行 | 迁移去向 | Kotlin |
|---|---|---|---|
| `GET /api/health` | 36 | ❌ 原生不需要 | |
| `POST /api/web-search` | 97 | ✅ 原生 Ktor 直连 | `WebSearchClient` |
| `POST /api/skill-search` | 198 | ✅ 原生 Ktor 直连 | `WebSearchClient`（oRPC） |
| `POST /api/skill-preview` | 229 | ✅ 原生 Ktor 直连 | 同上 |
| `POST /api/mcp/test` | 311 | ✅ 归入 MCP 客户端 | `McpClient` |
| `POST /api/models` | 363 | ✅ 原生 Ktor 直连 | `ProviderApi` |
| `POST /api/chat` | 457 | ✅ **原生 Ktor 流式** | `ChatStreamClient` |
| `GET /api/check-quota` | 793 | ✅ 原生 Ktor | `ProviderApi` |
| static | 836+ | ❌ 原生不需要 | |

---

# 原生版（Kotlin）现状

> §A–§K 是 **React 版规格书**（只读参照）。下面才是本仓已落地的 Kotlin 代码。
> 每完成一个任务就补进来 —— 以后要在原生侧定位功能，看这里，不要回去翻 React 版。

## §N1 工程与启动（截至 T01）

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| `main` | `desktopApp/src/main/kotlin/com/watchworld/desktop/Main.kt` | 桌面入口：开 1100×720 窗口，调 `App()` |
| `App` | `shared/src/commonMain/kotlin/com/watchworld/ui/App.kt` | ★ **双端唯一根组合**。Android 与桌面都从这里进 |

### 构建脚本（不是函数，但改配置必查）

| 位置 | 作用 |
|---|---|
| `gradle/libs.versions.toml` | ★ 版本唯一来源（Kotlin / Compose MP / material3 / jvmTarget） |
| `desktopApp/build.gradle.kts` | `compose.desktop.application`：`mainClass`、`nativeDistributions`、**渲染后端开关 `-PwwRender`** |
| `shared/build.gradle.kts` | KMP 目标（当前只有 `jvm("desktop")`）、源集依赖 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本（8.14.4） |

---

## §N2 聊天界面与假流式（截至 T02）

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| ~~`MockChatStream`~~ | ~~`shared/.../net/MockChatStream.kt`~~ | T02 临时脚手架（本地假流式），**T03 已删除**（连同 `MockChatStreamTest`）；替代者见 §N3 |
| `ChatScreen` | `shared/.../ui/chat/ChatScreen.kt` | 聊天主界面：消息列表 + 跟随滚动。**T14 起吃正式 `ChatSession` + `ChatMessage`**，流式与停止由 `ChatViewModel` 负责（见 §N10） |
| `ChatInputBar` | `shared/.../ui/chat/ChatInputBar.kt` | 输入栏：发送/停止二态按钮；`Ctrl+Enter` 发送 |
| `MessageBubble` | `shared/.../ui/chat/MessageBubble.kt` | 单条气泡；助手正文走 `StreamingMarkdown`。T14 起同时有 `UiMessage` 与 `ChatMessage` 两个重载 |
| `UiMessage` / `UiRole` | `shared/.../ui/chat/UiMessage.kt` | ⚠️ **T02 临时模型**：只剩 `DarkBubbleScreenshotTest` 在用（T12 的深色气泡回归资产），正式界面已改吃 `ChatMessage` |
| `StreamingMarkdown` | `shared/.../ui/markdown/StreamingMarkdown.kt` | ★ 流式渲染（**T13 起改用 mikepenz `multiplatform-markdown-renderer` 0.45.0**）：`rememberStreamingMarkdownState` 追加式，只重解析不稳定尾部（PLAN §7.3）；`resetKey`（本仓传 `message.id`）变化即重建状态；`streaming` 控制渲染期行内光标 |
| `streamingDelta` | 同上 | 纯函数：`committed`/`rendered` → 前缀追加则返回新后缀，非前缀(替换/变短)返回 null（调用方重建状态） |
| `rememberStreamCaretAnnotator` / `rememberStreamCaretInlineContent` / `isTrailingVisibleNode` | `shared/.../ui/markdown/StreamCaret.kt` | T13 流式光标：真实末尾 AST 节点后注入 `InlineTextContent`，800ms 硬切闪烁；不修改正文、不破坏 append-only |
| `preRenderMarkdown` / `markToolStatusLines` | `shared/.../ui/markdown/MarkdownPreRender.kt` | 渲染前**纯文本**预处理：先 `applyRegexRules`(OUTPUT) 再把整块 `[工具…]` 包成行内代码（T13） |
| ~~`splitBlocks` / `MarkdownBlock` / `inlineAnnotated` / `stripFence`~~ | ~~同上~~ | T02 自建极简渲染器，**T13 换库后删除**（切块与缓存改由库内部负责） |
| `App` | `shared/.../ui/App.kt` | 唯一根组合；T02 起挂 `ChatScreen`，**T14 起接真实数据层**（开库 + 两个 ViewModel + 侧边栏） |

测试：`shared/src/commonTest/.../ui/markdown/StreamingDeltaTest.kt`、`MarkdownPreRenderTest.kt`、`StreamCaretMarkingTest.kt`；桌面离屏 `StreamingMarkdownRenderTest.kt`、`StreamingMarkdownEdgeAndPerformanceTest.kt`（未闭合语法 + 360 段增量压测）

---

## §N3 协议层 · 真流式与 Provider 归一化（T04 完工；Gemini 在线生成受账号限制）

### 真实 fixture 状态

| Provider | 样本 | 当前覆盖 |
|---|---|---|
| OpenAI 兼容 | `fixtures/sse/openai-stream.txt` / `openai-toolcall-stream.txt` | 真实样本解析与正文/工具参数分片测试 |
| Claude | 用户持有外部文本样本 | 用户确认样本纯度可信且通过严格格式校验；本仓未保存样本文本，回归由协议形状单测覆盖 |
| Gemini | 尚未采集 | 使用 `thought` / `functionCall` 协议形状单测；真实样本待配置 Key 后采集 |

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| `HttpClientFactory.create` | `shared/.../net/HttpClientFactory.kt` | Ktor `HttpClient`（引擎自动发现 → 取 desktopMain 的 OkHttp）；**刻意不装 `HttpTimeout`** |
| **`ChatStreamClient.streamSse`** | `shared/.../net/ChatStreamClient.kt` | ★ **真流式核心**：`preparePost().execute{} + ByteReadChannel.readLine()`，发原始行流（PLAN §6.1） |
| `SseParser.parse` | `shared/.../net/SseParser.kt` | T04：逐帧消费行流；支持多行 `data:`、EOF flush、错误帧、`[DONE]` 与 `message_stop`，终止后取消上游收集 |
| `ProviderStreamAdapters.forProvider` | `shared/.../net/ProviderStreamAdapter.kt` | T04：OpenAI / Claude / Gemini wire event → `TextDelta` / `ReasoningDelta` / `ToolCallDelta` / `Finished`；Claude delta 缺 block start 抛协议错误 |
| `ChatStreamClient.streamAssistantEvents` | 同上 | 通过 parser + OpenAI adapter 输出标准事件；临时请求配置仍仅 OpenAI 兼容 |
| `ChatStreamClient.streamAssistantText` | 同上 | UI 正文视图，只转发 `TextDelta`；reasoning/tool deltas 留给后续任务消费 |
| `ChatStreamClient.close` | 同上 | 关闭底层 `HttpClient`（连接池/线程池） |
| `ApiException` | 同上 | 非 2xx 或错误帧时抛出 |
| `buildOpenAiRequest` | 同上（private） | T03 临时单轮 OpenAI 兼容请求体；后续 Provider 配置任务替换 |
| `HardcodedChatProvider.resolve` | `shared/.../net/HardcodedChatProvider.kt` | ⚠️ T03 临时配置（baseUrl/model 写死，**key 只从 env 取**）；T15 后应删除，但 `streamAssistantEvents` + 2 个测试仍依赖 → 待多 Provider 路由时一并清理 |
| `WwJson` | `shared/.../model/WwJson.kt` | 全局 Json 配置（PLAN §4.4，**T05 已补齐**：`ignoreUnknownKeys` / `isLenient` / `explicitNulls=false` / `encodeDefaults=false` / `coerceInputValues`）；协议层与数据层共用同一个实例 |
| `ArchiveV5` / `ChatSession` 等 24 个模型 | `shared/.../model/*.kt` | T05：v5 存档契约（扁平形状）；各自用途见 §A 的 Kotlin 列 |
| `platformEnv` | `shared/.../platform/PlatformEnv.kt`（expect）/ `shared/src/desktopMain/.../PlatformEnv.desktop.kt`（actual） | 读环境变量（commonMain 无 env 概念） |

构建配置：`gradle/libs.versions.toml` 新增 `ktor = "3.6.0"` / `kotlinxSerialization = "1.9.0"`；
⚠️ **kotlinx-serialization 编译器插件未启用**（T05 定义 `@Serializable` 时才加）。

测试：`shared/src/desktopTest/.../net/ChatStreamClientTest.kt`（★ PLAN §8.2 帧间隔验收 + 非 2xx 抛错）；
`SseProviderAdapterTest.kt`（OpenAI 真 fixture + SSE 状态机 + Claude/Gemini 协议级样例）；
`DeepSeekLiveSmokeTest.kt`（显式 `WW_T04_LIVE_DEEPSEEK=1` 才运行的在线验收；DeepSeek 官方 `deepseek-flash` 已实测通过，密钥从进程环境读取）。
⚠️ 仓库内可重放的真实 fixture 仍仅 OpenAI 两份；Claude 的外部样本验收状态由用户确认，原文未入库；Gemini 当前使用协议级样例。

---

## §N4 存档层 · v5 往返与老存档归一化（T06）

> 六条兼容逻辑**分两处**：解码前解决「解不开」，解码后解决「解开了但不对」。
> 少跑任一层都会**静默**漏掉规则 —— 定位置时先看清在哪一层。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`normalizeChatSession`** | `data/migration/SessionNormalizer.kt` | ★★ 模型层第 ②③④⑤ 条；`rawToolLimit` 默认参数**使归一化幂等**（导出路径依赖） |
| **`sanitizeSessionJson`** | 同上 | ★★ 解码**前**类型清洗：删遗留 `connectedSkillIds`（①）、非数组抹掉（②③）、非 boolean 抹掉（⑤⑥） |
| `toolCallStepLimitFrom` | 同上 | 第 ④ 条判据 `typeof === 'number' && isFinite ? max(0, floor(n)) : null`；越界钳到 `Int.MAX_VALUE` |
| `LEGACY_BUILTIN_KNOWLEDGE_ID` | 同上 | `"kb-quick-notes"`，第 ② 条要剔除的硬编码 ID |
| `normalizeMcpServerJson` | `data/migration/McpServerNormalizer.kt` | 旧版 MCP：`endpoint`→`url`、`custom_api`/`stdio` 丢弃、`testing`→`disconnected`；**只能在 JSON 层做** |
| `normalizeArchiveJson` | `data/migration/ArchiveNormalizer.kt` | 解码**前**：sessions 逐个 `sanitizeSessionJson`、mcpServers 逐个 `normalizeMcpServerJson` |
| `normalizeArchive` | 同上 | 解码**后**：sessions 逐个 `normalizeChatSession` |
| **`ArchiveRepository.parseArchive`** | `data/repository/ArchiveRepository.kt` | ★ 导入：`归一化(JSON前) → 解码 → 归一化(模型后)`，**三步缺一不可** |
| `ArchiveRepository.tryImportArchive` | 同上 | 容错版，坏存档返回 `null`（对应 `importAllData` 的 `catch`） |
| **`ArchiveRepository.exportArchive`** | 同上 | ★ 导出：盖 `version`/`exportTime`、跑归一化、**顶层铺 `encodeDefaults=true` 骨架**（否则 `version` 整个消失） |
| `ArchiveRepository.redactSecrets` | 同上 | `apiKey` / `password` → `''`（**空串，不是 null**） |
| `ArchiveRepository.ARCHIVE_VERSION` | 同上 | `5`；导出无条件写它，不读旧值 |
| `currentTimeMillis` | `platform/PlatformTime.kt`（expect）/ `shared/src/desktopMain/.../PlatformTime.desktop.kt`（actual） | 导出的 `exportTime`；⚠️ T22 加 Android 目标时必须补 actual |

测试：`shared/src/desktopTest/.../data/ArchiveRoundTripTest.kt`（17 用例：六条规则、判据逐项、幂等、
两份真实 fixture 往返、顶层形状、密钥擦除、MCP 迁移、合成富字段、坏输入）。

---

## §N5 存储层 · Room 表 + DAO + Repository（T07）

> **DAO 只在 `data/` 内出现**，UI / ViewModel 只拿 Repository（CONSTRAINTS §5）。
> 写操作全部 `suspend`，会话类写入在**同一事务**里盖 `updatedAt` 并写四张表。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`StorageKeys`** | `data/StorageKeys.kt` | ★ 21 个 Web 存储键原样保留 + 每个键在原生版的落点（表 or `kv` 行键）；**唯一权威表**，前缀不可改 |
| **`WwDatabase`** | `data/db/WwDatabase.kt` | `@Database(version = 1)`，17 张表；`open(databasePath)` 传**文件全路径**；`openInMemory()` 仅测试 |
| `WwDatabase.withTransaction` | `data/db/WwTransaction.kt` | Room KMP 无 `withTransaction` → `useWriterConnection { it.immediateTransaction { } }` |
| 实体 / DAO | `data/db/ConfigEntities.kt` `ConfigDaos.kt` `SessionEntities.kt` `SessionDaos.kt` | 12 张配置表 + `sessions`/`messages`/`timelines`/`summaries` + `kv`；子表带 `sortIndex` 与 `ON DELETE CASCADE`；⚠️ `messages` / `timelines` / `summaries` 主键都是 `(sessionId, id)`，按行更新/删除必须带 `sessionId` |
| `toEntity` / `toModel` | `data/db/Mappers.kt` | 模型 ↔ 实体；`timelines`/`summaries` 的 null vs `[]` 由 `SessionConfigBlob.timelinesNull`/`summariesNull` 决定 |
| `WwConverters` | `data/db/WwConverters.kt` | JSON 列（可空/非空各一份）+ 7 个枚举手写 `when` |
| **`SessionRepository`** | `data/repository/SessionRepository.kt` | ★ 会话树唯一入口：`all`/`byId`/`save`/`appendMessage`/`rename`/`setPinned`/`moveToGroup`/`delete`/`replaceAll`；自己盖 `updatedAt` |
| `ConfigRepository` | `data/repository/ConfigRepository.kt` | 12 个集合的 `xxx()`/`setXxx()`（**全量替换**）+ `transaction {}`；对应 `Storage.getXxx/setXxx` |
| `SettingsRepository` | `data/repository/SettingsRepository.kt` | `kv` 上的单体设置（主题/明暗/显示设置/偏好/WebDAV/当前会话·Provider/草稿）；读不抛错，没存过给默认值（⚠️ `DEFAULT_*` 未迁） |
| **`ArchiveStore`** | `data/repository/ArchiveStore.kt` | ★ v5 存档 ↔ SQLite：`importJson`（整段一事务、按 JS 真值逐分类判断）/ `snapshot` / `exportJson`；格式规则复用 §N4 |
| `appDataDir` / `createSqliteDriver` / `sqliteQueryDispatcher` | `platform/PlatformStorage.kt`（expect）/ `desktopMain/.../PlatformStorage.desktop.kt`（actual） | `%APPDATA%\WatchWorld`、`BundledSQLiteDriver`、`Dispatchers.IO`；⚠️ T22 补 Android actual |

测试：`shared/src/desktopTest/.../data/StorageRoundTripTest.kt`（10 用例：两会话同名消息/总结 id 互不覆盖、三份真实存档库往返、库→导出→再解析、
**文件库关库重开**、部分导入/空数组清空、坏存档不动库、盖 `updatedAt`、子表 null/[] 两态、顺序保持、删除连带子表）。
表结构契约：`shared/schemas/com.watchworld.data.db.WwDatabase/1.json`（要提交）。

---

## §N6 上下文拼装（T08）

> `ContextBuilder` 是**纯函数**：不读库、不联网、不读图。调用方从 Repository 取数据组装 `ContextRequest`。
> ⚠️ system 段落顺序以 `api.ts:311-321` 源码为准（与 CONSTRAINTS §4.1 文字不同）。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`ContextBuilder.build`** | `domain/ContextBuilder.kt` | ★★ 可见性过滤 → 推入通用过滤 → 时间线重排 → system 拼装（head → 正典 → MCP → Skill → 知识库 → 联网 → 楼号约定 → tail → 时间线树）→ 正则 → 标签格式化 → 图片保留 |
| `ContextRequest` / `AssembledContext` / `ContextMessage` | 同上 | 入参（默认值照抄 `SendChatParams`）/ 产物；`availableMcpServers`/`availableSkills` 是 T09 工具注册的唯一来源 |
| `ContextBuilder.filterByVisibility` / `pushedCommonFloors` / `isCommonMessage` | 同上 | `api.ts:157-196`；`PUSHED_COMMON_FLOOR_REGEX` 同正则；三重判定 |
| `WORLD_/KNOWLEDGE_/MCP_CONTEXT_HEADING`、`FLOOR_REFERENCE_PROTOCOL` | 同上 | system 标题字面量（逐字一致） |
| `buildSystemInstruction` / `dropSummarizedFloors` | 同上 | `App.tsx:417` / `:466` |
| `selectConnectedKnowledge` / `selectActiveWorldDocuments` / `selectActiveMcpServers` / `selectActiveSkills` / `selectActiveRegexRules` | 同上 | `App.tsx:801-854`；`null` ≠ `[]` 两态 |
| `LOAD_SKILL_TOOL_NAME` / `isMcpAvailableToModel` / `getAvailableMcpToolNames` / `isSkillAvailableToModel` / `formatSkillManifest` | `domain/McpAvailability.kt` | `mcpAvailability.ts` 1:1 |
| `applyRegexRules` / `compileJsRegex` / `replaceJs` | `domain/RegexProcessor.kt` | JS 语义：自展开替换串、无 `g` 只换一次、空 flags 当 `g`、非法跳过 |
| `rearrangeMessagesForAi` / `formatMessageWithFloorAndTag` / `messageContentForAi` / `DEFAULT_MAIN_TIMELINE_ID` | `domain/TimelineMemory.kt` | `timelineMemory.ts:74/146/226`（T08）；✅ T17 补齐 `TIMELINE_COLORS`/`getTimelineColorConfig`/`createDefaultMainTimeline`/`allocateTimelineCodeTag`/`detectTimelineIntent`/`enrichTimelineProgression`/`buildTimelineTreePromptContext` |
| `TIMELINE_DESCRIPTION_TAG_CONTROL_OPEN`/`_CLOSE`/`_MAX_LENGTH`/`REASON_MAX_LENGTH`、`stripTimelineDescriptionTagControl`、`parseTimelineDescriptionTagControl`、`createTimelineDescriptionTagRequest`、`approve`/`rejectTimelineDescriptionTagRequest` | `domain/TimelineTagControl.kt` | ✅ T17：`timelineDescriptionTagControl.ts` 1:1（私有控制标记协议 → 待审批申请） |

测试：`shared/src/desktopTest/.../domain/ContextBuilderTest.kt`（19 用例，期望字符串手写自 TS 源码）。⚠️ `formatMessageWithFloorAndTag` 的 `content` 参数是方案 A（时间线模式下正则仍生效，有意偏离 TS），别删。⚠️ **T17 起时间线树提示词由 `ContextBuilder` 内部构建**（`ContextRequest.timelineContextPrompt` 降级为可选覆盖，默认 `null`）。

---

## §N7 工具轮次循环（T09）

> 入口是 `ChatStreamClient` 的扩展函数 `runToolChat`。真 MCP 调用经 `McpToolCaller` 注入（实现归 T10）。
> ⚠️ 有意不迁：`viaProxy` / `emitPlainReply` / `replayAsStream`（见文件头「有意差异」）。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`ChatStreamClient.runToolChat`** | `net/ToolCallLoop.kt` | ★★ `sendNativeToolChat`(:194-544) 1:1：注册工具 → 每轮发请求（流式 `streamEvents` / 非流式 `postJson`+`pseudoEvents`）→ `Round.receive` 累积 → finish_reason 白名单 → 执行工具 → 回填 history；除取消外不抛，失败转 `note` |
| `McpToolCaller` | 同上 | `fun interface`，收**原始工具名**；T10 实现 |
| `ToolChatParams` / `ToolChatMessage` / `ToolChatListeners` / `ToolChatResult` | 同上 | 入参（`stream` 默认 true，`toolCallStepLimit` 0=无限）/ 三路输出回调 / 可见正文 |
| `registerTools` | 同上 | 原始名优先、跨服务重名加 `_2/_3`；`name`=注册名、`originalName`=原始名；内置 `load_skill`；schema 非对象抛 `ToolSchemaException` |
| `executeToolCall` / `decodeArguments` | 同上（private） | `load_skill` 本地拦截；错误转 `{error}`+`isError`；参数必须为 JSON 对象 |
| `toolResultMessage` / `providerMessage` / `requestBody` / `chatUrl` / `requestHeaders` | 同上（private） | 三家 wire 形状；Gemini key 走 `?key=`、`result` 是对象 |
| `allowedFinishReasons` | 同上（private） | OpenAI `stop`/`tool_calls`；Claude `end_turn`/`stop_sequence`/`tool_use`；Gemini `STOP` |
| `Round`（`receive` / `calls` / `openAiAssistantMessage` / `sortedBlocks`） | 同上（private） | TS 循环体内的单轮累积；OpenAI 分片 `+=`、回填补 `type`；Claude block 表；Gemini parts |
| `ChatStreamClient.streamEvents` / `postJson` | `net/ChatStreamClient.kt` | 按 adapter 出事件流 / 非流式一次性请求 |

测试：`shared/src/desktopTest/.../net/ToolCallLoopTest.kt`（21 用例，进程内 `HttpServer` 假上游）。⚠️ 每轮新建 adapter；取消分支不调 suspend 回调。

---

## §N8 MCP 客户端（T10）

> **从零实现**（无官方 Kotlin SDK）。真机 `https://github.windlife.site/mcp/andunya` 实测：
> `initialize` 把 `2025-06-18` 降级成 `2024-11-05`、响应头无 `mcp-session-id`、响应是 `application/json`。
> ⚠️ 超时一律用 `withTimeoutOrNull` 转普通异常：`withTimeout` 抛的 `TimeoutCancellationException`
> 属于 `CancellationException`，会被 T09 `executeToolCall` 当成「用户停止」外抛。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`McpClient`** | `net/McpClient.kt` | `McpToolCaller` 实现（= `mcpApi.ts:29 callMcpToolNative`）；每次调用建连、用完即关；两条可用性校验先于任何网络请求 |
| `McpClient.testConnection` | 同上 | `initialize` → `tools/list` → `McpConnectionTestResult`（`App.tsx:1641-1682` 的写回口径）；失败不抛、以 `error` 返回 |
| `McpSession` | 同上（private） | JSON-RPC id 分配、`initialize`（**接受服务端返回的版本**）、`notifications/initialized`、`tools/list` 翻页、请求头（handshake 不带 session id） |
| `StreamableHttpTransport` | 同上（private） | 单端点 POST；`application/json` 与 `text/event-stream` 双响应分流；202；有 session 时 `DELETE` 终止 |
| `SseTransport` | 同上（private） | 旧式 HTTP+SSE：常驻 GET + `event: endpoint` + POST 202 + 响应走 GET 流 |
| `mapTool` / `validateUrl` / `explainMcpError` | 同上（private / internal） | 工具映射（`MCP 工具方法:` 回退）、URL 五条校验、`explainNativeMcpError` 5 分支 |
| `McpConnectionTestResult` / `McpServerInfo` | 同上 | 测试结果结构（`success` / `latencyMs` / `tools` / `serverInfo` / `error` / `message`） |

测试：`shared/src/desktopTest/.../net/McpClientTest.kt`（13 用例，进程内 `HttpServer` 假上游）+ `McpLiveSmokeTest.kt`（`assumeTrue` 在线 smoke，地址 `WW_T10_LIVE_MCP_URL` 可覆盖）。

---

## §N9 联网搜索 + Skill 目录（T11）

> **只迁原生直连**：`webSearch.ts` 的 `searchWebViaProxy` / `parseSearchResponse` / `/api/…` 代理整个不要
> （桌面端与 Android 都是进程内应用，无 Node 服务端、无 Worker；原生无 CORS，可直接调 DDG 与 oRPC）。
> DDG 解析移植的是 `webSearch.ts` 里**修好的** `parseDuckDuckGoHtml`（全局匹配 `result__a`），
> **不是** `server.ts` 的旧 `parseDuckDuckGoResults`（旧 split 会吃掉 `_`，表现为「200 但 0 条」）。
> UA 统一用 `webSearch.ts` 原生路径的 Chrome-Android UA。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`WebSearchClient`** | `net/WebSearchClient.kt` | `searchWeb` / `searchAgentSkills` / `previewSkillMd`；`HttpClient` 由调用方持有 |
| `parseDuckDuckGoHtml` | 同上（public） | `parseDuckDuckGoHtml` 1:1：全局匹配 `result__a`、`uddg` 取真实 URL、过滤 `y.js` 广告、`source` 去 `www.`、`limit` 截断 |
| `searchWebAt` / `searchAgentSkillsAt` / `previewSkillMdAt` | 同上（internal） | 可注入假上游地址的测试入口；`limit` 钳 1..10、query 500 字、page≥1、pageSize 1..60 |
| `callSkillDirectoryRpc` | 同上（private） | oRPC：`POST base/<router>/<procedure>`，请求体包 `{ json: … }`，解包响应的 `json` |
| `decodeSearchHtml` / `stripSearchHtml` / `unwrapDuckDuckGoUrl` / `safeHostname` / `percentDecode` | 同上（internal） | 纯字符串逻辑 |
| `parseSkillFrontmatter` | 同上（internal） | 只认顶层无缩进的 `name` / `description` |
| `isSkillSearchIntent` / `shouldUseWebSearch` | 同上 | 是否触发搜索 |
| `formatSearchContext` / `formatSkillDirectoryContext` | 同上 | 注入 system 的上下文文案（逐字） |

测试：`shared/src/desktopTest/.../net/WebSearchClientTest.kt`（17 用例，进程内假上游）+ `WebSearchLiveSmokeTest.kt`（`assumeTrue` 在线 smoke，`WW_T11_LIVE=1`）。

---

## §N10 会话管理 · 侧边栏 · 正式请求链路（T14）

> **T14 起 UI 不再是「内存里追加消息」的临时界面**：会话树从 SQLite 读，发消息走
> `ContextBuilder.build → ChatRequestAssembler → runToolChat`，每条消息落库并回流侧边栏。
> ⚠️ UI **只拿 Repository**（三个 ViewModel 的构造参数里没有任何 `*Dao`）。

| 函数 / 声明 | 位置 | 职责 |
|---|---|---|
| **`DEFAULT_DISPLAY_SETTINGS` / `DEFAULT_APP_PREFERENCES`** | `data/DefaultData.kt` | `defaultData.ts` 里当前用得到的默认值；⚠️ 同时决定**全新安装的默认设置**与 `ArchiveStore.snapshot()` 对老存档缺字段的补齐 → 改值必须同步改 `StorageRoundTripTest.withStorageDefaults` |
| **`ChatRequestAssembler.assemble`** | `domain/ChatRequestAssembler.kt` | ★ 请求装配纯函数：采样参数**逐项照抄 `api.ts` 原生分支的回退链**（`session ?? defaults ?? agent ?? 字面量`），`stream = session.streamingEnabled != false`，`toolCallStepLimit = session ?? defaults ?? 0`；`null` = 不传这个键，不许填默认值 |
| **`SessionViewModel`** | `ui/session/SessionViewModel.kt` | 会话树状态机：`load` / `newSession` / `select` / `rename` / `clear` / `setPinned` / `moveToGroup` / `delete` / `createGroup` / `replace`；活动会话 id 落 `kv` |
| **`ChatViewModel`** | `ui/chat/ChatViewModel.kt` | ★ 发送链路：拼 system（`buildSystemInstruction`）→ `ContextBuilder.build` → 联网搜索（授权 + 意图命中才检索）→ 装配 → `runToolChat` → 落库；`stop()` 取消协程 |
| `ChatViewModel.stop` / 取消分支 | 同上 | ⚠️ 取消后的落库必须在 `withContext(NonCancellable)` 里：协程已取消，SQLite 写的第一个挂起点会再抛 `CancellationException`，部分正文就存不下来 |
| `ChatViewModel.searchContext` | 同上 | `session.webSearchEnabled == true` + `shouldUseWebSearch` 才检索；Skill 意图走 `searchAgentSkills`，否则 `searchWeb`；失败写成 `# 【联网搜索状态】` 段落而非抛出 |
| **`SidebarSheet`** | `ui/sidebar/SidebarSheet.kt` | 侧边栏：搜索（标题 + 正文）、分组折叠、新建分组、会话行操作（重命名 / 置顶 / 清空 / 删除 / 移动分组） |
| `MessageBubble(ChatMessage, streaming)` | `ui/chat/MessageBubble.kt` | T14 新增的正式模型重载；正文提取 `versions[currentVersionIndex] → versions[0] → content`（⚠️ 与 `api.ts:328` 的 `\|\|` 语义一致，别只读 `versions.first()`） |
| `ChatScreen(session, streamingMessageId, ...)` | `ui/chat/ChatScreen.kt` | 改吃正式 `ChatSession` + `ChatMessage`；滚动跟随按**显示文本**长度（assistant 取版本正文，不是顶层 `content`） |
| `ensureAppDataDir()` | `platform/PlatformStorage.kt`（expect）/ `desktopMain/.../PlatformStorage.desktop.kt` | 建好 `%APPDATA%\WatchWorld` 再开库（KMP 版 Room 把 `name` 当路径，目录不存在会报错）；⚠️ T22 补 Android actual |
| `App` | `ui/App.kt` | T14 起真实接线：开库 → 三个 Repository → 两个 ViewModel → 侧边栏 + 聊天区；启动读会话树，库里空则建一个 |

### 两条失败路径的落点（改 `ChatViewModel` 的 catch 前先看）

| 失败 | 落点 | 为什么 |
|---|---|---|
| 上游请求失败 | **正文里的一条 note**（`[请求失败：{原文}]`） | `runToolChat` 除取消外不抛，把原因写成 note；用户能对着那条消息继续对话 |
| 自己的装配失败（没 Provider / 没 Agent） | `ChatUiState.error`（顶栏） | 它没走到工具循环，没有任何消息可以承载原因 |

测试：`desktopTest/domain/ChatRequestAssemblerTest.kt`（3）、`ui/session/SessionViewModelTest.kt`（8）、
`ui/chat/ChatViewModelTest.kt`（8，含取消保留正文）、`ui/chat/ChatMessageBubbleRenderTest.kt`（3，离屏像素）。

### §N11 设置界面（T15）

| 符号 | 位置 | 说明 |
|---|---|---|
| `SettingsUiState` | `ui/settings/SettingsViewModel.kt` | 9 类配置列表 + 显示/偏好/主题/明暗 + 导入状态 |
| **`SettingsViewModel`** | 同上 | 配置 CRUD（泛型 `upsert`/`remove` + `ConfigRepository.transaction`）、主题/偏好写回、导入导出 |
| `SettingsHub` | `ui/settings/SettingsHub.kt` | 左栏 9 标签 + 右栏内容区分发 |
| `ConfigListPanel<T>` | `ui/settings/SettingsPanels.kt` | 通用配置列表（泛型，显示 label + 删除按钮）|
| `RegexPanel` | 同上 | 正则库：分组折叠 + 整组/逐条开关 + 作用域 + `pattern → replacement` 预览条 + 表单内实时沙箱测试。⚠️ **入口是侧边栏「正则库」格**（2026-10-10 起从设置里移除），窗口形态见 `ui/chat/WindowRegexPanel.kt` |
| `PreferencesPanel` | 同上 | 主题切换（**20 内置**）+ 明暗 + 7 个显示开关 + 2 个偏好开关 |
| `BackupPanel` | 同上 | 导出按钮 + 粘贴导入框 |
| `testRegexRule` | `domain/RegexProcessor.kt` | `regexProcessor.ts:26`；沙箱测试跑一次替换，返回 `RegexTestResult(success, result, matchCount, error)`（**会暴露**非法 pattern/flags，与清洗链路静默跳过不同）。⚠️ `matchCount` 一律按真实命中处数算，不复制参考版非全局分支「数捕获组」的怪癖 |

测试：`desktopTest/ui/settings/SettingsViewModelTest.kt`（6：空库加载 / Provider CRUD / Agent CRUD / Regex toggle / 主题明暗持久化 / 导入导出往返）。

### §N13 侧边栏功能入口 · FeatureDialog 弹窗壳（T16-接线）

> **设计标准**（对标 Web 版截图，所有功能弹窗必须遵守）：
>
> 1. **居中浮动**：弹窗不全屏，宽度最大 600dp，水平居中。
> 2. **高度随内容自适应**：内容少时弹窗小，内容多时涨到最大高度（屏幕 85%）后内部可滚动。
> 3. **标题区固定**：左标题 + 右 ✕ 关闭按钮（`TablerIcons.X`），不随内容滚动。
> 4. **`scrollable` 参数**：默认 `true`（内容区自带 `verticalScroll`，Panel 内不要再套）；
>    `false` 用于内容自管滚动的场景（如 SettingsHub 双栏布局）。
> 5. **调用时序**：侧边栏先关闭 → 开弹窗 → `settingsViewModel.load()` 加载数据。

| 符号 | 位置 | 说明 |
|---|---|---|
| **`FeatureDialog`** | `ui/settings/FeatureDialog.kt` | 统一弹窗壳子；`Dialog(usePlatformDefaultWidth=false)` + `Box(居中)` + `Surface(wrapContentHeight, maxHeight=85%)` |
| `SidebarSheet` 7 个新回调 | `ui/sidebar/SidebarSheet.kt` | `onOpenMcp`/`onOpenSkill`/`onOpenKnowledge`/`onOpenWorld`/`onOpenTimeline`/`onOpenProvider`/`onOpenBackup` |
| `SidebarNavGrid` | 同上（private） | 底部 8 宫格全部接线，不再有 `{ /* TODO */ }` |
| `App` 7 个弹窗状态 | `ui/App.kt` | `mcpOpen`/`skillOpen`/`knowledgeOpen`/`worldOpen`/`timelineOpen`/`providerOpen`/`backupOpen` |
| `SettingsHub` | `ui/settings/SettingsHub.kt` | 左栏标题与「关闭设置」按钮已移除（由 FeatureDialog 顶栏统一提供）；`onDismiss` 保留但默认空 |

### §N12 消息级操作（T16）

| 符号 | 位置 | 说明 |
|---|---|---|
| `switchVersion(messageId, newIndex)` | `ui/chat/ChatViewModel.kt` | 版本切换：更新 `currentVersionIndex`，镜像顶层 `latencyMs`/`tokens`，持久化 |
| `deleteMessage(messageId)` | 同上 | 删除单条消息并持久化 |
| `editAndResend(messageId, newContent)` | 同上 | 编辑 user 消息正文，截断后续消息，以新正文重新触发 `send` |
| `regenerate(messageId)` | 同上 | 滚动重答：在 assistant 消息上新增空版本，以上一条 user 正文调用 `regenerateInto` |
| `regenerateInto(session, assistantId, query)` | 同上（private） | 与 `send` 的生成部分同构，把内容写到已有消息的当前版本 |
| `MessageActions(...)` | `ui/chat/MessageBubble.kt` | 版本切换 ← N/M → + 重答🔄 / 编辑✏️ / 删除🗑️ 按钮行 + 内联编辑模式 |
| `ChatScreen(..., onSwitchVersion, onRegenerate, onEdit, onDeleteMessage)` | `ui/chat/ChatScreen.kt` | T16 新增四个可空回调，流式中自动屏蔽 |

测试：`desktopTest/ui/chat/ChatViewModelMessageOpsTest.kt`（6：版本切换持久化 / 越界钳位 / 删除 / 编辑重发 / 滚动重答 / user 重答无操作）。

---

### §N14 时间线树 + 描述标签审批流（T17）

| 符号 | 位置 | 说明 |
|---|---|---|
| `TimelineColorConfig` / `TIMELINE_COLORS` | `domain/TimelineMemory.kt` | `timelineMemory.ts:3` 7 套色板，**Tailwind class 原样保留**（UI 另做映射，不许改表） |
| `getTimelineColorConfig(colorId)` | 同上 | 未命中（含 `null`/空串）退回第 0 项 indigo |
| `createDefaultMainTimeline(now)` | 同上 | `timelineMemory.ts:76`；`createdAt`/`updatedAt` 由调用方给（commonMain 无 `Date.now()`） |
| `allocateTimelineCodeTag(existing, parent)` | 同上 | 根分支**永远只用 A 开头**（首个字母即 return）；子分支 `父code + 递增序号` |
| `TimelineAction` / `TimelineDetectionResult` / `detectTimelineIntent(text, timelines)` | 同上 | 三段判断（顺序不可换）：整串 codeTag/句式切换 → 显式声明新建 → 方括号标签 |
| `TimelineProgression` / `enrichTimelineProgression(branch, user, ai)` | 同上 | 里程碑关键词命中即 `break`；剧情日志留最后 8 条、里程碑留最后 10 条 |
| `buildTimelineTreePromptContext(active, all, msgs)` | 同上 | `timelineMemory.ts:399` 时间线树提示词；**不可见分支整段返回 `""`**；`allMessages` 形参未使用（`@Suppress`） |
| `TIMELINE_DESCRIPTION_TAG_CONTROL_OPEN`/`_CLOSE`/`_MAX_LENGTH`(120)/`_REASON_MAX_LENGTH`(240) | `domain/TimelineTagControl.kt` | 私有控制标记协议常量 |
| `stripTimelineDescriptionTagControl(raw)` | 同上 | 删完整控制块 + **未闭合标记吞掉其后全部内容**（流式安全） |
| `parseTimelineDescriptionTagControl(raw)` | 同上 | 解析私有申请；键数必须 = 2、值必须为**字符串**、**最多采纳 1 个**（`singleOrNull()`） |
| `createTimelineDescriptionTagRequest(...)` | 同上 | 生成待审批申请（不动时间线/消息）；id = `timeline-description-tag-{now}-{sourceMessageId}` |
| `approveTimelineDescriptionTagRequest(...)` | 同上 | 批准：改申请状态 + 分支 `descriptionTag`/`updatedAt` + **同步该分支下所有消息的 `descriptionTag`** |
| `rejectTimelineDescriptionTagRequest(...)` | 同上 | 拒绝：**只改申请状态**；不看记忆开关、不校验分支可见性（与 approve 不对称） |

测试：`desktopTest/domain/TimelineMemoryTest.kt`（17）+ `desktopTest/domain/TimelineTagControlTest.kt`（14）。
⚠️ **时间线树提示词由 `ContextBuilder` 第 6 步内部构建**（`ContextRequest.timelineContextPrompt` 已降级为可选覆盖，默认 `null`）；
`desktopTest/domain/ContextBuilderTest.kt` 的 `timelinePromptIsBuiltInternallyWhenNotOverridden` 钉住这一点。
⚠️ 时间线弹窗 UI（`TimelineModal.tsx`）、发送时的意图识别/剧情推进接线、流式回调里的控制标记剥离**仍未做**（见 `tasks/T17-时间线树.md`「遗留问题」）。

---

## §N15 世界观库 · 嵌套分组树（T18）

> 对标 `DocumentModal.tsx` 的**库形态**（侧边栏「世界观」入口）。
> 领域逻辑全部是纯函数（`domain/WorldLibrary.kt`），存储只有一个入口
> （`ConfigRepository` 的 world 系列），UI 只拿 `WorldViewModel`（**不碰 DAO**）。

| 符号 | 位置 | 说明 |
|---|---|---|
| **`resolveWorldDocumentGroupIds`** | `domain/WorldLibrary.kt` | ★ `storage.ts:360-373` 1:1：老 `group`（**组名字符串**）→ `groupId`；⚠️ 「有 `group` 但查无此名」= `groupId` 清成 `null`（**不是**保持原样）；`groupId` 已有值时不迁移 |
| `childWorldGroups(groups, parentId)` | 同上 | 直接子级（`DocumentModal` 的 `childrenOf`），保序 |
| **`selfAndDescendantWorldGroupIds`** | 同上 | 自己 + 全部后代；⚠️ **必须带 `guard` 环保护**，父级可被改成任意分组，有环时无 guard 会 `StackOverflowError` |
| `worldDocumentsInGroupTree` | 同上 | 某子树下的**全部**文档（含后代分组的文档，不是只算直接子级） |
| **`selectSessionWorldDocuments`** | 同上 | 窗口勾选 ∩ 启用；⚠️ 与知识库**不对称**：`null` 时取全部 `enabled == true`，非 `null` 时只认勾选（不看 `enabled`） |
| `UNGROUPED_WORLD_GROUP_ID` | 同上 | `"__ungrouped__"`，UI 侧未分组的哨兵分组键（模型层用 `groupId == null`） |
| `WorldDocument.legacyGroup` | `model/Content.kt` | `@SerialName("group")`；老 `group` 的载体，**只在读的第一个瞬间有值**，不许在 UI 读 |
| `WorldDocumentEntity.legacyGroup` | `data/db/ConfigEntities.kt` | 老 `group` 的落库列；⚠️ `toEntity` **必须原样带上**（见下） |
| **`ConfigRepository.worldDocuments()`** | `data/repository/ConfigRepository.kt` | ★ 读出后立刻跑 `resolveWorldDocumentGroupIds`（迁移位置对齐 `storage.ts:361`）；导入路径共用它 → 导入即归组 |
| **`MIGRATION_1_2`** | `data/db/WwMigrations.kt` | ★★ 本仓**第一条** Room Migration：`ALTER TABLE worldDocuments ADD COLUMN legacyGroup TEXT`。**必须注册进 `WwDatabase.open`**，否则已有 v1 库打不开（`Room cannot verify the data integrity`） |
| `WwDatabase` | `data/db/WwDatabase.kt` | `version = 2`（v1 → v2 = 本迁移）；`open()` 挂 `.addMigrations(MIGRATION_1_2)`；⚠️ 只要改实体就必须 bump + 加迁移 —— `openInMemory()` 测不出「老库打不开」 |
| `WwDatabaseMigrationTest` | `desktopTest/data/db/WwDatabaseMigrationTest.kt` | 2 用例：① `2.json` 列定义 == 迁移 SQL；② 按 `1.json` 物理造**完整** v1 库（17 表 + 4 索引 + v1 哈希）→ 真 `open` → 迁移 → 读写 → 关库重开。⚠️ **不依赖 `room-testing`**（KMP 里解析不到） |
| **`WorldViewModel`** | `ui/world/WorldViewModel.kt` | 分组 CRUD（`createGroup`/`renameGroup`/`setGroupParent`/`deleteGroup`）+ 文档 CRUD（`createDocument`/`saveDocument`/`deleteDocument`/`setDocumentEnabled`/`moveDocumentToGroup`）+ 视图状态（`select`/`setQuery`/`toggleCollapsed`）；写操作一律 `config.transaction { 读全量 → 改 → 写全量 }` |
| `WorldUiState` | 同上 | 分组/文档/选中/搜索词/折叠集合；`filteredDocuments` 按标题+正文+标签匹配 |
| `WorldPanel` | `ui/world/WorldPanel.kt` | 世界观库界面：建分组（可挂上级）、分组树行（缩进/折叠/改名/改上级/删除/计数）、文档列表（启用勾选/移动/删除）、新建与编辑表单 |
| `buildGroupRows` / `GroupRow` / `documentCountInGroup` | 同上（internal） | 扁平 → 带深度的行；违规数据里的**孤儿分组**（父级不存在）也会露出来，否则用户看不到也删不掉 |
| `GroupParentDialog` / `GroupPicker` / `ParentPicker` / `MoveDocumentPicker` | 同上（private） | 各类分组选择弹层；改父级的候选**排除自己与后代** |

测试：`desktopTest/domain/WorldLibraryTest.kt`（13：迁移 6 + 分组树 4 + 窗口勾选 2 + …）、
`desktopTest/ui/world/WorldViewModelTest.kt`（14）、`desktopTest/data/StorageRoundTripTest.kt` 的
`legacyWorldDocumentGroupNameMigratesToGroupId`。

⚠️ **老 `group` 是「读时迁移」，所以导入路径必须让它先落库**：Mapper 的 `toEntity` 早期写成
`legacyGroup = null`（理由是「`explicitNulls = false` 下 `null` = 键消失」），结果 `group` 永远进不了库，
`worldDocuments()` 那一步再也看不到 —— 表现为「导入老存档后文档全部未分组」，且**不报错**。
判据是「迁移发生在读还是写」，不是「字段属不属于新模型」。

🚨 **加这一列必须同时写 Room 迁移**（`MIGRATION_1_2` + `version 2` + `addMigrations`）。
第一版没有，理由是「库还是 v1、没发布过」—— 那个理由只对**全新空库**成立：开发机上跑过一次
`:desktopApp:run` 就已经有 v1 库文件，改完实体一启动就崩
`Room cannot verify the data integrity … Expected identity hash … found …`。
而 `openInMemory()` 每次都新建库，**所有测试都测不出来**。
详见 [`gotchas.md`](gotchas.md)「库还是 v1、没发布过 → 直接改表」。

---

## §N16 WebDAV 同步（T19）

> **两层**：`net/WebDavClient.kt`（传输：PROPFIND/PUT/GET/DELETE/MKCOL + Basic 认证）
> 与 `data/repository/SyncEngine.kt`（增量语义：时间戳较新者胜）。
> ⚠️ **原生版直连、不走 `/api/webdav` 代理**（进程内应用无 CORS）。
> ⚠️ 图片同步（`pushMissingImages` / 拉图循环）**未做**，归 T21 —— 清单字段与客户端方法已就位。

| 符号 | 位置 | 说明 |
|---|---|---|
| **`WebDavClient`** | `net/WebDavClient.kt` | `webdavSync.ts` 1:1；✅ `testConnection` / `pullBackup` / `pushBackup` / `ensureCollections` / `fetchManifest` / `pushManifest` / `pushSessionFile` / `fetchSessionFile` / `pushImageFile` / `fetchImageFile` |
| `WebDavSyncResult` / `WebDavConnectionTestResult` | 同上 | 结果结构；⚠️ `pushBackup` 成功文案是 `同步成功`（**有意去掉** Web 版 `toLocaleTimeString()`，commonMain 无本地时间格式化；时间在 `timestamp`） |
| `SyncManifest` / `SyncSessionMeta` / `SyncImageMeta` | 同上 | `index.json` 清单；`emptyManifest()` |
| `backupFileUrl` / `syncRoot` / `safeFileName` / `urlPath` | 同上（internal） | 路径拼接；`backupFileUrl` 空串按 JS 真值回退、`syncRoot` 补尾斜杠、`safeFileName` 非 `[A-Za-z0-9._-]` → `_` |
| **`SyncState`** | `data/repository/SyncEngine.kt` | `manifest` + `ready`（已建目录标记），跨多次同步保留 |
| `SyncOutcome` / `createSyncState` / `isSyncConfigured` | 同上 | `isSyncConfigured` 必须 `enabled` 且地址/账号/应用密码齐全 |
| **`pushChanges`** | 同上 | 只传「本地 `updatedAt` > 清单」的窗口；改动则重写 `index.json` |
| **`pullChanges`** | 同上 | 只拉「远端 `updatedAt` > 本地」的窗口；拉下来先过 `normalizeChatSession` |
| `SettingsViewModel` 的 WebDAV 方法 | `ui/settings/SettingsViewModel.kt` | `saveWebDavConfig` / `testWebDav` / `pushBackupToWebDav`（`includeApiConfig != true` 即擦密钥）/ `pullBackupFromWebDav`（拉到即导入整库） |
| `BackupPanel` | `ui/settings/SettingsPanels.kt` | WebDAV 配置输入 + 自动同步/含密钥开关 + 测试/同步/拉取/保存按钮 + 状态文案 + 本地导入导出 |
| App 接线 | `ui/App.kt` | 启动 `pullChanges` → 合并 → `SessionRepository.replaceAll`（**不盖 `updatedAt`**）→ `sessionViewModel.load()`；`sessionState.sessions` 变动后 3s 防抖 `pushChanges` |

测试：`desktopTest/net/WebDavClientTest.kt`（13，进程内 `FakeWebDavServer`）、
`desktopTest/net/FakeWebDavServer.kt`（假上游）、`desktopTest/data/SyncEngineTest.kt`（7）、
`desktopTest/net/WebDavLiveSmokeTest.kt`（`WW_T19_LIVE=1` 跳过）。

⚠️ **合并拉取结果必须用 `SessionRepository.replaceAll`，不能用 `save`**：`save` 会盖 `updatedAt`，
把远端时间戳改成本机时间，下一轮又反向推送。`SyncEngineTest.pulledUpdatedAtSurvivesReplaceAll` 钉住这一点。

---

## §N17 前文总结 / 角色关系分析（T20）

> 两个「窗口级 AI 分析功能」。总结的省钱关键不在加总结，而在**推进 `lastSummarizedFloor`**
> （T08 的 `dropSummarizedFloors` 据此剔除已总结原文）。
> 两者都是一次**非流式、无工具**调用，复用 T09 的 `runToolChat`。

| 符号 | 位置 | 说明 |
|---|---|---|
| `SUMMARY_SYSTEM_INSTRUCTION` | `domain/Summary.kt` | 总结的 system 提示词（`App.tsx:504` 逐字） |
| **`summaryRange`** / `SummaryRange` | 同上 | 待总结范围与「对 AI 隐藏」条数（`SummaryModal` 的 `useMemo`） |
| `unsummarizedMessages` | 同上 | `floorNumber >= lastSummarizedFloor + 1` |
| `buildSummaryTranscript` | 同上 | `#<楼号> [<codeTag>] <用户\|AI>：<正文>`；正文链 `versions[idx]→versions[0]→content` |
| `buildSummaryRequest` / `resolveSummaryModel` | 同上 | 请求拼接 / 模型回退链（override>session>prefs.summary>provider） |
| `DEFAULT_SUMMARY_PROMPT` | `data/DefaultData.kt` | `defaultData.ts:211` 逐字 |
| `ROLE_ANALYSIS_SYSTEM_INSTRUCTION` 及常量 | `domain/RoleAnalysis.kt` | 分析提示词 / `codeTag="分析"` / `descriptionTag="角色心理博弈"` / `wg-analysis` / 占位正文 |
| `currentBranchInfo` / `BranchInfo` | 同上 | `codeTag\|\|tag\|\|'通用'`、`descriptionTag\|\|name\|\|'主线'` |
| `buildRoleAnalysisContext` | 同上 | `[<codeTag\|通用>] #<楼号\|1> <用户\|AI>: <正文>`（与总结格式**不同**） |
| `buildRoleAnalysisFallback` | 同上 | 调用失败时的 `### 📊 角色关系分析 … 分析功能暂不可用` |
| **`ChatViewModel.generateSummary`** | `ui/chat/ChatViewModel.kt` | 取未总结楼层 → 一次性请求 → 追加 `SessionSummary` + 推进 `lastSummarizedFloor`；失败写 `summaryError` |
| `ChatViewModel.saveSummaryConfig` / `deleteSummary` / `clearSummaries` | 同上 | 配置与增删清；⚠️ `clearSummaries` **不**回退 `lastSummarizedFloor` |
| **`ChatViewModel.analyzeRoleRelationships`** | 同上 | 插入 `isAnalysis` 占位消息 → 一次性请求 → 回填报告；失败写兜底报告 |
| `ChatViewModel.toggleAnalysisVisibility` / `archiveAnalysisToDocument` | 同上 | 可见性开关（同时决定**是否进模型上下文**）/ 归档到 `wg-analysis` 世界观文档 |
| **`dropHiddenAnalysisMessages`** | `domain/RoleAnalysis.kt` | 剔除 `isAnalysis && analysisVisibility == HIDDEN` 的消息；供正式对话/总结/下一次分析三处共用 |
| **`oneShotText` / `isOneShotFailure`** | 同上（private） | 一次性调用；⚠️ **把 `runToolChat` 的失败 note 与空串转成异常** |
| `SummaryPanel` | `ui/summary/SummaryPanel.kt` | 总结面板 + 分析触发；⚠️ 是 `FeatureDialog` 内容区，不要再套整页滚动 |
| `MessageBubble` 分析徽章 | `ui/chat/MessageBubble.kt` | `isAnalysis` 的徽章 + 「切换可见性」/「归档到世界观」 |
| `SidebarSheet.onOpenSummary` | `ui/sidebar/SidebarSheet.kt` | 底部导航第 9 个入口（`TablerIcons.FileText`） |

⚠️ `oneShotText` 复用 `runToolChat`，但它**失败/空回复写 note 不抛**：空回复返回 `[模型未返回最终正文]`。`oneShotText` 必须把这些 note 当失败转异常，否则总结会把提示句当正文存下。

⚠️ **`analysisVisibility == HIDDEN` 的分析报告不进模型上下文**（2026-10-09 补）：`ContextBuilder.build` 第 0 步先调 `dropHiddenAnalysisMessages`，`buildSummaryRequest` / `buildRoleAnalysisUserPrompt` 也各自过滤；`ChatViewModel.requestLogin`/ 一次性请求均经此。**Web 版同步改了**（`api.ts` 的 `buildContext`、`App.tsx` 的总结/分析消息选取）。

---

## 变更记录

- 2026-10-09　T21 完工：新增 §N17（图片存储 —— `ImageStore` 框架 + `FileImageBlobStore` 实现 + `compressImageBytes` 实现）；`ImageStore.desktop.kt` 标 ✅ T21；`ImageBlobStore` 接口、`ImageStore` 类、`dataUrlBytes`、`compressImageBytes` 标 ✅ T21。
- 2026-10-09　T20 完工：新增 §N17（前文总结 / 角色关系分析）；§C `analyzeRoleRelationships` 标 ✅ T20。
- 2026-10-09　T20 续：隐藏分析报告真正从模型上下文过滤（`dropHiddenAnalysisMessages`，三处），Web 版同步。
- 2026-10-09　T19 完工：新增 §N16（WebDAV 同步 —— `WebDavClient` 直连 + `SyncEngine` 增量）；§G 补 Kotlin 列（全部 ✅ T19，⚠️ 图片归 T21）；§B `syncEngine.ts` 标 ✅ T19。
- 2026-10-09　T18 完工：新增 §N15（世界观库 · 嵌套分组树）；§B `getWorldDocuments` 的 `group`→`groupId` 迁移由 ⏳ T18 改标 ✅ T18；
  新增本仓第一条 Room 迁移 `MIGRATION_1_2`（v1→v2，`WwDatabase` + `WwMigrations.kt` + `schemas/2.json` + `WwDatabaseMigrationTest`）。

- 2026-10-09　T17 完工：新增 §N14（时间线树 + 描述标签审批流）；§E `timelineMemory.ts` 标 ✅ T17 补齐、`timelineDescriptionTagControl.ts` 标 ✅ T17；§N6 补记「时间线树提示词改为 ContextBuilder 内部构建」。

- 2026-10-09　T16-接线 完工：新增 §N13（侧边栏功能入口 · FeatureDialog 弹窗壳）；`FeatureDialog.kt` 新建（居中浮动 + 随内容自适应 + 最大 85% 屏幕高 + 固定标题栏 + ✕ 关闭）；`SidebarSheet` 新增 7 个回调、`SidebarNavGrid` 全部接线；`App.kt` 新增 7 个弹窗状态 + 接线 + 设置弹窗也改走 FeatureDialog(scrollable=false)；`SettingsHub` 移除冗余标题与关闭按钮。
- 2026-10-08　T16 完工：§I `handleSwitchVersion`/`handleRollMessage`/`handleEditContent`/`handleDeleteMessage` 标 ✅ T16；新增 §N12（消息级操作 `switchVersion` / `deleteMessage` / `editAndResend` / `regenerate` / `regenerateInto` / `MessageActions`）。
- 2026-10-08　T14 完工：新增 §N10（会话管理 / 侧边栏 / 正式请求链路）；§C `sendChatMessage` 的「请求装配/采样回退链」标 ✅ T14；§N2 的 `ChatScreen` 条目更新为「T14 起吃正式 `ChatSession` + `ChatMessage`」。
- 2026-10-07　T11 完工：新增 §N9（联网搜索 / Skill 目录 `WebSearchClient`）；§F `webSearch.ts`、`server.ts` 的搜索行标 ✅ T11（⚠️ `server.ts` 的 DDG 解析是旧版，有意不采用）。
- 2026-10-07　T10 完工：新增 §N8（MCP 客户端 `McpClient` / 两种 transport / `testConnection` / `explainMcpError`）；§D `mcpApi.ts`、`server.ts` 的 MCP 行标 ✅ T10。
- 2026-10-07　§C `sendChatMessage` 的「请求装配/采样回退链」归属由 ⏳ T09 改为 ⏳ T14（T09 未做，WORKPLAN 已把它划入 T14）。
- 2026-10-07　T09 完工：新增 §N7（工具轮次循环 `runToolChat` / `registerTools` / `McpToolCaller`）；§C `sendNativeToolChat`、工具名注册段、`receive` 标 ✅ T09。
- 2026-10-07　T08 完工：新增 §N6（上下文拼装）；§C `sendChatMessage`、§D `mcpAvailability.ts`、§E `regexProcessor.ts`/`timelineMemory.ts` 标 ✅ T08。
- 2026-10-07　T07 完工：新增 §N5（存储层 —— `StorageKeys`、`WwDatabase`/`withTransaction`、四个 Repository + `ArchiveStore`、
  平台层三个 expect）；§B 的 `STORAGE_KEYS`/`safeGet`/`safeSet`/`Storage` get-set 标 ✅ T07。
- 2026-10-07　T06 完工：新增 §N4（存档层 —— 六条兼容逻辑的**两层分工**、`ArchiveRepository` 的四个入口、
  `currentTimeMillis` 的 expect/actual）；§B 的 Kotlin 列补齐（`normalizeChatSession`/`normalizeMcpServer`/
  `exportAllData`/`importAllData` 标 ✅ T06，`STORAGE_KEYS`/`Storage` get-set 标 ⏳ T07，
  `group`→`groupId` 标 ⏳ T18，`getAppPreferences` 合并标 ⏳ T15）。
- 2026-10-06　T04 进行中：恢复 React 规格 §C；§N3 补入 SSE parser 与三家 adapter 入口；记录 Claude 样本纯度/格式已由用户确认通过，原文未入库。
- 2026-10-06　【补充 T03 后续工作】记录 OpenAI fixture 采集成功、Claude quota 耗尽、Gemini 无 Key；
  采集脚本 User-Agent 注入、错误隔离、密钥保护等设计要点。
- 2026-10-06　新增 §N3（T03 协议层）；§N2 标记 `MockChatStream` 已删。
- 2026-10-06　新增 §N2（T02）。
- 2026-10-06　§J 改名为 §K，腾出 J；新增「原生版（Kotlin）现状」与 §N1（T01）。

- 2026-10-06　建立；行号采集自 React 版当日状态。
