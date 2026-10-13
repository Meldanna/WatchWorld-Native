# T25 — 侧边栏按 Web 参考版重做（尺寸对齐 + 入口调整）

> 开新窗口执行。开工前先读：
> - `memory/ui-replication-requirements.md`（复刻纪律）
> - `memory/theme-color-map.md`（配色函数地图）
> - `E:\WatchWorld\WatchWorld\src\components\SidebarDrawer.tsx`（参考源，587 行）
> - `tasks/sidebar-source-map.md`（侧边栏源码对照表，若与本文冲突以本文为准）

---

## 0. 当前状态（交接事实）

**已可用，不要推倒重来。** 磁盘上的
`shared/src/commonMain/kotlin/com/watchworld/ui/sidebar/SidebarSheet.kt`（约 683 行）
是我按参考版重写的**唯一文件**，结构正确：

| 符号 | 行 | 说明 |
|---|---|---|
| `NavColors` | 96 | 底部导航图标色（Tailwind 500 档） |
| `SidebarSheet` | 108 | 主入口 |
| `SidebarHeader` | 238 | 顶部标题栏 |
| `SidebarActions` | 295 | 新建分组 / 导入对话 |
| `ActionButton` | 347 | 按钮原子 |
| `SidebarSearchBar` | 384 | 搜索框 |
| `GroupHeader` | 425 | 分组头 |
| `SessionRow` | 480 | 会话行 |
| `SessionActionRow` | 571 | 会话操作菜单项 |
| `SidebarNavGrid` | 591 | 底部 4×2 网格 |
| `NavGridItem` | 629 | 网格单元 |
| `parseGroupColor` | 677 | `#rrggbb` 解析 |

**已知的 3 个问题（本次要修的）：**

1. **`App.kt:422` 编译错误** —— `SidebarSheet` 现在没有 `onOpenBackup` 参数了
   （改成了 `onOpenRegex`），但 App.kt 的调用点还在传 `onOpenBackup`。
   当前 `:shared:compileKotlinDesktop` **不过**。
2. **底部导航第 7 格接线错** —— `SidebarSheet.kt:621` 的「正则库」回调
   `<TABLERIcons.Code, ..., onOpenSettings>` 手滑写成了 `onOpenSettings`，
   应为 `onOpenRegex`。
3. **尺寸/排版未按上次的报告逐项对齐** —— 见 §2 的数值表。

---

## 1. 本次任务

### 1.1 修编译（必做，先做）

`App.kt` 侧边栏调用点（约 362–427 行）：

- 删掉 `onOpenBackup = { backupOpen = true; scope.launch { settingsViewModel.load() } }` 这一整块
- 换成：

```kotlin
onOpenRegex = {
    settingsOpen = true
    scope.launch { settingsViewModel.load() }
},
```

> 说明：全局正则库的 UI 是 `SettingsHub` 里的 `SettingsPage.REGEX`
> （`ui/settings/SettingsHub.kt:45` → `SettingsPanel.kt` 的 `RegexPanel`），
> **没有**独立的顶层面板。所以「正则库」入口 = 打开设置并落到 REGEX 页。
> ⚠️ 若要做到「直接打开正则页」，需要先把 `SettingsHub` 的 `page`
> 状态提升成参数（当前是内部 `remember`，`SettingsHub.kt:73`）——
> 这算额外改动，**本次可选**，不做也不算失败，但要在这条下面注明「未做」。
> **未做**（2026-10-10）：`SettingsHub.page` 仍是内部 `remember`，「正则库」入口
> 落到设置首页，需用户再点一次「正则库」卡片。要做需把 `page` 提升为受控参数
> `initialPage: SettingsPage?` + `onPageChange`，再让 `App.kt` 为侧边栏入口传 `REGEX`。
> `backupOpen` / `BackupPanel` 仍然保留（设置页里还有「备份与导入导出」卡片），
> 只是侧边栏不再有独立入口。
> ⚠️ **副作用（2026-10-10 实测）**：侧边栏不再置 `backupOpen = true` 之后，
> `App.kt` 的 `if (backupOpen)` 弹窗**已不可达**（全仓再无 `onOpenBackup` 引用，
> `backupOpen` 只剩初始化与 `onDismiss` 两处写 `false`），成了死代码。
> 本次按本文要求保留，未删。

