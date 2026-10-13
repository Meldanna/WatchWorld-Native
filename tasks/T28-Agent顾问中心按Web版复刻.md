# T28 · 设置弹窗按 Web 版校正 — Agent 顾问中心

| | |
|---|---|
| 任务号 | T28 |
| 日期 | 2026-10-10 |
| 状态 | ✅ 完工 |
| 阶段 | 阶段 7 · UI 精修 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T28 |

## 目标与可见效果

- **目标**：把 Agent 顾问中心按参考版 `AgentModal.tsx`（572 行）复刻 ——
  列表卡片、选中态、完整编辑表单（含发散度滑杆、标签、长期记忆、文件权限、MCP / Skill 绑定）；
  并按参考版把它做成**独立弹窗**（不是设置的内部子页）。
- **可见效果**：从输入栏 Agent 按钮或设置 → Agent 管理打开，界面与参考版截图可对拍；
  点卡片能切换当前窗口顾问并关闭弹窗。
- **验收标准**：① 输入控件走 `WwFormControls`；② 分层底色走 `WwSurfaces`（无中性灰块）；
  ③ 编辑表单三段式（固定顶栏 + 滚动内容 + 固定底栏）；④ 关闭 / 选中返回正确上级
  （从设置进来的回设置总入口，从输入栏进来的回聊天界面）。

> 规格文档：[`tasks/agent-source-map.md`](agent-source-map.md)（参考版逐段行号对照）。

## 改动

| 文件 | 改动 |
|---|---|
| `tasks/agent-source-map.md` | **新增**：参考版 `AgentModal.tsx` 的逐段行号规格（入口 / 两层结构 / 表单 10 个字段 / 逻辑 / Native 落点 / 有意分歧） |
| `ui/settings/SettingsPanels.kt` | `AgentPanel` **重写**（列表卡片 + 选中态 + 新建按钮 + 删除确认）；**新增** `AgentEditorDialog`（完整编辑表单）与 `AgentBindingChips`（多选 chip），以及 `toggleAgentId` / `snapTemperature` / `formatTemperature` / `fileAccessLabel` 四个私有辅助 |
| `ui/settings/SettingsHub.kt` | `SettingsPage` **去掉 `AGENT` 项**（它不再是内联子页），改为入口卡片列表里第一张卡调用新参数 `onOpenAgent`；签名里删掉 `onSaveAgent` / `onDeleteAgent` / `agentActiveId` / `onSelectAgent` |
| `ui/chat/ChatViewModel.kt` | **新增** `selectAgent(agentId)` —— 把选中顾问写进 `ChatSession.agentId` |
| `ui/App.kt` | 新增 `agentOpen` + `agentReturnsToSettings` 一对状态与顶层 `FeatureDialog`（标题 `Agent 顾问中心` + 副标题 + 徽章）；输入栏入口与设置入口两处接线（设置入口先 `settingsOpen = false`）；`SettingsHub` 调用点改传 `onOpenAgent` |
| `desktopTest/.../TimelinePanelScreenshotTest.kt` | 补 `onOpenKnowledge = {}`（`WindowFunctionSheetContent` 新增参数的既有缺口，非本次引入） |

**图标替代**（参考版 lucide → Tabler 子集里实际存在的）：

| 位置 | 参考版 | Native | 理由 |
|---|---|---|---|
| 顶栏徽章 / 设置卡 | `Bot` | `UserCheck` | 子集无 `Bot`（核过 jar：`UserCheck` / `Users` / `MoodSmile` 有，`Robot` 无） |
| 记忆与文件权限 | `FileLock` | `Lock` | 子集无 `FileLock` |
| 选中徽章 | `Check` | `Check` | 同名可用 |
| 编辑 / 删除 | `Edit2` / `Trash2` | `Edit` / `Trash` | 子集只有这两个名字 |

## 心得

- **⚠️ 先看清「设置里的入口」到底是子页还是独立弹窗**：第一版把 Agent 做成了
  `SettingsHub.kt` 里的 `SettingsPage.AGENT` 内联子页（点卡 → 面板内容替换卡片列表），
  被用户指出「它应该是弹窗，不是设置内置的子页面」。
  回源码核实：参考版 `SettingsHubModal.tsx:43-47` 的 `handle()` 是 `onClose()` + `setTimeout(fn)`
  —— **先把设置总入口关掉，再开目标弹窗**（注释原文「避免多层级弹窗叠压」），
  两者从不同时在场。这意味着设置里的**每一个**二级入口在参考版都是独立弹窗，
  Native 那套「`SettingsPage` 枚举 + 内联 `when`」整体是偏离的（本轮只修了 Agent）。
  **教训**：看到「设置里的一张卡」不要默认它是内联子页 —— 先读那张卡的 `onClick` 做了什么。
- **先核 jar 再写图标名**：`tabler-icons 1.1.1` 是子集。本次先按印象写了 `Bot` / `FileLock`，
  编译才报 `Unresolved reference`；`unzip -l` 出 1264 个名字里这两个确实不存在。
  T27 已经踩过一次，这次仍然踩了，值得记牢。
- **参考版「嵌套弹窗」在原生要换外壳**：`AgentModal` 的表单层是又套了一层
  `fixed inset-0 z-[80]` 的完整弹窗（遮罩 + 卡片 + 自己的 ✕），列表层那个弹窗仍留在下面。
  原生没有「Dialog 里再开 Dialog」的必要，统一按项目 §七 走 `FeatureDialog` 三段式、
  底栏固定 —— 与 `RegexPanel` / `KnowledgePanel` / `McpPanel` 的表单层同一套做法。
