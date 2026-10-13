# 观界 WatchWorld · Kotlin + Compose Multiplatform 原生迁移技术方案

> 版本 1.0 ｜ 目标产物：Android APK + Windows 桌面 `.exe`（**放弃 WebView / 浏览器版**）
> 本文档基于对现有代码库的**实测统计**编写，非估算。所有"现状"数字均可在仓库中复核。

---

## 0. 摘要（先读这一节）

### 0.1 为什么要迁移

现有 APK 是 Capacitor 套壳（WebView + React）。经过对 Capacitor 源码的完整溯源，确认了一个**无法在 Capacitor 生态内解决的硬限制**：

```
@capacitor/android native-bridge.js
  ├─ window.fetch          → cap.nativePromise('CapacitorHttp','request', …)
  └─ XMLHttpRequest.send   → 同一个 CapacitorHttp.request
                                    ↓
HttpRequestHandler.java: readData() → readStreamAsString()
  while ((in.read(buffer)) != -1) { … }   ← 循环读到底才回传 JS
```

**结论：Capacitor 的 `CapacitorHttp` 是非流式的，且是生态内唯一 HTTP 通道**（已核实 capawesome 100+ 插件清单中无任何 HTTP 客户端插件）。因此 WebView 里永远拿不到首字增量，只能靠 `replayAsStream()` 事后回放伪装流式。

迁移到原生后，**首 token 延迟、逐字输出、光标闪烁、气泡截断全部是真实现象，不再是伪装**。

### 0.2 迁移的三个真实收益

| 收益 | 说明 |
|---|---|
| **真流式** | Ktor `ByteReadChannel` 增量读，首字 1–2s 内出现；无需 Worker 中转 |
| **真正离线可用** | 移除 Worker / server.ts 依赖；模型直连、搜索走原生网络栈 |
| **双端一份代码** | `commonMain` 同时产出 Android 与 Windows，无 Electron 运行时负担 |

### 0.3 工作量总览

| 阶段 | 内容 | 工期 |
|---|---|---|
| P0 | 工程骨架 + 数据模型 + 存储层 | 1.5 周 |
| P1 | 协议层（Ktor 流式 / 工具循环 / MCP / WebDAV） | 3 周 |
| P2 | 聊天主界面 + Markdown 流式渲染 | 2 周 |
| P3 | 设置类 15+ 弹窗 + 主题系统 | 2.5 周 |
| P4 | 时间线树 / 世界书 / 知识库 | 2 周 |
| P5 | Android 平台层 + 桌面适配 + 打包 | 1.5 周 |
| | **合计（全职熟练）** | **约 12–13 周（3 个月）** |
| | **业余时间（每晚 2–3h）** | **约 6–8 个月** |

### 0.4 强烈建议的推进顺序

**桌面优先，Android 跟随。** 理由见 §7.1：

1. 桌面调试循环比 Android 快 5–10 倍（无 Gradle 同步、无真机连接、无 `cap sync`）
2. `commonMain` 代码自动双端可用，桌面调通的 Ktor 流式客户端，搬到 Android 只换引擎依赖
3. **P0–P4 结束即可交付可用 `.exe`**，不必等 Android 平台层完事

---

## 1. 现状盘点（实测数据）

以下全部为仓库实测，用于校准工作量。

### 1.1 代码规模

| 指标 | 实测值 |
|---|---|
| TS/TSX 源码 | **57 文件 / 20,566 行** |
| `src/lib/` 逻辑层 | **19 文件 / 4,783 行** |
| React 组件 | **34 个** |
| 全局样式 | 288 行（`index.css`，含 CSS 变量主题） |
| 后端代理 | `server.ts` **756 行 / 9 个端点** |
| Cloudflare Worker | `worker/src/index.ts`（本次新增，迁移后废弃） |

### 1.2 `src/lib/` 模块清单（逻辑层 = 迁移核心）

```
   520  api.ts                        ← 请求组装 + 上下文拼装（最复杂）
    73  apiBase.ts                    ← 中转地址解析（迁移后废弃）
   355  apiNative.ts                  ← 原生直连封装（迁移后废弃）
   233  appFolders.ts                 ← 应用目录管理 ★桌面需要
    91  configTextImporter.ts          ← 配置文本导入解析
   362  defaultData.ts                ← 默认数据（Provider/Agent/...）
   194  imageStore.ts                 ← IndexedDB 图片存储 → 需换实现
   194  mcpApi.ts                     ← MCP 工具调用
    54  mcpAvailability.ts            ← MCP 可用性判定
   480  nativeToolChat.ts             ← 工具循环 + SSE 解析（流式核心）
    45  regexProcessor.ts             ← 正则规则处理
   410  storage.ts                    ← 21 个 localStorage 键（三向同步纪律）
   121  syncEngine.ts                 ← 增量同步引擎
   279  theme.ts                      ← 主题系统（CSS 变量 → 需重建）
   216  timelineDescriptionTagControl.ts  ← 时间线描述标签审批流
   401  timelineMemory.ts             ← 时间线树 + 消息重排（特色功能）
    48  tokenEstimator.ts             ← Token 估算
   259  webdavSync.ts                 ← WebDAV 同步
   448  webSearch.ts                  ← 联网搜索 + Skill 搜索
```

### 1.3 数据模型（`src/types.ts`，24 个 interface）

```
ApiProviderConfig      ModelFavorite          Agent                  PromptPreset
MessageVersion         TimelineBranch         TimelineDescriptionTagRequest
ChatMessage            MessageDisplaySettings DefaultModelConfig     DefaultSessionConfig
AppPreferences         ChatGroup              RegexRule              KnowledgeItem
McpTool                McpServerConfig        AgentSkill             CustomTheme
WebDavConfig           WorldGroup             WorldDocument          ChatSession
SessionSummary
```

其中两个**巨型结构**是迁移难点的核心：

- **`ChatSession`**（~64 个字段）：聚合消息列表、时间线树、总结、知识库连接、正则绑定、世界书绑定、采样参数、UI 控制开关。典型的"上帝对象"，迁移时必须**原样保留字段语义**，否则存档不兼容。
- **`ChatMessage`**（19 个字段）：含 `versions[]` 版本数组（滚动重答）、`floorNumber` 楼层号、`timelineTag`、`imageIds` 图片引用。

### 1.4 持久化现状

**21 个 localStorage 键**（`storage.ts:38-60`）：

```
omnichat_providers          omnichat_active_provider_id   omnichat_groups
omnichat_agents             omnichat_prompts              omnichat_sessions
omnichat_active_session_id  omnichat_regex_rules          omnichat_knowledge_base
omnichat_theme_palette      guanjie_custom_themes         omnichat_ui_mode
omnichat_mcp_servers        omnichat_skills               omnichat_display_settings
guanjie_webdav_config       guanjie_world_documents       guanjie_world_groups
guanjie_draft_*（前缀）      guanjie_model_favorites       guanjie_app_preferences
```

**另有 IndexedDB** 用于图片二进制（`imageStore.ts`，194 行）。

> ⚠️ **迁移红线**：`exportAllData()` 导出格式为 `version: 5`，被 WebDAV 远端备份依赖。原生版的导入/导出必须**字节级兼容该格式**，否则现有存档无法恢复。详见 §5.4。

### 1.5 后端协议（`server.ts`，756 行 / 9 端点）

这是 Kotlin 侧需要实现的**完整规格书**：

| 端点 | 职责 | 迁移去向 |
|---|---|---|
| `GET  /api/health` | 健康检查 | ❌ 原生不需要 |
| `POST /api/web-search` | DDG 搜索（含 HTML 解析） | ✅ 原生 Ktor 直连 |
| `POST /api/skill-search` | agent-skills.md oRPC 代理 | ✅ 原生 Ktor 直连 |
| `POST /api/skill-preview` | Skill 详情 | ✅ 原生 Ktor 直连 |
| `POST /api/mcp/test` | MCP 连通性探测 | ✅ 归入 MCP 客户端 |
| `POST /api/models` | 拉取模型列表 | ✅ 原生 Ktor 直连 |
| `POST /api/chat` | **聊天（含 SSE 转发）** | ✅ 原生 Ktor 流式 |
| `GET  /api/check-quota` | 配额查询 | ✅ 原生 Ktor |
| `*` static | 静态文件托管 | ❌ 原生不需要 |

