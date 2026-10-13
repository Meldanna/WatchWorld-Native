# Agent 顾问中心源代码地图

> 源码仓库：`E:\WatchWorld\WatchWorld\src`（React/TypeScript）
> 目标仓库：`E:\WatchWorld\WatchWorld-Native\shared\src\commonMain\kotlin\com\watchworld`
> 采集时间：2026-10-10。**行号会漂，以符号名为准。**

---

## 一、入口（两个，**都是独立弹窗**）

| 入口 | 参考版 | 行号 | Native |
|---|---|---|---|
| 输入栏 Agent 按钮 | `ChatInput.tsx` → `onOpenAgentModal` → `setIsAgentModalOpen(true)` | `App.tsx:2210, 2316-2327` | `App.kt` 的 `agentOpen`（`ChatScreen` 的 `onOpenAgent`，`agentReturnsToSettings = false`） |
| 设置总入口「Agent 管理」卡 | `SettingsHubModal.tsx` 第 1 张卡（Bot 图标 indigo） | `SettingsHubModal.tsx:81-93` | `SettingsHub.kt` 的 `onOpenAgent` 卡片（`agentReturnsToSettings = true`） |

⚠️ **Agent 不是设置的内部子页**。参考版 `SettingsHubModal` 的每个二级入口都走
`handle()`（`SettingsHubModal.tsx:43-47`）：

```tsx
const handle = (fn: () => void) => {
  onClose();                              // 先关掉设置总入口
  window.setTimeout(fn, 0);               // 再开目标弹窗（注释：避免多层级弹窗叠压）
};
```

即**设置总入口在目标弹窗打开时并不在场**，两者从不同时出现。

**关闭返回**：设置入口经 `closeWithReturn` 回设置总入口，不是聊天界面 ——
`returnToHubRef.current = true` 在 `onOpenAgentManager` 里置位（`App.tsx:2488-2491`），
关闭时 `closeWithReturn` 检查该标志并 `setIsSettingsHubOpen(true)`（`App.tsx:280-287`）。
输入栏入口不置位，关闭即回聊天界面。

Native 对应：`App.kt` 的 `agentOpen` + `agentReturnsToSettings` 一对状态；
`onOpenAgent`（设置卡）先 `settingsOpen = false` 再 `agentOpen = true`，关闭时按标志回设置。

---

## 二、组件结构（参考版 `AgentModal.tsx`，572 行）

### 2.1 外层弹窗（L121-168）

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 遮罩 | 123-126 | `fixed inset-0 z-[70] bg-black/80 backdrop-blur-sm`，点击 = `onClose` |
| 卡片 | 129-135 | `w-full max-w-xl`(576dp) · `max-h-[90vh] flex flex-col` · `rounded-2xl` · `p-4 sm:p-5` · `border`；底 `var(--surface-elevated)`、描边 `var(--border-default)` |
| 顶栏 | 137-168 | 32dp 徽章（`w-8 h-8 rounded-xl`，bg `--m2-soft` + border/文字 `--m1-500`，Bot 18）+ 标题 `Agent 顾问中心`（`chat-text-sm font-semibold`）+ 副标题 `切换不同角色设定或创建专属顾问`（`text-[11px]` tertiary）+ ✕ |

### 2.2 两种内容（L171 起，二选一）

`isCreating || editingAgent` 决定渲染**表单层**还是**列表层**。

#### 列表层（L465-567）

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 工具条 | 467-481 | 左 `可用顾问列表`（`chat-text-xs` tertiary）；右「+ 新建顾问」按钮：`py-1 px-2.5 rounded-lg border`，文字 `--m1-500`、底 `--m2-soft`、描边 `--m1-500`，Plus 14 |
| 顾问卡 | 483-566 | `p-3 rounded-xl border flex items-start gap-3`；**选中**：底 `--surface-2`、描边 `--m1-500`、`box-shadow: 0 0 0 1px var(--m2-soft)`；**未选**：底 `--surface-1`、描边 `--border-default` |
| 头像 | 500-502 | `chat-text-2xl mt-0.5 shrink-0 select-none` |
| 名称行 | 505-517 | 名称 `chat-text-xs font-semibold`（选中 = `--m1-500`，否则 `--text-primary`）；标签逐个 `text-[10px] font-mono` tertiary，前缀 `· ` |
| 简介 | 518-520 | `text-[11px] line-clamp-2 leading-relaxed` secondary |
| 选中徽章 | 524-534 | `w-5 h-5 rounded-full`，bg `--m2-soft`、色 `--m1-500`，Check 13；`mr-1` |
| 编辑按钮 | 536-546 | `p-1 rounded` hover 底色，Edit2 13 tertiary |
| 删除按钮 | 548-562 | **仅 `!agent.isBuiltin` 渲染**；Trash2 13，hover `text-rose-500`，点击 `confirm()` 后 `onDeleteAgent` |
| 卡片点击 | 489-492 | `onSelectAgent(agent.id)` + `onClose()` |

