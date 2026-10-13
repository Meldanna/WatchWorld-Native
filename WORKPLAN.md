# WatchWorld-Native · 工作文档

> 本文件是**执行清单**：项目要求见 §〇；按 §三 的顺序往下做；
> 每完成一个任务，就更新它的任务文档，并在 §一 的最上面记一笔、在 §二 登记路径。

---

## §〇 项目要求（每个任务都必须遵守）

### 要求 1：每个任务留下「改动 / 心得 / 要点」

**这是硬性要求，不是建议。** 这三样是未来修改本项目时最重要的助手 ——
迁完之后没人记得"当时为什么这么写"，只有这三栏能回答。

| 栏 | 写什么 | 判断标准 |
|---|---|---|
| **改动** | 改了/加了哪些文件，各自一句话职责 | 陌生人能据此判断"改这里会影响什么" |
| **心得** | 为什么这么选、试错过程、被否决的方案及原因 | 三个月后自己看了能少走一次弯路 |
| **要点** | 未来修改**必须知道**的事实：不可动的约定、易踩的坑、数值/行号基准、跨模块联动 | 每条能独立看懂，不依赖上下文 |

⚠️ **不要攒着写**。任务收尾时立刻写，隔天写出来的就是"完成情况"而不是"心得"了。

### 要求 2：每个任务一份独立文档

- 位置：`tasks/`
- 命名：`T<两位号>-<简短中文名>.md`（例：`tasks/T00-建立原生仓骨架.md`）
- 模板：[`tasks/_TEMPLATE.md`](tasks/_TEMPLATE.md)
- **路径与名字登记在 §二 的索引表**（放在任务清单上方，方便顺手点开）

### 要求 3：进度权威只有一处

- **进度看 `WORKPLAN.md` §一**；`memory/` 下不重复记进度，只做"资料查询"。
- 已完成任务的**细节**在它的任务文档里；WORKPLAN 只留一句话摘要 + 链接。

---

## §一、完成记录（最新在上）

> 格式：`### 日期 · 任务号 · 任务名`，四行必填 + 一行文档链接。**做完就写。**

### 2026-10-13 · T31 · 构建修复 + 主题废案清理 + 夜晚气泡修复
- **状态**：✅ 完工（`:shared:desktopTest` **388 用例 / 0 失败 / 5 跳过**；`:desktopApp:build` 零警告）
- **交付物**：**① 乱码事故恢复**：`ui/settings/PreferencesPanels.kt`、`ui/theme/WwSurfaces.kt` 被 GBK 乱码损坏（源文件字节即乱码、多出 BOM），从 `.git` 里 agent-host checkpoint 的最后干净版本还原（前者纯损坏直接还原 `af261ee8`；后者含 1 处真实重构需重放）；**② 删除颜色减淡废案**：`ThemeSource.Family`（**从未被构造**）+ `ThemeVars` 的 m2 = m1 × 0.4 分支 + `ThemeScale.buildScale` 的 `chromaScale` 预留参数 + `ThemeDialog.m2Preview` 的重复分支 + 零引用的 `PRESETS` 表 + `ThemeSnapshotTest.familyThemeM2IsChromaTimes04`；**③ 夜晚气泡修复**：`ThemeVars` 的 `bubbleDark` 由 `m1[3]`(300 档 L≈84.5% **浅**) → `m1[10]`(950 档 L≈26.2%)、`themeRoles` 的 `dark:bg-m1-300` → `dark:bg-m1-900`；**④ 设置入口行框灰白修复**：`SettingsHub.SettingsCard` 的 `Surface` 补 `color = WwSurfaces.accentSoft`（原来漏传 → M3 取中性灰 `#F8FAFC`）；**⑤ 快照 fixture 重生成**：新增 `ThemeBaselineGenerator`（替换已删的 React 侧 `gen-theme-baseline.ts`），15 套 → **22 套**；**⑥ 测试自误修正**：`TokenEstimatorTest.mixedCjkAndEnglish` 期望 6 → **5**（"Hello世界" 只有 1 个英文单词，node 跑 TS 原实现实测 5）；**⑦ 新增回归**：`SettingsHubScreenshotTest`；**⑧ 过期引用清理**：`memory/theme-color-map.md`、`memory/gotchas.md`、`memory/function-map.md`、`tasks/T30-…md`、`fixtures/theme-baseline/README.md`、两个测试文件头注释
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿；**388 用例 / 0 失败 / 5 跳过（在线 smoke）**；`SettingsHubScreenshotTest` 离屏截图 **肉眼确认行框已为主题淡绿**（PNG 落 `shared/build/t28-screenshots/`）
- **踩坑**：🚨 **`gradlew :desktopApp:build` 可能报 `UP-TO-DATE` 却根本没编译 shared** —— 改完 shared 源码必须 `--rerun-tasks` 或显式跑 `:shared:compileKotlinDesktop` 才作数；⚠️ **「对拍测试全是红的」不等于实现对**：本仓主题 fixture 是 React 旧模型产物、从 `c836984` 起就没更新，7 个键在新模型里查不到直接回退 → 15 套「全失败」里一半是"主题改了名"；⚠️ **测试期望值也会算错**：`TokenEstimatorTest` 把 `"Hello世界"` 数成 2 个英文单词（实际 1 个），判据要拿 TS 原实现（`node -e`）实测而不是读注释；⚠️ **删除死代码的判据是「谁能构造它」**：`ThemeSource.Family` 只有 `when` 分支在匹配、全仓无构造点 → 安全删；反过来 `SaturationLevel.MID` 的 ×0.7 **是活的**（`emerald-day` 的 m2Level），不能一起删
- **备注**：⚠️ 三个待办未动 —— ① `m2Level` 全组同值（白天全 `MID`、夜晚全 `HIGH`）使饱和度分级在 m2 上形同虚设；② **SLATE 三张饱和度表逐值相同**，灰阶无色度可分 → `midnight`/`slate-*` 的 level 参数**完全无效**；③ 气泡档位（`m1[2]`/`m1[10]`）是本次临时定案，**未经用户视觉确认**
- **文档**：本次为修复+清理，未新建任务文档（细节见本节与各文件注释）

### 2026-10-10 · T29 · 设置弹窗按 Web 版校正 — 备份与导入导出
- **状态**：✅ 完工
- **交付物**：`tasks/T29-备份导入导出按Web版复刻.md` + `tasks/backup-source-map.md` 新增（参考版 `BackupSyncModal.tsx` 349 行的逐段规格）；`ui/settings/BackupSyncDialog.kt` 新增（两个 Tab：WebDAV 云同步 / 导入与导出，三态结果条、软底操作按钮、通栏 Tab 行不随内容滚）；`ui/settings/SettingsPanels.kt` **删除 `BackupPanel`**（含参考版没有的「粘贴存档 JSON」文本框）；`ui/settings/SettingsViewModel.kt` 状态 `webDavStatus`/`importResult` → `webDavNotice`/`importNotice`（`BackupNotice` 三态语义）+ 新增 `markWebDavConfigSaved`/`clearImportNotice` + `testWebDav` 补三字段校验；`ui/settings/SettingsHub.kt` 去掉 `SettingsPage.BACKUP` 改独立弹窗入口（卡片顺序对齐参考版：Agent 首、备份末）；`ui/App.kt` 新增 `backupOpen` + `backupReturnsToSettings`，导出接 `saveTextFile`；`domain/BackupNaming.kt` 新增（`watchworld-backup-YYYY-MM-DD.json`，UTC 日期手写公历换算）；**顺带**：`ui/components/WwFormControls.kt` 新增公共 `WwRangeSlider`（`WindowApiParamsPanel` 的私有 `CompactRangeSlider`/`snapToStep` 提取上来），修掉「滑杆里看见圆点」（M3 `Slider` 的 `steps != 0` 会画刻度点），附 `WwRangeSliderScreenshotTest` 离屏截图 + 刻度点回归
- **验收**：`:shared:compileKotlinDesktop` + `compileTestKotlinDesktop` 干净；`:desktopApp:build :shared:desktopTest` 全绿 = **362 用例 / 0 失败 / 0 错误 / 5 跳过**
- **踩坑**：⚠️ `FeatureDialog` 的 `content` **不是 `ColumnScope`**，正文里写 `weight` 报未解析，要自己套一层 `fillMaxHeight()` 的 Column 且必须 `fixedHeight = true`；⚠️ 「设置里的一张卡」在参考版几乎都是**独立弹窗**（`handle()` 先 `onClose()` 再开），别做成内联子页
- **文档**：[`tasks/T29-备份导入导出按Web版复刻.md`](tasks/T29-备份导入导出按Web版复刻.md)、[`tasks/backup-source-map.md`](tasks/backup-source-map.md)

