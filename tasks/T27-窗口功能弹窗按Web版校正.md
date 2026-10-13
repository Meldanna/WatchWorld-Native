# T27 · 窗口功能弹窗按 Web 版校正

| | |
|---|---|
| 任务号 | T27 |
| 日期 | 2026-10-10 |
| 状态 | 🚧 进行中（① 前文总结、② 窗口 API 参数、③ 本窗口正则已完成；剩本窗口世界观 + 双框） |
| 阶段 | 阶段 7 · UI 精修 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T27 |

## 目标与可见效果

- **目标**：把输入栏齿轮弹出的「窗口功能」上拉面板及其**子弹窗**，逐个对照参考版
  （`E:\WatchWorld\WatchWorld\src\components\*`）校正：结构、尺寸、分层底色、文案、交互一致。
- **可见效果**：子弹窗打开后与参考版截图可对拍 —— 标题栏、卡片、输入框、按钮不再是
  M3 默认控件顶替的观感。
- **验收标准**：① 输入控件全部走 `WwFormControls`；② 分层底色全部走 `WwSurfaces`（无中性灰块）；
  ③ 固定底栏走 `FeatureDialog(footer=…)`；④ 关闭一律回「窗口功能」弹层。

> 与 [`tasks/sidebar-source-map.md`](sidebar-source-map.md) §三 的分工：那份文档写**面板本体**
> 与侧边栏；本文写**面板 7 个入口各自的子弹窗**（参考版组件逐个对照）。

## 入口与去向总表（参考版）

| 面板项 | 参考版组件 | 行数 | Native 文件 | 状态 |
|---|---|---|---|---|
| 前文总结 | `SummaryModal.tsx` | 356 | `ui/summary/SummaryPanel.kt` | ✅ T27-① |
| 全文搜索 | `SearchModal.tsx` | 173 | `ui/chat/SearchPanel.kt` | ⬜ 待校正 |
| 本窗口世界观 | `DocumentModal.tsx`（窗口形态） | 1067 | `ui/chat/WindowWorldPanel.kt` | ⬜ 待校正 |
| 角色心理分析 | **无弹窗**（触发即执行，`WindowFunctionPanel.tsx:154-171`） | — | `App.kt` → `ChatViewModel.analyzeRoleRelationships` | ⬜ 待校正 |
| 窗口 API 参数 | `WindowApiParamsModal.tsx` | 581 | `ui/chat/WindowApiParamsPanel.kt` | ✅ T27-② |
| 窗口导出删除 | `SessionTextModal.tsx` | 240 | `ui/chat/SessionActionsPanel.kt` | ⬜ 待校正 |
| 本窗口正则 | `RegexModal.tsx`（窗口形态） | 675 | `ui/settings/SettingsPanels.kt` 的 `RegexPanel` | ✅ T27-③ |

⚠️ 面板项以定案为准：**「双框提示词」已从面板移除**（用户 2026-10-10 定案，
见 `WindowFunctionSheet.kt` 的 KDoc）——它的入口只剩输入栏「提示词」按钮，
弹窗本体 `PromptDualBoxModal.tsx`（415 行）→ `ui/chat/DualBoxPromptDialog.kt` 仍待校正，
但**不占面板项**。

## 通用规格（所有子弹窗共用，已定案）

1. **外壳**：一律 `ui/settings/FeatureDialog.kt`。`maxWidth` 按参考版 `max-w-*` 换算
   （lg=512 · xl=576 · 2xl=672 · 3xl=768 · 4xl=896 dp），`heightFraction` 按 `max-h-[9xvh]`。
2. **三段式**：顶栏固定（图标徽章 + 标题 + 副标题 + ✕）· 中段滚动 · 底栏常驻。
   只有底栏时 `FeatureDialog` 是「内容自适应 + 封顶 + 中段 `weight(1f, fill=false)`」，
   等价参考版 `max-h-[90vh] flex flex-col` + 中段 `flex-1 overflow-y-auto`。
3. **输入控件**：一律 `ui/components/WwFormControls.kt`（`WwTextField` / `WwSelectField` /
   `WwPrimaryButton` / `WwSecondaryButton` / `Modifier.dashedRoundedBorder`），
   **不用** `OutlinedTextField`（M3 强制 56dp 高、16sp 正文，与参考版 40dp/12sp 差一截）。
4. **分层底色**：底板 `WwTheme.colors.surfaceElevated`；卡片 `WwSurfaces.card`；
   凹槽（参考版的 `--surface-1`）`WwSurfaces.inset`；描边 `WwSurfaces.cardBorderSubtle`。
   **不要**用 `colorScheme.surfaceVariant`（本主题里是纯中性灰 `--surface-3`）。
5. **错误色**：参考版写死 rose-500（`#f43f5e`）；本项目 error 系**未主题化、保留 M3 默认**，
   因此原生用 `MaterialTheme.colorScheme.error` 及其 `alpha` 变体。
6. **图标**：允许用语义一致的 Tabler 图标替代（`tabler-icons 1.1.1` 是**子集**，
   参考版的 lucide 图标不保证同名存在 —— 用之前先核 jar 里的名字，见「要点」）。
