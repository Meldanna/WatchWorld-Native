# UI 排版标准（文字基准 · 注释收起 · 内外框高）

> 用户 2026-10-11 定案；2026-10-13 改为「设置值即字号、嵌套边距每级 +2sp」。改任何界面文字 / 间距之前先看本文件。
> 落地代码：`ui/theme/WwType.kt`（字号三档 + `WwPad` 嵌套间距 + `WwFont`/`UiFontFamily` 字体族）、
> `ui/components/InfoHint.kt`（注释浮层）、`ui/components/WwTag.kt`（标签 / 同行小按钮的统一高度）。

---

## 一、文字三档（`WwType`）ba

| 档位 | API | 默认基准 15 时 | 用途 |
|---|---|---|---|
| 默认正文 | `WwType.body` | **15sp** | 标签、值、按钮、列表项……一切「正常文字」 |
| 标题 | `WwType.title`（**仅弹窗顶栏标题**再加 `WwType.titleWeight`） | **17sp**（弹窗顶栏标题为 17sp 加粗） | 弹窗标题、分区/卡片标题 |
| 框外提示 | `WwType.hint` | **13sp** | 框外说明、注释浮层文字、次级/元数据 |

### 跟随「界面字体大小」

三档挂到 `WwTypographyTokens`，**不做倍率换算、直接用 sp**（用户 2026-10-13）：

```
body  = 设置值本身   （base 15 → 15sp）
title = body + 2sp   （base 15 → 17sp）
hint  = body − 2sp   （base 15 → 13sp）
```

`base` = 设置 →「文本设置 → 界面字体大小」的 `AppPreferences.chatFontSize`
（12–22px，默认 15），经 `App.kt` 的 `WwTheme(chatFontSize = …)` 注入 →
`WwColors.typography` → `WwType`。**用户在设置里拖大字体，全应用三档一起按 sp 平移**
（行高同样直加：紧凑 +3sp、正文 +5sp、标题 +7sp，默认仍是 16 / 20 / 24）。

- `WwType.body/title/hint` 是 `@Composable` 取值 —— **只能在组合作用域里读**
  （`Text(fontSize = WwType.body)`），不要存进顶层常量或非组合上下文。
- `WwType.titleWeight`（Bold）不随字号变，是普通 `val`。
- **加粗只给弹窗顶栏标题**（用户 2026-10-13：除弹窗顶栏标题外，全应用取消加粗）——
  分区标题、卡片标题、列表项、按钮都只靠字号（`title` vs `body`）拉开层级，
  不要再传 `FontWeight.SemiBold/Bold/Medium`，也不要再用 `WwTag(bold = …)`（参数已删）。

### 输入框里的文字

能输入内容的框（`WwTextField` / `SearchField` / 选择框 / 聊天输入框）**统一用默认正文 `WwType.body`**
（用户 2026-10-12 改口径：早前用 `hint`，结果「窗口 API 参数」这类面板里**标签 14sp 比框里的值 12sp 还大**）。
占位与正文同大小，且随「界面字体大小」缩放。注意区分：文字的**字号**跟着走，
**框自身的高度**不受三档 / 2sp 规则影响（仍由 `WwSize.formFieldVerticalPadding` 等固定留白决定）。

### 豁免（**不要动**）

1. 等宽位（模型名、Tag、代码块、URL、模式串）：`FontFamily.Monospace`，不随字体族设置变。
2. 结构性微徽章（头像 emoji、未读数字角标等）按各自需要，不受三档约束。

---

## 二、注释 / 提示一律收成 `InfoHint`

- **凡「能输入内容的框以外」的注释 / 提示**，不再直接铺成文字，改成
  **注释图标 `ⓘ` + 浮层**（`ui/components/InfoHint.kt`）。**默认收起**。
  已有的暴露写法也一并换掉。
- 字段标签行：`label + InfoHint`（图标紧跟标题/标签文字）。
- 分区标题若带说明：说明收进图标（标题分区因此**变矮**，高度≈图标+标题一行）。
- **括号式说明、成对注释、页脚说明行也收**（用户 2026-10-12）：
  - 标签里内嵌的 `（说明）`（如「连续工具轮数（0 为无限…）」「上级分支（用于构建因果链）」）
    → 标签只留名字，说明进 ⓘ；
  - 标题旁的括注（如 `(始终展开·手写固定规则)` `(自动推入·系统提示词身份)`）→ 直接换成 ⓘ；
  - 页脚 / 底栏的说明行（如「保存后立即应用到下一次请求」「AI 可见分支可自由多选…」）→ 收成 ⓘ。
  - 能走 `hint` 参数的优先走（`WwTextField` / `FieldLabel` / `SectionTitle` / `NumberInputField`
    都渲染成 ⓘ）；自绘标签写 `Text(label) + InfoHint(hint)`。