### 2026-10-10 · T28 · 设置弹窗按 Web 版校正 — Agent 顾问中心
- **状态**：✅ 完工
- **交付物**：`tasks/T28-Agent顾问中心按Web版复刻.md` + `tasks/agent-source-map.md` 新增（参考版 `AgentModal.tsx` 572 行的逐段规格）；`ui/settings/SettingsPanels.kt` 的 `AgentPanel` 重写（列表卡片 + 选中态 + 删除确认）并新增 `AgentEditorDialog`（完整表单：头像/名称/简介/系统提示词/发散度滑杆/标签/长期记忆/文件权限/MCP 与 Skill 绑定 chip）与 `AgentBindingChips`；`ui/settings/SettingsHub.kt` 去掉 `SettingsPage.AGENT` 改独立弹窗入口；`ui/App.kt` 新增 `agentOpen` + `agentReturnsToSettings`（设置入口来的关闭后回设置总入口）；`ui/chat/ChatViewModel.kt` 新增 `selectAgent`（写 `ChatSession.agentId`）
- **验收**：`:shared:compileKotlinDesktop` + `compileTestKotlinDesktop` 干净；`:desktopApp:build :shared:desktopTest` 全绿
- **踩坑**：⚠️ 第一版把 Agent 做成 `SettingsHub` 的**内联子页**，被用户指出「它应该是弹窗，不是设置内置的子页面」——回源码核实参考版 `SettingsHubModal.tsx:43-47` 的 `handle()` 确实是「先关设置总入口再开目标弹窗」；⚠️ Tabler 是**子集**，`Bot`/`FileLock` 都不存在（→ `UserCheck`/`Lock`）
- **文档**：[`tasks/T28-Agent顾问中心按Web版复刻.md`](tasks/T28-Agent顾问中心按Web版复刻.md)、[`tasks/agent-source-map.md`](tasks/agent-source-map.md)

### 2026-10-10 · T27 · 窗口功能弹窗按 Web 版校正（进行中）
- **状态**：🚧 进行中（① 前文总结、② 窗口 API 参数、③ 本窗口正则已完成；剩本窗口世界观 + 双框提示词弹窗）
- **交付物**：`tasks/T27-窗口功能弹窗按Web版校正.md` 新增（窗口功能 7 个入口的参考版对照表 + 通用规格 + **前文总结 / 窗口 API 参数的逐段行号规格**）；`ui/summary/SummaryPanel.kt` 重写（`FeatureDialog` 外壳移入本文件、正文按 `SummaryModal.tsx` 五段重排、提示词/模型换 `WwFormControls`、模型由文本框改**下拉**（当前 Provider 的模型清单）、分层底色走 `WwSurfaces`、错误条移到滚动区之外、删掉参考版没有的「角色关系分析」区）；`ui/chat/WindowApiParamsPanel.kt` 重排版（一行式「当前生效」条、M3 `Slider` → 自绘 20dp 紧凑滑杆、10px 药丸按钮、分段按钮选中态、底栏换 `WwPrimaryButton`/`WwSecondaryButton` + 带图标自绘、修正 Top K 图标、去掉参考版没有的清除位）；`ui/App.kt` 接线（总结的 `models`/`currentModel`；窗口 API 参数补徽章与副标题）；`ui/chat/WindowFunctionSheet.kt` 删「双框提示词」项；**本窗口正则合并为一个组件两种形态**：`ui/settings/SettingsPanels.kt` 的 `RegexPanel` 加 session 三件套（`sessionRuleIds`/`onToggleForSession`/`onSetSessionRules`）+ 参考版 `ToggleLeft/Right` 方形开关 + 窗口专属工具条（本窗口全选/全不选）+ 底部说明，**删掉 `ui/chat/WindowRegexPanel.kt`**（旧的窗口精简版），`ui/App.kt` 的两处正则入口共用它；`ui/components/WwFormControls.kt` 的 `WwSecondaryButton` 加可选 `leadingIcon`
- **验收**：`:shared:compileKotlinDesktop` + `compileTestKotlinDesktop` 干净（仅剩 `SessionActionsPanel.kt:49` 的既有 deprecation 警告）；`:desktopApp:build :shared:desktopTest` 全绿 = **361 用例 / 0 失败 / 0 错误 / 5 跳过**。⚠️ 离屏截图走查仍缺（前文总结 / 窗口 API 参数）
- **踩坑**：⚠️ 前两个弹窗「不构建只走查」交付，第三个弹窗一编译就报 3 个错（漏 `Modifier.border` import、尾随 lambda 绑到 `dimmed` 而非 `onClick`、给已类型收窄的非空接收者多写 `?.`）——**这三类只有编译期看得见，别的都不能替代 `compileKotlinDesktop`**；⚠️ `RegexToggleButton` 的 `dimmed` 必须排在 `onClick` 前，否则尾随 lambda 绑错参数
- **备注**：⚠️ 参考版 `SummaryModal` **没有**「角色关系分析」（分析走「窗口功能 → 角色心理分析」，点完即收面板），别再照旧版加回面板；⚠️ `tabler-icons 1.1.1` 是**子集**（1264 个图标），参考版的 `Sparkles`/`Loader2`/`Slider(s)`/`Radio` 等不存在 → 用 `ListCheck`/`Feather`/`Loader`/`Adjustments`/`AntennaBars5` 替代，**写图标前先核 jar**；⚠️ **弹窗排版难看的病根是 M3 控件的固有高度**（`Slider` 自带 48dp 最小交互区、`TextButton`/`OutlinedButton` 内边距偏大）——参考版用原生 `input`/`button` 的地方都别拿 M3 控件顶替，本次窗口 API 参数的六个滑杆改成自绘 24dp；⚠️ **采样参数继承链是「窗口 → 全局默认 → 顾问 → 内置兜底」**（`ChatRequestAssembler.kt:72`），面板原先把顾问放在全局默认前，会把「(跟随默认 X.XX)」显示错，本次已修正；⚠️ 用户提供的「代码复刻报告」行号与细节有误（顶栏 `dl grid-cols-3` 三列统计源码里不存在、删/清空并无 confirm），规格一律回源码逐行核；⚠️ 模型下拉的「跟随本窗口当前模型」显示文案**不是**模型名，选中后要映射回空串；⚠️ 字号缩放（`chat-text-*` 跟随 `chatFontSize`）**全项目未接**，要接得所有弹窗一起接；⚠️ **「同一份数据两种形态」是参考版的既有设计** —— `RegexModal`/`DocumentModal` 在 `App.tsx` 各挂两个实例、靠「传不传 session props」区分，原生曾把窗口形态拆成独立精简组件（`WindowRegexPanel` 只能拨开关），已按参考版合并；下一步 `WindowWorldPanel` 同样处理
- **文档**：[`tasks/T27-窗口功能弹窗按Web版校正.md`](tasks/T27-窗口功能弹窗按Web版校正.md)

### 2026-10-09 · T21 · 图片存储 expect/actual（IndexedDB → 文件系统）
| - **状态**：✅ 完工（`ImageStore` 框架 + 桌面端文件系统实现）
| - **交付物**：`platform/ImageStore.kt` 新增（`StoredImage`、`ImageUsage`、`CompressedImage`、`ImageBlobStore` 接口、`ImageStore` 类、`dataUrlBytes`、`expect fun defaultImageBlobStore()`、`expect fun compressImageBytes()`）；`desktopMain/ImageStore.desktop.kt` 新增（`FileImageBlobStore` 实现、`compressImageBytes` 实现、`actual fun defaultImageBlobStore()`）
| - **验收**：`:desktopApp:build :shared:desktopTest` 全绿，图片上下文测试通过，压缩规则与 Web 版一致
| - **文档**：[`tasks/T21-图片存储.md`](tasks/T21-图片存储.md)