7. **关闭返回**：子弹窗关闭 → 回「窗口功能」弹层（`App.kt` 的 `windowChild = null`），
   见 `memory/ui-replication-requirements.md` §三。

---

## 一、WindowFunctionPanel — 上拉面板（`WindowFunctionPanel.tsx`，234 行）

**Native**：`ui/chat/WindowFunctionSheet.kt`（面板本体，T24/T27 已对齐三行卡片网格 + 遮罩 + 拖拽短横）。

| 参考版 | 行号 | 参考版 | Native |
|---|---|---|---|
| 开关按钮 | 44-58 | `px-2.5 py-1.5 rounded-full`，`bg=var(--m1-500)`（展开时）/ `--surface-2`，Settings 12 + 文案 + ChevronUp/Down 12 | 输入栏 `InputFeatureButton`（纯图标 34dp 点击区） |
| 遮罩 | 66-69 | `fixed inset-0 z-40 bg-black/50 backdrop-blur-sm` | 一致（`Color.Black.copy(alpha = .5f)`） |
| 上拉框 | 72-78 | `fixed inset-x-0 bottom-0 rounded-t-2xl border-t shadow-2xl p-4 max-h-[70vh]` | 一致（贴底 24dp 圆角、`p-4` 16dp） |
| 拖动条 | 80 | `w-10 h-1 rounded-full bg-slate-400/40` | 一致（40×4dp） |
| 卡片 | 82-225 | `flex flex-col items-center gap-1.5 p-3 rounded-xl bg(--surface-2)`，图标 20 主色 + `chat-text-xs font-medium` | 一致（`WwSurfaces.card`、22dp 图标） |
| 网格 | 81 | `grid grid-cols-3 gap-2` | 一致（每行 3 槽，短行用 `Spacer(weight)` 补位） |

**定案分歧（有意，勿按参考版回改）**：

- 「双框提示词」项已删（用户 2026-10-10），面板现 7 项；
- 角色心理分析**点完即收起面板**（参考版 L158-160 `onTriggerRoleAnalysis(); onToggle();`）——
  Native 已一致（`App.kt` 的 `onAnalyzeRole`）。

---

## 二、SummaryModal — 前文总结（`SummaryModal.tsx`，356 行）★本次

**Native**：`ui/summary/SummaryPanel.kt` + `App.kt` 的 `WindowFunctionDestination.Summary` 分支。

### 2.1 结构（自上而下，行号指向参考版）

| 段 | 行号 | 参考版内容与规格 |
|---|---|---|
| 容器 | 95-107 | 遮罩 `bg-black/70`；卡片 `max-w-2xl` · `max-h-[90vh] flex flex-col` · `rounded-2xl` · `p-4 sm:p-5` · `border` |
| 顶栏 | 109-140 | 32dp 徽章（`w-8 h-8 rounded-xl`，bg `--m2-soft`、border/color `--m1-500`，FileText 18）+ 标题 `前文总结`（`chat-text-sm font-semibold`）+ 副标题 `总结结果按顺序推入系统提示词 · 只处理未总结过的内容`（`text-[11px]`）+ ✕ |
| 滚动区 | 142 | `flex-1 overflow-y-auto py-3 space-y-3.5 chat-text-xs`（字段间距 14 → 原生 12dp） |
| ① 进度卡 | 144-171 | `rounded-xl border p-3`，bg `--surface-1`；13px 图标（有待总结 = Sparkles / 无 = AlertCircle，主色）+ **主行**：`将总结 #a – #b 共 N 条消息`（楼号 `font-mono`）/ 无待总结 `没有新的内容需要总结` + **次行**：`已总结至 #X，不会重复处理。` / `已总结至 #X（当前共 Y 楼）。`（tertiary） |
| ② 省钱行 | 174-185 | `flex items-center justify-between rounded-xl border px-3 py-2`，bg `--surface-1`；左 `已总结、不再发送给 AI 的原文`（tertiary）；右 `N 条`（`font-mono font-medium`，**>0 用主色**、否则 tertiary） |
| ③ 总结提示词 | 188-211 | 标签行：`总结提示词` + `InfoHint('留空即用内置默认：只提炼已确认事实与当前状态，不复述原文。')` + 右侧 `恢复默认`（`text-[10px] hover:underline`，tertiary）；textarea `rows=3` + `p-2.5 rounded-xl border` + **`font-mono text-[11px]`** + `resize-y`，placeholder `留空则使用内置默认提示词（只记录已确认事实、保留因果、记录人物状态变化…）` |
| ④ 模型选择 | 213-239 | 标签 `总结使用的模型` + `InfoHint('可用模型来自全局 Provider 配置；总结不写入聊天记录。')`；`<select>` `p-2.5 pr-8 rounded-xl appearance-none`：**首项** `跟随本窗口当前模型（{currentModel \|\| '未指定'}）`（`value=""`），其后逐个 `models`；触发器右侧 ChevronDown 14（tertiary） |
| ⑤ 已推入的总结 | 241-306 | 标签行 `已推入的总结（N）` + 非空时右侧 `清空全部`（`text-[10px]`，**rose**）；**空态** = `rounded-xl border p-3` 的说明段（`text-[11px]` tertiary）：`暂无总结。点击下方「总结前文」后会按顺序出现在这里，并自动作为系统提示词发送。`；**非空** = 卡片 `rounded-xl border p-2.5`，首行 `#{idx+1} · 覆盖 #{fromFloor}–#{toFloor}{ · model}`（**`font-mono text-[10px]`**，主色）+ 右侧 Trash2 12 删除按钮（tertiary），正文 `text-[11px] whitespace-pre-wrap leading-relaxed`，且 **`max-h-32 overflow-y-auto`**（单条限高 128px、内部滚） |
| 错误条 | 309-321 | `总结失败：{error}`；`mt-3 rounded-xl border px-3 py-2 text-xs whitespace-pre-wrap`；bg `rgba(244,63,94,.08)`、border `rgba(244,63,94,.35)`、文字 `#f43f5e`。**位置在滚动区之外、底栏之上** |
| 底栏 | 323-352 | `pt-3 border-t`，`flex items-center justify-between`；左：Check 12（主色）+ `总结以系统提示词身份发送，不占用聊天消息`（`text-[11px]` tertiary）；右：`关闭`（`px-3 py-1.5 rounded-lg bg(--surface-2)`）+ `总结前文`（`px-4 py-1.5 rounded-lg shadow-md bg(--m1-600)` 白字，Sparkles 13；总结中换 Loader2 旋转 + `总结中...`；`disabled = isSummarizing \|\| pendingCount === 0` 且 `disabled:opacity-40`） |