**关键洞察**：`server.ts` 里 `web-search` 的 DDG HTML 解析（`parseDuckDuckGoResults` / `unwrapSearchUrl` / `stripSearchHtml`）是**纯字符串逻辑，可 1:1 翻译成 Kotlin**，零设计成本。

### 1.6 流式现状（最关键的迁移对象）

`nativeToolChat.ts` 有三条数据路径，迁移后合并为一条：

```
现状（WebView）
├─ viaProxy = true   → Worker /api/chat → readNativeEvents() 真流式 ✅
├─ viaProxy = false  → 直连非流式 → toPseudoEvents() → replayAsStream() 伪装 ⚠️
└─ (Android 特例)     → readSseWithXhr() 实测只有 1 次 drain，等同非流式 ❌

迁移后（原生）
└─ Ktor preparePost().execute{} → ByteReadChannel.readUTF8Line() 真流式 ✅
   （toPseudoEvents / replayAsStream 全部删除，不再需要）
```

**沉淀下来的、必须保留的核心逻辑**（与传输方式无关）：

1. **`sseLine()`**（`nativeToolChat.ts:73-92`）— SSE 行解析状态机：空行派发、`data:` 累积、`[DONE]` / `message_stop` 结束、错误帧抛异常
2. **三家 Provider 的事件归一化**（`nativeToolChat.ts:354-395`）：
   - OpenAI: `choices[0].delta` → `content` / `reasoning_content` / `tool_calls`
   - Claude: `content_block_start` / `content_block_delta`（`text_delta` / `input_json_delta` / `thinking_delta` / `signature_delta`）
   - Gemini: `candidates[0].content.parts[]` + `thought` 标记 + `promptFeedback.blockReason`
3. **工具名注册规则**（`nativeToolChat.ts:200-214`）：用**原始工具名**，仅在跨服务重名时追加 `_2`/`_3`，且必须与 `api.ts` 注入 system 的 `mcpContext` 清单**完全一致**，否则模型会调用不存在的工具
4. **工具轮次循环**：`toolCallStepLimit` 控制上限，0 = 无限制
5. **内置 Skill 工具** `LOAD_SKILL_TOOL_NAME`：`server` 为 null 的特殊工具，在执行分支单独拦截

### 1.7 特色功能复杂度（这些决定 UI 工作量）

| 功能 | 核心文件 | 迁移难度 | 说明 |
|---|---|---|---|
| **时间线分支树** | `timelineMemory.ts` (401) + `TimelineModal.tsx` (879) | 🔴 高 | 树形展开、分支切换、`rearrangeMessagesForAi` 消息重排、描述标签审批流 |
| **世界书（正典库）** | `WorldDocument` + `KnowledgeBaseModal.tsx` (369) | 🟠 中 | 任意嵌套分组，`groupId` 树 |
| **正则处理器** | `regexProcessor.ts` (45) + `RegexModal.tsx` (619) | 🟢 低 | 纯文本替换，逻辑简单但 UI 大 |
| **MCP 客户端** | `mcpApi.ts` (194) + `McpModal.tsx` (474) | 🟠 中 | 需 SSE + StreamableHTTP 两种 transport |
| **WebDAV 同步** | `webdavSync.ts` (259) + `syncEngine.ts` (121) | 🟠 中 | PROPFIND / PUT / GET，增量同步 |
| **主题系统** | `theme.ts` (279) + 288 行 CSS | 🔴 高 | CSS 变量 → 需重建为 Compose 主题体系 |
| **消息版本/滚动重答** | `MessageVersion[]` | 🟠 中 | 版本切换 UI + 重新生成 |
| **Markdown 流式渲染** | `marked` + `MessageItem.tsx` (559) | 🔴 高 | 增量渲染不能全量重排，否则卡顿 |

---

## 2. 技术选型

### 2.1 版本矩阵（已核对官方 release，2026-10）

| 组件 | 版本 | 用途 | 备注 |
|---|---|---|---|
| Kotlin | 2.2.x | 语言 | KMP 稳定 |
| **Compose Multiplatform** | **1.12.1** | UI（Android + Desktop） | 2026-09-22 发布，最新 stable |
| **Ktor** | **3.6.0** | HTTP 客户端/服务端 | 2026-09 发布，`ByteReadChannel` 流式成熟 |
| kotlinx-serialization | 1.9.x | JSON | 替代 `JSON.parse/stringify` |
| kotlinx-coroutines | 1.10.x | 异步 | `Flow` 承载流式增量 |
| AndroidX Room (KMP) | 2.8.x | 存储 | 支持 KMP，Desktop 可用 |
| SQLDelight（备选） | 2.1.x | 存储 | 若 Room KMP 在桌面有阻碍则切换 |

> ⚠️ **不要用 Compose MP 的 alpha/beta**（如 `1.13.0-alpha01`）。桌面端 IME 与渲染稳定性，stable 和 alpha 差距明显。
> ⚠️ Material3 版本：Compose MP 1.12.x 对应 `org.jetbrains.compose.material3:material3:1.5.x`，直接用 `compose.material3` 别名即可。

### 2.2 存储方案决策：Room KMP vs SQLDelight

| 维度 | Room KMP | SQLDelight |
|---|---|---|
| 与 Compose/Android 亲和 | 高（官方 AndroidX） | 中 |
| 桌面端成熟度 | 2.7+ 支持，仍有小坑 | 更早支持，稳定 |
| 复杂查询（时间线树） | `@Relation` / `@Transaction` 方便 | 手写 SQL |
| 大 JSON 字段 | 需 `TypeConverter` | 需自定义 ColumnAdapter |

**建议：Room KMP**。理由：你的数据模型是**强结构化 + 大量嵌套**（`ChatSession` 含 `messages[]` / `timelines[]` / `summaries[]`），Room 的 `@Relation` 比 SQLDelight 手写 JOIN 省很多事。

**但有一个例外必须处理**：`ChatSession` 64 字段里有十几个是"开关/配置"（`streamingEnabled`、`uiRenderLimit`、`aiContextVisibility`...）。这些**不要拆表**，直接用一个 JSON 列 + `TypeConverter` 存整个 `SessionConfig` 对象。拆表会让迁移成本和出错率暴涨。

**表结构建议（详见 §5）**：

```
providers( id PK, ... , customHeadersJson, defaultParamsJson )
agents( id PK, ... , tagsJson, mcpServerIdsJson, skillIdsJson )
sessions( id PK, agentId, groupId, configJson, createdAt, updatedAt, pinned )
messages( id PK, sessionId FK, role, versionsJson, currentVersionIndex, ... )   ← 关键：拆出来
timelines( id PK, sessionId FK, parentId, ... )
summaries( id PK, sessionId FK, fromFloor, toFloor )
worldGroups / worldDocuments / knowledge / regexRules / prompts / skills / mcps / themes / favorites
kv( key PK, value )   ← 杂项 + 草稿
```

**`messages` 必须从 `sessions` 里拆出来**。原因：一个重度会话可能有几千条消息，若全塞在 `sessions.configJson` 里，每次保存会话都要序列化整个消息数组 —— 在 Compose 的 `StateFlow` 更新里会造成明显卡顿，也会让 SQLite 单行体积失控。

### 2.3 HTTP 引擎决策

| 平台 | 引擎 | 理由 |
|---|---|---|
| Android | `ktor-client-okhttp` | 流式成熟、连接池好、支持 HTTP/2 |
| Desktop (JVM) | `ktor-client-okhttp` | 相同引擎可最大化行为一致性 → **统一用 OkHttp** |

**统一 OkHttp 的好处**：桌面调试通过的行为，在 Android 上几乎不会变。

### 2.4 Markdown 渲染决策

现有 Web 用 `marked`。Compose 侧选项：

| 方案 | 评价 |
|---|---|
| `compose-markdown` (mikepenz) | 功能全，但对**流式增量**支持需自己包一层 |
| `Markwon`（Android only） | 成熟但**不支持 Desktop**，破坏双端一致性 |
| `richtext-commonmark` | KMP 友好，较轻量 |
| 自己写简化 parser | 可控但耗时 |