#### 表单层（L171-463，参考版是**嵌套的第二个弹窗** `z-[80]`）

| 字段 | 行号 | 参考版规格 |
|---|---|---|
| 头像 | 216-233 | `grid-cols-4` 的 `col-span-1`；label `头像`；input `w-full text-center chat-text-xl p-2 rounded-xl border`，`maxLength=4` |
| 顾问名称 * | 234-251 | 同行的 `col-span-3`；`input p-2.5 rounded-xl border`；placeholder `例如：世界观推演师、剧情评估师...`；`required` |
| 简介说明 | 254-270 | `input p-2.5 rounded-xl border`；placeholder `简述该顾问的专业特长与适用场景...` |
| 系统提示词 * | 272-292 | 标签行左 `系统提示词 (System Prompt) *` + 右 `InfoHint('决定回答风格与思考链路')`；`textarea rows=5` + `font-mono chat-text-xs leading-relaxed resize-y` |
| 发散度 + 标签 | 294-327 | `grid-cols-2 gap-3 items-center`；左 = `发散度: {temperature}`（`text-[11px]`）+ `<input type="range" min=0 max=1.5 step=0.05 accentColor=var(--m1-500)>`；右 = `标签 (逗号分隔)` + `input p-2 rounded-xl` |
| 记忆与权限分隔块 | 329-433 | `space-y-2 pt-1 border-t`；标题行左 `FileLock 12 + 记忆与文件权限`，右 `<select class="text-[11px] p-1.5 rounded-lg border">` 三选项：`不可读写文件` / `只读文件` / `可读写（可主动写入记忆）` |
| 长期记忆 | 351-362 | `textarea rows=4 p-2.5 rounded-xl` + `resize-y chat-text-xs leading-relaxed`；placeholder 说明「会作为系统提示词的一部分发送」 |
| 绑定 MCP | 364-398 | 标签行 `Server 12 + 绑定 MCP（创建窗口时默认启用）`；空态 `text-[10px]` `暂无可绑定的 MCP 服务`；否则 `flex flex-wrap gap-1.5` 的 chip：`px-2 py-1 rounded-lg border text-[11px]`，选中 = bg `--m2-soft` / 描边 `--m1-500` / 文字 `--m1-500`；文案 = `{name} · 可用｜未就绪`（`modelReady = status==='connected' && tools.length>0`，L374-375） |
| 绑定 Skill | 400-432 | 同款；图标 `Zap`；空态 `暂无可绑定的 Skill`；chip 文案只有 `{name}` |
| 底部操作区 | 435-460 | `flex justify-end gap-2 pt-2 border-t`；`取消`（`px-3 py-1.5 rounded-lg bg --surface-2`）+ `保存顾问`（`px-4 py-1.5 rounded-lg font-medium shadow-md bg --m1-600` 白字，`type=submit`） |

⚠️ 参考版底部操作区在**表单滚动流内**（不是固定底栏），因为整张卡是 `flex flex-col` + 表单 `overflow-y-auto`，
按钮排在表单最后一个子元素。原生按项目统一规格改用 `FeatureDialog(footer = …)` 的**固定底栏**
（`memory/ui-replication-requirements.md` §七：可提交编辑内容的弹窗必须有固定底部操作区）。

### 2.3 表单逻辑（L61-118）

| 项 | 行号 | 行为 |
|---|---|---|
| `startCreate` | 61-74 | 清空并置默认：头像 `🤖`、发散度 `0.7`、标签 `自定义`、文件权限 `none`、绑定清空 |
| `startEdit` | 76-89 | 用顾问现有值回填；`tags` 用 `', '` join；`?? ` 兜底（temperature `?? 0.7`、fileAccess `?? 'none'`） |
| 保存 | 91-118 | 名称空 → 直接返回；`tags` 按 `split(/[,，\s]+/)` 切、过滤空串，空则回退 `['自定义']`；新 id = `agent-custom-${Date.now()}`；`isBuiltin` 沿用原值；`memory.trim() \|\| undefined` |
| 保存后 | 116-117 | 关表单层、回列表层（**不关整个弹窗**） |