### 2.2 数据与行为（行号指向参考版）

| 项 | 行号 | 行为 |
|---|---|---|
| 表单初值 | 56-61 | 弹窗打开时 `prompt = session.summaryPrompt ?? ''`、`model = session.summaryModel ?? ''` |
| 统计 | 63-74 | `maxFloor` = 最大楼号；`done = lastSummarizedFloor ?? 0`；`from = done + 1`；`pendingCount` = 楼号 ≥ from 的条数；`hiddenCount` = 楼号 ≤ done 的条数 |
| 生成 | 88-92 | **保存配置与生成一起做**：先 `onSaveConfig({summaryPrompt: trim \|\| undefined, summaryModel: model \|\| undefined})`，再 `onGenerate({prompt: trim \|\| DEFAULT_SUMMARY_PROMPT, model \|\| undefined})` |
| 删除 | 286-294 | 直接 `onDeleteSummary(s.id)`，**没有 confirm** |
| 清空 | 247-256 | 直接 `onClearSummaries()`，**没有 confirm** |
| 模型来源 | `App.tsx:2616-2617` | `models = activeProvider?.models \|\| []`（**当前 Provider**，不是全部 Provider）；`currentModel = session.model \|\| provider.defaultModel \|\| ''` |
| 默认提示词兜底 | — | 面板传 `undefined` 即可：原生 `ChatViewModel.generateSummary` 内部已有 `prompt?.takeIf{…} ?: DEFAULT_SUMMARY_PROMPT` |

### 2.3 ⚠️ 用户提供的「代码复刻报告」与源码不一致处（**以源码为准**）

| 报告写法 | 源码实际 |
|---|---|
| 顶栏有 `dl grid-cols-3` 的「总楼数 / 待总结楼层 / 已总结楼层」三列统计（报告 108-148） | **不存在**。顶栏只有徽章 + 标题 + 副标题 + ✕（109-140）；统计在进度卡（144-171）与省钱行（174-185） |
| textarea `min-h-[80px]` | 实际是 `rows={3}`，无 `min-h` |
| 模型下拉 `px-2.5 py-2` | 实际 `p-2.5` |
| 删/清空带 `confirm(...)` | 源码**没有** confirm（286-294 / 247-256） |
| 段落行号（总结列表 223-267、错误 238-321、底部 323-352） | 实际为 241-306 / 309-321 / 323-352 |
| WindowFunctionPanel 摘录把 100-152 标成「窗口世界观」、漏掉「双框提示词」 | 实际 100-116 = 双框提示词、136-152 = 本窗口世界观 |
| 未提及 | 进度卡图标（Sparkles/AlertCircle）、省钱行卡片、`恢复默认`、`清空全部`、Trash2 图标删除、错误条配色、底栏 Check 提示与 `关闭` 按钮、`models`/`currentModel` 两个 props、**`SummaryModal` 里没有角色关系分析** |

### 2.4 本次落地（Native）

| 文件 | 改动 |
|---|---|
| `ui/summary/SummaryPanel.kt` | **重写**：外壳移入本文件（`FeatureDialog` + `footer`）；正文按 §2.1 五段重排；输入控件换 `WwTextField`(等宽 11sp)/`WwSelectField`（模型下拉，替代原纯文本框）；单条总结加 `#{n}` 序号、Trash2 图标删除、正文限高 128dp 内滚；错误条移入底栏上方并改主题化 error 底色；**删掉面板底部的「角色关系分析」区**（参考版无） |
| `ui/App.kt` | `Summary` 分支：改为只调 `SummaryPanel(..., onDismiss, models, currentModel)`；模型清单取「当前会话的 Provider」（`session.providerId` 优先，否则第一个，同 `ChatViewModel.resolveProvider`），`currentModel = session.model \|\| provider.defaultModel` |
| `ui/chat/WindowFunctionSheet.kt` | 上一轮已删「双框提示词」项（本任务顺带） |