**建议：`compose-markdown`（mikepenz）**。它是 Compose 原生（非 Android View），双端可用。**关键必须自己实现流式适配**（见 §7.3）。

---

## 3. 目标工程结构

```
WatchWorld-Native/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml           ← 版本集中管理
│
├── shared/                             ← KMP 共享模块
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/watchworld/
│       │   ├── model/                  ← §4.1：24 个 @Serializable data class
│       │   │   ├── Provider.kt / Agent.kt / Session.kt / Message.kt / Timeline.kt ...
│       │   ├── data/                   ← §5：存储层
│       │   │   ├── db/                 ← Room entities + DAO
│       │   │   ├── repository/
│       │   │   └── migration/          ← v5 JSON ↔ DB 双向
│       │   ├── net/                    ← §6：协议层
│       │   │   ├── HttpClientFactory.kt
│       │   │   ├── ChatStreamClient.kt ← 真流式核心
│       │   │   ├── SseParser.kt        ← sseLine() 的 Kotlin 版
│       │   │   ├── provider/           ← OpenAI / Claude / Gemini 适配
│       │   │   ├── ToolCallLoop.kt     ← 工具轮次循环
│       │   │   ├── WebSearchClient.kt  ← DDG 解析
│       │   │   ├── McpClient.kt
│       │   │   └── WebDavClient.kt
│       │   ├── domain/                 ← 纯业务逻辑（无 UI 无 IO）
│       │   │   ├── TimelineMemory.kt   ← rearrangeMessagesForAi
│       │   │   ├── RegexProcessor.kt
│       │   │   ├── TokenEstimator.kt
│       │   │   └── ContextBuilder.kt   ← api.ts 的 system 拼装逻辑
│       │   ├── platform/               ← expect 声明
│       │   │   ├── FileSystem.kt / SecureStore.kt / ImageStore.kt
│       │   └── ui/                     ← Compose UI（双端共享）
│       │       ├── theme/ chat/ settings/ timeline/ worldbook/ components/
│       ├── androidMain/kotlin/         ← Android 平台实现
│       └── desktopMain/kotlin/         ← Desktop 平台实现
│
└── (可选) androidApp/ desktopApp/      ← 独立启动器模块
```

**模块划分原则**：
- `model` / `domain` / `data` / `net` **零 Compose 依赖** → 可单独写单元测试
- `ui` 依赖 Compose，但不依赖平台 API
- `platform` 用 `expect/actual` 隔离，只有这里需要分平台写两份

---

## 4. 数据模型迁移

### 4.1 迁移原则

1. **字段名保持完全一致**（含 `id` / `createdAt` / `updatedAt`）→ 保证 JSON 存档互通
2. **可选字段用 `= null` 默认值**，不用 `lateinit` → 老存档缺字段时不炸
3. **不要"改良"字段名或结构**。看着别扭的 `codeTag` / `descriptionTag`，改造它就等于打破存档兼容
4. `kotlinx.serialization` 的 `@SerialName` 在字段名与 Kotlin 命名冲突时才用

### 4.2 示例：`ChatMessage`（19 字段）

```kotlin
// shared/src/commonMain/kotlin/com/watchworld/model/Message.kt
package com.watchworld.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 消息的一个版本（滚动重答产生多个版本，用户可切换）。 */
@Serializable
data class MessageVersion(
    val content: String,
    val timestamp: Long,
    val model: String? = null,
    val providerName: String? = null,
    val latencyMs: Long? = null,
    val tokens: Int? = null,
    /** 思维链原文（reasoning/thinking），仅展示，不进入后续上下文 */
    val reasoning: String? = null,
)

/** 一条聊天消息。字段与 src/types.ts 的 ChatMessage 一一对应。 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val versions: List<MessageVersion> = emptyList(),
    val currentVersionIndex: Int = 0,
    val content: String = "",
    val timestamp: Long = 0L,
    val latencyMs: Long? = null,
    val tokens: Int? = null,
    val timelineId: String? = null,          // 关联的时间线 ID
    val timelineTag: String? = null,         // 缓存的时间线标签显示
    val floorNumber: Int? = null,            // 楼号系统 (PRD 4.2)
    val codeTag: String? = null,             // 归属分支编号标签（如 A12、通用）
    val descriptionTag: String? = null,      // 归属分支描述标签（如 学园线·结识好友）
    val isCommon: Boolean? = null,           // 是否标记为通用信息 (PRD 4.5)
    val isAnalysis: Boolean? = null,         // 是否为心理动力学角色关系分析报告
    val analysisVisibility: AnalysisVisibility? = null,
    val imageIds: List<String>? = null,      // 图片引用（本体存文件系统）
    val analysisBranchCode: String? = null,
)

@Serializable
enum class Role {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("system") SYSTEM,
}

@Serializable
enum class AnalysisVisibility {
    @SerialName("visible") VISIBLE,
    @SerialName("hidden") HIDDEN,
}
```

> 📌 **`Role` 用 enum + `@SerialName`**：TypeScript 的字符串联合类型在 Kotlin 里最贴切的表达。若直接写 `String` 会丢类型安全；注意 enum 反序列化对未知值的容错（老存档若含新角色会抛异常 → 见 §4.4 容错策略）。

### 4.3 最难的一块：`ChatSession` 的 64 字段

`ChatSession` 不能照直翻译成一个 data class 塞进数据库单行（见 §2.2 理由）。**拆成三部分**：

```kotlin
// 1) 会话的"配置部分"—— 一个独立 @Serializable 对象，JSON 存一列
@Serializable
data class SessionConfig(
    val providerId: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val maxTokens: Int? = null,
    val toolCallStepLimit: Int? = null,
    val systemPromptOverride: String? = null,
    val systemPromptFixed: String? = null,       // 用户手写的固定提示词
    val systemPromptInjectedCommon: String? = null,
    val isCommonPushed: Boolean? = null,
    val tagDisplayMode: TagDisplayMode? = null,
    val aiContextVisibility: AiContextVisibility = AiContextVisibility.ALL,
    val aiContextCustomLimit: Int? = null,
    val uiRenderLimit: Int = 0,
    val connectedKnowledgeIds: List<String> = emptyList(),
    val connectedWorldDocIds: List<String>? = null,
    val regexRuleIds: List<String>? = null,
    val timelineMemoryEnabled: Boolean? = null,
    val activeTimelineId: String? = null,
    val timelineDescriptionTagRequests: List<TimelineDescriptionTagRequest>? = null,
    val summaryPrompt: String? = null,
    val summaryModel: String? = null,
    val lastSummarizedFloor: Int? = null,
    val imageContextMode: ImageContextMode? = null,
    val webSearchEnabled: Boolean? = null,
    /** undefined 视为流式（老存档兼容）→ Kotlin 用 null 表示"未设置" */
    val streamingEnabled: Boolean? = null,
)

// 2) 会话本体（DB 行）—— 只放检索/排序需要的列 + config JSON
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val groupId: String,
    val agentId: String,
    val pinned: Boolean? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val configJson: String,                  // SessionConfig 的 JSON
)

// 3) 内存态聚合对象（UI 用）—— 把 messages/timelines/summaries 组装回来
data class ChatSession(
    val id: String,
    val title: String,
    val groupId: String,
    val agentId: String,
    val config: SessionConfig,
    val messages: List<ChatMessage> = emptyList(),
    val timelines: List<TimelineBranch>? = null,
    val summaries: List<SessionSummary> = emptyList(),
    val pinned: Boolean? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
```

**⚠️ 必须注意的语义坑**：

原 TS 里 `streamingEnabled?: boolean` 用 `undefined` 表示"老存档 → 视为流式"，`false` 表示明确关闭。Kotlin 的 `Boolean?` 用 `null` 表达 `undefined`，语义等价，**但序列化时必须确认 `null` 不被写成 `false`**（见 §4.4 的 `explicitNulls` 配置）。

`storage.ts:106-108` 原注释：
```ts
// 流式开关：undefined 视为流式（老存档兼容），显式 boolean 才写入。
```
迁移后 `SessionConfig.streamingEnabled: Boolean?` 保持这个三态语义。

