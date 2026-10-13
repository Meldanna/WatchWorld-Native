# 设置弹窗源代码地图（四项二级入口）

> 参考版：`E:\WatchWorld\WatchWorld\src\components\` 四个 Modal
> Native：`shared/src/commonMain/kotlin/com/watchworld/ui/settings/`
> 采集时间：2026-10-11。**行号会漂，以符号名为准。**

本文件覆盖设置总入口剩下的四张卡（Agent 与备份见
[`agent-source-map.md`](agent-source-map.md) / [`backup-source-map.md`](backup-source-map.md)）。

---

## 〇、共同结构（四个弹窗一致）

参考版 `SettingsHubModal.tsx:43-47` 的 `handle()`：

```tsx
const handle = (fn: () => void) => {
  onClose();                 // 先关掉设置总入口
  window.setTimeout(fn, 0);  // 再开目标弹窗（注释：避免多层级弹窗叠压）
};
```

即**设置总入口在目标弹窗打开时并不在场**；关闭后由 `closeWithReturn`
（`App.tsx:280-287`）重新打开设置总入口。

Native 对应：`App.kt` 的 `settingsChild: SettingsDestination?` +
`settingsChildReturnsToSettings: Boolean`（一对状态，不是「每张卡一对布尔」——
理由见 `memory/ui-replication-requirements.md` §三）。

| 卡 | 参考版组件 | 行数 | Native 弹窗文件 |
|---|---|---|---|
| Agent 管理 | `AgentModal.tsx` | 572 | `SettingsPanels.kt` 的 `AgentPanel`（App.kt 包壳） |
| 默认与偏好 | `PreferencesModal.tsx` | 687 | `PreferencesPanels.kt` 的 `AppPreferencesPanel`（App.kt 包壳） |
| 文本设置 | `SettingsModal.tsx` | 261 | `PreferencesPanels.kt` 的 `DisplaySettingsPanel`（App.kt 包壳） |
| 主题 | `ThemeModal.tsx` | 317 | `ThemeDialog.kt`（自带外壳） |
| 提示词库 | `PromptModal.tsx` | 585 | `PromptLibraryDialog.kt`（自带外壳） |
| 备份与导入导出 | `BackupSyncModal.tsx` | 349 | `BackupSyncDialog.kt`（自带外壳） |

---

## 一、PreferencesModal — 默认与偏好（687 行）

**Native**：`ui/settings/PreferencesPanels.kt` 的 `AppPreferencesPanel`
（`App.kt` 用 `FeatureDialog(title = "偏好 · 默认模型 · 全局 API 参数", maxWidth = 672.dp)` 包壳）。

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 顶栏 | 336-358 | 32dp 徽章（`accentBadge`，Sliders 18）+ `偏好 · 默认模型 · 全局 API 参数`（`chat-text-sm font-semibold`）+ 副标题 `控制默认模型、全局 API 参数、新窗口默认项与启动行为`（`text-[11px]`）+ ✕ |
| 滚动区 | 366 | `flex-1 overflow-y-auto py-3 space-y-5 chat-text-xs` |
| ① 默认模型 | 368-380 | 标题行 `Cpu 14 主色 + 默认模型（留空则回退到窗口/Provider 默认）`；`grid-cols-1 sm:grid-cols-2 gap-3` 四个模型选择器（聊天主模型 / 会话标题生成 / 前文总结 / 角色关系分析） |
| ② 全局 API 参数 | 382-465 | 标题行 `Sliders 14 + 全局 API 参数 + InfoHint('窗口未单独设定时跟随此处偏好；采样参数留空表示不设置全局覆盖。')`；六个采样块（温度/Top P/Top K/重复/话题/最大 Token），每块 = 标签行（图标 + 名称 + InfoHint + 右侧 `font-mono` 值或 `未设置` + 条件出现的「清除」药丸）+ `<input type=range>`；最大 Token 下方多一个「精确 Token 数」输入；末尾「连续工具轮数」输入 + InfoHint |
| ③ 默认新窗口配置 | 468-553 | `Rocket 14` 标题；两列网格：顾问 / Provider / 默认模型 / AI 视野 / AI 最新条数 N / 屏幕渲染条数（带 InfoHint）；下方 `默认开启时间线记忆整理` 复选框 |
| ④ 新建窗口默认开关 | 555-622 | `Globe 14 sky-500` 标题 + InfoHint（原文「只决定「新建窗口」时的初始状态…」）；两组二选一按钮（联网 / 流式输出），选中 = `--m2-soft` 底 + 主色字 + 主色描边 |
| ⑤ API 中转服务器 | 624-645 | `Server 14 sky-500` 标题 + InfoHint（Android WebView 限制）；URL 输入 + 一行「当前生效 / 未配置」说明 |
| ⑥ 启动应用时 | 646-676 | `Power 14 emerald-500` 标题；两个按钮 `进入上次窗口` / `打开新窗口`，选中 = `theme.primaryBg` 实底白字 |
| 底栏 | 678-687 | `Trash2 12 + 清空默认项`（`pt-3 border-t`，左对齐，hover 底色） |

**与参考版的有意分歧**

- ⑤ **API 中转服务器整节不做**：那是 React 版为 Android WebView 的「拿不到逐字增量」准备的
  Cloudflare Worker 中转（`apiBase.ts`）。原生是直连 HTTP 客户端（T04 起），没有 `/api` 同源概念，
  也没有可填的运行时 base —— 保留这一节只会给用户一个填了没用的输入框。
- 底栏「清空默认项」在原生放在内容区末尾（`TextButton`），不是固定底栏；参考版是
  `pt-3 border-t` 的贴底一行。

---

## 二、SettingsModal — 文本设置（261 行）

**Native**：`ui/settings/PreferencesPanels.kt` 的 `DisplaySettingsPanel`
（`App.kt` 用 `FeatureDialog(title = "文本设置", maxWidth = 576.dp)` 包壳）。

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 顶栏 | 107-130 | 32dp 徽章（Settings 20）+ `文本设置`（`chat-text-base font-bold`）+ 副标题 `消息样式、界面字体与显示指标` + ✕ |
| ① 聊天区消息样式 | 133-198 | 小标题 `MessageSquare 13 + 聊天区消息样式`（`text-[11px] uppercase tracking-wider`）；`grid-cols-2 gap-2.5` 两张卡（小气泡 / 大气泡），每张 = 图标 + 名称 + InfoHint + **样式预览条**（`h-9 rounded-lg bg-slate-100` 内两条：主色条 + 灰条，大气泡时都占满宽） |
| ② 界面字体大小 | 200-227 | 小标题 `Type 13 + 界面字体大小`；一行 `A` 小字 + `<input type=range min=12 max=22 step=1>` + `A` 大字 + `{n}px`（`font-mono text-[11px]`） |
| ③ 消息元数据显示开关 | 229-272 | 小标题 `消息元数据显示开关`；五行，每行 = 图标（Cpu/Clock/Zap/FileText/Coins）+ 标题 + 预览文案（`text-[10px] font-mono`）+ 说明 + 右侧**自绘胶囊开关**（`w-9 h-5 rounded-full` + `w-4 h-4` 白点）；整行可点 |
| 底栏 | 275-284 | `RotateCcw 12 + 恢复默认设置`（`p-3.5 border-t`，左对齐，hover 变深） |

---

## 三、ThemeModal — 主题（317 行）

**Native**：`ui/settings/ThemeDialog.kt`（自带 `FeatureDialog`）。

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 容器 | 162-176 | `max-w-md`(448dp) · `max-h-[88vh] flex flex-col` · `rounded-2xl` · `bg-slate-50`（**注意底色是 slate-50，比其它弹窗深一档**） |
| 顶栏 | 172-215 | 列表态：32dp 方块（`bg-slate-100 border`，Palette 18）+ `UI 主题色彩切换` + 副标题 `个性化定制气泡配色、高亮主题与交互风格`；**DIY 态：徽章位换成 `ChevronLeft` 返回按钮**，标题变 `DIY 主题`、副标题变 `模块 1 管气泡与按钮，模块 2 管焦点与选中态` |
| 主题列表 | 272-320 | 每行 = 24dp 主色圆点（选中时内含白色 ✓）+ 名称（`chat-text-xs font-semibold`）+ 主色 HEX（`text-[10px] font-mono`）+ 右侧两个 12dp 预览圆点（模块 1 / 模块 2）+ 自定义主题才有的 Trash 13 删除；选中行 = `bg-slate-100 border-slate-400 shadow-md ring-1` |
| 自定义主题分组 | 296-315 | 有自定义主题时插一行 `自定义主题`（`text-[11px] uppercase`） |
| DIY 入口 | 317-325 | 整宽**虚线**框 `border-dashed p-3 rounded-xl` + `Plus 14 + DIY 新建主题` |
| DIY 表单 | 239-279 | 主题名称输入 + 两组 `SwatchGrid`（模块 1 主色 / 模块 2 辅助色，各带 InfoHint + 当前 HEX）+ 错误行 + `取消 / 保存` |
| 调色盘 | 34-58 | `COLOR_SWATCHES` 共 32 色，`flex flex-wrap gap-2`，12dp 圆点；选中 = `ring-2 ring-offset-2 scale-125` |
| 保存 | 146-160 | 名字空 → `请先给主题起个名字`（不关表单）；否则 id = `custom_${Date.now().toString(36)}`，保存**并立即选中**该主题 |

**⚠️ 参考版没有「明暗模式」这一节** —— 明暗由顶栏 / 侧边栏的日/月按钮切换
（`Header.tsx`），主题弹窗只管配色。原生旧 `ThemePanel` 里那组「浅色 / 深色」按钮是自造，
本次删除（明暗入口仍在侧边栏与顶栏）。

---

## 四、PromptModal — 提示词库（585 行）

**Native**：`ui/settings/PromptLibraryDialog.kt`（自带 `FeatureDialog`）。

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 容器 | 208-216 | `max-w-3xl`(768dp) · `max-h-[92vh]` · `p-5 sm:p-6` |
| 顶栏 | 212-232 | 32dp 徽章（`bg-emerald-500/10 border-emerald-500/30 text-emerald-400`，Sparkles 18）+ `提示词库与快捷切换` + 副标题 `可填入输入框，或直接设为当前会话窗口的主控提示词` + ✕ |
| 工具条 | 435-486 | 搜索框（`Search 14` + 输入 + 有内容时 `X` 清除）+ `从当前内容存入`（`ArrowDownToLine`，无可用来源时 disabled）+ `新建`（`Plus`，主色实底） |
| 分类筛选 | 466-484 | 六个药丸：`全部 / 效率分析 / 编程开发 / 写作润色 / 深度思考 / 自定义`（id：`all / productivity / coding / writing / reasoning / custom`）；选中 = `bg-slate-100 text-emerald-400 border` |
| 反向保存来源 | 458-475 | 展开面板列出「输入框草稿 / 窗口·本身提示词 / 窗口·通用提示词」三条（**内容非空的才出现**），每条显示 `{n} 字` + 一行截断预览 |
| 重名处理 | 477-505 | 已存在同名提示词时给三选一：`覆盖原提示词`（rose 实底）/ `另存为新的「X (2)」`（主色实底）/ `取消` |
| 卡片列表 | 476-575 | `grid-cols-1 md:grid-cols-2 gap-2.5`；卡片 = 标题 + 标签（`· tag`，`text-[10px] font-mono`）+ 说明 + **内容预览**（`bg-slate-50 rounded-lg p-2 text-[11px] font-mono line-clamp-2`）+ 右上 Edit2 12 / Trash2 12 + 底部动作区 |
| 动作区 | 547-573 | `(onApplyAsSystemPrompt \|\| onInsertToInput) &&` 才渲染：`设为窗口提示词`（生效中时变 `当前窗口生效中` + Check 图标 + emerald 态）/ `填入输入框`（主色实底 + ArrowUpRight）；两者点击后都 `onClose()` |
| 表单层 | 306-433 | 又一个独立弹窗（`z-[80]`，`max-w-2xl`）：标题 * / 简要说明 / 分类 `<select>` / 内容 *（`rows=12`，等宽）+ 取消 / 保存提示词；`importNotice` 为琥珀色提示条 |
| 保存逻辑 | 253-270 | 标题或内容空 → 直接返回；id 新建为 `prompt-${Date.now()}`；标签 `split(/[,，\s]+/)`，空则回退 `['自建']` |

**入口差异（关键）**

- **设置入口**（`App.tsx:2456-2465`）只传 CRUD 三件套 —— 卡片底部那两个动作按钮**不渲染**。
- 输入栏的「提示词」按钮开的是**双框提示词**（`PromptDualBoxModal`），不是本弹窗
  （`App.tsx:2210` 的 `onOpenPromptModal` → `setIsDualBoxPromptModalOpen(true)`）。

Native 对应：`PromptLibraryDialog` 的 `onInsertToInput` / `onApplyAsSystemPrompt`
两个参数**默认 `null`**（设置入口不传）；将来若要在输入栏加库入口，传上即可，
不需要第二份组件。

**参考版有、Native 未做的部分**

- 「从当前内容存入」+ 重名三选一：依赖**输入框草稿**（`inputDraft`）与窗口提示词两路来源。
  原生 `ChatInputBar` 的 draft 仍是组件内部状态（没提升到 `App.kt`，见 T27 遗留），
  且窗口提示词那两路要额外接线。**本轮未做**，卡片上留了说明性 hint。
  要做时按参考版 458-505 补，别自创交互。

---

## 五、Native 落点汇总

| 关注点 | 文件 |
|---|---|
| 卡片数据 + 目的地枚举 | `ui/settings/SettingsHub.kt` 的 `SettingsPage` / `SettingsDestination` |
| 六个子弹窗的渲染 | `ui/App.kt` 的 `settingsChild?.let { … when (destination) { … } }` |
| 状态 | `ui/App.kt` 的 `settingsChild` / `settingsChildReturnsToSettings` |
| 主题弹窗 | `ui/settings/ThemeDialog.kt` |
| 提示词库弹窗 | `ui/settings/PromptLibraryDialog.kt` |
| 偏好 / 文本设置的内容 | `ui/settings/PreferencesPanels.kt`（`AppPreferencesPanel` / `DisplaySettingsPanel`） |
| 主题数据 | `ui/theme/ThemeCatalog.kt` 的 `builtInThemes()` / `COLOR_SWATCHES` / `ThemeSource` |

## 六、视觉与规格约束

- 表单控件走 `WwFormControls`（`WwTextField` / `WwSelectField` / `WwPrimaryButton` /
  `WwSecondaryButton` / `WwRangeSlider` / `Modifier.dashedRoundedBorder`）。
- 分层底色走 `WwSurfaces`（`card` / `cardStrong` / `inset` / `cardSelected` / `accentSoft` /
  `cardBorder` / `cardBorderSubtle`）；**不用** `MaterialTheme.colorScheme.surfaceVariant`。
- 图标：`Sparkles` / `ArrowDownToLine` / `ArrowUpRight` / `Maximize2` / `ChevronLeft` 等在
  Tabler 子集里**不存在**，一律换语义等价的（`Wand` / `Download` / `Edit` / `Maximize` / `ChevronLeft` 存在）；
  用之前先 `unzip -l` 核名字。
- 删除确认统一 `AlertDialog`（参考版用浏览器 `confirm()`，原生无等价物）。