- **不收**（它们不是「注释」）：空态文案（「还没有…，点击…开始」）、确认弹窗的正文警告、
  选项卡上的描述行、纯数据 / 状态（计数、楼层、「已总结至 #N」）、以及讲解 / 图例本体（见 §已知例外）。
- **弹窗顶栏的副标题也收**：`FeatureDialog` 的 `subtitle` 改成标题旁的 ⓘ ——一级弹窗只留一行标题，
  顶栏因此变矮（用户 2026-10-11）。**例外**：数据型副标题（条数 / 当前值这类要一眼看到的）
  传 `subtitleAsHint = false`，仍按标题下一行渲染（例：时间线库的「N 条分支 • M 次会话」）。

### 浮层要求（对标参考文件 `WatchWorld/src/components/InfoHint.tsx`）

1. **触发 / 收起**：点 ⓘ 展开、再点收起；点别处（含另一个 ⓘ）收起 —— **同一时刻只有一个浮层**。
2. **图标**：`12dp` **上标**（比所在行基线抬高约 3dp）、**主色**，展开时**不变色**（用户 2026-10-11）；
   紧跟在标签 / 标题文字之后（间距 6dp），调用处不要再自己调垂直对齐。
3. **位置**：默认在图标**下方 6dp**；下方放不下 → **上翻**到图标上方；上下都放不下 → **贴住下缘**；
   水平**居中对齐图标**后夹进范围（两侧各留 8dp）。**任何情况都不许越界**。
4. **范围 = 宿主弹窗的面板**（不是屏幕）：弹窗在 Compose 里是**独立窗口**，只夹屏幕的话浮层会压在面板外。
   面板用 `HintBounds { … }` 包住即注入范围 —— `FeatureDialog` 已接，**全部一级弹窗因此都受约束**；
   没注入才退回整个窗口。
5. **尺寸不越界**：最大宽 = **宿主面板宽 − 16dp**、最大高 = `面板高 − 16dp`；
   （原来还夹了个写死的 `260dp` 上限 —— 弹窗很宽时注释会在「远远没到弹窗边缘」处就换行，用户 2026-10-12 修）
   弹窗又窄又矮时浮层跟着缩，装不下就**内部滚动**（`verticalScroll`）—— **不许截字、不许顶出面板**。
6. **样式**：`13sp` 文字（`WwType.hint`）+ 底 `surfaceElevated` + 1dp 描边（`outlineVariant`）
   + 12dp 圆角 + `10×8dp` 内边距 + 12dp 投影。
7. **只收「说明」，不收「正文」**：讲解 / 图例本体（`PromptInjectionGuideDialog` 的步骤说明、
   `SessionActionsPanel` 的导出格式说明）仍是常驻文字，不塞进浮层。

> 全局单例仲裁在 `InfoHint.kt` 的 `InfoHintState.openKey`：每个实例一个 `remember` key，
> 点开即写入，其它实例读到不匹配自动收起 —— 不需要事件总线。

---

## 五、垂直留白与行高（**只有两个口径**）

**框内留白**：带子 / 卡片 / 行 / 功能框里的内容到框边 = **每侧 2sp**
（`WwSize.boxVerticalPadding`，直接用 sp）—— 即「框总高 = 文字行 + 4」，那 **4 是上下合计**。
**框与框之间**：相邻卡片 / 条目、分隔线上下、弹窗内容区上下 = **4dp**（`WwSize.cardListGap`）。
**嵌套层级**（用户 2026-10-13）：每级每侧 **+2sp**，相邻层级总高度差 **4sp**（上下各 2sp）。

> 2026-10-13 起，字号与边距**不再做倍率 / 缩放换算**，全部直接用 sp：
> 字号三档按「设置值 / +2 / −2」，边距按每级 +2sp，删掉了原来的 `WwSize.fromHint`。

**间距只算一次**（用户 2026-10-13）：相邻两项的间隙由**容器**负责
（`Column(verticalArrangement = Arrangement.spacedBy(...))` 或分隔线），子项**不再加垂直外边距**。
Compose 的 `padding` 不像 CSS 相邻 margin 那样折叠，两处都给就会相加 ——
卡片自己 `padding(vertical = cardListGap)`（每侧 4dp）时相邻会叠成 8dp，这正是「上下两行空隙太大」的根源。
带底色卡片列表的容器间距用 `WwSize.cardGap`（6dp）。

> ⚠️ 早前的「嵌套链」（字段行 10 → 卡片 11 → 内容区 12 → 外壳 15）**已废除**（用户 2026-10-12）：
> 它被当成「框内留白」用，行里的文字离框边快一个字高；同一批还把「字号 + 4」错当成**每侧**值
> （功能框 20dp/侧、模型胶囊 8dp/侧），框被撑得老高。