**图标替代**（参考版 lucide → Tabler 子集里实际存在的）：

| 位置 | 参考版 | Native | 理由 |
|---|---|---|---|
| 进度卡·有待总结 | Sparkles | `ListCheck` | 子集无 Sparkles；「有待整理清单」语义最近 |
| 生成按钮 | Sparkles 13 | `Feather` | 同上；「生成/撰写」语义 |
| 生成按钮·进行中 | Loader2（旋转） | `Loader` + 无限旋转 | 子集只有 `Loader` |
| 删除单条 | Trash2 12 | `Trash` 12 | 同名可用 |
| 进度卡·无待总结 | AlertCircle | `AlertCircle` | 同名可用 |
| 底部提示 | Check 12 | `Check` 12 | 同名可用 |

---

## 三、WindowApiParamsModal — 窗口 API 参数（`WindowApiParamsModal.tsx`，581 行）★本次

**Native**：`ui/chat/WindowApiParamsPanel.kt`（旧版 451 行）+ `App.kt` 的 `WindowFunctionDestination.WindowApi` 分支。

### 3.1 结构（自上而下，行号指向参考版）

| 段 | 行号 | 参考版内容与规格 |
|---|---|---|
| 容器 | 132-147 | 遮罩 `bg-black/70`；卡片 `max-w-lg`(512dp) · `max-h-[90vh] overflow-y-auto` · `rounded-2xl` · `p-4 sm:p-5`；**根 font-size = `preferences.chatFontSize`**（`App.tsx:2599`） |
| 顶栏 | 148-180 | 32dp 徽章（Sliders 18）+ 标题 `窗口 API 参数`（`chat-text-sm`）+ 副标题 `仅影响当前窗口，不修改全局 API 配置`（`text-[11px]`）+ ✕ |
| 当前生效条 | 182-193 | `mt-3` + `rounded-xl border px-2.5 py-2`，bg `--surface-1`；Info 12（tertiary）+ `当前生效`（`text-[11px]` tertiary）+ `{providerName \|\| '未选择服务商'}{modelName ? ' · '+modelName : ' · 未选择模型'}`（主色、`truncate`）——**一整行**，不是两列 label/value |
| 六个采样块 | 195-428 | 每块 = **标签行**（`flex items-center justify-between mb-1.5`：左 = 13px 主色图标 + 标签 + InfoHint；右 = `font-mono` 值 + 条件出现的 10px 药丸）+ `<input type="range" className="w-full" accentColor=var(--m1-500)>`；**块与块之间没有额外间距** |
| 最大 Token 的输入行 | 407-427 | 滑杆下方 `mt-1.5`，`flex items-center gap-2`：数字输入（`flex-1 p-2 rounded-xl`）+ `tokens`（`text-[11px]`） |
| 连续工具轮数 | 430-467 | 整块 `py-3`；**无图标、无 InfoHint**；标签 `chat-text-base` + 有值时右侧「跟随全局」药丸；输入 `p-2 rounded-xl` + **`chat-text-base font-mono`**，placeholder `留空 = 跟随全局（X）`；非法时 10px rose 报错；常驻 11px 帮助行（`mt-1.5`，**全文**含「一个响应内多个工具算一步；完成 N 轮立即结束，不再请求模型。」） |
| 流式输出 | 469-502 | 整块 `py-3 border-t`；Radio 13 + 标签 + InfoHint；两个按钮 `flex-1 px-3 py-2 rounded-xl text-[11px]`：选中 `bg --m2-soft` / 文字与边框 `--m1-500`，未选 `bg --surface-2` / `--text-secondary` / `--border-default`。**默认（undefined）按「流式」高亮，没有「跟随默认」清除位** |
| 图片是否留在上下文 | 504-543 | ImagePlus 13 + 标签 + InfoHint；两个按钮同上；选中判定 = `session.imageContextMode \|\| 'once'`（缺省即「只发一轮」），同样**无清除位** |
| 底栏 | 545-577 | `pt-3 border-t`，`justify-between`：左「全部跟随默认」（RotateCcw 12 + 文案，`px-3 py-1.5 rounded-lg bg --surface-2`）；右「取消」（同款）+「保存」（`px-4 py-1.5 rounded-lg bg-m1-600` 白字，非法时 `disabled:opacity-50`）。⚠️ 底栏**在卡片的滚动流里**（整卡 `overflow-y-auto`），不是固定底栏 |

**六个采样块的差异表**（滑杆「未覆盖时」的位置 = 参考版 `value ?? fallback`）：

