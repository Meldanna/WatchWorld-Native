# T30 · 设置弹窗按 Web 版校正 — 剩余四项（默认与偏好 / 文本设置 / 主题 / 提示词库）

| | |
|---|---|
| 任务号 | T30 |
| 日期 | 2026-10-11 |
| 状态 | ✅ 完工 |
| 阶段 | 阶段 7 · UI 精修 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T30 |

## 目标与可见效果

- **目标**：把设置总入口剩下的四张卡按参考版做成**独立弹窗**（不是设置内的内联子页），
  并把内容对齐参考版：`PreferencesModal`(687) / `SettingsModal`(261) /
  `ThemeModal`(317) / `PromptModal`(585)。
- **可见效果**：设置里点任意一张卡 → 设置总入口关闭、目标弹窗打开；关闭后回到设置总入口。
  主题弹窗有配色列表 + DIY 调色；提示词库有搜索 + 分类 + 两列卡片。
- **验收标准**：① 四个弹窗都是独立 `FeatureDialog`；② 输入控件走 `WwFormControls`；
  ③ 分层底色走 `WwSurfaces`；④ 关闭一律回设置总入口。

> 规格文档：[`tasks/settings-dialogs-source-map.md`](settings-dialogs-source-map.md)（四项逐段行号对照）。

## 改动

| 文件 | 改动 |
|---|---|
| `tasks/settings-dialogs-source-map.md` | **新增**：四个弹窗的逐段行号规格 + 共同结构（`handle()` 先关再开）+ Native 落点 |
| `ui/settings/SettingsHub.kt` | **重写为纯卡片列表**：新增 `SettingsDestination` 枚举（六项）；`SettingsPage` 枚举保留卡片文案/图标并带上 `destination`；`SettingsHub` 签名从 12 个回调收敛成 `onOpen(destination)`；删掉内联子页的 `page` 状态与 `when` 分支 |
| `ui/App.kt` | 状态 `agentOpen`/`agentReturnsToSettings`/`backupOpen`/`backupReturnsToSettings` 四者 → **`settingsChild` + `settingsChildReturnsToSettings` 一对**；新增统一的 `settingsChild?.let { when (destination) { … } }` 渲染器（六个弹窗集中一处）；删掉**从未被打开过**的 `inputPromptsOpen` 入口 |
| `ui/settings/ThemeDialog.kt` | **新增**：主题列表（**20 内置 + 自定义**，主色圆点 + 双色预览点 + 删除）+ DIY 表单（名称 + 两组 32 色托盘 + 返回）+ 保存即选中 |
| `ui/settings/PromptLibraryDialog.kt` | **新增**：搜索 + 六个分类筛选 + 两列卡片（标题/标签/说明/内容预览/编辑/删除）+ 独立编辑表单（标题/说明/分类/内容/标签）+ 删除确认；`onInsertToInput` / `onApplyAsSystemPrompt` 默认 `null`（设置入口不传） |
| `ui/settings/PreferencesPanels.kt` | `AppPreferencesPanel` / `DisplaySettingsPanel` 去掉**内联标题**（标题归弹窗外壳） |
| `ui/settings/SettingsPanels.kt` | **删除** `PromptPanel`、`ThemePanel`（被独立弹窗取代）与因此成孤儿的 `ItemRow` / `FormActions` / `PromptPreset` import |
| `ui/chat/PromptInsertDialog.kt` | **删除** —— 它挂在 `inputPromptsOpen` 上，而该状态全项目无人置 true（死代码）；参考版输入栏的提示词按钮开的是双框弹窗，提示词库只从设置进 |

## 心得

- **「设置里的一张卡」在参考版一律是独立弹窗**：`SettingsHubModal.tsx:43-47` 的 `handle()`
  先 `onClose()` 再 `setTimeout(fn, 0)`（注释原文「避免多层级弹窗叠压」）。
  这次把最后四项一次搬完，`SettingsHub` 于是退化成**纯卡片列表** —— 它本来就不该知道
  「子页」是什么。**判据**：如果设置总入口需要在目标界面打开时仍然在场，那是子页；
  参考版明确不让它在场。
- **状态从「每张卡一对布尔」收敛成一对**：六张卡若各来一对 Boolean，就是 12 个状态，
  而且「同时为真时该回哪一层」没有唯一答案（需求 §三 的原话）。
  改成 `settingsChild: SettingsDestination?` + `settingsChildReturnsToSettings` 后，
  关闭逻辑只写一次，新增卡片零成本。这与 T24 的 `WindowFunctionDestination` 是同一套理由。
- **死代码要顺着入口查**：`inputPromptsOpen` 全项目只有「声明 / 读取 / 关闭」三处，
  **没有任何地方置 true** —— 那个提示词弹窗从来没被打开过。删掉它顺带省下一份重复实现
  （新 `PromptLibraryDialog` 覆盖了它的全部能力，并且参数化保留了输入栏入口的可能性）。