| 用途 | API | 值 |
|---|---|---|
| **框内每侧**（卡片 / 行 / 带子 / 功能框） | `WwSize.boxVerticalPadding` · `WwPad.card` · `WwPad.featureBox` | 2sp（直接用 sp，不随字号缩放） |
| 嵌套层级每级递增 | `WwSize.verticalLevelStep` | 每侧 **2sp**（相邻层级总高差 4sp） |
| 功能框外面那圈**框** | `WwPad.featureFrame` | `featureBox` + 2sp = 4sp（外框总高比功能框 +4） |
| **框与框之间**、字段之间 | `WwSize.cardListGap` · `WwPad.fieldGap` | 4dp |
| **带底色卡片**列表相邻卡片（由容器负责） | `WwSize.cardGap`（容器 `spacedBy`，卡片自身不加外边距） | 6dp |
| 弹窗内容区上下、分隔线上下 | `WwPad.content` | 4dp |
| 弹窗外壳（面板 ↔ 可视区） | `WwPad.shell` | `content` + 每级 2sp = **6sp** |
| **弹窗面板（底板）↔ 内部最近框**（顶栏上 / 内容·底栏下） | `WwPad.dialogInset` | 3sp |
| 列表 / 树可点行、下拉里的行 | `WwSize.listRowVerticalPadding` | 4dp |
| 空态 / 占位提示 | `WwSize.emptyStateVerticalPadding` | 32dp |
| 侧边栏功能导航格 | `WwSize.navCellVerticalPadding` | 6dp |
| **侧边栏功能格里的图标** | `WwSize.navIconSize` | = 该格标签的字号本身（**1:1**，随「界面字体大小」一起变；用户 2026-10-13） |
| **滑杆盒高** | `WwSize.sliderHeight` | 16dp（贴紧内容；原来的 24dp 有一半被当成「组与组的距离」） |
| 紧凑框（标签 / 输入 / 选择 / 搜索 / 按钮） | `WwSize.compactFrameVerticalPadding` · `formFieldVerticalPadding` · `searchFieldVerticalPadding` · `buttonVerticalPadding` | 每侧固定 **1dp**，不设单行最小高度 |
| 紧凑标签 | `WwSize.compactTagMinHeight` / `compactTagVerticalPadding` | 不设固定最小高度 / 每侧 1dp |
| **框内文字行高** | `WwSize.compactTextLineHeight` | 提示字号 + 3sp（默认 15 时 16sp） |
| 正文段落行高 | `WwType.bodyLineHeight` / `WwType.titleLineHeight` | 正文 +5sp / 标题 +7sp（20sp / 24sp） |
| 开关图形缩放 | `WW_SWITCH_SCALE`（`WwFormControls`） | 0.85（布局仍是 M3 的 52×32） |

- **只作用于垂直内边距**；水平内边距照各自规格。
- 多行编辑器的自定义最小高度作为固定基准值保留，字号增大时由文字自然撑高。
- 命名对应口径、不写数字；改基准时一处生效（`WwSize` / `WwPad`）。
- **裸 `Text` 的默认样式**（`WwTheme` 里的 `ProvideTextStyle`）= **正文档 + 紧凑行高**：
  任何没显式给 `lineHeight` 的文字都拿这一档，不再继承 M3 `bodyLarge` 的 24sp（见 §十.2）。

### ⚠️ 待定：正文用了「小字档」的行高（用户 2026-10-13 提「有点挤压感」）

**现象**：`WwType.body`（15sp，正文档）配上 `WwSize.compactTextLineHeight`（= 提示字号 +3 = **16sp**），
比例只有 **1.07em**，CJK 字身几乎贴上下沿。Web 版没有这一档：

| 档 | 原生 | 比例 | Web 对应 |
|---|---|---|---|
| 提示 / 小字 | `hint` 13sp × 16sp | 1.23 | `chat-text-xs` 12/16 = 1.33 |
| **正文（错的档）** | `body` 15sp × 16sp | **1.07 ← 挤** | 无 |
| 正文（正确档） | `body` 15sp × `bodyLineHeight` 20sp | 1.33 | `chat-text-sm` 14/20 = 1.43 |

**规模**：全项目 `fontSize = WwType.body` 共 89 处，其中 **43 处**配了紧凑行高；
正确的 `WwType.bodyLineHeight` 只用了 **4 处**（`StreamingMarkdown` 等）。

**已改**：`MessageBubble` 的**用户气泡正文** → `bodyLineHeight`（原来同一屏里用户气泡 1.07em、
助手气泡 markdown 1.33em，一边挤一边松）。

**未定**（用户 2026-10-13：「小字的那个，记在文档里，我之后看效果再决定」）：其余 42 处。
- 建议只改**会折行的正文**（卡片说明 / 警告正文 / 总结正文 / 条目描述），
  单行框内标签保持紧凑 —— 否则每个单行框各高 4sp，会回到用户 2026-10-12 报的「框内边距大」；
- 不建议把 `WwTheme` 的默认 `lineHeight` 直接换成 `smLineHeight`：那正是当年「框被撑高」的根因（§十.2）。