### 4.4 JSON 兼容配置（决定存档能否互通）

```kotlin
// shared/src/commonMain/kotlin/com/watchworld/model/Json.kt
val WwJson = Json {
    ignoreUnknownKeys = true    // 新版字段出现在老存档时忽略，不抛异常
    isLenient = true
    explicitNulls = false       // 保持默认 false
    encodeDefaults = false
    coerceInputValues = true    // null 出现在非空字段时退回默认值
}
```

**验证方法**：写往返测试 —— 用现有 Web 版 `exportAllData()` 的产物反序列化，再序列化，比对关键字段。见 §8.1。

---

## 5. 存储层设计

### 5.1 表结构全貌

```sql
-- 配置类（数据量小，直接一表一实体）
providers          (id PK, name, type, baseUrl, apiKey, defaultModel,
                    modelsJson, isCustom, enabled, isSystemDefault,
                    customHeadersJson, defaultTemperature, defaultTopP,
                    defaultTopK, defaultFrequencyPenalty, defaultPresencePenalty, defaultMaxTokens)
agents             (id PK, name, avatar, description, systemPrompt, temperature, topP,
                    contextMessageLimit, isDefault, tagsJson, isBuiltin, model, memory,
                    mcpServerIdsJson, skillIdsJson, fileAccess)
prompts            (id PK, title, category, content, description, tagsJson)
regexRules         (id PK, ...)
knowledgeBase      (id PK, ...)
mcpServers         (id PK, ...)
skills             (id PK, ...)
worldGroups        (id PK, name, parentId)
worldDocuments     (id PK, title, groupId, content, enabled, createdAt, updatedAt, tagsJson)
customThemes       (id PK, ...)
modelFavorites     (providerId, modelName, addedAt)   -- 复合主键
groups             (id PK, ...)                       -- ChatGroup

-- 核心（拆表，见 §2.2）
sessions           (id PK, title, groupId, agentId, pinned, createdAt, updatedAt, configJson)
messages           (id PK, sessionId FK→sessions.id, role, versionsJson,
                    currentVersionIndex, content, timestamp, latencyMs, tokens,
                    timelineId, timelineTag, floorNumber, codeTag, descriptionTag,
                    isCommon, isAnalysis, analysisVisibility, imageIdsJson, analysisBranchCode,
                    sortIndex)                        -- 保序用
timelines          (id PK, sessionId FK, name, tag, codeTag, descriptionTag, visible,
                    color, description, plotSummary, keyMilestonesJson, messageIdsJson,
                    parentId, forkMessageId, createdAt, updatedAt)
summaries          (id PK, sessionId FK, content, fromFloor, toFloor, model, createdAt)

-- 杂项
kv                 (key PK, value)                    -- 主题调色板 / uiMode / displaySettings /
                                                      -- activeSessionId / activeProviderId /
                                                      -- webdavConfig / appPreferences / 输入草稿
```

**索引建议**：
```sql
CREATE INDEX idx_messages_session  ON messages(sessionId, sortIndex);
CREATE INDEX idx_timelines_session ON timelines(sessionId);
CREATE INDEX idx_summaries_session ON summaries(sessionId);
CREATE INDEX idx_sessions_updated  ON sessions(updatedAt DESC);
```

### 5.2 三向同步纪律（现有设计的核心约束，必须保留）

现有 `storage.ts` 有一套**三向同步纪律**：本地存储 ↔ WebDAV 远端 ↔ 导入/导出 JSON，三者必须一致。迁移到 SQLite 后，这套纪律**不能破坏**。

```
                 ┌──────────────────────┐
                 │   Repository (单一入口) │   ← 所有读写只走这里
                 └──────────┬───────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   Room/SQLite        WebDavClient        JsonArchive
   (本地)             (远端增量)          (导入/导出 v5 格式)
```

**规则**：
1. **不绕过 Repository**。UI 层不许直接碰 DAO。
2. **写操作统一走 `suspend fun`**，并在同一事务内更新 `updatedAt` → `syncEngine` 靠它判断增量。
3. **导出 JSON 的 `version: 5` 字段不可改**，除非同时升级 `importAllData` 的兼容分支。

### 5.3 图片存储迁移（IndexedDB → 文件系统）

现有：`imageStore.ts` 用 IndexedDB 存 dataURL/base64。目标：

| 平台 | 实现 |
|---|---|
| Android | `context.filesDir/images/` 或 MediaStore |
| Desktop | `%APPDATA%/WatchWorld/images/` |

```kotlin
// commonMain
expect class ImageStore {
    suspend fun save(bytes: ByteArray, mime: String): String   // 返回 imageId
    suspend fun load(imageId: String): ByteArray?
    suspend fun delete(imageId: String)
}
```

`ChatMessage.imageIds` 仍存 id 引用，shape 不变 → 存档兼容。

### 5.4 存档迁移：v5 JSON ↔ SQLite（**最关键的一步**）

迁移器的输入是 Web 版 `exportAllData()` 导出的 JSON：

```json
{
  "version": 5, "exportTime": 1234567890,
  "providers": [...], "groups": [...], "agents": [...], "prompts": [...],
  "regexRules": [...], "knowledgeBase": [...], "theme": {...}, "uiMode": "...",
  "mcpServers": [...], "skills": [...], "displaySettings": {...},
  "webdavConfig": {...}, "worldDocuments": [...], "worldGroups": [...],
  "sessions": [...], "appPreferences": {...}
}
```

```kotlin
// shared/.../data/migration/ArchiveV5.kt
@Serializable
data class ArchiveV5(
    val version: Int,
    val exportTime: Long = 0,
    val providers: List<ApiProviderConfig> = emptyList(),
    val groups: List<ChatGroup> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val prompts: List<PromptPreset> = emptyList(),
    val regexRules: List<RegexRule> = emptyList(),
    val knowledgeBase: List<KnowledgeItem> = emptyList(),
    val theme: String? = null,              // ⚠️ 单数；TS 的 ThemePalette = BuiltinThemePalette | string（types.ts:300），即主题 id 字符串，默认 'emerald'
    val uiMode: String? = null,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val displaySettings: MessageDisplaySettings? = null,
    val webdavConfig: WebDavConfig? = null,
    val worldDocuments: List<WorldDocument> = emptyList(),
    val worldGroups: List<WorldGroup> = emptyList(),
    val sessions: List<ArchiveSessionV5> = emptyList(),
    val appPreferences: AppPreferences? = null,
)
```

**⚠️ 三个必须处理的兼容点**：

1. **`theme` 是单数、`worldDocuments` 是复数** —— 别写错
2. **`sessions[]` 里的嵌套**：v5 的 session 内嵌了 `messages` / `timelines` / `summaries`，导入时要拆到三张表
3. **`normalizeChatSession()` 的迁移逻辑必须一并搬过来**（`storage.ts:86-110`）：
   - 删除 `connectedSkillIds`（遗留字段）
   - 过滤 `connectedKnowledgeIds` 里的 `'kb-quick-notes'`（内置产品指南已迁出）
   - `timelineDescriptionTagRequests` 非法值 → `[]`
   - `toolCallStepLimit` 非法值 → `null`；合法则取 `max(0, floor(n))`
   - `webSearchEnabled` 非 `true` → `false`
   - `streamingEnabled` 非 boolean → `null`（视为流式）

这六条**不是可选的**，是现有数据能正确加载的前提。

### 5.5 桌面端存储位置

```
Windows: %APPDATA%/WatchWorld/
macOS:   ~/Library/Application Support/WatchWorld/
Linux:   ~/.local/share/WatchWorld/
```
自己写 `System.getProperty("user.home")` + 平台判断即可，避免引额外重依赖。

### 5.6 API Key 安全存储

现有 Web 版把 `apiKey` 明文存 localStorage（`redactSecrets` 只在导出时擦除）。原生版应升级：

| 平台 | 方案 |
|---|---|
| Android | `EncryptedSharedPreferences`（AndroidX Security） |
| Desktop | 加密文件 + 随机 key（`%APPDATA%`）；或系统 keychain |

```kotlin
// commonMain
expect class SecureStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}
```

---

## 6. 协议层迁移（迁移的技术核心）

### 6.1 真流式实现（Ktor）