| 字段 | 参考版图标 | 范围 / step | 未覆盖时滑杆位置 | 值文案 | 药丸 |
|---|---|---|---|---|---|
| 发散度 (Temperature) | Thermometer | 0–1.5 / 0.05 | `inheritedTemperature` | `X.XX` 或 `X.XX (跟随默认)` | 跟随默认 |
| 核采样 (Top P) | Layers | 0–1 / 0.05 | `inheritedTopP` | 同上（换 inheritedTopP） | 跟随默认 |
| 候选词上限 (Top K) | Filter | 1–200 / 1 | **40** | `K` 或 `未设置` | 清除 |
| 重复惩罚 (Frequency) | Repeat | -2–2 / 0.1 | **0** | `X.XX` 或 `未设置` | 清除 |
| 话题惩罚 (Presence) | TrendingUp | -2–2 / 0.1 | **0** | 同上 | 清除 |
| 最大输出 Token | Hash | 0–20000 / 256 | **0** | **没有值文案** | 清空 |

### 3.2 数据与行为（行号指向参考版）

| 项 | 行号 | 行为 |
|---|---|---|
| 打开时同步 | 64-89 | 每次打开都用会话字段重置本地编辑态（避免上次编辑残留）；`toolCallStepLimit` → 字符串（`undefined` → `''`）；`streamingEnabled` 只有**布尔**才算覆盖 |
| 工具轮数校验 | 95-98 | 空串合法；否则必须 `/^\d+$/` + `Number.isSafeInteger` + `>= 0` |
| 保存 | 101-115 | 空串 → `undefined`；保存后关闭弹窗 |
| 全部跟随默认 | 117-126 | 清空**除 `imageContextMode` 之外**的所有字段（参考版如此；因「缺省 = once」与显式值行为一致，观感无差别） |
| Provider / 模型来源 | `App.tsx:2597-2598` | `modelName = session.model \|\| provider.defaultModel \|\| ''`；`providerName = provider.name \|\| ''`（**activeProvider**） |
| 继承值来源 | `App.tsx:2594-2596` | `preferences.defaultSession.temperature ?? activeAgent?.temperature ?? 0.7`；topP `?? 0.95`；toolCallStepLimit `?? 0` |

### 3.3 本次落地（Native）

| 文件 | 改动 |
|---|---|
| `ui/chat/WindowApiParamsPanel.kt` | **重写排版**：删掉自绘的副标题（副标题归顶栏）与「Provider / 模型」两列块 → 换成参考版的一行「当前生效」条；M3 `Slider` → 自绘 **24dp 紧凑滑杆**（4dp 轨道 + 12dp 圆点，按下即取值/拖动跟随）；药丸按钮（跟随默认/清除/清空/跟随全局）改成 10px + 4dp 圆角 + 凹槽底；`OutlinedButton` 分段按钮 → 参考版的选中态药丸（选中 `accentSoft` 底 + 主色字/描边）；底栏三个按钮换成参考版规格（`WwSecondaryButton`/`WwPrimaryButton` + 带图标的「全部跟随默认」自绘）；修正 Top K 的图标（原用 `Hash`，参考版是 `Filter`）；最大 Token 的精确输入去掉多出来的 label 与 260dp 宽度上限；连续工具轮数去掉图标与 InfoHint、帮助行补全整句；流式/图片分段去掉参考版没有的「跟随默认」清除位，图片缺省按「一次」高亮；删掉「当前生效」下方那根多出来的分隔线 |
| `ui/App.kt` | `WindowApi` 分支的 `FeatureDialog` 补 `icon = TablerIcons.Adjustments` + `subtitle = "仅影响当前窗口，不修改全局 API 配置"`（参考版顶栏本来就有徽章与副标题）；**修正继承值回退链** —— 原写成「顾问 → 内置兜底」，漏了全局默认；参考版 `App.tsx:2594-2596` 与实际生效的 `ChatRequestAssembler.kt:72` / `api.ts:393` 都是「**全局默认 → 顾问 → 内置兜底**」 |

**图标替代**（参考版 lucide → Tabler 子集里实际存在的）：

| 位置 | 参考版 | Native | 理由 |
|---|---|---|---|
| 顶栏徽章 | Sliders | `Adjustments` | 子集无 Sliders；同名语义最近的「滑杆」图标 |
| 发散度 | Thermometer | `Temperature` | 同名可用 |
| 核采样 | Layers | `Stack` | 子集无 Layers；沿用原实现 |
| 候选词上限 | Filter | `Filter` | **本次修正**（原误用 `Hash`） |
| 最大输出 Token | Hash | `Hash` | 同名可用 |
| 流式输出 | Radio | `AntennaBars5` | 子集无 Radio |
| 图片上下文 | ImagePlus | `Photo` | 子集无 ImagePlus |
| 全部跟随默认 | RotateCcw | `RotateClockwise` | 子集无 RotateCcw |
| 当前生效条 | Info | `InfoCircle` | 同名可用（项目内通用） |

---

## 四、RegexModal — 本窗口正则 / 正则库（`RegexModal.tsx`，675 行）★本次

**参考**：同一个组件、`App.tsx` 挂**两个实例** ——
窗口形态 `App.tsx:2356-2369`（多传 `sessionRuleIds` / `onToggleForSession` / `onSetSessionRules`）；
库形态 `App.tsx:2372-2381`（不传这三个 prop）。