### 2026-10-09 · T20 · 前文总结 / 心理动力学角色关系分析
- **状态**：✅ 完工（领域纯函数 + `ChatViewModel` 链路 + 总结面板 + 分析消息展示/开关/归档）
- **交付物**：`domain/Summary.kt` 新增（`summaryRange`/`unsummarizedMessages`/`buildSummaryTranscript`/`buildSummaryRequest`/`resolveSummaryModel` + `SUMMARY_SYSTEM_INSTRUCTION`）；`domain/RoleAnalysis.kt` 新增（`currentBranchInfo`/`buildRoleAnalysisContext`/`buildRoleAnalysisUserPrompt`/`buildRoleAnalysisFallback`/`resolveAnalysisModel`/`buildAnalysisDocTitle` + 常量 + **`dropHiddenAnalysisMessages`**）；`domain/ContextBuilder.kt` 第 0 步过滤隐藏分析；`data/DefaultData.kt` 新增 `DEFAULT_SUMMARY_PROMPT`；`ui/chat/ChatViewModel.kt` 新增总结/分析链路 + `ChatUiState` 三字段（`publish` 改 `copy`）；`ui/summary/SummaryPanel.kt` 新增；`MessageBubble`/`ChatScreen` 加分析徽章与回调；`SidebarSheet` 加「总结」入口；`App.kt` 接线；测试 `SummaryTest` 7 + `RoleAnalysisTest` 8 + `ContextBuilderTest` +2 + `ChatViewModelSummaryTest` 12
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **40 套 / 326 用例 / 0 失败 / 0 错误 / 5 跳过**；既有测试全部继续通过
- **备注**：⚠️ **`runToolChat` 失败/空回复写 note 不抛** → 一次性调用必须把 known 失败 note 转异常，否则总结会把 `[模型未返回最终正文]` 当正文存下；⚠️ `publish` 必须用 `copy` 保留 `summarizing`/`analyzing`；⚠️ 总结省钱的关键是**推进 `lastSummarizedFloor`**（`dropSummarizedFloors` 才会丢原文），`clearSummaries` **不**回退它；⚠️ **`analysisVisibility == HIDDEN` 的分析报告不进模型上下文**（正式对话/总结/下一次分析三处过滤，`dropHiddenAnalysisMessages`）；⚠️ **同步改了 Web 版**（`api.ts` + `App.tsx`，`tsc --noEmit` 通过）—— 规格书仓也动了；⚠️ 总结失败只写 `summaryError`、不落消息；⚠️ 总结/分析模型目前手填（无模型列表，`fetchModelsFromProvider` 未迁）
- **文档**：[`tasks/T20-总结与关系分析.md`](tasks/T20-总结与关系分析.md)

### 2026-10-09 · T19 · WebDAV 同步（直连 + 增量引擎 + 坚果云应用密码）
- **状态**：✅ 完工（`WebDavClient` 直连 + `SyncEngine` 增量 + 设置面板 + 启动拉取/防抖推送；图片同步见遗留问题）
- **交付物**：`net/WebDavClient.kt` 新增（`webdavSync.ts` 1:1：`testConnection`/`pullBackup`/`pushBackup`/`ensureCollections`/`fetchManifest`/`pushManifest`/`pushSessionFile`/`fetchSessionFile`/`pushImageFile`/`fetchImageFile` + `SyncManifest` + 路径纯函数；**原生直连、去掉 `/api/webdav` 代理**）；`data/repository/SyncEngine.kt` 新增（`SyncState`/`SyncOutcome`/`isSyncConfigured`/`pushChanges`/`pullChanges`，时间戳较新者胜；会话级，图片留 T21）；`ui/settings/SettingsViewModel.kt` + `SettingsPanels.kt`/`SettingsHub.kt` 的 `BackupPanel` 扩成 WebDAV 配置与按钮；`ui/App.kt` 接线（启动 `pullChanges` → `replaceAll` 合并、会话变动 3s 防抖 `pushChanges`）；`WebDavClientTest` 13 + `SyncEngineTest` 7 + `WebDavLiveSmokeTest`（`WW_T19_LIVE=1`）+ `FakeWebDavServer` 测试资产
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **37 套 / 297 用例 / 0 失败 / 0 错误 / 5 跳过**；T06 `ArchiveRoundTripTest`、T07 `StorageRoundTripTest`、T15 `SettingsViewModelTest` 继续通过
- **备注**：⚠️ **原生无 CORS → 直连、不走代理**；⚠️ Basic 认证的 Base64 用 stdlib `kotlin.io.encoding.Base64`（Ktor `encodeBase64()` 已废弃，零警告要求）；⚠️ `pushBackup` 成功文案**有意去掉** Web 版本地时间（commonMain 无本地时间格式化）；⚠️ **拉取合并必须 `SessionRepository.replaceAll`（不盖 `updatedAt`）**，用 `save` 会「时间戳乒乓」；⚠️ `WebDavClient` 的 `isConfigured` **必须含 `enabled`**（用例抓到的真 bug）；⚠️ 图片同步（`pushMissingImages`/拉图）归 T21；⚠️ 无真人账号 E2E，只有 opt-in 在线 smoke
- **文档**：[`tasks/T19-WebDAV同步.md`](tasks/T19-WebDAV同步.md)

### 2026-10-09 · T18 · 世界书与知识库（嵌套分组树 + 文档 CRUD + 老存档 `group`→`groupId` 迁移 + Room v1→v2）
- **状态**：✅ 完工（领域层迁移 + 分组树/文档 CRUD + 世界观库弹窗 + **Room 迁移**；窗口勾选形态见遗留问题）
- **交付物**：`domain/WorldLibrary.kt` 新增（`resolveWorldDocumentGroupIds` = `storage.ts:360-373` 1:1、`selfAndDescendantWorldGroupIds` 带环保护、`worldDocumentsInGroupTree`、`selectSessionWorldDocuments`）；`model/Content.kt` 的 `WorldDocument` 新增 `@SerialName("group") legacyGroup`；`data/db/{ConfigEntities,Mappers}.kt` 为老 `group` 加可空列并**原样落库**；**`data/db/WwMigrations.kt` 新增 `MIGRATION_1_2` + `WwDatabase` `version 1→2` + `open()` 挂 `addMigrations`**（`schemas/1.json` 还原、`schemas/2.json` 新增）；`ConfigRepository.worldDocuments()` 读出即迁移；`ui/world/WorldViewModel.kt` + `ui/world/WorldPanel.kt` 新增；`ui/App.kt` 接线；`WorldLibraryTest` 13 用例 + `WorldViewModelTest` 14 用例 + `WwDatabaseMigrationTest` 2 用例 + `StorageRoundTripTest` 新增 1 用例
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **34 套 / 276 用例 / 0 失败 / 0 错误 / 4 在线 smoke 跳过**；T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 11/11、`WwDatabaseMigrationTest` 2/2 继续通过；**本机真实旧库实测迁移成功**（`user_version` 1→2、`worldDocuments` 多出 `legacyGroup TEXT`、4 索引齐全、数据完整）
- **备注**：🚨 **「库还是 v1、没发布过 → 直接改表不用迁移」是错的** —— 开发机上跑过一次 `:desktopApp:run` 就有 v1 库文件，改完实体直接崩 `Room cannot verify the data integrity`（`openInMemory` 测不出来，因为每次都是新建空库）；**只要改实体就必须 bump 版本 + 写 Migration**；⚠️ 老存档的 `group` 是「**读**时迁移」的字段，导入路径必须让它**先落库**；⚠️ 删除分组是**连带删除**整棵子树的文档；⚠️ 世界观与知识库的启用语义**不对称**；⚠️ 窗口勾选形态与知识库侧的分组/导入仍未做
- **文档**：[`tasks/T18-世界书与知识库.md`](tasks/T18-世界书与知识库.md)

### 2026-10-09 · T17 · 时间线树 + `rearrangeMessagesForAi` + 描述标签审批流
- **状态**：✅ 完工（领域层：时间线树提示词 + 编号分配 + 意图识别 + 剧情推进 + 描述标签审批流；时间线弹窗 UI 见遗留问题）
- **交付物**：`domain/TimelineMemory.kt` 扩写（`TIMELINE_COLORS`/`getTimelineColorConfig`/`createDefaultMainTimeline`/`allocateTimelineCodeTag`/`detectTimelineIntent`/`enrichTimelineProgression`/`buildTimelineTreePromptContext`）；`domain/TimelineTagControl.kt` 新增（`timelineDescriptionTagControl.ts` 1:1：常量 + strip/parse/create/approve/reject）；`domain/ContextBuilder.kt` 第 6 步改为**内部构建**时间线树提示词（`timelineContextPrompt` 降级为可选覆盖）；`TimelineMemoryTest` 17 用例 + `TimelineTagControlTest` 13 用例 + `ContextBuilderTest` 新增 1 用例
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **31 套 / 246 用例 / 0 失败 / 0 错误 / 4 在线 smoke 跳过**；T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10 继续通过
- **备注**：⚠️ `detectTimelineIntent` 的正则**比自然语言窄得多**（「回到分支【A1】」「这是一个新时间线」在 TS 里**就不命中**），验收样本须照 TS 正则实测生成；⚠️ `parseTimelineDescriptionTagControl` 最多只采纳 **1** 个申请（TS `length === 1`）；⚠️ 时间线弹窗 UI、意图识别/剧情推进接线、流式回调里的控制标记剥离均归后续 UI 任务
- **文档**：[`tasks/T17-时间线树.md`](tasks/T17-时间线树.md)