顺手把 `SidebarSheet.kt:621` 的 `onOpenSettings` → `onOpenRegex`。

验收：`./gradlew.bat :shared:compileKotlinDesktop` 通过，0 warning。

### 1.2 按参考版对齐尺寸与排版

逐项照抄参考版 Tailwind 值（1 单位 = 4px）：

| 位置 | 参考版 | 原生目标值 | 现状 |
|---|---|---|---|
| 容器 | `w-[84%] max-w-[320px]` | `widthIn(max = 320.dp)`（宽度交给调用点） | ✅ 已对 |
| 容器底 | `bg-white dark:bg-slate-900` | `WwTheme.colors.surfaceElevated` | ✅ |
| 容器描边 | `border-r border-slate-200` | `WwSurfaces.cardBorderSubtle` | ✅ |
| 顶栏内边距 | `p-3` | `padding(12.dp)` | ✅ |
| Logo | `w-8 h-8 rounded-xl` + 阴影 | `size(32.dp)` + `RoundedCornerShape(10.dp)` | ✅ |
| 顶栏图标 | `Sun/Moon size=16`、`X size=18` | 16dp / 18dp | ✅ |
| 按钮区 | `p-3 gap-2` | `padding(12.dp)` + `spacedBy(8.dp)` | ⚠️ 见下 |
| 主按钮 | `py-2 px-3 rounded-xl` | `padding(h=12, v=8)` + 圆角 10dp | ⚠️ 见下 |
| 次按钮 | `py-2 px-2.5` | `padding(h=10, v=8)` | ⚠️ 见下 |
| 按钮图标 | `size={15}` | 15dp | ✅ |
| 搜索区 | `px-3 pt-2.5 pb-1.5` | `padding(start=12, end=12, top=10, bottom=6)` | ✅ |
| 搜索框内 | `px-2.5 py-1.5 rounded-xl` | 内边距 `h=10, v=6` + 圆角 10dp | ⚠️ 见下 |
| 搜索图标 | `size={14}`，清除 `size={13}` | 14dp / 13dp | ✅ |
| 列表区 | `px-2 py-2 space-y-3` | `padding(h=8, v=8)` + `spacedBy(12.dp)` | ⚠️ 见下 |
| 分组头 | `px-2 py-1`，箭头 `size={13}`，色点 `w-2 h-2` | `padding(h=8, v=4)`；13dp；8dp 圆点 | ✅ |
| 分组名 | `chat-text-xs` + `font-semibold` | `12.sp` + `SemiBold` | ✅ |
| 计数 | `text-[10px] font-mono` | `10.sp` + `FontFamily.Monospace` | ✅ |
| 会话行 | `px-2.5 py-2 rounded-xl` | `padding(h=10, v=8)` + 圆角 10dp | ✅ |
| 会话头像 | `chat-text-sm` | `13.sp` | ✅ |
| 会话标题 | `chat-text-xs`，激活态 `font-medium` | `12.sp` + `Medium` | ✅ |
| 会话删除图标 | `size={12}`，**悬停才出现** | 12dp，`⋯` 常驻 | ⚠️ 见下 |
| 列表内缩进 | `space-y-0.5 pl-2` | 子项左缩进 8dp | ⚠️ 见下 |
| 底部网格 | `p-2 gap-1 py-1.5 grid-cols-4` | `padding(8.dp)`；`spacedBy(4.dp)`；`spacedBy(6.dp)` | ⚠️ 见下 |
| 网格图标 | `size={14}` | 14dp | ✅ |
| 网格标签 | `text-[10px]` | `10.sp` | ✅ |
| 徽章 | `-top-1.5 -right-2 min-w-[13px] h-[13px] text-[8px]` | 13dp 圆，`8.sp`，偏移 `(6, -6)` | ✅ |

**「⚠️ 见下」的具体待办：**

1. **按钮等宽**：参考版主按钮是 `flex items-center` 不撑满，次按钮 `hidden sm:flex-row`
   在窄屏只显示图标。原生侧当前两个按钮没有 `weight`，宽度由内容决定 ——
   与参考版一致，**保持现状即可**，但要在按钮上加 `Arrangement.Center` 让文字水平居中。