**Native**：`ui/settings/SettingsPanels.kt` 的 `RegexPanel`（两形态共用）；
旧的 `ui/chat/WindowRegexPanel.kt`（窗口精简版）**本次删除**。

### 4.1 形态差异（行号指向参考版）

| 差异点 | 窗口形态 | 库形态 |
|---|---|---|
| 判定 | `sessionRuleIds && onToggleForSession` 都传了（L71-73） | 不传这两个 |
| 标题 | `本窗口正则` + `Regex` 徽章（L272-276） | `正则库` + 同款徽章 |
| 副标题 | `开头的开关即「本窗口是否生效」；分组标题上的开关作用于整组`（L279） | `维护全部正则规则，可按分组归档；具体窗口在「窗口功能 → 本窗口正则」里启用`（L280） |
| 工具条左侧 | `开头的开关即本窗口是否生效`（L479） | `共 N 条规则`（L479） |
| 工具条按钮 | **多两个**：`本窗口全选` / `全不选`（L482-501） | 只有 `全部折叠` + `新建规则` |
| 一行开头开关代表 | 是否**在本窗口生效**（L221） | 全局 `enabled`（L221） |
| 全局被禁用的行 | 开关按 50% 透明画（L222、L234）——仍可加入本窗口 | 无此态 |
| 整组开关 | 整组加/移出本窗口（L197-204） | 逐条对齐到目标状态（L206-209） |

**两种形态共有**（⚠️ 窗口形态**不是**精简版）：新建 / 编辑 / 删除（两个实例都传了
`onSaveRule`/`onDeleteRule`）、分组折叠 + 组内新建、作用域徽标、`pattern ➔ replacement` 预览条、
表单内的实时沙箱测试、底部 `正则规则说明` + InfoHint（L668-671）。

### 4.2 本次落地（Native）

| 文件 | 改动 |
|---|---|
| `ui/settings/SettingsPanels.kt`（`RegexPanel`） | 新增三个可选 session 参数 + `windowScope` 判定与 `isOn(rule)`；行首与整组开关换成参考版的 `ToggleLeft/Right` 方形开关（新私有 `RegexToggleButton`，含「全局已禁用 → 50% 透明」）；工具条在窗口形态加 `本窗口全选 / 全不选`、左侧文案随形态切换，`全部折叠 / 新建规则` 改用 `WwSecondaryButton(leadingIcon = …)`；底部补 `正则规则说明` + InfoHint |
| `ui/chat/WindowRegexPanel.kt` | **删除** —— 窗口形态不再另起一套（参考版就是同一个组件） |
| `ui/App.kt` | `WindowRegex` 分支改用 `RegexPanel` + session 三件套（`sessionRuleIds` 未配置时回退「全部已启用」，同参考版 `App.tsx:2362-2365`）；顶栏补副标题与图标；正则库分支的注释同步 |
| `ui/components/WwFormControls.kt` | `WwSecondaryButton` 新增可选 `leadingIcon`（参考版一批次按钮带图标；本次 3 处调用点） |
| `ui/chat/WindowApiParamsPanel.kt` | 顺带：自绘的 `SecondaryIconButton` 删掉，改用带图标的 `WwSecondaryButton` |

**仍与参考版有差（已记录、未做）**：

- 单条规则参考版是**一张卡**（`p-3 rounded-xl border flex flex-col gap-2`，全局禁用时**整卡** `opacity-60`，
  预览条嵌在卡内的独立底色块里）；原生仍是扁平行。改动会同时影响侧边栏「正则库」，留给用户定。
- `Regex` 标题徽章（`.*` mono 小牌）与 `全部折叠` 的 `disabled` 态：原生外壳 `FeatureDialog` 没有徽章位；
  `WwSecondaryButton` 也没有 `enabled` 参数（无分组时点击是无害的空操作）。
- 沙箱「运行测试」按钮参考版带 `Play` 图标 —— Tabler 子集里没有 `Play`。

---

## 五、后续待校正（同一任务剩 1 项 + 双框）

按 §「入口与去向总表」逐个做，每个都照 §通用规格 + 参考版行号补一节 §六/§七/…，
再落 Native。已知先验（后续开工时先复核）：

- `DocumentModal.tsx`（1067 行）的**窗口形态**：参考版同样是**一个组件两种形态**
  （`App.tsx:2558-2571` 窗口形态多传 `sessionDocIds`/`onToggleForSession`/`onSetSessionDocs`；
  `:2574-2586` 库形态不传）。原生现在是 `world/WorldPanel.kt`（库，870 行、已用 `WwSurfaces`
  + `WwFormControls` 精修）+ `chat/WindowWorldPanel.kt`（窗口精简版，138 行，只有全选/全不选
  + `▶/▼` 文字折叠 + `Switch`）——**与正则同一个病**，做法也应一致：给 `WorldPanel` 加
  session 三件套、删掉 `WindowWorldPanel`。开工前还需核对参考版窗口形态的
  开关位置、分组整组开关、工具条与搜索框是否随形态变化。