### 2026-10-08 · T16 · 消息版本切换 / 滚动重答 / 图片
- **状态**：✅ 完工（版本切换 + 滚动重答 + 编辑重发 + 删除单条 + 图片占位）
- **交付物**：`ChatViewModel` 新增 `switchVersion`/`deleteMessage`/`editAndResend`/`regenerate` + `regenerateInto` 私有生成路径；`MessageBubble` 新增版本指示器 ← N/M → + 重答/编辑/删除按钮行 + 内联编辑模式 + 图片占位；`ChatScreen` 传递四个可空回调；`App.kt` 接线；`ChatViewModelMessageOpsTest` 6 用例
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **29 套 / 214 用例 / 210 通过 / 4 在线 smoke 跳过 / 0 失败**；T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10 继续通过
- **备注**：⚠️ 图片本体渲染归 T21；⚠️ `regenerateInto` 与 `send` 的后半段结构重复，第三个生成入口出现时应提取公共方法
- **文档**：[`tasks/T16-消息版本与图片.md`](tasks/T16-消息版本与图片.md)

### 2026-10-08 · T15 · 设置类弹窗

- **状态**：✅ 完工（9 类配置面板 + 主题/明暗切换 + 导入/导出 + testRegexRule）
- **交付物**：`ui/settings/SettingsViewModel.kt`（配置 CRUD + 主题/偏好写回 + 导入/导出）、`ui/settings/SettingsHub.kt`（左栏标签 + 右栏内容分发）、`ui/settings/SettingsPanels.kt`（9 个子面板：通用列表/正则/偏好/备份）；`domain/RegexProcessor.kt` 补 `testRegexRule`；`ui/App.kt` 主题从库读取 + 设置按钮接线；`desktopTest/ui/settings/SettingsViewModelTest.kt`（6 用例）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **28 套 / 208 用例 / 204 通过 / 4 在线 smoke 跳过 / 0 失败**；T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10 继续通过
- **备注**：⚠️ `HardcodedChatProvider` 暂未删除（`ChatStreamClient.streamAssistantEvents` + `ChatStreamClientTest` 仍依赖；正式多 Provider 路由时一并清理）；⚠️ ConfigListPanel 只有列表+删除，内联编辑表单属 UI 打磨；⚠️ 导出到文件归 T23
- **文档**：[`tasks/T15-设置类弹窗.md`](tasks/T15-设置类弹窗.md)

### 2026-10-08 · T14 · 会话管理 / 侧边栏 / 分组

- **状态**：✅ 完工（真实落库的会话树 + 侧边栏 + 正式请求链路）
- **交付物**：`data/DefaultData.kt`（`DEFAULT_DISPLAY_SETTINGS` / `DEFAULT_APP_PREFERENCES` 迁移）、`domain/ChatRequestAssembler.kt`（采样参数回退链 + `ToolChatParams` 装配）、`ui/session/SessionViewModel.kt`（会话树状态机）、`ui/chat/ChatViewModel.kt`（发送 / 落库 / 停止 / 联网搜索调用点）、`ui/sidebar/SidebarSheet.kt`（分组折叠 / 搜索 / 会话行操作）；`ui/App.kt` 真实接线、`ChatScreen` 改吃 `ChatSession`+`ChatMessage`、`MessageBubble` 新增正式模型重载、`PlatformStorage.ensureAppDataDir()`；三个新测试类共 19 用例
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 **27 套 / 202 用例 / 198 通过 / 4 在线 smoke 跳过 / 0 失败**；T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10 继续通过；真窗口启动 OK，库落在 `%APPDATA%\WatchWorld\watchworld.db`
- **备注**：⚠️ 上游失败走 T09 工具循环的 note 路径（写进正文），`state.error` 只接「没 Provider / 没 Agent」这类装配失败，否则同一失败显示两遍；⚠️ 取消分支的落库必须包 `NonCancellable`，否则部分正文存不下来；⚠️ `DefaultData` 的常量同时影响全新安装默认值与 `ArchiveStore.snapshot()` 的老存档补齐 → 必须同步改 `StorageRoundTripTest.withStorageDefaults`
- **文档**：[`tasks/T14-会话与侧边栏.md`](tasks/T14-会话与侧边栏.md)

### 2026-10-08 · T13 · Markdown 流式渲染（决策 #2 定案：mikepenz 库）

- **状态**：✅ 完工（追加式流式 + 渲染前预处理 + 行内闪烁光标 + 边界/性能测试）
- **交付物**：`gradle/libs.versions.toml` + `shared/build.gradle.kts` 接入 `com.mikepenz:multiplatform-markdown-renderer(-m3):0.45.0`；`StreamingMarkdown.kt` 用 `StreamingMarkdownState` 只喂新增后缀；`MarkdownPreRender.kt` 落地正则 `OUTPUT` → 工具状态行预处理；`StreamCaret.kt` 用 annotator + inline content 注入不破坏追加契约的 800ms 行内闪烁光标；`MessageBubble` 传 `resetKey = message.id`；新增增量/预处理/真实 GFM AST 光标/离屏边界/360 段性能测试
- **验收**：`:shared:desktopTest --rerun-tasks` 全绿，**180 用例 / 176 通过 / 4 在线 smoke 跳过 / 0 失败**，编译无新增警告；未闭合围栏/行内标记离屏渲染可见；360 段、超过 20K 字符逐段 append + 渲染压测约 2.6s；光标真实 GFM AST 边界测试 4/4
- **备注**：⚠️ **决策 #2 定案：mikepenz `multiplatform-markdown-renderer` 0.45.0**；⚠️ 流式状态不可重置，消息切换靠 `resetKey = message.id`；⚠️ 预处理产生非前缀变化时才重建整段；⚠️ 光标不写进正文，渲染期注入以保住 append-only；代码围栏内光标、硬换行瞬间位置和肉眼并排走查见任务文档「遗留」
- **文档**：[`tasks/T13-Markdown流式渲染.md`](tasks/T13-Markdown流式渲染.md)

### 2026-10-08 · T12 · 主题系统（CSS 变量 → Compose）

- **状态**：✅ 完工（快照对拍全等 + 真窗口 E2E 通过）
- **交付物**：`ui/theme/` 八个文件 —— `OklchColorMath`（theme.ts 正向矩阵逐字 + CSS Color 4 逆向）、`CssColors`（hex/oklch → Color，不认识的语法不猜）、`ThemeScale`（`buildScale` + JS 数字格式化助手）、`ThemeCatalog`（7 族 v4 oklch 表 + 13 内置主题 + 32 色板）、`ThemeVars`（`resolveThemeVars` 直译：family 精确表 + m2 彩度 ×0.4，custom 走 buildScale）、`themeRoles`（两套角色串 1:1 + 类名两维解引用）、`WwTheme`（双轨入口 + `DesignTokens` + 字号 token）、`MaterialColorScheme`（主档位从角色串解出）；`desktopTest/ui/theme/` 6 个测试类 **41 用例** + `ThemeSnapshotCompat`（对拍器，仅测试源集）；`App.kt` 根部挂 `WwTheme(emerald)`，`MessageBubble` 改走角色层
- **验收**：★ 快照对拍 **15 套主题 × 24 变量逐字相等**；oklch→sRGB 7 色相向量 = 浏览器渲染值；`:shared:desktopTest` 共 **151 用例 147 通过 4 跳过 0 失败**（T06/T07 往返测试继续全绿）；★ **真窗口 E2E**：截图像素校验 —— 背景 = surface1 `#F8FAFC`、用户气泡 = emerald-600 v4 oklch 渲染值 `#009966`（带白字）、**M3 默认紫 0 像素**；键入发送后 T03 流式链路无恙
- **备注**：⚠️ **Tailwind v4 的 oklch 表与 v3 hex 同名不同色**（emerald-600 → #009966 而非 #059669，仅色度 C 不同）——浏览器才是 ground truth，向量测试因此全部重写；⚠️ **theme.ts 同一模板串混用两种精度**：L 是 toString（去尾零）、C/H 是 toFixed（固定位留尾零），node 实证定案；⚠️ 双轨方案用户拍板：①生成 M3 ColorScheme 喂 MaterialTheme，②新代码走 `WwTheme.colors`；⚠️ 主题切换/明暗设置入口归 T15（参数已就位）；⚠️ 2026-10-08 收尾清理：`SnapshotCompat` 移入 `desktopTest`（原在 commonMain 会打进产物）、删除未接线的 `resolveThemePair`/`tintAt45`、去掉 `CssColors` 的 no-op 分支（百分比色度改为显式不匹配）；补 `CssColorsTest`（11 用例）时抓到并修复 alpha 真 bug：`oklch(… / 0.9)` 曾被当 0.9% 不透明（深色 AI 气泡近乎全透明），现按「无单位=比例/`%`=百分数」折算；另加 `DarkBubbleScreenshotTest`（无头 `ImageComposeScene` 离屏截图，DARK 下断言 AI 气泡 ~90% 不透明；故意还原 bug 时会失败）作渲染侧回归保险
- **文档**：[`tasks/T12-主题系统.md`](tasks/T12-主题系统.md)

