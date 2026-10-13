# T13 · Markdown 流式渲染(mikepenz 库 + 追加式)

| | |
|---|---|
| 任务号 | T13 |
| 日期 | 2026-10-08 |
| 状态 | ✅ 完工 |
| 阶段 | 阶段 5 · 界面补全 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T13 |

## 目标与可见效果

- **目标**:把 T02 的自建极简渲染器换成真正的 Markdown 库(决策 #2 定案),并保留 PLAN §7.3 的**流式**要求 —— 不整段重排,长回复不掉帧。
- **可见效果**:助手回复按完整 GFM 渲染(标题/列表/表格/任务列表/删除线/自动链接/GitHub 警告/代码块),流式逐字出现时只重解析不稳定尾部。
- **验收标准**:只重解析末块;未闭合代码块/行内标记容错;正则替换在**渲染前**作用于纯文本。

## 决策 #2 定案(2026-10-08)

**采用 `com.mikepenz:multiplatform-markdown-renderer`(+ `-m3`)0.45.0。**

理由:Compose 原生、KMP 双端可用(符合 PLAN §2.4 建议);且 **0.42.0 起自带 `StreamingMarkdownState`**——
追加式流式,每次 `append` 只重解析文档的「不稳定尾部」,而不是整段重排,正好对上 §7.3 的验收。
0.45.0 面向 Compose Multiplatform 1.12.0 / Kotlin 2.4.x,与本仓(Compose MP 1.12.1 / Kotlin 2.4.20)同线,
**已实测 `:shared:compileKotlinDesktop` 通过**。

## 改动

| 文件 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | **新增版本** `markdownRenderer = "0.45.0"` 与坐标 `compose-markdown` / `compose-markdown-m3`(核心 + M3 主题模块) |
| `shared/build.gradle.kts` | commonMain **新增依赖** `libs.compose.markdown` + `libs.compose.markdown.m3` |
| `shared/.../ui/markdown/StreamingMarkdown.kt` | **重写**。改用 `rememberStreamingMarkdownState()` + `Markdown(streamingMarkdownState = …)`;正文先过 `preRenderMarkdown`,只把「渲染文本」的新增后缀喂给库(只重解析尾部);预处理若造成非前缀变化则重建状态整段重解析一次;纯函数 `streamingDelta` |
| `shared/.../ui/markdown/MarkdownPreRender.kt` | **新增**。渲染前**纯文本**预处理:`preRenderMarkdown` = 先应用用户正则(`OUTPUT` scope)→ 再 `markToolStatusLines`(把「整块只有 `[工具…]`」的块包成行内代码,等价 Web 的 `<p><code data-tool-status>`);纯函数、幂等 |
| `shared/.../ui/markdown/StreamCaret.kt` | **新增**。通过库的 `MarkdownAnnotator` + `MarkdownInlineContent` 在渲染期注入行内闪烁光标,不把光标写进正文、不破坏 append-only 契约;颜色取当前主题 `m1-500`,周期 800ms 硬切闪烁 |
| `shared/.../ui/chat/MessageBubble.kt` | 调用处加 `resetKey = message.id`(流式状态重建键) |
| `shared/src/commonTest/.../ui/markdown/StreamingDeltaTest.kt` | **新增 4 用例**。追加式增量(`streamingDelta`):首块整段、增长只取后缀、无增长空串、非前缀(变短/替换)返回 null |
| `shared/src/commonTest/.../ui/markdown/MarkdownPreRenderTest.kt` | **新增 11 用例**。工具状态行(独立块/多行块/行内/已包裹/尖括号/无标记快速返回)+ 正则钩子(output 生效、input 不生效、disabled 忽略、无规则等价) |
| `shared/src/commonTest/.../ui/markdown/StreamCaretMarkingTest.kt` | **新增 4 用例**。基于库的真实 GFM AST 验证普通段落、尾随空白、旧段落排除、未闭合行内标记均只有一个真实光标落点 |
| `shared/src/desktopTest/.../ui/markdown/StreamingMarkdownRenderTest.kt` | **新增 1 用例**。离屏(`ImageComposeScene`)渲染 GFM 内容,断言渲染出可见正文(暗像素 > 100) |
| `shared/src/desktopTest/.../ui/markdown/StreamingMarkdownEdgeAndPerformanceTest.kt` | **新增 2 用例**。未闭合围栏/行内标记离屏容错;360 段、超过 20K 字符的追加式解析 + 渲染压测,实测该测试约 2.6s |
| ~~`shared/src/commonTest/.../ui/markdown/SplitBlocksTest.kt`~~ | **删除**。自建切块逻辑不再需要(库内部负责) |

## 心得

- **换库的最大收益是「流式状态」本身,其次才是 GFM 覆盖**。T02 的 `splitBlocks` 手写方案本质是在自造
  库的 `StreamingMarkdownState`;PLAN §7.3 想要的效果该库 0.42 起原生提供,自己维护切块/缓存反而更易错。
- **append-only 契约必须由调用方保证**:库的流式状态不提供「重置」,只 `append`。所以本仓把
  `message.id` 当 `resetKey` 传进去,`key(resetKey)` 变化即重建 —— 这解决了「换成另一条消息」的场景。
- **同长度替换检测不到**,但业务上不会发生(同一消息内容只增长);真发生时靠 `resetKey` 兜底。写测试时
  一开始把「同长度替换」也当非追加(期望 null),被实现纠正 —— 长度法本就看不出等长替换。
- **离屏渲染要喂两帧**:`ImageComposeScene.render()` 第一帧提交组合与 `LaunchedEffect`,
  第二帧才拿到 `append` 后的内容(冒烟测试里 `render()` 调两次)。
- **导入包名易错**:`Markdown` / `markdownColor` / `markdownTypography` 在 `com.mikepenz.markdown.m3`,
  而 `rememberStreamingMarkdownState` 在 `com.mikepenz.markdown.model`(不是 `.m3.streaming`)。
- **渲染前文本变换与库的「只追加」是一对矛盾**:工具状态行/正则都可能改动**已渲染部分**(如括号刚刚闭合),
  因此不能用「长度」判断增量(只对变短返回 null 不够,同长度替换漏判),改为**前缀匹配**:
  `rendered.startsWith(committed)` 才 append 后缀,否则重建整段。这也是把 `streamingDelta`
  从「长度版」改成「前缀版」的原因(同长度替换也能识别了)。

## 要点

- **坐标固定**:`com.mikepenz:multiplatform-markdown-renderer:0.45.0` + `…-m3:0.45.0`。
  升级时注意它自带的 Compose/Kotlin 基线(0.45.0 = Compose MP 1.12.0 / Kotlin 2.4.x),与本仓同线才升。
- **导入路径**:`com.mikepenz.markdown.m3.Markdown`、`com.mikepenz.markdown.model.rememberStreamingMarkdownState`。
- **`StreamingMarkdown` 契约**:同一 `resetKey` 下 `content` **只允许追加**;消息切换/整体替换必须换 `resetKey`
  (本仓传 `message.id`)。这层由 `streamingDelta` 实现,已有单测。
- **渲染前预处理顺序**:正则(`OUTPUT`)→ 工具状态行标记;两者都作用于**解析前的纯文本**,不是解析结果。
- **预处理与追加式流式的调和**:若预处理使「渲染文本」不再是前缀追加(如某条 `[工具…]` 块刚补全),
  `streamingDelta` 返回 null → 重建流式状态整段重解析一次。这只发生在边界,不是每帧。
- **流式光标不进入正文**:`streaming = true` 时由 `MarkdownAnnotator` 在真实末尾 AST 节点后追加 `InlineTextContent`;因此光标移动不触发流式状态重建,也不破坏只追加语义。
- **只重解析尾部**:库内部把文档切成稳定 AST + 不稳定尾部;所以我们只需保证「只 append 新增后缀」,不必自己做块缓存。
- **M3 主题自动跟随**:`Markdown` 的 M3 模块默认吃 `MaterialTheme`(双轨①),所以主题色/暗色无需额外接线。
- **依赖体积**:库自带 JetBrains Markdown 解析器;图片加载(Coil)与语法高亮(-code 模块)是**可选**依赖,本任务未引入。

## 验收结果

- [x] `:shared:compileKotlinDesktop` 通过,零警告(依赖与 Kotlin 2.4.20 / Compose MP 1.12.1 兼容)。
- [x] `:shared:desktopTest --rerun-tasks` 全绿:**180 用例 / 176 通过 / 4 跳过(在线 smoke) / 0 失败**,编译阶段无新增警告。
- [x] 只重解析末块:由库的 `StreamingMarkdownState` 保证(追加式),我们只喂新增后缀(`streamingDelta`)。
- [x] 正则替换在渲染前作用于纯文本:`preRenderMarkdown` 接 T08 `applyRegexRules`(`OUTPUT` scope),有单测。
- [x] 工具状态行样式:整块只有 `[工具…]` 的块包成行内代码(等价 Web 的 `data-tool-status`),有单测。
- [x] 流式光标:注释器 + 行内占位实现,不修改已提交正文;真实 GFM AST 边界测试 4/4。
- [x] 未闭合代码块/行内标记容错:离屏真实渲染测试验证正文仍可见。
- [x] 长回复增量压测:360 段、超过 20K 字符逐段 append 后离屏渲染;测试用例实测约 2.6s,低于 10s smoke 预算。

## 遗留问题 / 下一步

- **正则规则尚未从设置读入**:钩子([preRenderMarkdown])已就位并测过,但 `regexRules` 目前恒为空表 ——
  等 T14/T15 把会话/全局规则接进来即生效。
- **代码围栏内的光标未注入**:库的围栏代码块不经过行内 annotator;若必须与 Web 版完全一致,需自定义 `codeFence` 组件。普通段落、列表、行内标记均已接入。
- **硬换行瞬间的光标位置略有差异**:Compose 库把硬换行渲染为换行,Web 版先 `trimEnd`;只影响流式恰好停在硬换行上的瞬间。
- **更细的工具状态行样式未用组件级**:当前走「渲染前文本」方案(行内代码);若需背景/圆角等,再考虑 `MarkdownComponents`。
- **肉眼并排走查**:与 Web 版的观感差异(缩进/间距/代码块配色)待与 T15 一起收尾。

## 相关红线

- 仅 UI 渲染层;未改字段名 / 存储键 / 执行顺序 / 字符串字面量。
- 存档往返与存储往返测试仍通过(本任务不碰存储层)。