这是整个迁移的**根本目的**。Ktor 的 `HttpStatement.execute {}` 提供 scoped 流式块，配合 `ByteReadChannel` 增量读：

```kotlin
// shared/src/commonMain/kotlin/com/watchworld/net/ChatStreamClient.kt
package com.watchworld.net

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 上游 SSE 的原始行流。 */
fun HttpClient.streamSse(
    url: String,
    headers: Map<String, String> = emptyMap(),
    buildBody: HttpRequestBuilder.() -> Unit,
): Flow<String> = flow {
    // preparePost + execute 是 scoped：只有 execute 块内才能读到流式 body
    preparePost(url) {
        headers.forEach { (k, v) -> header(k, v) }
        buildBody()
    }.execute { response ->
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, response.bodyAsText().take(300))
        }
        val channel: ByteReadChannel = response.body()
        // ⚠️ 关键：按行增量读。readUTF8Line 遇到 \n 立即返回，不等整个 body ——
        // 这正是 WebView 里永远拿不到的行为。
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            emit(line)
        }
    }
}
```

**与 WebView 的本质差异**：`readUTF8Line()` 是挂起函数，每读到一行就 resume 一次协程 → UI 立刻能渲染。而 Capacitor 的 Java 侧是阻塞循环读到底。

**两个性能注意**：
1. Ktor 使用 `execute {}` 时**不会**缓冲响应体。**不要**改成 `response.bodyAsText()`，那会一次性读完全部，前功尽弃。
2. 不要在 `flow {}` 里额外套 `withContext(Dispatchers.IO)` —— Ktor 引擎有自己的 dispatcher，多套一层反而破坏增量语义。

### 6.2 SSE 解析（`sseLine()` 的 Kotlin 直译）

`nativeToolChat.ts:73-92` 的状态机语义要**完整保留**（空行派发、`data:` 累积、`[DONE]` 与 `message_stop` 双结束条件、错误帧抛异常）：

```kotlin
// shared/.../net/SseParser.kt
class SseLineParser(private val onEvent: (JsonObject) -> Unit) {
    private val data = mutableListOf<String>()
    private var ended = false

    private fun dispatch() {
        if (data.isEmpty()) return
        val payload = data.joinToString("\n").trim()
        data.clear()
        if (payload == "[DONE]") { ended = true; return }
        val obj = WwJson.parseToJsonElement(payload).jsonObject
        // 与 TS 版 `if (event.error || event.type === 'error')` 等价
        if (obj.containsKey("error") || obj["type"]?.jsonPrimitive?.contentOrNull == "error") {
            throw ApiException(0, obj["error"]?.jsonObject?.get("message")
                ?.jsonPrimitive?.contentOrNull ?: "模型返回流式错误")
        }
        onEvent(obj)
        if (obj["type"]?.jsonPrimitive?.contentOrNull == "message_stop") ended = true
    }

    /** 逐行喂入。 */
    fun feed(rawLine: String) {
        if (ended) return
        val line = rawLine.removeSuffix("\r")
        when {
            line.isEmpty() -> dispatch()
            line.startsWith("data:") -> data += line.removePrefix("data:").removePrefix(" ")
        }
    }
}
```

> 📌 **不要换现成的 SSE 库**。你已有一套经生产验证的语义（双结束条件、错误帧处理），换库会引入行为差异，而流式 bug 极难排查。

---
### 6.3 三家 Provider 归一化（`nativeToolChat.ts:354-395` 直译）

**最容易写错的部分**。三家事件形状完全不同，但 UI 只认一套。原 TS 用 `kind` 分支，Kotlin 建议改成 **sealed interface + adapter**，类型更安全：

```kotlin
sealed interface ChatEvent {
    data class Text(val delta: String) : ChatEvent
    data class Reasoning(val delta: String) : ChatEvent
    data class BlockStart(val index: Int, val type: String, val text: String?) : ChatEvent
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?,
                             val argsJsonDelta: String?) : ChatEvent
    data class Finish(val reason: String?) : ChatEvent
    data class Error(val message: String) : ChatEvent
}

interface ProviderAdapter {
    fun adapt(event: JsonObject): List<ChatEvent>
    fun buildRequest(model: String, messages: List<ChatMessage>, system: String,
                     tools: List<ToolDef>, stream: Boolean): JsonObject
    fun authHeaders(apiKey: String): Map<String, String>
}
```

**三家关键差异（照抄现有实现，勿凭记忆重写）**：

| Provider | 文本增量 | 思维链 | 工具调用 | 结束标志 |
|---|---|---|---|---|
| **OpenAI 系** | `choices[0].delta.content` | `delta.reasoning_content` | `delta.tool_calls[]`（分片累积） | `[DONE]` 或 `finish_reason` |
| **Claude** | `content_block_delta.delta.text`（`type == text_delta`） | `delta.thinking`（`thinking_delta`） | `input_json_delta.partial_json`（需拼接） | `message_stop` |
| **Gemini** | `candidates[0].content.parts[].text` | 同字段 + `part.thought == true` | `parts[].functionCall` | `finishReason` |

**Claude 特别注意**（`nativeToolChat.ts:375-383`）：
- `content_block_start` 先建立 block 记录；`content_block_delta` 找不到 block 时**必须抛错**（原代码：`'Claude 流缺少内容块起始事件'`）
- `signature_delta` 要累积到 block 上（用于 thinking 回放）
- `input_json_delta.partial_json` 是**分片字符串**，需按 index 累积后整体 `parse`

**Gemini 特别注意**（`nativeToolChat.ts:384-393`）：
- `part.thought == true` 的文本走 `pushReasoning`，**不进入正文、不回放到上下文**
- `promptFeedback.blockReason` 要捕获（安全拦截）

### 6.4 工具轮次循环

```
loop (step = 0; step < limit || limit == 0; step++) {
    1. 发请求（stream = true）
    2. 边收边渲染正文 / 思维链
    3. 收集 tool_calls
    4. 若无 tool_calls → 本轮结束，退出循环
    5. 若有 tool_calls：
       a. 若含 LOAD_SKILL_TOOL_NAME → 拦截，从本地 Skill 表取正文，作为 tool result 回填
       b. 否则 → callMcpToolNative() 真实调用 MCP
       c. **工具名必须用 originalName**（注册时可能加了 _2/_3 后缀）
       d. 把 assistant 的 tool_calls 消息 + 各 tool result 追加到 messages
       e. 继续下一轮
}
```

**必须保留的细节**：
- 工具名注册：原始名优先，重名才加 `_2`/`_3`，且**必须与注入 system 的清单一致**（`nativeToolChat.ts:200-214`）
- `isMcpAvailableToModel` 的防御性过滤（`api.ts:189`）
- 参数 schema 无效时抛错（原逻辑：`工具 xxx 的参数 schema 无效`）

### 6.5 联网搜索（DDG 解析直译）

`server.ts:44-121` 是**纯字符串逻辑**，零设计成本：

```kotlin
private fun decodeSearchHtml(v: String): String = v
    .replace("&amp;", "&").replace("&quot;", "\"")
    .replace(Regex("&#x27;|&#39;"), "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&#x2F;", "/")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toInt().toChar().toString() }

private fun stripSearchHtml(v: String): String =
    decodeSearchHtml(v.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim())

private fun unwrapSearchUrl(v: String): String   // 解析 DDG 的 uddg 跳转参数
private fun parseDuckDuckGoResults(html: String, limit: Int): List<SearchResult>
    // html.split(Regex("class=\"result(?:\\s|_)")).drop(1)
    // 每块匹配 result__a 的 href+标题，result__snippet 的摘要
```

**关键点**：
- 请求头必须带 `User-Agent`（`Mozilla/5.0 (compatible; WatchWorld/0.2.5)`），否则 DDG 返回反爬页
- 超时 15s（原 `AbortSignal.timeout(15_000)`）；`limit` 钳制在 1..10
- **原生版的优势**：不再有 CORS 限制，也**不需要 Cloudflare Worker 中转**

`webSearch.ts` 还有 **Skill 搜索**（`agent-skills.md` 的 oRPC 协议）：
- 端点：`POST https://agent-skills.md/rpc/<router>/<procedure>`
- 请求体必须包一层 `{ "json": <payload> }`；响应同样是 `{ "json": <result>, "meta": [...] }`
- skill id 三段格式：`owner/repo/skillName`