2. **搜索框内边距**：改用 `OutlinedTextField` 后内边距由 M3 控制，无法精确设
   `px-2.5 py-1.5`。要么接受 M3 默认（当前状态），要么换成
   `BasicTextField` + 自绘 `Surface`。**建议保留 `OutlinedTextField`**，
   但把 `contentPadding` 显式调小到接近 `vertical = 6.dp`。
3. **列表 `space-y-3`**：当前 `SidebarSheet.kt:184` 是 `spacedBy(2.dp)`，
   改成 `spacedBy(12.dp)`（分组之间 12dp）。
4. **会话行操作入口**：参考版是「长按 / 右键 → 弹窗」，桌面端另有一个
   悬停才出现的删除按钮。原生当前是常驻 `⋯` 展开行内菜单。
   **保留 `⋯` 方案**（桌面无长按语义，且现有菜单已含重命名/置顶/清空/移动/删除），
   但要把删除项做成红色 + 加 `contentDescription`。
5. **子项缩进**：参考版会话列表整体 `pl-2`。把 `SessionRow` 外面包一层
   `padding(start = 8.dp)`，或直接在 `SessionRow` 的 modifier 上加。
6. **底部网格间距**：`Arrangement.spacedBy(4.dp)` 横向已对；
   两行之间参考版是 `py-1.5`（6dp），当前是 `spacedBy(4.dp)` → 改成 `6.dp`。

### 1.3 图标风格统一（用户要求）

用户明确要求**图标风格统一**，且底部导航**保留参考版的彩色语义**。
规则：

- 一律用 `compose.icons.tablericons.*`（`TablerIcons`），**不要**混用
  Material Icons 或 emoji 当图标（Logo 的 💬 保持 emoji，参考版就是 emoji）。
- 底部 8 格配色照抄参考版 Tailwind 500 档（已写在 `NavColors`，含新增
  `regex = Color(0xFF06B6D4)` = cyan-500）。
- 其余位置的图标一律走主题色：
  主色用 `MaterialTheme.colorScheme.primary`，
  次级用 `onSurfaceVariant`。

**⚠️ 图标可用性已核对（TablerIcons 1.1.1）：**

| 需要 | 用哪个 | 状态 |
|---|---|---|
| 导入对话 | `FileImport` | ✅ 存在（`FileUp` 不存在，**已改掉**） |
| 新建分组 | `Plus` | ✅ |
| 折叠箭头 | `ChevronDown` / `ChevronRight` | ✅ |
| MCP | `Server` | ✅ |
| Skill | `Bolt` | ✅（参考版用 `Zap`，Tabler 无 Zap） |
| 知识库 | `Book` | ✅（参考版 `BookOpen`，Tabler 无） |
| 世界观 | `Globe` | ✅ |
| 时间线 | `GitBranch` | ✅ |
| API | `Plug` | ✅ |
| 正则库 | `Code` | ✅ |
| 设置 | `Settings` | ✅ |
| 搜索 | `Search` | ✅ |
| 清除 | `X` | ✅ |
| 明/暗 | `Sun` / `Moon` | ✅ |

---

## 2. 底部导航最终定义（8 格，4×2）

| # | 标签 | 图标 | 颜色 | 回调 | 去向 |
|---|---|---|---|---|---|
| 1 | MCP | `Server` | emerald-500 `#10B981` | `onOpenMcp` | `mcpOpen = true` |
| 2 | Skill | `Bolt` | amber-500 `#F59E0B` | `onOpenSkill` | `skillOpen = true` |
| 3 | 知识库 | `Book` | blue-500 `#3B82F6` | `onOpenKnowledge` | `knowledgeOpen = true` |
| 4 | 世界观 | `Globe` | teal-500 `#14B8A6` | `onOpenWorld` | `worldOpen = true` |
| 5 | 时间线 | `GitBranch` | indigo-500 `#8B5CF6` | `onOpenTimeline` | `timelineOpen = true` |
| 6 | API | `Plug` | rose-500 `#EF4444` | `onOpenProvider` | `providerOpen = true` |
| 7 | **正则库** | `Code` | cyan-500 `#06B6D4` | **`onOpenRegex`** | `settingsOpen = true`（落 REGEX 页） |
| 8 | 设置 | `Settings` | slate-500 `#64748B` | `onOpenSettings` | `settingsOpen = true` |