### 2026-10-07 · T11 · 联网搜索 + Skill 搜索

- **状态**：✅ 完工
- **交付物**：`net/WebSearchClient.kt`（只迁 `webSearch.ts` **原生直连**：修好的 `parseDuckDuckGoHtml`、`searchWeb`、oRPC `searchAgentSkills`/`previewSkillMd` 包 `{ json: … }`、`parseSkillFrontmatter`、`isSkillSearchIntent`/`shouldUseWebSearch`、两个 `formatXxxContext`；`/api/…` 代理与 `parseSearchResponse` 有意不迁）；`desktopTest/net/WebSearchClientTest.kt`（17 用例）+ `WebSearchLiveSmokeTest.kt`（在线 smoke，`assumeTrue` 跳过）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 121 用例 117 通过 4 跳过（三个在线 smoke）；`WebSearchClientTest` 17/17、T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10；`WW_T11_LIVE=1` 对真实 DuckDuckGo + agent-skills.md 检索成功
- **备注**：⚠️ DDG 解析采用 `webSearch.ts` 修好的那版，PLAN §6.5 / WORKPLAN 原先指向的 `server.ts parseDuckDuckGoResults` 是**旧的有 bug 那版**（split 会吃掉 `_`）；⚠️ UA 统一 Chrome-Android；⚠️ 接进请求装配（注入 system）归 T14
- **文档**：[`tasks/T11-联网搜索.md`](tasks/T11-联网搜索.md)

### 2026-10-07 · T10 · MCP 客户端（SSE + StreamableHTTP）

- **状态**：✅ 完工
- **交付物**：`net/McpClient.kt`（从零实现，无官方 Kotlin SDK：`McpClient : McpToolCaller`、`testConnection` 返回 `McpConnectionTestResult`、两种 transport —— `StreamableHTTP` 单端点 POST 双响应分流 / `SSE` 常驻 GET + `event: endpoint`；`initialize` 接受服务端降级版本；`mcp-session-id` 有则带无则不带、关闭发 `DELETE`；`tools/list` 翻页；宽松 SSE 帧解析；`explainMcpError` 5 分支）；`desktopTest/net/McpClientTest.kt`（13 用例）+ `McpLiveSmokeTest.kt`（在线 smoke，`assumeTrue` 跳过）；`fixtures/mcp/*.json`
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 102 用例 100 通过 2 跳过（两个在线 smoke）；`McpClientTest` 13/13、T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10；`WW_T10_LIVE_MCP=1` 对真机跑通 `initialize → tools/list → tools/call`
- **备注**：⚠️ 旧式 SSE 只有进程内假上游验证（真机目标是 StreamableHTTP）；⚠️ 不主动 `ping`、收到服务端请求/通知只忽略；⚠️ 版本降级只实测 `2025-06-18 → 2024-11-05`；⚠️ 接设置界面（T15）才写回 `McpServerConfig`
- **文档**：[`tasks/T10-MCP客户端.md`](tasks/T10-MCP客户端.md)

### 2026-10-07 · T09 · 工具轮次循环 + 内置 load_skill

- **状态**：✅ 完工
- **交付物**：`net/ToolCallLoop.kt`（`ChatStreamClient.runToolChat` = `nativeToolChat.ts sendNativeToolChat` :194-544；`registerTools` 原始名优先、重名加 `_2/_3`；内置 `load_skill` 本地拦截；三家 history / tool result 形状、finish_reason 白名单、`note()` 文案逐字；`McpToolCaller` 注入接口）；`net/ChatStreamClient.kt` 补 `streamEvents` / `postJson`；`desktopTest/net/ToolCallLoopTest.kt`（21 用例）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 88 用例 87 通过 1 跳过（T04 在线 smoke），`ToolCallLoopTest` 21/21、T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10；原始名优先 / 重名后缀 / `originalName` 回填 / `toolCallStepLimit` 0=无限均有专门用例
- **备注**：⚠️ 真 `McpToolCaller` 归 T10、图片只留位归 T21、「已停止生成」提示由调用方补（T14）；⚠️ Claude / Gemini 只用协议样例测过（Claude 采集 503，Gemini 免费账号调不了模型）；⚠️ `viaProxy` 与 `replayAsStream` 有意不迁
- **文档**：[`tasks/T09-工具轮次循环.md`](tasks/T09-工具轮次循环.md)

### 2026-10-07 · T08 · 上下文拼装 ContextBuilder

- **状态**：✅ 完工
- **交付物**：`domain/ContextBuilder.kt`（`ContextBuilder.build` = `api.ts sendChatMessage` 拼装段 :154-373；App.tsx 侧 `buildSystemInstruction` / `dropSummarizedFloors` / 五个 `selectXxx` 选择辅助）；`domain/McpAvailability.kt`（`mcpAvailability.ts` 1:1，含 `LOAD_SKILL_TOOL_NAME` 与 `formatSkillManifest`）；`domain/RegexProcessor.kt`（`applyRegexRules` + JS 替换串/`g` 标志语义补丁）；`domain/TimelineMemory.kt`（`rearrangeMessagesForAi` / `formatMessageWithFloorAndTag`）；`desktopTest/domain/ContextBuilderTest.kt`（18 用例）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 67 用例 66 通过 1 跳过（T04 在线 smoke），`ContextBuilderTest` 18/18、T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10；同正则 `\[来自\s+#(\d+)\]`、三重判定、三个 system 标题逐字一致均有专门用例
- **备注**：⚠️ **system 段落顺序以 `api.ts:311-321` 源码为准**（正典 → MCP → Skill → 知识库 → 联网 → 楼号约定 → tail → 时间线树），CONSTRAINTS §4.1 / PLAN §6.6 已按源码修正；⚠️ **有意偏离 TS（方案 A，用户批准）**：时间线模式下正文先过正则再加「[标签] #楼号」前缀（TS 原行为是正则结果被覆盖）；⚠️ 联网搜索（T11）、图片读取（T21）、请求装配与采样参数回退链未做（归 T14），`ContextBuilder` 保持纯函数
- **文档**：[`tasks/T08-上下文拼装.md`](tasks/T08-上下文拼装.md)

### 2026-10-07 · T07 · Room 表 + DAO + Repository

- **状态**：✅ 完工
- **交付物**：Room 2.8.5 / KSP 2.3.12 / sqlite-bundled 2.7.1 接入（`kspDesktop`、`room { schemaDirectory }`）；`data/StorageKeys.kt`（21 个存储键及落点，唯一权威）；`data/db/` —— 17 张表实体与 DAO（`ConfigEntities`/`ConfigDaos`/`SessionEntities`/`SessionDaos`）、`Mappers.kt`、`WwConverters.kt`、`WwDatabase.kt`（`open(全路径)` / `openInMemory`）、`WwTransaction.kt`；`data/repository/` —— `SessionRepository`、`ConfigRepository`、`SettingsRepository`、`ArchiveStore`；`platform/PlatformStorage.kt` + desktop actual（`appDataDir`/`createSqliteDriver`/`sqliteQueryDispatcher`）；`shared/schemas/.../1.json`（要提交）；`desktopTest/data/StorageRoundTripTest.kt`（10 用例）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 49 用例 48 通过 1 跳过（T04 在线 smoke），`StorageRoundTripTest` 10/10、T06 `ArchiveRoundTripTest` 18/18；**文件库关库重开后会话树与 31 条消息完全一致**（会话重启后还在）；三份真实存档经 SQLite 往返模型逐一相等
- **备注**：⚠️ **子表（`messages` / `timelines` / `summaries`）主键必须是 `(sessionId, id)`**：rich 样本两个会话都有 `timeline-main`，单列主键让后写的会话把前一个的同名分支 upsert 走（真实样本抓出来的，合成用例全过）；消息与总结一并改掉，并加了同名 id 互不覆盖的测试；⚠️ Room KMP 没有 `withTransaction`，封装为 `useWriterConnection { it.immediateTransaction { } }`；⚠️ 已知缺口：`defaultData.ts` 未迁（全新安装时显示设置/应用偏好默认值与 Web 版不同）、API 密钥明文存储、Android `actual` 推迟到 T22
- **文档**：[`tasks/T07-Room存储层.md`](tasks/T07-Room存储层.md)

### 2026-10-07 · T06 · v5 存档导入/导出 + 往返测试