- `SessionTextModal.tsx`（240 行）：窗口导出/删除，含危险操作确认文案；
- `SearchModal.tsx`（173 行）：结果卡片两行堆叠 + 右侧「跳转 →」；
- 双框提示词弹窗 `PromptDualBoxModal.tsx`（415 行）→ `ui/chat/DualBoxPromptDialog.kt`
  （入口在输入栏「提示词」按钮，不占面板项）。

## 心得

- **先核 jar 再写图标**：`tabler-icons` 是**子集**（本次 1264 个图标），参考版常用的
  `Sparkles` / `Loader2` 都不在里面。凭印象写 `TablerIcons.Sparkles` 会直接编译失败，
  而本任务期间「不构建」（另有构建在跑），所以改成先 `unzip -l` 核名字再落笔。
- **报告的「行号」不可信**：用户给的复刻报告结构对、细节错（见 §2.3），
  包括「顶栏三列统计」这种整段不存在的块。规格一律回源码逐行核，报告只当线索。
- **「排版问题」的病根是 M3 控件的固有高度**：窗口 API 参数这个弹窗功能早已齐全，
  难看的是 `Slider`（自带 48dp 最小交互高度）+ `TextButton`/`OutlinedButton`（最小高度与内边距都比
  参考版大一截）。六个滑杆各多 28dp，整卡就多出 170dp —— 「排版不对」多半是这一条，
  而不是某个间距写错。结论：**凡是参考版用原生 `input`/`button` 的地方，都别拿 M3 控件顶替。**
- **先量再改**：改之前把参考版每一段的类名逐个换算成 dp（`mb-1.5`=6dp、`px-2.5 py-2`=10×8dp、
  `rounded`=4dp、`rounded-lg`=10dp），改完能对着表复核；凭印象「差不多」正是排版反复返工的原因。
- **⚠️ 不编译就一定会漏错**：本任务前两个弹窗靠「代码走查」交付，结果第三个弹窗一编译就报
  3 个错（缺 `Modifier.border` 的 import、尾随 lambda 绑错参数、给已收窄的非空接收者多写了 `?.`）。
  这三类**都不是逻辑错，是编译期才看得见的**——`textarea`/IDE 都不跑 Kotlin 分析时，
  「我看了一遍」不能替代 `compileKotlinDesktop` 那 20 秒。
- **`RegexToggleButton` 的参数顺序有讲究**：`dimmed: Boolean = false` 必须排在 `onClick` **前面**，
  否则调用点的**尾随 lambda 会绑到最后一个参数**（`dimmed`）上，报「expected Boolean」。

## 要点

- **`SummaryPanel` 现在自带 `FeatureDialog` 外壳**（不再由 `App.kt` 包壳）：底栏里的
  「总结前文」需要 `prompt`/`model` 两个本地状态，状态留在面板内、外壳跟着进来才不用把状态提到 `App.kt`。
- **模型下拉的值约定**：`session.summaryModel` 为空串 = 「跟随本窗口当前模型」；
  `WwSelectField` 的 `current` 传的是**显示文案**（`跟随本窗口当前模型（X）`），
  选中后要把这个文案映射回 `""`，别把它当模型名存进去。
- **模型清单来源**：`activeProvider.models`（`session.providerId` 优先、否则第一个 Provider），
  与 `ChatViewModel.resolveProvider` 同一套回退；`currentModel = session.model || provider.defaultModel`。
- **参考版 `SummaryModal` 没有角色关系分析**：分析由「窗口功能 → 角色心理分析」触发（点完即收面板）。
  面板底部旧的那个按钮是 Native 特有，已按参考版删除 —— 别再照旧版加回来。
- **错误条位置**：参考版在**滚动区之外**（不随内容滚走），原生靠 `FeatureDialog(footer=…)` 的第一项实现。
- **单条总结限高 128dp 内滚**（参考版 `max-h-32 overflow-y-auto`），是**局部**滚动，
  不违反「面板内不要再套整页滚动」那条（那条针对的是包住整个面板的滚动容器）。
- **滑杆是自绘的**：`WindowApiParamsPanel` 里的 `CompactRangeSlider`（24dp 高、4dp 轨道、6dp 半径圆点、
  按下即取值 + 拖动跟随、`snapToStep` 吸附）**不依赖 M3 `Slider`**。参考版用的是
  `<input type="range">`，全项目目前只有这一处这么做 —— 别的面板若也要这种细滑杆，
  再把它提到 `WwFormControls`，别复制第二份。
- **`pointerInput` 里读不到 `size` 的坑**：`awaitEachGesture { }` 的接收者是 `AwaitPointerEventScope`，
  **没有** `size`；要在 `pointerInput { }` 那层（`PointerInputScope`）先取。写成本地函数在每次手势里现读，
  窗口缩放后才不会用到旧宽度。
- **未覆盖时滑杆的停位是写死的**（参考版 `value ?? fallback`）：温度/Top P 停在**继承值**，
  Top K 停在 **40**，两个惩罚与最大 Token 停在 **0** —— 别改成「停在 range.start」。