**⚠️ 用户定案：删掉「备份」入口。**
参考版第 7 格是「备份」（`FileUp` + cyan-500），用户要求删掉并换成**正则库**。
备份与同步仍可从 设置 → 「备份与导入导出」进入，只是侧边栏不再有独立入口。

**徽章**：`activeMcpCount` / `connectedKnowledgeCount` 由 App.kt 传入。
⚠️ 目前调用点**没传这两个参数**（走默认 0）。本次可选补齐：
`sessionState` 里没有这两个计数，需要从 `settingsState.mcpServers.count { it.enabled }`
和 `settingsState.knowledgeBase.count { it.connected }` 之类的字段推。
**若字段名对不上就不要硬接**，留注释说明「计数来源未定」即可。

---

## 3. 落地后必须跑的验证

```powershell
.\gradlew.bat :shared:compileKotlinDesktop      # 必须 0 error / 0 warning
.\gradlew.bat :desktopApp:build :shared:desktopTest --rerun-tasks
```

测试基线（改动前）：
```
42 套 / 341 用例 / 336 通过 / 5 跳过 / 0 失败 / 0 错误
```
改动后允许新增用例，但**不允许新增失败**。

截图自检（若 `TimelinePanelScreenshotTest` 的离屏渲染方式可复用）：
新增一个 `SidebarPanelScreenshotTest`，把 `SidebarSheet` 直接渲进
`ImageComposeScene`（**不要**包 `Dialog`，Dialog 在里面不渲染），
输出到 `shared/build/t25-screenshots/`，人工看一眼配色是否还是「灰白块」。
配色断言用现成的通道差谓词（`TimelinePanelScreenshotTest` 里那份 `isTinted`）。

---

## 4. 配置/纪律提醒（踩过的坑）

1. **改配色前先读 `memory/theme-color-map.md`。** 卡片底一律走
   `WwSurfaces.card` / `cardStrong` / `inset` / `cardSelected` / `accentSoft`，
   描边走 `cardBorder` / `cardBorderSubtle`。
   ⚠️ **不要**再用 `MaterialTheme.colorScheme.surfaceVariant` 做卡片底 ——
   它落到中性灰 `--surface-3`（亮色 `#E2E8F0`），就是用户抱怨的「灰白块」。
2. **`WwSurfaces` 的所有取值都是 `@Composable`**，依赖当前
   `WwTheme.colors`，**不能**缓存成顶层常量。
3. **圆角值**：参考版 `rounded-xl` = 10dp；`rounded-2xl` = 16dp；
   `rounded-lg` = 8dp。本项目统一 10dp，别再写 8/12 混用。
4. **字体档位**：`chat-text-xs` ≈ 12sp，`chat-text-sm` ≈ 13sp，
   `text-[10px]` = 10sp。列表区一律 12sp，不要用 `bodyMedium`（14sp）撑大行高。
5. **`onOpenSummary` 参数已删除** ——「前文总结」只能从窗口功能底部弹层进入
   （T24 定案）。不要加回侧边栏。
6. **不要提交或推送。** 用户明确要求。
7. App.kt 里 `sidebarOpen` 用 `Dialog(usePlatformDefaultWidth = false)` +
   半透明遮罩实现（`App.kt:347-430`）。遮罩点击关闭、`SidebarSheet` 用
   `Modifier.fillMaxHeight().width(320.dp).align(Alignment.CenterStart)`。
   参考版的 84% 宽在桌面无意义，**保持固定 320dp**。

---

## 5. 完成标准

- [ ] `:shared:compileKotlinDesktop` 通过，0 error / 0 warning
- [ ] `:shared:desktopTest` 无新增失败
- [ ] App.kt 不再引用 `onOpenBackup`，改传 `onOpenRegex`
- [ ] `SidebarSheet.kt` 第 621 格回调是 `onOpenRegex`
- [ ] 底部导航无「备份」格
- [ ] 尺寸按 §1.2 表格逐项核对（列出的 6 条 ⚠️ 处理完）
- [ ] 图标全部来自 `TablerIcons`，无 Material/emoji 混用
- [ ] 没有新引入 `surfaceVariant` 做卡片底