- **状态**：✅ 完工
- **交付物**：`data/migration/` 三个新文件 —— `SessionNormalizer.kt`（六条兼容逻辑：模型层 `normalizeChatSession` + 解码前 `sanitizeSessionJson` + 第 ④ 条判据 `toolCallStepLimitFrom`）、`McpServerNormalizer.kt`（旧版 MCP 的 `endpoint`→`url`、`custom_api`/`stdio` 丢弃）、`ArchiveNormalizer.kt`（`normalizeArchiveJson` 解码前 + `normalizeArchive` 解码后）；`data/repository/ArchiveRepository.kt`（`parseArchive`/`tryImportArchive`/`exportArchive`/`redactSecrets`）；`platform/PlatformTime.kt` + desktop actual（`currentTimeMillis`）；`desktopTest/data/ArchiveRoundTripTest.kt`（18 用例）；**新增 fixture `fixtures/sse/archive-v5-rich.json`**（真实导出：2 会话 / 31 消息 / 28 版本，脱敏后入库）；**脱敏工具修 2 增 1**（`redact-archive.mjs` 补身份类字段+邮箱扫描、`scrub-archive-messages.mjs` 扩到总结与梗概、新增 `neutralize-endpoints.mjs`）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿**零警告**；`:shared:desktopTest` 共 39 用例 38 通过 1 跳过（T04 在线 smoke），新增 `ArchiveRoundTripTest` 18/18；**三份真实存档** `v5 JSON → 内存 → v5 JSON` 模型逐一相等；六条兼容逻辑逐条断言；`redactSecrets` 为 `""`
- **备注**：⚠️ **导出时 `version` 会静默消失**：`encodeDefaults = false` 会略过「等于默认值」的键，而 `version` 默认值就是 5、`worldGroups` 默认是空数组 → 修法是给**顶层**铺一层 `encodeDefaults = true` 的骨架（**只铺顶层**，嵌套实体保持 spread 语义）；⚠️ 归一化**被迫分两层**（解码前解决「解不开」、解码后解决「解开了但不对」），少一层会静默漏掉规则 —— 这是测试抓出来的真 bug；⚠️ `normalizeChatSession` 的 `rawToolLimit` 默认参数**必须**用模型自身值回推，否则导出时会把合法的 `toolCallStepLimit` 抹成 `null`（不幂等）；⚠️ 补充了超出原验收范围的 `normalizeMcpServer`（不做则 legacy 的 MCP 地址静默丢失）；⚠️ **采集时连续认错两次文件**（当天导出三次、文件名只差 ` (1)`/` (2)` 后缀，只有最后一次有完整历史）→ 现实测到多版本消息、思维链、总结、待审批队列等；⚠️ **脱敏连续漏了四样东西**（邮箱 / 803 字对话总结 / 端点里的账号段 / plotSummary）→ 判据从「字段叫什么名字」换成「**这段内容是不是从人或对话来的**」，并固化成三脚本分工，详见 `gotchas.md`
- **文档**：[`tasks/T06-v5存档往返.md`](tasks/T06-v5存档往返.md)

### 2026-10-06 · T05 · 24 个数据模型 data class

- **状态**：✅ 完工
- **交付物**：`model/` 新增 10 个文件 —— `Enums.kt`（TS 字符串联合 → 14 个 @SerialName enum）、`Provider.kt`、`Agent.kt`、`Content.kt`、`Message.kt`、`Timeline.kt`、`Settings.kt`、`Session.kt`（`ChatSession`，存档契约的扁平形状）、`ArchiveV5.kt`；**改写** `WwJson.kt`（补 `isLenient` / `explicitNulls=false` / `encodeDefaults=false`）；**启用** T03 起一直缺的 kotlinx-serialization 编译器插件；`desktopTest/model/ArchiveV5ModelTest.kt`（8 用例）
- **验收**：`clean` 后 `:desktopApp:build :shared:desktopTest` 全绿零警告；`:shared:desktopTest` 21 用例 20 通过 1 跳过（T04 的在线 smoke），其中 `ArchiveV5ModelTest` 8/8；legacy（缺 `worldGroups`/`appPreferences`）与 minimal 两份真实存档均解析通过
- **备注**：⚠️ **唯一偏离 PLAN §4.3 之处**：`ChatSession` 保持扁平（不拆 `SessionConfig`/`SessionEntity`）——存档里它本就是扁平对象，拆开会让 T06 往返测试多一层重组；拆解放 T07 的 `data/` 层。⚠️ 实测 `explicitNulls=false` **挡不住显式 null**（无默认值的可空字段仍写成 `"k":null`）→ 本仓约定「新增可空字段必须给 `= null` 默认值」。⚠️ `normalizeChatSession` 的 6 条一条未做，全归 T06，且第 ④ 条要求反序列化时保留原始 `JsonElement`
- **文档**：[`tasks/T05-数据模型.md`](tasks/T05-数据模型.md)

### 2026-10-06 · T03 · 接真实 Provider：Ktor 硬编码直连真流式 **（+采集 fixture）**

- **状态**：✅ 完工 + ✅ 前置采集 70% 完成
- **交付物**：`net/HttpClientFactory.kt`（OkHttp 自动发现，**不装超时**）；`net/ChatStreamClient.kt`（★ `streamSse` = `ByteReadChannel.readLine()` 真流式行流）；`net/HardcodedChatProvider.kt`；`model/WwJson.kt`；`platform/PlatformEnv.kt`（expect/actual）；`desktopTest/ChatStreamClientTest.kt`；**删除** T02 的 `MockChatStream`；**补充**：`fixtures/tools/collect-sse-fixtures.sh`（采集脚本增强）、`fixtures/tools/check-fixtures.mjs`（校验脚本改进）、`fixtures/sse/`（OpenAI 兼容文本流+工具调用 fixture）
- **验收**：`:desktopApp:build` 零警告；`:shared:desktopTest` 6 用例通过；★ **帧间隔用例耗时 1.88s ≈ 6×300ms**，`gaps ∈ 250..400` 通过 → 真流式成立；窗口启动 OK；**采集验证**：OpenAI 文本流通过（1543 行）、OpenAI 工具流通过（89 行含 7 个参数分片）
- **备注**：⚠️ PLAN §6.1 示例的 `readUTF8Line()` 在 Ktor 3.6.0 **已废弃**，改用 `readLine()`（`readLineStrict` 会因长行抛异常，**不可用**）；⚠️ kotlinx-serialization **编译器插件尚未启用**（T05 才需要）；⚠️ **OpenAI 端到端已验证** ✅，Claude 原生 fixture 因中转站模型无渠道暂缺（待配额恢复），Gemini 无 Key；**采集脚本优化**：支持 User-Agent 头注入、错误响应隔离到 `.gitignore` 目录、工具参数分片校验自动化
- **文档**：[`tasks/T03-真实Provider真流式.md`](tasks/T03-真实Provider真流式.md)

### 2026-10-06 · T02 · 最小聊天界面与假流式

- **状态**：✅ 完工
- **交付物**：`net/MockChatStream.kt`（本地假流式，逐字 50ms/帧）；`ui/chat/`（`UiMessage` / `MessageBubble` / `ChatInputBar` / `ChatScreen`）；`ui/markdown/StreamingMarkdown.kt`（★ 块级缓存骨架）；`App.kt` 挂上 `ChatScreen`；两个 commonTest
- **验收**：`:desktopApp:build` 零警告；`:shared:desktopTest` 8 用例通过；窗口启动（`tasklist /V` 见 `WatchWorld Native`）
- **备注**：⚠️ 决策 #2（Markdown 库）仍未定，本任务**自建极简渲染器**占位，T13 换库时只替换 `MarkdownBlock` 正文解析；⚠️ `MockChatStream` 是临时脚手架，T03 删除；⚠️ **逐字/光标/停止三项只做了单测与启动验证，肉眼确认待补**
- **文档**：[`tasks/T02-最小聊天界面与假流式.md`](tasks/T02-最小聊天界面与假流式.md)

### 2026-10-06 · T01 · 建 KMP 工程与空窗口

- **状态**：✅ 完工
- **交付物**：Gradle wrapper 8.14.4；`libs.versions.toml`（Kotlin 2.4.20 / Compose MP 1.12.1 / material3 1.9.0）；`settings.gradle.kts` + 根 `build.gradle.kts` + `gradle.properties`；`shared/`（KMP + Compose，`App()`）；`desktopApp/`（窗口 + 渲染后端开关）
- **验收**：`./gradlew :desktopApp:run` 弹出标题 `WatchWorld Native` 的窗口（`tasklist /V` 实测可见）；`:desktopApp:build` 零警告
- **备注**：⚠️ 本机 skiko 默认 Direct3D 后端硬崩（`0xC0000409`），已默认改用 `OPENGL`，可用 `-PwwRender=` 覆盖；⚠️ material3 是**独立版本线**（1.9.0 ≠ 插件 1.12.1），升级 Compose MP 时必须重新核对
- **文档**：[`tasks/T01-建KMP工程与空窗口.md`](tasks/T01-建KMP工程与空窗口.md)