---

## 四、按钮与标签的统一分类（用户 2026-10-11 定案）

**目标**：全应用的"带文字的框/按钮"按视觉功能统一，同一行相邻控件高度一致，字号增大时边框随文字行高自然增高但上下留白保持紧凑。

### 1. 紧凑标签/小按钮（`WwTag`）

**凡「框里装着文字」的小标签、徽标、状态药丸、筛选 chip、控制行里的小操作按钮，都必须是 `ui/components/WwTag.kt`**，不许再自己写 `Surface { Text(padding(h, v)) }`。

统一规格（`WwTag` 内部已固定）：

| 项 | 值 |
|---|---|
| 文字 | `WwType.hint`（13sp，随界面字体大小缩放） |
| 内边距 | 左右 6dp、上下每侧固定 1dp（`WwSize.compactTagVerticalPadding`） |
| 最小高度 | 不设固定值，按紧凑文字行高自然布局（`WwSize.compactTagMinHeight`） |
| 行高 | `WwSize.compactTextLineHeight`（数值 token，默认 15 时 16sp） |
| 颜色 / 圆角 / 描边 | 按语义由调用方传（`background` / `contentColor` / `shape` / `border`） |

**使用场景**：
- **不可点击标签**：状态显示、元数据、时间线标签、只读信息标签
- **可点击小按钮**：控制行里的操作（传 `onClick`）
  - 例：时间线管理的"切换至此""拒绝""同意并写入"
  - 例：各功能面板的"全选""全不选""新建"
- **带图标的小按钮**：传 `leadingIcon` + `onClick`，高度与旁边的标签一模一样
  - 例："AI 操作说明""新建分支""提示词讲解"

**为什么必须收成一处**：此前 5×1、6×2、8×1、8×4、10×4 各写各的 —— **同一行里的标签高度不齐、文字与框边的距离也不一致**（用户 2026-10-11 连续两轮反馈）。

**❌ 禁止混用 Material3 按钮**：同一行里不要用 `TextButton` / `OutlinedButton` / `Button` —— 它们自带 40dp 最小高，与字号绑定高度的 `WwTag` 摆一起必然高低不齐。

> **输入栏那一排**（分支 chip、Agent 胶囊、模型胶囊）：每侧走 `WwSize.boxVerticalPadding`（2sp，框总高 = 文字行 + 4），
> 不走 `WwTag` —— 它们对标的是同排的图标按钮，不是标签。
> 同排的图标按钮是**透明** 32dp 点击区、图标本身只有 16dp，所以胶囊矮一截也不显得「高低不齐」。

### 2. 表单操作按钮（较宽 padding）

用于**弹窗/表单底部的主次操作按钮**，比 `WwTag` 的横向 padding 更宽（12dp vs 6dp）：

#### **主操作按钮**（`WwPrimaryButton`）
- 规格：15sp 文字（`WwType.body`，默认正文）+ primary 背景 + 12dp 横向 padding + 每侧固定 1dp 纵向 padding
- 使用：弹窗底部的"保存""确认""提交"

#### **次操作按钮**（`WwSecondaryButton`）
- 规格：15sp 文字（`WwType.body`，默认正文）+ inset 背景/描边 + 12dp 横向 padding + 每侧固定 1dp 纵向 padding
- 使用：弹窗底部的"取消""关闭"

### 3. 输入/选择/搜索控件

已有统一规格（垂直走 §五 的紧凑口径，文字与占位都取默认正文 `WwType.body`）：

| 控件 | 组件 | 规格 |
|---|---|---|
| 单行/多行文本输入 | `WwTextField` | label 在框上方（15sp）+ 框内水平 10dp / 垂直每侧 1dp + 单行不设固定高（随文字行高） |
| 下拉选择 | `WwSelectField` | 同 `WwTextField`（label 也走 15sp） |
| 搜索框 | `SearchField` | 同 `WwTextField` + 放大镜图标 |

### 4. 纯图标按钮

**不属于"带文字的框"，不纳入本次改造**：
- `IconButton`（Material3）
- `TransparentIconButton`（自定义，输入栏工具条）
- 保持现状，不修改

### 5. 信息展示卡片

**有结构性留白，不是紧凑文字框**：
- 单行键值对显示框（`LabelBox` 等）：横向 8dp + 纵向 `compactFrameVerticalPadding`
- 多行提示卡片（`InfoBlock` / 警告块）：按 `WwPad` 层级保留结构性间距

---

## 四（原）、嵌套容器的垂直内边距（`WwPad`）重新编号为五

| 选项 | 实现 | 备注 |
|---|---|---|
| 黑体（默认） | `FontFamily.Default` | 平台默认无衬线（Windows = Segoe UI + 微软雅黑） |
| 宋体 | `FontFamily.Serif` | 平台衬线；Windows 拉丁回退 Times New Roman、中文宋体 |