- **参考版「没有的」也是规格**：`ThemeModal` 里**没有**明暗模式那一节（明暗在顶栏/侧边栏），
  原生旧 `ThemePanel` 里那组「浅色 / 深色」按钮是自造，本次删掉。
  同理 `PreferencesModal` 的「API 中转服务器」整节是给 React 版 Android WebView 用的，
  原生直连 HTTP 客户端没有这个概念 —— 照抄只会给用户一个填了没用的输入框。
- **图标名先核 jar**：`Sparkles` / `ArrowDownToLine` / `ArrowUpRight` / `Maximize2` 在
  Tabler 1.1.1 子集里都不存在（→ `Wand` / `Download` / `Edit` / `Maximize`）。这条 T27/T28/T29
  都踩过，这次先 `unzip -l` 再落笔，一次过。
- **`FeatureDialog` 的 `titleContent` 是给「顶栏徽章位要换成返回按钮」准备的**：
  主题弹窗 DIY 态按参考版把徽章换成 `ChevronLeft` 返回，用 `titleContent` 表达，
  不用为此改外壳结构。

## 要点

- **六张卡的顺序照参考版**：Agent 管理 → 默认与偏好 → 文本设置 → 主题 → 提示词库 →
  （正则库，本项目删）→ 备份与导入导出。`SettingsHub.kt` 里 Agent 与备份是**单独渲染的两张卡**，
  中间四项才走 `SettingsPage.entries`。
- **`SettingsDestination` 是唯一的「设置子弹窗」标识**。新增设置卡 = 枚举加一项 +
  在 `App.kt` 的 `when` 里加一个分支，别再加布尔。
- **关闭逻辑只写一处**（`closeChild`）：清 `settingsChild`；备份弹窗额外
  `clearImportNotice()`；若 `settingsChildReturnsToSettings` 则重开设置总入口。
- **`settingsChildReturnsToSettings` 只有输入栏 Agent 入口置 false**（关闭回聊天界面），
  其余全部由 `SettingsHub.onOpen` 置 true。
- **主题弹窗的数据源是 `ui/theme/ThemeCatalog.kt`**：`builtInThemes()`（**20 套**，含 name/primaryHex/source）、
  `COLOR_SWATCHES`（32 色）、`ThemeSource.Custom` / `ThemeSource.SaturationLevel`。**别再写第二份主题清单**。
  （⚠️ 2026-10-13：原 `ThemeSource.Family` 及其 m2 = m1 × 0.4 的派生**已作为废案删除** —— 它从未被构造过。）
  模块 2 的预览色用 `m2Preview`（对齐参考版 `ThemeModal.tsx:64-76` 的映射表）。
- **提示词库的动作按钮是「能力驱动」的**：`onInsertToInput` / `onApplyAsSystemPrompt`
  为 `null` 时整条动作区不渲染（参考版 L547 的 `&&` 守卫）。设置入口不传这两个参数。
- **提示词库未做的部分**：参考版的「从当前内容存入」+ 重名三选一（458-505）依赖输入框草稿
  与窗口提示词两路来源，本轮未接线（`ChatInputBar` 的 draft 仍是组件内部状态）。
- **`PromptPanel` / `ThemePanel` / `PromptInsertDialog` 已删除**，别再照旧版加回来。

## 验收结果

- [x] 四项全部改成独立弹窗；`SettingsHub` 退化为纯卡片列表。
- [x] 状态收敛为 `settingsChild` + `settingsChildReturnsToSettings` 一对。
- [x] `ThemeDialog` / `PromptLibraryDialog` 新增；偏好 / 文本设置复用既有面板并去掉内联标题。
- [x] 死代码清理：`inputPromptsOpen` + `PromptInsertDialog` + 旧 `PromptPanel`/`ThemePanel` 及孤儿 import。
- [x] 规格文档 `tasks/settings-dialogs-source-map.md` 新增（带行号）。
- [x] `:shared:compileKotlinDesktop` + `compileTestKotlinDesktop` 干净（0 错误 0 警告）。
- [x] `:desktopApp:build :shared:desktopTest` 全绿 = **362 用例 / 0 失败 / 0 错误 / 5 跳过**。
- [ ] 手工走查（未做）：六张卡逐个点开 / 关闭，确认都回设置总入口；主题 DIY 保存后立即生效。
- [ ] 离屏截图走查：四个新弹窗未加（`Dialog` 在 `ImageComposeScene` 里不渲染，需要另找办法）。

## 遗留问题 / 下一步

- **提示词库的「从当前内容存入」未做**（见「要点」）。
- **偏好弹窗的「API 中转服务器」不做**（原生直连，无此概念）。
- **`SettingsPanels.kt` 里仍有一个无人调用的旧 `PreferencesPanel`**（与 `AppPreferencesPanel` 重复）——
  本次未删：它在我改动之前就已经没有调用点，属于既有死代码，按约定「只清自己造成的孤儿」留待确认。
- **设置里现在已无内联子页**，`SettingsHub` 的 `page` 机制彻底移除。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §5：UI 只拿 Repository / ViewModel —— 本次只改 UI 层与 `App.kt` 的界面状态。
- `MIGRATION_CONSTRAINTS.md` §6：字段名不改 —— `CustomTheme` / `PromptPreset` / `AppPreferences` 原样使用。