- **`Slider` 的 `steps` 不吸附**：M3 `Slider` 的 `steps` 只影响无障碍的落点提示，
  拖动仍返回连续值。参考版是 `<input type="range" step="0.05">`（真吸附），
  所以额外写了 `snapTemperature` 在 `onValueChange` 里吸附 —— 否则 `temperature` 会存成 `0.7234...`。
- **`"%.2f".format()` 在 commonMain 不可用**：`String.format` 是 JVM-only。
  改成手写 `formatTemperature`（`round(x*100)` + 手动补零），
  顺带与参考版 `{temperature}` 直接插值的渲染结果一致（`0.7` 而不是 `0.70`）。
- **删枚举项会连带删掉它的 import**：`SettingsPage.AGENT` 用的 `TablerIcons.Bolt` 在改完后
  成了唯一未使用的 import —— 这类孤儿由自己的改动产生，必须一起清掉。
- **`FileAccess.entries` 的顺序 = 声明顺序**（`NONE` / `READ` / `READWRITE`），
  正好与参考版 `<select>` 选项顺序一致，下拉不需要额外排序。

## 要点

- **⚠️ Agent 是独立弹窗，不是设置的内部子页**（用户 2026-10-10 定案 + 参考版 `handle()` 佐证）。
  别再把 `SettingsPage.AGENT` 加回来，也别让它退回「替换设置内内容」的做法。
- **两个入口共用同一个 `agentOpen` 弹窗**：输入栏 Agent 按钮（`agentReturnsToSettings = false`）
  与设置卡（`agentReturnsToSettings = true`）。**关闭返回靠这一个布尔**：
  从设置来的回设置总入口，从输入栏来的回聊天界面（对齐 `closeWithReturn`，`App.tsx:280-287`）。
- **设置入口开弹窗前必须先 `settingsOpen = false`**（参考版 `handle()` 的 `onClose()`）——
  两个 `FeatureDialog` 同时在场会叠两层遮罩。
- **`SettingsPage` 枚举里剩下的五项仍是内联子页**，与参考版不一致（参考版每个都是独立弹窗）。
  本轮的「一个入口一个独立弹窗」只做了 Agent，其余待后续任务逐项改造。
- **选中态写的是当前窗口**：`ChatViewModel.selectAgent` 改的是 `ChatSession.agentId`，
  **不是**全局偏好里的 `appPreferences.defaultSession.agentId`（那个在「默认与偏好」里改）。两者语义不同。
- **新建的 id 前缀是 `agent-custom-`**（对齐参考版 `agent-custom-${Date.now()}`）；
  内置顾问 id 不带这个前缀，且 `isBuiltin == true` 的行**不渲染删除按钮**。
- **`settingsState` 里已经有 `mcpServers` / `skills`**：绑定 chip 直接用这两份数据，不需要新开状态源。
  MCP chip 的「· 可用」判据是 `status == CONNECTED && tools.isNotEmpty()`（= 参考版 `modelReady`）。
- **删除用 `AlertDialog` 确认**，不是浏览器 `confirm()` —— 项目内删除一律这么写
  （`RegexPanel` / `KnowledgePanel` / `McpPanel` 同款）。
- **标签解析**：`split(Regex("[,，\\s]+"))` + 过滤空串，空则回退 `listOf("自定义")`（参考版同）。
- **发散度范围 0–1.5 / step 0.05**，上限**不是** 1.0 —— 参考版 `max="1.5"`，别按 M3 惯例改成 1.0。
- **`fileAccess` 三档文案**：`不可读写文件` / `只读文件` / `可读写（可主动写入记忆）`，
  第三档的「可主动写入记忆」是参考版原文（`types.ts:62-63` 的语义说明），不要精简掉。
- **删除顾问后不清理引用**（参考版同）：删掉当前顾问会让 `session.agentId` 悬空，
  运行时由 `ChatViewModel` 的 `agents.firstOrNull() ?: agents.first()` 兜底。

## 验收结果

- [x] `AgentPanel` 重写 + `AgentEditorDialog` + `AgentBindingChips`，字段与顺序对齐 `AgentModal.tsx`。
- [x] **改成独立弹窗**：`SettingsPage` 去掉 `AGENT`，`App.kt` 新增 `agentOpen` + `agentReturnsToSettings`。
- [x] `SettingsHub` / `App.kt` 两处入口接线（设置入口先关设置总入口）。
- [x] `ChatViewModel.selectAgent` 新增。
- [x] 规格文档 `tasks/agent-source-map.md` 新增（带行号）。
- [x] `:shared:compileKotlinDesktop` 干净（0 错误 0 警告）。
- [x] `:desktopApp:build :shared:desktopTest` 全绿 = **361 用例 / 0 失败 / 0 错误 / 5 跳过**。
- [ ] 离屏截图走查：本轮未加（Agent 面板尚无截图回归）。
- [ ] 手工走查（未做）：从设置进 Agent → 关闭应回设置；从输入栏进 → 关闭应回聊天。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §5：UI 只拿 Repository / ViewModel —— 本次只改 UI 层 + 一个 `ChatViewModel` 方法。
- `MIGRATION_CONSTRAINTS.md` §6：字段名不改 —— `Agent` 的全部字段名原样使用，未改模型。