- 设置项：设置 →「文本设置 → 界面字体」（`PreferencesPanels.DisplaySettingsPanel`）。
- 存储：`StorageKeys.UI_FONT_FAMILY`（kv 原串 `sans` / `serif`，默认 `sans`）——
  **原生独有、不进 v5 存档也不进 WebDAV**；枚举是 `ui/theme/WwType.kt` 的 `UiFontFamily`。
- 注入：`App.kt` → `WwTheme(uiFontFamily = …)` →
  ① `wwTypography(family)` 逐档把 family 写进 M3 `Typography`（覆盖所有
  `style = MaterialTheme.typography.…` 的地方）；
  ② `LocalWwFontFamily` → `WwFont.family`（给自绘 `TextStyle` 用）。
- 自绘 `TextStyle` 里**不许写 `FontFamily.Default`**（会盖掉宋体）：写 `WwFont.family`。

> 为什么逐档 copy：`style` 优先于 `LocalTextStyle`，只提供后者的话，凡是指定了
> `MaterialTheme.typography.xxx` 的文字都会退回黑体。

## 六、界面字体族（黑体 / 宋体）

| 文件 | 改动 |
|---|---|
| `ui/theme/WwType.kt` | **新增**：三档字号 + `WwPad` 嵌套间距；字号挂 `chatFontSize` |
| `ui/components/InfoHint.kt` | **重写**：上标图标、屏幕内夹紧、全局单开一个、13sp 浮层 |
| `ui/settings/FeatureDialog.kt` | 标题 17sp 加粗；副标题收成标题旁 ⓘ；内容区 `WwPad.content`、外壳 `WwPad.shell`（§五） |
| `ui/components/WwFormControls.kt` | label / 框内文字 15sp、字段 `hint` 收成 InfoHint、按钮 15sp；框内文字/占位不变 |
| 窗口功能：`WindowFunctionSheet` / `WindowApiParamsPanel` / `SummaryPanel` / `SearchPanel` / `SessionActionsPanel` / `RegexPanel` | 字号三档 + 卡片/字段行垂直内边距 + 常驻帮助行收进 InfoHint |
| 其它：`PreferencesPanels` / `SettingsPanels` / `ProviderPanel` / `BackupSyncDialog` / `PromptLibraryDialog` / `ThemeDialog` / `ModelSelectorDialog` / `AiVisibilityDialog` / `PromptInjectionGuideDialog` / `WorldPanel` / `CurrentWindowOverview` / `SettingsHub` | 同上 |
| `ui/components/WwTag.kt` | **新增**：标签统一规格 + `leadingIcon`（同行小按钮） |
| `TimelineManagerDialog` | 全部标签 / 徽标改 `WwTag`；控制栏三个入口同为 20dp，不再 40dp + 26dp 混排 |
| `DualBoxPromptDialog` / `SettingsPanels` / `PromptLibraryDialog` | 残留的手写标签 chip 改 `WwTag` |
| `ChatInputBar` | 模型胶囊与下拉里的模型名补上 `FontFamily.Monospace`（与全项目的模型名一致） |
| `SummaryPanel` / `SidebarSheet` | 清掉最后两处写死的 `sp`（行高 / emoji） |

### 已知例外

- `PromptInjectionGuideDialog` 的步骤说明、`SessionActionsPanel` 的导出格式说明：
  本体就是「讲解/图例」的内容，保留为 13sp 文字而非收进图标。

---

## 七、落地位置（本次覆盖）

- [x] 能输入内容的框里的**占位文字没被动**（WwTextField/SearchField 已接 compact lineHeight）
- [x] 其它注释 / 提示都收起来了（图标 + 浮层）
- [x] 弹窗标题是 17sp 加粗；默认文字 15sp；框外提示 13sp（且随界面字体大小缩放）
- [x] 每个外层比相邻内层高 4sp（WwPad 层级，每级每侧 +2sp；弹窗外壳见 §五）
- [x] 输入框高度没有被 2dp 规则影响
- [x] 浮层不飘出屏幕外；同一面板不同时多个浮层
- [x] 浮层不超出**宿主弹窗面板**（窄 / 矮弹窗时跟着缩，必要时内部滚动）
- [x] 一级弹窗的副标题收成标题旁 ⓘ（数据型副标题除外）
- [x] 注释图标位置统一；标题分区处理后变矮
- [x] **同一行里的标签 / 小按钮高度一致**（标签走 `WwTag`，同行不混 M3 Button）
- [x] 标签内文字与框边的距离一致（左右 6dp、上下每侧 1sp）
- [x] 「界面字体」切宋体后全应用一起变（只有等宽位不变）
- [x] 弹窗里没接全局的垂直 / 整圈内边距已接线（口径与豁免清单见 §八）
- [x] 标签括号说明 / 成对注释 / 页脚说明行都收成 ⓘ（范围与例外见 §二）
- [x] 带文字的框（功能框 / 输入栏胶囊）留白 = 每侧 2sp（「+4」是上下合计，不是每侧）