- **窗口 API 参数的两个分段控件没有「跟随默认」清除位**（参考版没有）：
  流式输出默认按「流式」高亮（`streaming != false`），图片上下文缺省按「只发一轮」
  （`imageMode ?: ONCE`）。「全部跟随默认」也**不重置** `imageContextMode`（见 §3.2）。
- **⚠️ 采样参数的继承链是「窗口 → 全局默认 → 顾问 → 内置兜底」**（`ChatRequestAssembler.kt:72`、
  参考版 `api.ts:393`）。`WindowApiParamsModal` 的 props 注释写成「顾问值 → 全局默认」是**注释过期**，
  以调用点 `App.tsx:2594-2596` 为准。原生原先把顾问放在了全局默认前面 —— 那会让「(跟随默认 X.XX)」
  显示一个并不会被用到的数，本次已改回正确顺序。
- **「同一份数据、两种形态」是参考版的既有设计**：`RegexModal` / `DocumentModal` 在 `App.tsx`
  各挂**两个实例**，靠「传不传 session props（`sessionRuleIds` / `sessionDocIds` 那一组）」区分
  窗口形态与库形态。原生一度把两种形态拆成两个组件（`WindowRegexPanel` / `WindowWorldPanel`），
  结果窗口那个被削成「只能拨开关」。**遇到窗口级弹窗，先翻参考版是不是同一个组件**。
- **窗口形态的开关语义与全局不是一回事**：窗口形态下「全局已禁用」的规则仍可加入本窗口
  （参考版把它画成 50% 透明而不是禁用），`ChatViewModel.toggleWindowRegexRule` 也按
  「未配置 = 全部已启用」再落成显式列表 —— 别把两套 `enabled` 混成一个。
- **⚠️ 字号仍与参考版不同（全项目级缺口）**：参考版整卡的正文基准是 `preferences.chatFontSize`
  （`chat-text-*` 是 `calc(--chat-font-size * 系数)`），原生全部弹窗都还在用固定字号
  （本卡：标签 14sp、小字 10/11sp、输入 12sp）。要接就得**所有弹窗一起接**，别只改这一处。

## 验收结果

- [x] `SummaryPanel` 重写：结构/文案/尺寸对齐 §2.1，输入控件走 `WwFormControls`，底色走 `WwSurfaces`。
- [x] `App.kt` 接线：`models` / `currentModel` / `onDismiss`。
- [x] 删掉面板底部的角色关系分析区（参考版无）。
- [x] `WindowApiParamsPanel` 重排版：对齐 §3.1（当前生效条、六个采样块的紧凑滑杆与药丸、分段按钮、
  底栏规格），修正 Top K 图标，`App.kt` 补徽章与副标题与继承值回退链。
- [x] 正则合并为**一个组件两种形态**（§四）：`RegexPanel` 加 session 三件套 + 参考版方形开关
  + 窗口专属工具条 + 底部说明；删 `WindowRegexPanel.kt`；`App.kt` 两处入口同时接线。
- [x] 规格文档补入本文件（带行号）。
- [x] 编译与测试（2026-10-10 补跑）：`:shared:compileKotlinDesktop` + `compileTestKotlinDesktop` 干净
  （只剩 `SessionActionsPanel.kt:49` 的**既有** deprecation 警告，见「遗留」）；
  `:desktopApp:build :shared:desktopTest` 全绿 = **361 用例 / 0 失败 / 0 错误 / 5 跳过**。
- [ ] 截图走查：仍缺离屏截图（前文总结 / 窗口 API 参数）。

## 遗留问题 / 下一步

- **未验证**：本轮零构建，三个弹窗的新代码都没有编译过。首次构建时要重点看
  图标名（`ListCheck` / `Feather` / `Loader` / `Trash` / `Adjustments` / `ToggleLeft` / `ToggleRight`）、
  `WwSelectField`、`WwSecondaryButton(leadingIcon = …)`、`CompactRangeSlider` 的手势/Canvas 调用。
- **`ChatUiState.analyzing` 可能变孤儿**：面板不再消费它；先保留（`ChatViewModel` 与测试仍用），
  若后续确认无人读再清理。
- **`CompactRangeSlider` 是私有实现**：等别的面板也要细滑杆时再提到 `WwFormControls`。
- **字号缩放（`chat-text-*`）全项目未接**：要接得所有弹窗一起接，见「要点」。
- **正则的单条规则仍是扁平行**，参考版是卡片（见 §4.2 末尾）——影响侧边栏「正则库」，待定。
- **既有警告（不是本次引入）**：`ui/chat/SessionActionsPanel.kt:49` 用了已废弃的
  `LocalClipboardManager`（应换 `LocalClipboard` + suspend 版复制）。这正是「窗口导出删除」
  那个待办弹窗的文件，做它时顺手清掉。
- 本窗口世界观（`DocumentModal` 窗口形态）+ 双框提示词弹窗逐个校正（见 §五）。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §5：UI 只拿 Repository / ViewModel —— 本次只改 UI 层，未新增状态源。
- `MIGRATION_CONSTRAINTS.md` §6：字段名不改 —— `summaryPrompt` / `summaryModel` / `summaries` /
  `lastSummarizedFloor` 原样使用。