### 2026-10-06 · T00 · 建立原生仓骨架

- **状态**：✅ 完工
- **交付物**：`WatchWorld-Native/` 独立 git 仓；`docs/` 文档副本 + `FILE_LAYOUT.md`；`.gitignore`；`fixtures/` 三个模板；`memory/` 索引与函数地图；`tasks/` 体系
- **验收**：`git check-ignore local.properties` 命中；`fixtures/` 正式资产未被忽略；React 仓零改动
- **备注**：仓建在默认分支 `master`（未提交）。fixtures 按 `FILE_LAYOUT.md` §2 改为放原生仓，与原文档路径有偏差
- **文档**：[`tasks/T00-建立原生仓骨架.md`](tasks/T00-建立原生仓骨架.md)

---

## §二、任务文档索引（路径与名字）

> 做完一个任务就在此登记一行。点开即见该任务的**改动 / 心得 / 要点**。

| 任务号 | 文档路径 | 名字 | 状态 |
|---|---|---|---|
| T00 | [`tasks/T00-建立原生仓骨架.md`](tasks/T00-建立原生仓骨架.md) | 建立原生仓骨架 | ✅ 完工 |
| T01 | [`tasks/T01-建KMP工程与空窗口.md`](tasks/T01-建KMP工程与空窗口.md) | 建 KMP 工程与空窗口 | ✅ 完工 |
| T02 | [`tasks/T02-最小聊天界面与假流式.md`](tasks/T02-最小聊天界面与假流式.md) | 最小聊天界面与假流式 | ✅ 完工 |
| T03 | [`tasks/T03-真实Provider真流式.md`](tasks/T03-真实Provider真流式.md) | 真实 Provider 真流式 | ✅ 完工 |
| T04 | [`tasks/T04-SSE解析与三家Provider.md`](tasks/T04-SSE解析与三家Provider.md) | SSE 解析与三家 Provider | ✅ 完工（Gemini 在线生成受账号限制，见文档） |
| T05 | [`tasks/T05-数据模型.md`](tasks/T05-数据模型.md) | 数据模型 | ✅ 完工 |
| T06 | [`tasks/T06-v5存档往返.md`](tasks/T06-v5存档往返.md) | v5 存档往返 | ✅ 完工 |
| T07 | [`tasks/T07-Room存储层.md`](tasks/T07-Room存储层.md) | Room 存储层 | ✅ 完工 |
| T08 | [`tasks/T08-上下文拼装.md`](tasks/T08-上下文拼装.md) | 上下文拼装 | ✅ 完工 |
| T09 | [`tasks/T09-工具轮次循环.md`](tasks/T09-工具轮次循环.md) | 工具轮次循环 | ✅ 完工 |
| T10 | [`tasks/T10-MCP客户端.md`](tasks/T10-MCP客户端.md) | MCP 客户端 | ✅ 完工 |
| T11 | [`tasks/T11-联网搜索.md`](tasks/T11-联网搜索.md) | 联网搜索 | ✅ 完工 |
| T12 | [`tasks/T12-主题系统.md`](tasks/T12-主题系统.md) | 主题系统 | ✅ 完工 |
| T13 | [`tasks/T13-Markdown流式渲染.md`](tasks/T13-Markdown流式渲染.md) | Markdown 流式渲染 | ✅ 完工 |
| T14 | [`tasks/T14-会话与侧边栏.md`](tasks/T14-会话与侧边栏.md) | 会话与侧边栏 | ✅ 完工 |
| T15 | [`tasks/T15-设置类弹窗.md`](tasks/T15-设置类弹窗.md) | 设置类弹窗 | ✅ 完工 |
| T16 | [`tasks/T16-消息版本与图片.md`](tasks/T16-消息版本与图片.md) | 消息版本与图片 | ✅ 完工 |
| T17 | [`tasks/T17-时间线树.md`](tasks/T17-时间线树.md) | 时间线树 | ✅ 完工 |
| T18 | [`tasks/T18-世界书与知识库.md`](tasks/T18-世界书与知识库.md) | 世界书与知识库 | ✅ 完工 |
| T19 | [`tasks/T19-WebDAV同步.md`](tasks/T19-WebDAV同步.md) | WebDAV 同步 | ✅ 完工（图片归 T21） |
| T20 | [`tasks/T20-总结与关系分析.md`](tasks/T20-总结与关系分析.md) | 总结与关系分析 | ✅ 完工 |
| T21 | [`tasks/T21-图片存储.md`](tasks/T21-图片存储.md) | 图片存储 | ✅ 完工 |
| T22 | `tasks/T22-Android平台层.md` | Android 平台层 | ⬜ 待建 |
| T23 | `tasks/T23-打包交付.md` | 打包交付 | ⬜ 待建 |
| T27 | [`tasks/T27-窗口功能弹窗按Web版校正.md`](tasks/T27-窗口功能弹窗按Web版校正.md) | 窗口功能弹窗按 Web 版校正 | 🚧 进行中（前文总结、窗口 API 参数、本窗口正则已完成） |
| T28 | [`tasks/T28-Agent顾问中心按Web版复刻.md`](tasks/T28-Agent顾问中心按Web版复刻.md) | 设置弹窗按 Web 版校正 · Agent 顾问中心 | ✅ 完工 |
| T29 | [`tasks/T29-备份导入导出按Web版复刻.md`](tasks/T29-备份导入导出按Web版复刻.md) | 设置弹窗按 Web 版校正 · 备份与导入导出 | ✅ 完工 |
| T30 | [`tasks/T30-设置弹窗剩余四项按Web版复刻.md`](tasks/T30-设置弹窗剩余四项按Web版复刻.md) | 设置弹窗按 Web 版校正 · 剩余四项 | ✅ 完工 |
| T31 | （见 §一 2026-10-13 记录） | 构建修复 + 主题废案清理 + 夜晚气泡修复 | ✅ 完工 |

> 备查：[`tasks/_TEMPLATE.md`](tasks/_TEMPLATE.md)

---

## §三、任务清单（严格按序执行）

**排序原则：先让效果可见，再补正确性，最后补完整性。**
每个任务都标了「可见效果」—— 没有可见效果的任务不排在前面。

### 阶段 0 · 能跑起来

- [x] **T01 建 KMP + Compose MP 工程，桌面弹出空窗口** ✅
  - 可见效果：一个能打开的 Windows 窗口
  - 验收：`gradlew :desktopApp:run` 出窗口；版本固定为 Compose MP 1.12.1 / Ktor 3.6.0（PLAN §2.1）
  - 产出：`settings.gradle.kts` `gradle/libs.versions.toml` `shared/` `desktopApp/`

### 阶段 1 · 看见"流式"

- [x] **T02 最小聊天界面 + 本地假流式（不联网）** ✅
  - 可见效果：点发送后文字**一个字一个字蹦出来**，带光标闪烁与停止按钮
  - 验收：mock 每 50ms 推一帧；停止按钮能中断
  - 参考：`ChatInput.tsx`(760) `MessageItem.tsx`(559)
  - ⚠️ 此时就开始用**块级缓存**渲染 Markdown（PLAN §7.3），别等 T13 再改

- [x] **T03 接真实 Provider：Ktor 硬编码直连真流式** ✅
  - 可见效果：**真实模型回复逐字出现**（配置写死在代码里，先绕过存档与设置界面）
  - 验收：★ 帧间隔呈稳定节奏（上游 300ms/帧 → 实测 250–400ms），**非一次性到达**（PLAN §8.2）
  - 产出：`net/HttpClientFactory.kt`（统一 OkHttp）`net/ChatStreamClient.kt`
  - ⚠️ 红线：不得用 `bodyAsText()` / `body<String>()` / `flow{}.buffer()` / 额外 `withContext(IO)`（CONSTRAINTS §3.2）

### 阶段 2 · 让流式"对"

- [ ] **T04 SSE 解析器 + 三家 Provider 归一化 + fixture 单测** 🚧（OpenAI/DeepSeek 官方真流均验证通过；Claude 样本纯度/格式校验由用户确认通过；Gemini 协议样例通过）
  - 前置：`fixtures/sse/` 采集完成（见 `docs/BASELINE_COLLECTION.md` §A）
  - 可见效果：切到 Claude / Gemini 也能正常流式
  - 验收：三家 fixture 单测全过；Claude 缺 `content_block_start` 必须抛错；Gemini `part.thought==true` 走思维链不进正文
  - 执行说明：T04 尚未完成的 Gemini 真实流 fixture 与 Claude/Gemini 应用内实时请求验收，不影响后续功能开发；执行后续任务时 T04 不是阻塞条件，可继续按计划推进。
  - 参考：`nativeToolChat.ts` 的 `sseLine`(:73) `readNativeEvents`(:96) 与 receive 分支(:354-395)
  - ⚠️ 红线：不换第三方 SSE 库；双结束条件（`[DONE]` + `message_stop`）都要保留