### 现状核对（2026-10-12）

- `MaterialTheme.typography.*` 在 `ui/` 下**已归零**（只剩 `WwTheme.kt` 的解释性注释）。
- 写死的字号 `N.sp` 在 `ui/` 下**已归零**（`WwType.kt` 注释里的举例与 §一 豁免 #2 的徽章除外）。
- 弹窗里的垂直 / 整圈内边距已按 §八 接线；剩下的字面量只有这四类：§八 的豁免、
  **非弹窗表面**（输入栏 / 聊天气泡 / 侧边栏 / 图片格）、组件自有规格（`InfoHint` 浮层 10×8、
  `WwDropdownItem` 12×9、输入栏里与 32dp 按钮同排的胶囊）、微调与 `spacedBy` 两类。
- 「新建请求头」按钮已合并（`SettingsPanels` 与 `ProviderPanel` 都是 `WwTag(leadingIcon = Plus)`）。
- `WwSize.compactTextLineHeight` 已是**数值 token**（不再按字号比例算）。

---

## 八、弹窗统一与豁免清单（用户 2026-10-12 定案）

**所有弹窗只有一套规格，不做「一个弹窗一套」的局部豁免**：垂直尺寸取 §五 的层级 token
（`WwPad.shell / content / card / fieldRow`、`WwSize.*`），字号取 §一 的三档，注释取 §二 的 ⓘ 浮层；
「框里装文字」的控件（标签 / 小按钮 / 分段选择 / 下拉触发器）一律走 §四 的 `WwTag`。

### 只允许这四类例外

1. **世界观弹窗**（`WorldPanel`）：树 / 文档的密集列表，保留自身行高。
2. **知识库弹窗**（`SettingsPanels` 的知识库分区）：同上。
3. **本来就留大白空的弹窗**（如引导页 `TimelineGuideDialog` 的 32 / 48）：留白是设计的一部分，不压紧。
4. **多行占位的输入框**：占位 / 内容是 N 行的框，保留各自的固定 `minHeight`（56 / 64 / 72 / 88 / 112 /
   160 / 180 / 220 / 300 / 320dp），字号变大由文字撑高，**不套单行框的紧凑规则**。

> 除这四类之外，弹窗里出现 `padding(vertical = N.dp)` / `padding(N.dp)` 的都算**没接全局**。
>
> **非弹窗表面**（聊天气泡 / 输入栏 / 侧边栏 / 图片格 / 聊天区空态）**不在本节范围内**：
> 它们有自己的固定约定（含气泡里元信息与正文之间的那条横线），见 **§九** ——
> 别用弹窗口径去改它们，也别把 §九 的值当成「没接全局」又改回去。

### 接线口径（2026-10-12 更新：只剩两个口径）

| 位置 | token | 默认 15 时 |
|---|---|---|
| 卡片 / 内框 / 带子的**框内每侧留白** | `WwPad.card` | 2sp |
| 字段之间、相邻卡片 / 条目、网格行 | `WwSize.cardListGap` · `WwPad.fieldGap` | 4dp |
| 弹窗正文滚动区 | `WwPad.content` | 4dp |
| 列表 / 树可点行、下拉里的行（分组标题、模型行） | `WwSize.listRowVerticalPadding` | 4dp |
| 空态提示 | `WwSize.emptyStateVerticalPadding` | 32dp |
| 分区标题 → 其内容 | `WwSize.cardListGap` | 4dp |
| 列 / 行里的垂直 `Arrangement.spacedBy(N.dp)` | `WwSize.cardListGap` | 4dp |
| 图标基线微调（如 `padding(top = 2.dp)`） | —— 不是框的垂直内边距，保留 | 2dp |
| 水平 `spacedBy` / 水平内边距 | —— **横向不管**（用户 2026-10-12） | —— |

### 本轮顺手统一的两处「带文字的框」

- `ModelSelectorDialog` 的「收藏」开关、`WindowApiParamsPanel` 的分段按钮：
  原来各自自绘 `Surface/Box + 10×8 / 12×8`，现统一成 `WwTag`（与设置里的分段选择同规格）。

---

## 九、非弹窗表面的版式约定（用户 2026-10-12 补录）

这些**不是弹窗**，所以**不套 §八「弹窗只有一套规格」**；但它们**共用 §一（三档）与 §五（行高口径）**，
并且仍然遵守一条硬规则：**任何单行文字都必须显式给行高**（`WwSize.compactTextLineHeight`，见 §十.2）。
下面把各表面的**现状值**钉死 —— 下次改版式先看这里，别把这些当成「没接全局」又改一遍。

### 1. 聊天气泡（`chat/MessageBubble.kt`）