---

## 三、数据与状态（参考版 `App.tsx`）

| 项 | 行号 | 行为 |
|---|---|---|
| 选中顾问 | 1751-1756 | `handleSelectAgent(agentId)` → `updateCurrentSession(prev => ({...prev, agentId}))` |
| 保存顾问 | 1758-1768 | `setAgents`：命中 id 则替换，否则**前插**（`[agent, ...prev]`） |
| 删除顾问 | 1770-1772 | `setAgents(prev => prev.filter(a => a.id !== agentId))`，**不做引用清理** |
| 当前顾问来源 | `App.tsx` | `activeAgent = agents.find(a => a.id === activeSession.agentId)` |
| 可绑定清单 | 2325-2326 | `mcpServers` / `skills` 由 App 全局状态传入 |

---

## 四、Native 落点

| 关注点 | 文件 |
|---|---|
| 面板本体（列表 + 表单） | `shared/src/commonMain/kotlin/com/watchworld/ui/settings/SettingsPanels.kt` 的 `AgentPanel` / `AgentEditorDialog` / `AgentBindingChips` |
| 设置入口卡片 | `shared/src/commonMain/kotlin/com/watchworld/ui/settings/SettingsHub.kt`（`AGENT_CARD_TITLE`；**不在** `SettingsPage` 枚举里） |
| 弹窗外壳与两处接线 | `shared/src/commonMain/kotlin/com/watchworld/ui/App.kt` 的 `agentOpen` / `agentReturnsToSettings` |
| 数据模型 | `shared/src/commonMain/kotlin/com/watchworld/model/Agent.kt`（字段已与 `types.ts:43-64` 对齐） |
| 持久化 | `ui/settings/SettingsViewModel.kt` 的 `saveAgent` / `deleteAgent` |
| 当前窗口选择 | `ui/chat/ChatViewModel.kt` 的 `selectAgent(agentId)` → 写 `ChatSession.agentId` |

面板签名（两处入口共用同一个组件，与参考版「一个组件」一致）：

```kotlin
AgentPanel(
    items, onSave, onDelete,
    mcpServers = …, skills = …,
    activeAgentId = …,
    onSelectAgent = null,   // 本地状态编辑后可空；两处入口都传（选中即关弹窗）
)
```

---

## 五、视觉与规格约束

- 表单控件一律 `ui/components/WwFormControls.kt`：label 11sp 在框**上方**、框高约 40dp、正文 12sp、字段间距 12dp。
  **不用** `OutlinedTextField`（M3 强制 56dp / 16sp）。
- 分层底色：底板 `WwTheme.colors.surfaceElevated`；卡片 `WwSurfaces.card`、选中 `cardSelected`、凹槽 `inset`、高亮 `accentSoft`；描边 `cardBorder` / `cardBorderSubtle`。
  **不用** `MaterialTheme.colorScheme.surfaceVariant`（本主题里是纯中性灰 `--surface-3`）。
- 图标走 Tabler 子集：参考版 `Bot` → `UserCheck`、`FileLock` → `Lock`；`Check` / `Edit2` / `Trash2` / `Server` / `Zap` / `Plus` 有同名或语义等价项。
- 关闭返回遵循 `memory/ui-replication-requirements.md` §三：设置入口的顾问面板关闭回设置总入口。

### 与参考版的有意分歧

1. **表单层用 `FeatureDialog` 固定底栏**，而不是参考版的「嵌套 `z-[80]` 弹窗 + 按钮排在滚动流末尾」——
   项目 §七 的三段式统一规格。
2. **删除走 `AlertDialog` 确认**，而不是浏览器 `confirm()`（原生没有等价的原生 `confirm`，
   项目内删除一律用 `AlertDialog`，与 `RegexPanel` / `KnowledgePanel` / `McpPanel` 一致）。
3. **图标**：`Bot` → `UserCheck`（Tabler 子集无 `Bot`）；`FileLock` → `Lock`。

### 尚未按参考版改造的同级项

设置总入口里的其余卡片（默认与偏好 / 文本设置 / 主题 / 提示词库 / 备份与导入导出）
在参考版里同样是 `handle()` 打开的**独立弹窗**，Native 目前仍是 `SettingsHub.kt` 内的
内联子页（`SettingsPage` 枚举 + `page` 状态）。要逐项对齐就得按 Agent 这次的做法
各起一个顶层 `FeatureDialog`。