### 阶段 3 · 让数据落地

- [x] **T05 24 个数据模型 data class** ✅
  - 可见效果：（无）为后续铺路
  - 验收：字段名与 `src/types.ts` 逐一对应；`WwJson` 配置 `ignoreUnknownKeys`/`explicitNulls=false`/`coerceInputValues`；两份真实 v5 存档样本解析通过
  - 参考：`memory/function-map.md` §A
  - ⚠️ `ChatSession` 保持扁平形状（存档契约），拆表留到 T07 的 `data/` 层

- [x] **T06 v5 存档导入/导出 + 往返测试** ✅
  - 前置：`fixtures/sse/archive-v5-*.json` 已脱敏采集（⚠️ rich 仍缺，见任务文档「遗留问题」）
  - 可见效果：**导入真实老存档不丢数据**
  - 验收：`v5 JSON → 内存 → v5 JSON` 关键字段一致；`normalizeChatSession` 的 6 条兼容逻辑一条不漏
  - 参考：`storage.ts` `exportAllData`(:417) `importAllData`(:453) `normalizeChatSession`(:86)
  - ⚠️ 红线：`theme` 单数、`worldDocuments` 复数、`version: 5` 不可改、`redactSecrets` 是 `''` 不是 `null`；`streamingEnabled` 三态

- [x] **T07 Room 表 + DAO + Repository** ✅
  - 可见效果：会话重启后还在
  - 验收：`messages` 从 `sessions` 拆出；写操作走 `suspend`，同事务更新 `updatedAt`；UI 不直接碰 DAO
  - 参考：PLAN §5.1 表结构

### 阶段 4 · 接回完整对话链路

- [x] **T08 上下文拼装 ContextBuilder** ✅
  - 可见效果：世界观/知识库/MCP 能力清单正确注入
  - 验收：`pushedCommonFloors` 过滤用同正则 `\[来自\s+#(\d+)\]`；`isCommonMessage` 三重判定；system 段落标题逐字一致
  - 参考：`api.ts` `sendChatMessage`(:77)，拼装段 109–250

- [x] **T09 工具轮次循环 + 内置 `load_skill`** ✅
  - 可见效果：模型能调用 MCP 工具并继续回答
  - 验收：原始工具名优先、重名才加 `_2/_3`、回填 `originalName`；`toolCallStepLimit` 0=无限
  - 参考：`nativeToolChat.ts` 工具循环段；`mcpAvailability.ts`

- [x] **T10 MCP 客户端（SSE + StreamableHTTP）** ✅
  - 可见效果：MCP 工具真的能连通并调用
  - 验收：`initialize → tools/list → tools/call` 三步；降级可只做后两步
  - ⚠️ 风险：**无官方 Kotlin SDK，从零实现**（PLAN §6.7）

- [x] **T11 联网搜索 + Skill 搜索** ✅
  - 可见效果：能联网检索并注入上下文
  - 验收：DDG HTML 解析 1:1 直译；`User-Agent` 必带；`limit` 钳 1..10；Skill 走 oRPC `{ "json": ... }` 包一层
  - 参考：`server.ts` `parseDuckDuckGoResults`(:70)；`webSearch.ts`（⚠️ 实际采用 `webSearch.ts` 修好的 `parseDuckDuckGoHtml`，`server.ts` 那版是旧的）

### 阶段 5 · 界面补全

- [x] **T12 主题系统（CSS 变量 → Compose）+ 并排走查** ✅
  - 可见效果：明暗主题与 Web 版观感一致（快照逐字对拍 + 截图像素校验实证）
  - 验收：对照 `fixtures/theme-baseline/` 逐项调色 —— 15 套 × 24 变量全等；`resolveThemeVars` 映射逻辑直译；真窗口 E2E：背景/气泡色逐像素命中，M3 默认紫 0 像素
  - 参考：`theme.ts` `resolveThemeVars`(:284)；`src/index.css` 288 行
  - ⚠️ 原计划「两版并排肉眼走查」以快照逐字对拍 + 像素级 E2E 替代了主体；MIDNIGHT 变体与主题切换的肉眼走查待 T15 出设置界面后收尾
  - ⚠️ **2026-10-13 后续变更**：主题模型已从 React 的 `family`/`custom` 两分法换成**「白天/黑夜 × 饱和度分级」**（内置 13 → **20** 套，id 形如 `emerald-day`/`emerald-night`）。快照 fixture 同步重生成（15 → **22** 套，改由本侧 `ThemeBaselineGenerator` 导出，"与 React 逐字一样"这条验收**已不适用**）。气泡规则由「预设固定 `#ffffff`/深灰」改为**主色派生档**（亮 `m1[2]`、暗 `m1[10]`）。详见 §一 2026-10-13 记录

- [x] **T13 Markdown 流式渲染压测** ✅（追加式流式 + 渲染前预处理 + 行内光标 + 边界/性能测试）
  - 可见效果：长回复不掉帧
  - 验收：只重解析末块；未闭合代码块/行内标记容错；正则替换在**渲染前**作用于纯文本
  - 参考：PLAN §7.3；`MarkdownRenderer.tsx`；决策 #2 定案 = mikepenz `multiplatform-markdown-renderer` 0.45.0

- [x] **T14 会话管理 / 侧边栏 / 分组** ✅
  - 可见效果：侧边栏能管理历史会话（分组 / 置顶 / 改名 / 清空 / 删除 / 移动），**重启后会话与消息都还在**
  - 验收：UI 不直调 DAO；写操作 `suspend` 且同事务盖 `updatedAt`；采样参数回退顺序与 `api.ts` 逐项一致（有专门用例）；停止生成保留部分正文并落库
  - 参考：`SidebarDrawer.tsx`(555)；`App.tsx` 的 session 系列 handler
  - 范围补充（2026-10-07 从 T08/T09 遗留划入）：**请求装配与采样参数回退链** —— 已迁 `DEFAULT_APP_PREFERENCES`；`temperature ?? defaults ?? agent ?? 0.7` 等逐项照抄；`ContextBuilder.build` 的产物与回退后的参数组装成 `ToolChatParams` 交给 `runToolChat`
  - ⚠️ T04 的 Gemini 在线生成验证按用户确认为**环境限制**（账号只能拉模型列表），不作为本任务的阻塞条件

- [x] **T15 设置类弹窗** ✅
  - 范围：Provider / Agent / Prompt / Regex / Knowledge / MCP / Skill / Preferences / BackupSync
  - 参考：`memory/function-map.md` §I

- [x] **T16 消息版本切换 / 滚动重答 / 图片** ✅

### 阶段 6 · 特色功能

- [x] **T17 时间线树 + `rearrangeMessagesForAi` + 描述标签审批流** ✅（领域层：树提示词/编号分配/意图识别/剧情推进/审批流；弹窗 UI 见任务文档「遗留问题」）
  - ⚠️ 最难项；`memory/function-map.md` §E
- [x] **T18 世界书 / 知识库（嵌套分组树）** ✅（领域层迁移 + 分组树/文档 CRUD + 世界观库弹窗 + Room v1→v2 迁移；窗口勾选形态见任务文档「遗留问题」）
- [x] **T19 WebDAV 同步（含坚果云应用密码）** ✅（直连 + 增量引擎 + 设置面板 + 启动拉取/防抖推送；图片同步归 T21）
- [x] **T20 前文总结 / 心理动力学角色关系分析** ✅（领域纯函数 + `ChatViewModel` 链路 + 总结面板 + 分析消息展示/开关/归档）

### 阶段 7 · 平台与交付

- [x] **T21 图片存储 expect/actual（IndexedDB → 文件系统）** ✅（`ImageStore` 框架 + 桌面端文件系统实现）
- [ ] **T22 Android 平台层**（权限 / `BackHandler` / `WindowInsets` / `imePadding`）
- [ ] **T23 打包 `.exe`（jpackage）+ APK（assembleRelease）+ 签名**

---

## §四、每次交付前的自检（CONSTRAINTS §8）

- [ ] 有没有**改字段名**？（= 存档不兼容）
- [ ] 有没有**改存储键**？（= 数据丢失）
- [ ] 有没有**改执行顺序**？
- [ ] 有没有**改字符串字面量**（system 标题 / 工具名 / 正则）？
- [ ] 改的东西**是否需要同步改另一处**？
- [ ] 判断某处"应该重构"时，**是否先问了**而不是直接改？
- [ ] 存档往返测试**是否仍然通过**？
- [ ] 本任务的**任务文档是否已写「改动 / 心得 / 要点」**并登记到 §二？