| 项 | 值 |
|---|---|
| 气泡框内留白 | `padding(horizontal = 12.dp, vertical = 10.dp)`（横向 12 / 纵向 10，**写死**） |
| 最大宽 / 圆角 | `widthIn(max = 640.dp)` / `RoundedCornerShape(12.dp)` |
| 元信息行 | `WwType.hint`(13sp)；头像占位 = `(avatarSize / 2).sp` |
| **元信息 ↔ 正文** | **一条横线**：`HorizontalDivider(color = 元信息色 × 0.25)`，上下各 `WwSize.cardListGap`(4dp)；
  触发条件与元信息行一致（有头像或至少一项元数据才画） |
| **正文 ↔ 动作行** | 同上**另一条横线**（`secondary × 0.15`），上下也各 `cardListGap`（用户 2026-10-12：「气泡的底部横线也加上」）；
  没有动作行时（流式中）这一块连横线一起不画 |
| 动作按钮 | 一律 `WwIconButton` + **Tabler 线条图标**（不再用 emoji / `◀ ▶` 字符）：上一/下一分支 `ChevronLeft/Right`、
  重新生成 `Refresh`、复制 `Copy`（点后 1.2s 变 `Check` 主色）、编辑 `Pencil`、删除 `Trash`（error 色）；
  版本序号 `1/2` 走 `hint` + 等宽 |
| 编辑框（仅用户气泡） | 三个 `WwTag`：**取消** · **保存**（只写正文、不重发，`ChatViewModel.editMessage`）·
  **保存并发送**（写回 + 删后续 + 重新生成，`editAndResend`）—— 用户 2026-10-12 加 |
| 徽章行 / 图片行之后 | `Spacer(Modifier.height(WwSize.cardListGap))` |
| 用户消息正文 | `WwType.body` + `WwSize.compactTextLineHeight` |
| 助手消息正文 | `StreamingMarkdown` → `WwType.body` + **`WwType.bodyLineHeight`**（多行段落走正文行高） |
| 操作行 | `WwType.hint`；其上 `Spacer(6.dp)`；可点文字 `padding(horizontal = 4.dp)` |

> ⚠️ 气泡的纵向留白（10dp）**有意不接 `boxVerticalPadding`(2sp)**：气泡里是 markdown 多行块，
> 留白太紧会与段落行距抢空间。新加气泡内的元素请复用上面这套，不要引入新的数值层级。

### 2. 输入栏（`chat/ChatInputBar.kt`）

- 外壳：`padding(horizontal = 12.dp, vertical = 8.dp)`（贴窗口底，纵向 8 写死）。
- 输入框「框内」：`WwSize.boxVerticalPadding`（**每侧 2sp**，已接）、行间 `WwSize.cardListGap`。
- 输入区文字：`WwType.body` + `WwType.bodyLineHeight`；其内部 `padding(horizontal = 6.dp, vertical = 6.dp)`。
- **草稿即时保持**（用户 2026-10-12）：正文**不由输入栏内部 `remember` 持有**，
  而是 `ChatUiState.draft` + `ChatViewModel.setDraft`，**逐键**写 kv（`guanjie_draft_<sessionId>`，
  空串 = 删行）；`open(session)` 读回该窗口的草稿，消息真正落库后 `clearInputDraft`。
  ⚠️ 新加输入框时不要再在组件内部自存正文。
- **底部图标行**：顶 `WwSize.cardListGap`；图标按钮 = **24dp 紧凑点击区**（图标 16dp）——
  M3 `IconButton` 默认 32dp 会让图标离框边多空 8dp（用户 2026-10-12 报「底部图标离框边很大」）。
- 模型胶囊：`padding(horizontal = 10.dp, vertical = WwSize.boxVerticalPadding)`，文字 `WwType.hint` + 等宽。
- 分支行 chip / 工具按钮：横向 8–10dp + `vertical = boxVerticalPadding / compactFrameVerticalPadding`。

### 3. 侧边栏（`sidebar/SidebarSheet.kt`）

- 列表容器 `padding(horizontal = 8.dp, vertical = 8.dp)`；分组之间 `top = 10.dp`。
- **窗口行（标题框）**：`padding(horizontal = 10.dp, vertical = WwSize.boxVerticalPadding)`；
  头像 emoji 与窗口标题**必须显式给 `WwSize.compactTextLineHeight`**（否则继承 M3 `bodyLarge`
  的 24sp 把行撑胖约 8dp —— §十.2 的实例）。
- 功能入口格 `padding(horizontal = 12.dp, vertical = WwSize.boxVerticalPadding)`；
  底部导航格 `vertical = WwSize.navCellVerticalPadding`（6dp）。
- 搜索框：外层 `start/end 12 · top 10 · bottom 6`，内层 `horizontal 10, vertical = searchFieldVerticalPadding`。
- 分组表头：`horizontal 8, vertical = compactFrameVerticalPadding`。

### 4. 其它