这套协议在原生里同样可直接调（无 CORS）。

### 6.6 上下文拼装（`api.ts` 的核心逻辑）

`api.ts` 520 行里，**系统提示词拼装**最复杂，且**极易翻译错**。必须原样复刻顺序：

```
1. 按 aiContextVisibility 过滤历史消息
   ├─ 'all'          → 全保留
   ├─ 'hide_all'     → 去掉 assistant
   └─ 'latest_custom'→ user 全保留 + 最近 N 条 assistant
2. 过滤"已推入通用"的楼层（extraSystemTail 里含 `[来自 #楼层]` 标记的消息）
3. 若 timelineMemoryEnabled → rearrangeMessagesForAi(当前分支, 全部分支, 消息)
4. 拼 system 上下文（顺序不可变，以 api.ts:311-321 源码为准）：
   ├─ head（Agent 人格 + 窗口指令 + 长期记忆）
   ├─ 世界观正典（activeWorldDocuments）→ "# 【世界观·正典设定】"
   ├─ MCP 能力清单（availableMcp）       → "# 【已验证可用的 MCP 服务能力】"
   ├─ Skill 清单（activeSkills）         → formatSkillManifest
   ├─ 知识库（connectedKnowledge）      → "# 【外部参考资料·知识库】"
   ├─ 本轮联网结果（webSearchContext）
   ├─ 楼号引用约定（有楼号时）           → "# 【楼号引用约定】"
   ├─ extraSystemTail（通用缓存 + 前文总结）
   └─ 时间线树提示词（当前分支不可见时不注入）
5. 应用正则规则（regexRules）
6. 图片按 imageContextMode 决定是否保留在历史
```

**⚠️ 两个必须保留的边界逻辑**（都是踩过坑的）：

1. **`pushedCommonFloors` 过滤**（`api.ts:138-153`）：
   ```
   已"推入通用"的消息正文已在 system 尾部缓存里，不能再作为历史重复发送。
   只过滤明确记录过楼号的：如果撤回推入，extraSystemTail 不再有标记，消息自动恢复。
   ```
   必须用同样的正则：`\[来自\s+#(\d+)\]`

2. **`isCommonMessage` 三重判定**（`api.ts:147-150`）：
   ```kotlin
   val isCommonMessage = m.isCommon == true ||
       m.codeTag == "通用" ||
       m.timelineId == "timeline-main"
   ```

### 6.7 MCP 客户端（⚠️ 最高风险项）

现有 `mcpApi.ts` 支持两种 transport：SSEClientTransport / StreamableHTTPClientTransport。
**MCP 官方只有 TS / Python SDK，没有 Kotlin SDK** → 这部分是**从零实现**。

```kotlin
class McpClient(private val config: McpServerConfig) {
    suspend fun listTools(): List<McpTool>
    suspend fun callTool(name: String, args: JsonObject): JsonElement
}
```

**协议要点**：
- JSON-RPC 2.0
- 旧版 SSE transport：`GET /sse` 建连 + `POST /message` 发请求
- StreamableHTTP：`POST /mcp`，响应可能是 SSE 流或普通 JSON
- 需实现 `initialize` → `tools/list` → `tools/call` 三步

> 💡 **降级策略**：若耗时超预期，先只支持 `tools/list` + `tools/call` 最小集，跳过 `resources` / `prompts` / `sampling`。

### 6.8 WebDAV 同步

`webdavSync.ts` (259) + `syncEngine.ts` (121) 都是标准 HTTP 方法：

| 操作 | 方法 |
|---|---|
| 列目录 | `PROPFIND` (Depth: 1) |
| 下载 / 上传 | `GET` / `PUT` |
| 建目录 / 删除 | `MKCOL` / `DELETE` |
| 存在性 | `HEAD` |

**坚果云注意**：需用应用密码（非登录密码），`PROPFIND` 要带 `Depth: 1`。

---


## 7. UI 层迁移

### 7.1 为什么桌面优先（**关键策略建议**）

| 维度 | 桌面 | Android |
|---|---|---|
| 编译-运行循环 | 改完点运行，< 10s | Gradle sync + 安装，30s–2min |
| 调试 | 直接断点，热重载 | 需连真机/模拟器 |
| 设备依赖 | 无 | 权限、后台保活、厂商 ROM 差异 |
| 文件访问 | 普通文件 API | SAF / MediaStore / scoped storage |

**更重要的收益**：Compose MP 的 `commonMain` 代码**双端通用**。桌面调通的 Ktor 流式客户端、SSE 解析、Repository，搬到 Android **一行不用改**。Android 阶段只剩"平台差异"。

**所以：P0–P4 全在桌面完成，P5 才接 Android。** 这样 P4 结束就有可用 `.exe`。

### 7.2 主题系统重建（🔴 高难度）

现有：288 行 CSS + CSS 变量（`resolveThemeVars`）+ `guanjie_custom_themes` 自定义主题。
Compose 无 CSS 变量 → 必须重建：

```kotlin
// shared/.../ui/theme/WwTheme.kt
@Immutable
data class WwColors(
    val background: Color, val surface: Color, val surfaceVariant: Color,
    val primary: Color, val onPrimary: Color,
    val textPrimary: Color, val textSecondary: Color,
    val border: Color, val accent: Color,
    // … 与现有 CSS 变量一一对应
)

val LocalWwColors = staticCompositionLocalOf { DefaultWwColors }

@Composable
fun WwTheme(themeId: String, content: @Composable () -> Unit) {
    val colors = remember(themeId) { resolveTheme(themeId) }   // 复用 theme.ts 的映射表
    CompositionLocalProvider(LocalWwColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterialScheme()) { content() }
    }
}
```

**工作量说明**：`theme.ts` 的 279 行**映射逻辑可以直译**，真正的工作量在**把 288 行 CSS 里的每个变量对应到 Compose 属性**，以及**视觉走查**（对比 Web 版逐项调色）。这部分**无法通过"看代码"完成**，必须两个版本并排跑。

### 7.3 Markdown 流式渲染（🔴 高难度，最容易做成卡顿）

**这是整个 UI 层最大的技术陷阱。**

朴素做法（错的）：
```kotlin
// ❌ 每来一个 token 就重新解析整段 Markdown
Text(markdown = renderMarkdown(message.content))
```
→ 长回复时每秒重排几十次，**必然卡顿**，甚至比现在的 WebView 还差。

正确做法：**块级缓存 + 只重解析末块**。

```kotlin
// 思路：markdown 按空行切块，除最后一块外全部缓存渲染结果
@Composable
fun StreamingMarkdown(content: String) {
    val blocks = remember(content) { splitBlocks(content) }
    LazyColumn {
        items(blocks.size) { i ->
            if (i == blocks.lastIndex) {
                MarkdownText(blocks[i])          // 只对最后一块（可能未闭合）重解析
            } else {
                key(blocks[i].hashCode()) { MarkdownText(blocks[i]) }   // 已完成块缓存
            }
        }
    }
}
```

**必须处理的边界**：
- **未闭合的代码块**：三个反引号出现但还没闭合时不能当代码块渲染（否则整段闪成灰底），要按普通文本渲染或保留"等待闭合"态
- **未闭合的行内标记**：`**bo` 这种半截语法，渲染时要容错
- **正则替换位置**：`applyRegexRules` 应在**渲染前**对纯文本应用，而不是对渲染结果应用

### 7.4 34 个 React 组件的迁移映射

| 现有组件 | 行数 | Compose 对应 | 难度 |
|---|---|---|---|
| `App.tsx` | 2369 | `AppState` + 多个 ViewModel | 🔴 87 个 hook 拆分 |
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
| … 其余 20 个 | — | — | 🟢 多数 |

**`App.tsx` 的 87 个 hook 是最大的单点风险**。建议拆成多个 ViewModel：

```kotlin
class ChatViewModel(repo: Repository) : ViewModel() {
    val sessions: StateFlow<List<ChatSession>>
    val activeSession: StateFlow<ChatSession?>
    val streamingMessage: StateFlow<StreamingState?>
    fun send(text: String) { … }
    fun stop() { … }
}

class SettingsViewModel(repo: Repository) : ViewModel() { … }
class TimelineViewModel(repo: Repository) : ViewModel() { … }
```

### 7.5 桌面端特有适配

| 项 | 说明 |
|---|---|
| 窗口 | `Window` composable，记忆尺寸/位置 |
| 菜单栏 | `MenuBar`：文件 / 编辑 / 视图 |
| 快捷键 | Ctrl+Enter 发送、Ctrl+N 新会话、Esc 停止 |
| 滚动 | 鼠标滚轮，`LazyColumn` 自动适配 |
| 剪贴板 | 粘贴图片（`LocalClipboard`） |
| 输入法 | ⚠️ Windows IME 在 Compose Desktop 有已知光标错位问题，需实测 |

### 7.6 Android 端特有适配

| 项 | 说明 |
|---|---|
| 权限 | `INTERNET`（必须）、`POST_NOTIFICATIONS`（可选） |
| 存储 | SAF 选择导出目录；图片走 `filesDir` |
| 返回键 | `BackHandler`：关弹窗 → 关侧栏 → 退出 |
| 安全区 | `WindowInsets` 处理状态栏/导航栏 |
| 后台 | 长回复防被杀 → 前台 Service 可选 |
| 键盘 | `imePadding()` 处理输入框被顶起 |

---

## 8. 测试与质量保障

### 8.1 必须做的测试（按优先级）

| 测试 | 目的 | 优先级 |
|---|---|---|
| **存档往返测试** | v5 JSON → DB → v5 JSON，断言关键字段一致 | 🔴 必须 |
| **SSE 解析单测** | 构造分片跨行的 SSE，验证 `sseLine` 语义 | 🔴 必须 |
| **三家 Provider 适配单测** | 用**真实抓包样本**作 fixture | 🔴 必须 |
| **工具循环单测** | mock MCP，验证重名后缀、originalName 回填 | 🟠 重要 |
| **上下文拼装单测** | 验证 pushedCommonFloors 过滤、三重 isCommon 判定 | 🟠 重要 |
| **真流式端到端** | 起本地 SSE 服务器，断言帧间隔稳定 | 🔴 必须 |
| **UI 截图测试** | 主题视觉回归 | 🟢 可选 |

### 8.2 真流式验收标准（**这是迁移成败的判据**）

迁移**唯一不可妥协的目标**是真流式。验收方式：

```kotlin
@Test
fun `帧间隔应贴近上游节奏而非一次性到达`() = runTest {
    // 起假上游，每 300ms 推一帧（与之前 Worker 验证用的完全一致）
    val ts = mutableListOf<Long>()
    client.streamSse(url) { /* body */ }.collect { ts += now() }
    val gaps = ts.zipWithNext { a, b -> b - a }
    assertTrue(gaps.size >= 5, "应收到多帧而非一次性: ${gaps.size}")
    assertTrue(gaps.all { it in 250..400 }, "帧间隔应贴近 300ms: $gaps")
}
```

**这个测试必须跑通，才算迁移成功。** 参照之前 Worker 验证结果：帧间隔 `311, 310, 313, 309, 310, 311, 314` ms。

### 8.3 迁移期间的双版本并行

**建议保留现有 React 版一段时间**（不删 `src/`），原因：
1. 原生版可用前，APK 仍要能用
2. 主题视觉走查需要两版并排对比
3. 存档兼容性验证需要 Web 版生成样本

**但要注意**：迁移期间 **Web 版冻结新功能**，否则两边永远追不上。

---

## 9. 实施计划（分阶段可交付）

### 阶段总览

```
P0 骨架+模型+存储    ██████                    1.5 周  → 可跑测试
P1 协议层（流式核心）        ████████████        3 周    → ★ 真流式验收通过
P2 聊天主界面                     ████████       2 周    → 桌面可对话
P3 设置+主题                           ██████████ 2.5 周  → ★ 桌面版功能完整
P4 时间线/世界书/知识库                      ████████ 2 周    → ★ 交付可用 .exe
P5 Android平台+打包                             ██████ 1.5 周 → ★ 交付 APK
```

### P0：工程骨架 + 模型 + 存储（1.5 周）

**产出**：可编译运行的 KMP 工程 + 数据模型 + Room 表 + v5 存档往返测试通过

- [ ] 用 KMP Wizard 生成 Compose MP 工程（Android + Desktop 目标）
- [ ] `gradle/libs.versions.toml` 固定版本（§2.1）
- [ ] 24 个 `@Serializable` data class（§4）
- [ ] `ChatSession` 拆解为 `SessionConfig` + `SessionEntity` + 聚合对象（§4.3）
- [ ] Room entity + DAO + TypeConverter（§5.1）
- [ ] `WwJson` 配置 + 存档往返测试（§4.4）
- [ ] `normalizeChatSession` 的 6 条兼容逻辑（§5.4）

**验收**：用 Web 版导出的真实存档 JSON 导入，字段无丢失。

### P1：协议层（3 周）★ 最关键

**产出**：真流式跑通 + 工具循环 + 搜索 + MCP + WebDAV

- [ ] `HttpClientFactory`（OkHttp 引擎，双端统一）
- [ ] `streamSse()` 真流式（§6.1）
- [ ] `SseLineParser`（§6.2）
- [ ] 三个 `ProviderAdapter`（§6.3）
- [ ] `ToolCallLoop`（§6.4）
- [ ] `ContextBuilder`（§6.6，含 pushedCommonFloors 过滤）
- [ ] `WebSearchClient`（§6.5，DDG 解析）
- [ ] `McpClient`（§6.7，⚠️ 无官方 Kotlin SDK，风险点）
- [ ] `WebDavClient`（§6.8）
- [ ] 单元测试 + **真流式端到端验收**（§8.2）

**验收**：`streamSse` 的帧间隔测试显示 `250~400ms` 稳定间隔（非一次性到达）。

### P2：聊天主界面（2 周）

**产出**：桌面能完成一次完整对话

- [ ] `ChatViewModel`（拆 App.tsx 的 87 个 hook）
- [ ] `MessageList`（LazyColumn + 自动滚底）
- [ ] `MessageBubble` + **流式 Markdown 渲染**（§7.3，块级缓存）
- [ ] 光标闪烁 + 停止生成
- [ ] `ChatInputBar`（多行、Ctrl+Enter、模型切换）
- [ ] 消息版本切换 UI
- [ ] 侧边栏会话列表 + 分组

**验收**：发消息 → 首 token 1–2s 出现 → 逐字显示 → 光标闪 → 内容截断正常。

### P3：设置 + 主题（2.5 周）

**产出**：桌面版功能完整（除时间线/世界书）

- [ ] `WwTheme` + 主题映射（§7.2）
- [ ] 与 Web 版并排视觉走查
- [ ] Provider / Agent / Prompt / Regex / Knowledge / MCP / Skill / Preferences 各屏
- [ ] 导入 / 导出 UI
- [ ] WebDAV 同步 UI
- [ ] 桌面菜单栏 + 快捷键（§7.5）

**验收**：★ **此时桌面 `.exe` 已可日常自用**。

### P4：时间线 / 世界书 / 知识库（2 周）

**产出**：功能 100% 对等

- [ ] `TimelineMemory`（`rearrangeMessagesForAi` 直译）
- [ ] 时间线树递归渲染（🔴 难）
- [ ] 描述标签审批流（`timelineDescriptionTagControl`）
- [ ] 世界书嵌套分组树
- [ ] 前文总结（`SessionSummary`）
- [ ] 心理动力学角色关系分析

**验收**：★ 桌面版功能完整，`.exe` 正式交付。

### P5：Android + 打包（1.5 周）

- [ ] `androidMain` 平台实现（FileSystem / SecureStore / ImageStore）
- [ ] 权限、`BackHandler`、`WindowInsets`、`imePadding`（§7.6）
- [ ] 前台 Service（可选，防后台杀）
- [ ] 真机测试（含 IME、滚动性能）
- [ ] `jpackage` 打 Windows exe / `assembleRelease` 打 APK
- [ ] 签名配置

**验收**：★ Android APK 交付，真流式在真机验证通过。

---

## 10. 风险清单

| # | 风险 | 等级 | 影响 | 缓解 |
|---|---|---|---|---|
| 1 | **MCP 需从零实现**（无官方 Kotlin SDK） | 🔴 高 | 拖慢 P1，可能延期 1–2 周 | 先做最小集（tools/list + tools/call）；或先只支持 HTTP 型 MCP |
| 2 | **存档兼容性**（v5 JSON 双向） | 🔴 高 | 老数据无法恢复 | P0 就建立往返测试；用真实存档做 fixture |
| 3 | **Markdown 流式卡顿** | 🔴 高 | 体验比 Web 版还差 | 块级缓存 + 性能压测（§7.3） |
| 4 | **`App.tsx` 87 hook 拆分** | 🟠 中 | 状态管理混乱、bug 多 | 严格按领域拆 ViewModel，禁止单巨类 |
| 5 | **主题视觉走查耗时** | 🟠 中 | P3 超期 | 提前做并排截图对比；接受初期"接近"而非"完全一致" |
| 6 | **Compose Desktop Windows IME** | 🟠 中 | 中文输入体验问题 | 早期实测；必要时锁定特定 Compose 版本 |
| 7 | **时间线树嵌套深时性能** | 🟠 中 | 深层树滚动掉帧 | 递归 `LazyColumn` + 局部重组 |
| 8 | **DDG 直连在手机网络不通** | 🟠 中 | 搜索功能不可用 | 保留 Worker 作为可选回退（现有代码已可用） |
| 9 | **Compose MP 桌面打包** | 🟢 低 | `jpackage` 配置繁琐 | 官方文档有模板；早期跑通一次 |
| 10 | **MCP tool schema 非法** | 🟢 低 | 请求失败 | 保留现有防御性抛错逻辑 |

---

## 11. 决策点（需要你拍板）

| # | 决策 | 选项 | 建议 |
|---|---|---|---|
| 1 | **存储方案** | Room KMP / SQLDelight | **Room KMP**（嵌套结构友好） |
| 2 | **Markdown 库** | compose-markdown / richtext-commonmark / 自写 | **compose-markdown**（KMP 支持） |
| 3 | **是否保留 Worker** | 保留做搜索回退 / 完全移除 | **保留**（手机网络 DDG 可达性未知） |
| 4 | **推进顺序** | 桌面优先 / Android 优先 / 并行 | **桌面优先**（§7.1） |
| 5 | **旧 React 版** | 保留并行 / 立即删除 | **保留**到 P4 结束 |
| 6 | **功能范围** | 全量对等 / 先核心后补 | **全量对等**（自用，无妥协必要） |

---

## 12. 附录

### 12.1 现有代码 → Kotlin 模块映射总表

| 现有文件 | 行数 | 迁移目标 | 说明 |
|---|---|---|---|
| `src/types.ts` | 397 | `model/*.kt` | 24 interface → data class |
| `src/lib/storage.ts` | 410 | `data/repository/*` + `data/migration/` | 含 6 条 normalize 逻辑 |
| `src/lib/api.ts` | 520 | `domain/ContextBuilder.kt` + `net/provider/*` | system 拼装 + 请求组装 |
| `src/lib/nativeToolChat.ts` | 480 | `net/ChatStreamClient.kt` + `SseParser.kt` + `ToolCallLoop.kt` | **流式核心** |
| `src/lib/apiNative.ts` | 355 | ❌ 废弃 | Capacitor 专用 |
| `src/lib/apiBase.ts` | 73 | ❌ 废弃（或降级为搜索回退开关） | Worker 中转 |
| `src/lib/webSearch.ts` | 448 | `net/WebSearchClient.kt` | DDG + oRPC |
| `src/lib/timelineMemory.ts` | 401 | `domain/TimelineMemory.kt` | 树 + 重排 |
| `src/lib/defaultData.ts` | 362 | `data/DefaultData.kt` | 默认数据直译 |
| `src/lib/theme.ts` | 279 | `ui/theme/WwTheme.kt` | CSS 变量 → Compose |
| `src/lib/webdavSync.ts` | 259 | `net/WebDavClient.kt` | PROPFIND / PUT / GET |
| `src/lib/appFolders.ts` | 233 | `platform/FileSystem.kt` | expect/actual |
| `src/lib/timelineDescriptionTagControl.ts` | 216 | `domain/TimelineTagControl.kt` | 审批流 |
| `src/lib/mcpApi.ts` | 194 | `net/McpClient.kt` | ⚠️ 从零实现 |
| `src/lib/imageStore.ts` | 194 | `platform/ImageStore.kt` | IndexedDB → 文件 |
| `src/lib/syncEngine.ts` | 121 | `data/repository/SyncEngine.kt` | 增量同步 |
| `src/lib/configTextImporter.ts` | 91 | `domain/ConfigImporter.kt` | 文本解析 |
| `src/lib/mcpAvailability.ts` | 54 | `domain/McpAvailability.kt` | 可用性判定 |
| `src/lib/tokenEstimator.ts` | 48 | `domain/TokenEstimator.kt` | 直译 |
| `src/lib/regexProcessor.ts` | 45 | `domain/RegexProcessor.kt` | 直译 |
| `server.ts` | 756 | `net/*` | 8 个端点 → 客户端方法 |
| `worker/src/index.ts` | — | ❌ 废弃（可保留作搜索回退） | — |
| `src/components/*.tsx` | ~11,000 | `ui/**` | 34 组件 |
| `src/App.tsx` | 2369 | `ui/AppState.kt` + 多个 ViewModel | 🔴 87 hook 拆分 |

### 12.2 可 1:1 直译的模块（零设计成本）

这几个是"照着抄"就能对的，工作量主要在打字：

- `tokenEstimator.ts` (48) — 纯计算
- `regexProcessor.ts` (45) — 纯文本替换
- `mcpAvailability.ts` (54) — 纯条件判断
- `server.ts` 的 DDG 解析 (44-94) — 纯字符串处理
- `timelineMemory.ts` 的 `rearrangeMessagesForAi` — 纯数组操作
- `defaultData.ts` (362) — 数据搬运
- `storage.ts` 的 `normalizeChatSession` — 逻辑明确

**这几块合计约 900 行，可以快速完成**，是 P0/P1 的"低风险填充"。

### 12.3 必须删除/废弃的代码

| 文件 / 函数 | 原因 |
|---|---|
| `src/lib/apiNative.ts` (355) | Capacitor 专用 |
| `src/lib/apiBase.ts` (73) | Worker 中转地址解析 |
| `nativeToolChat.ts` 的 `replayAsStream()` | 伪装流式，真流式后无用 |
| `nativeToolChat.ts` 的 `toPseudoEvents()` | 同上 |
| `nativeToolChat.ts` 的 `readSseWithXhr()` | WebView 特例 |
| `worker/` | 如确定手机能直连 DDG 则可删 |
| `capacitor.config.ts` / `android/` | Capacitor 工程 |

> ⚠️ **但不要在迁移完成前删**。理由见 §8.3。

### 12.4 迁移完成后的验证清单

- [ ] **真流式**：首 token < 2s，帧间隔呈稳定节奏（§8.2 测试通过）
- [ ] **存档兼容**：Web 版导出的 v5 JSON 能完整导入
- [ ] **存档往返**：导入后再导出，与原始 JSON 关键字段一致
- [ ] **工具调用**：MCP 工具能调用，重名后缀正确
- [ ] **上下文正确**：pushedCommonFloors 过滤生效，无重复发送
- [ ] **时间线**：分支切换后消息重排正确
- [ ] **搜索**：DDG 返回结果并正确解析
- [ ] **WebDAV**：坚果云同步成功
- [ ] **主题**：与 Web 版视觉对比无明显偏差
- [ ] **双端**：Android 和 Windows 均通过以上全部

---

**文档结束。**

> 编制说明：本方案的所有"现状"数据均来自对 `E:\WatchWorld\WatchWorld` 的实际代码统计（2026-10-06）。
> 版本号来自 Compose Multiplatform 与 Ktor 的官方 release 页面。
> 工作量估算基于模块行数、复杂度分级与双端复用带来的节省，供决策参考。