- **聊天区空态**（`ChatScreen`）：`padding(24.dp)` 居中提示 —— 留白是设计的一部分。
- **图片格**（`image/StoredImageUi.kt`）：`padding(bottom = 4.dp)`、空态 `padding(24.dp)`；
  图片本身的尺寸是存储 / 显示规格，不接三档。
- **markdown 正文**（`markdown/StreamingMarkdown.kt`）：`WwType.body` + `WwType.bodyLineHeight`。
- **世界观树 / 知识库网格 / 引导页**：见 §八 的四类例外。

---

## 十、错误原因档案（用户 2026-10-12：**每次修复的根因都要记在这里**）

> 目的：同一类现象反复出现时，先查这里 —— 它们大多不是「某个值写错了」，
> 而是**规则被误用 / 组件自身把容器撑高 / 字体度量差异**这三类根因之一。

### 1. 留白口径被误用

- **现象**：框里的文字离框边快一个字高；功能框 / 模型胶囊「老高」；弹窗顶栏、标题到分区都很空。
- **根因**：把两件事混在一起 ——
  (a) 旧的**嵌套链**（字段行 10 → 卡片 11 → 内容区 12 → 外壳 15）被当成「框内留白」用；
  (b) 「**字号 + 4**」里的 **4 被当成每侧值**（于是功能框 20dp/侧、胶囊 8dp/侧）。
- **定案**：只剩两个口径 —— **框内每侧 2sp**（`WwSize.boxVerticalPadding`）、**框与框之间 4dp**
  （`WwSize.cardListGap`）；外壳 = 内容区 + 固定 3。（§五）

### 2. 行高没显式给（**最高频的一个**）

- **现象**：「行距太大」「框的内边距太大」「侧边栏窗口标题框太胖」「开关行很高」——
  同一类现象反复出现在**不同**的弹窗 / 列表里。
- **根因**：M3 的 `MaterialTheme` 会 `ProvideTextStyle(bodyLarge)`，而 `bodyLarge` 是
  **16sp / 24sp 行高**。裸 `Text`（不传 `style`，也不传 `lineHeight`）就继承它；
  **即使把 `fontSize` 改成 14sp，`lineHeight` 仍是 24sp（1.7em）** —— 单行标签所在的框
  被凭空撑高约 8dp。
- **修法**：`WwTheme` 里统一 `ProvideTextStyle(正文档 + 紧凑行高)`（治本，一次覆盖全应用）；
  单个文字仍可显式给 `WwSize.compactTextLineHeight`（框内）/ `WwType.bodyLineHeight`（多行正文）。
- **连带**：M3 的 `Switch` 自带 **48dp 最小交互尺寸**，裸放一个开关就把行撑到 48dp
  → 统一走 `WwSwitch`（放开最小尺寸 + 图形缩 0.85）。

### 3. 自绘组件的盒子高 = 组间距

- **现象**：偏好弹窗里「标签 + 滑杆」算一组，组与组之间特别空。
- **根因**：滑杆的盒子是 24dp、可见轨道只有 4dp 并居中 —— 轨道上下各空 10dp，
  被当成「组与组之间的距离」（组内反而没留白，一组读不出来）。
- **修法**：`WwSize.sliderHeight` = 16dp（贴紧内容）；组内给 `cardListGap`（4dp），
  组间只剩盒子半高（≈ 8dp）—— 组内 < 组间，分组才成立。（§五）

### 4. 西文比汉字显小（字体度量，不是字号写错）

- **现象**：`Skill 技能库` 里 `Skill` 比旁边汉字小一圈。
- **根因**：同一个 sp 下汉字占满**字身**（≈ 1.0em），拉丁字母的可见高度只有大写高度
  （≈ 0.72em）、小写更矮。
- **修法**：标题走 `mixedTitle(...)`（`WwType.kt`）—— 西文段 × `TITLE_LATIN_SCALE`(1.15)，
  汉字段保持基准；纯汉字 / 纯西文原样返回。

### 5. 写死的上限 / 默认值把自适应变成「永远最大」

- **现象 1**：弹窗很宽时，注释浮层「换行太早，远远没到弹窗边缘」。
  **根因**：浮层最大宽写死 `min(260dp, 面板宽 − 16dp)`。**修法**：直接用面板宽 − 16dp。
- **现象 2**：备份弹窗（WebDAV 短表单）默认占满一屏。
  **根因**：`fixedHeight = true` + 内容列 `fillMaxHeight()`，高度被钉死在 90%。
  **修法**：`fixedHeight = false`，交给 `heightIn(max = 0.9×可视区)` —— 短则随内容，长则封顶内滚。

### 6. 同一组件在不同面板用不同「档」

- **现象**：窗口 API 参数面板里标签（14sp）比框里的值（12sp）还大。
- **根因**：输入框内文字过去一律取 `hint`(12sp)，而标签取 `body`(14sp)。
- **修法**：输入框内文字改用 `WwType.body`（§一「输入框里的文字」）；同一面板内所有文字同档。
