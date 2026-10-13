# Handoff: 全应用紧凑文字框与卡片留白

## 目标

用户反馈调大「界面字体大小」后，部分文字标签外框会随文字增高，但边距仍显得接近半个字高；另一些框几乎没有变化。用户明确要求把这一问题扩展到**全应用其他界面**，不应只修时间线管理弹窗。

最终视觉目标：紧贴文字的框只比文字行高一点，字号增大时框随文字行高自然增高，但上下留白保持很小；同一行相邻标签/小按钮高度一致。不要因此压缩外层弹窗或破坏卡片/区块的结构性间距。

## 根因

当前 UI 并非都由 `WwTag` 绘制。存在多条互不统一的尺寸路径：

- `WwTag` 使用共享的紧凑 padding / 行高，因此部分标签已经变紧凑。
- 表单和搜索控件由 `WwTextField`、`WwSelectField`、`SearchField` 等独立绘制；它们曾有自己的 padding、行高和最小高度。
- 大量带边框卡片是 `Surface` + `Column` / `Row`，直接设置固定 `padding(8.dp)`、`padding(10.dp)` 等。
- Material3 `Button`、`OutlinedButton`、`TextButton`、`OutlinedTextField` 带有自己的默认最小高度、内容 padding 或行高，单改 `WwTag` 不会影响它们。
- Compose 的 `defaultMinSize`、最小触控布局、Switch / IconButton 等可能继续撑高所在行。`graphicsLayer { scaleX/scaleY }` 仅缩放绘制，不改变测量尺寸。

所以仅替换标签组件或仅改 token 不能解决全局问题；需要从共享组件开始，再逐屏查找绕开共享组件的实现并迁移或明确保留。

## 当前已存在的基础与部分改动

- `shared/src/commonMain/kotlin/com/watchworld/ui/theme/WwType.kt`
  - `WwSize` 已含 `compactFrameVerticalPadding`（1dp）、表单/搜索/按钮垂直 padding、紧凑文字行高等 token。
  - `WwPad` 用于嵌套容器层级；字段行基准 10dp，向外层级每侧增加 1sp（容器高度增加 2sp）。
- `shared/src/commonMain/kotlin/com/watchworld/ui/components/WwTag.kt`
  - 标签已用 `WwType.hint`、显式紧凑行高、固定 1dp 上下 padding，且无固定最小高度。
- `shared/src/commonMain/kotlin/com/watchworld/ui/components/WwFormControls.kt`
  - `WwTextField` 等共享表单控件已有部分紧凑尺寸处理；保留调用方明确传入的多行 `minHeight`。
- `shared/src/commonMain/kotlin/com/watchworld/ui/components/SearchField.kt`
  - 已接入紧凑行高和垂直 padding。
- `shared/src/commonMain/kotlin/com/watchworld/ui/timeline/TimelineManagerDialog.kt`
  - 用户指出该弹窗控制行/卡片没有普遍随共享标签规则变化。已对一部分 padding 做局部调整，但仍有 Material 按钮和各自的内部留白需要核对。
  - 本次继续把删除确认块、`InfoBlock` 上下 padding 收紧至共享 compact token，并减小其内部间隔；`:desktopApp:compileKotlin` 已通过。
- `memory/ui-typography-standard.md`
  - 是字号、紧凑框、标签、间距规则的现有规范。开始全局迁移前请阅读，并在规则调整时同步维护。

## 实施要求

1. **做全应用盘点，而非仅继续修当前弹窗。** 查找所有单行文字边框/标签、状态 chip、带框输入/选择/搜索控件、标题/提示小框及 Material3 按钮；重点找 `Surface` 包 `Text`、手写 `border/background/padding` 和默认 M3 控件。优先迁移共享组件和高复用控件，再处理逐页自定义实现。
2. **紧凑边框**统一遵循 `WwSize`：紧凑文字行高显式设定；上下 padding 使用固定的小值（当前 token 为 1dp）；单行文字框不靠固定最小高度撑高。不要只改 token 却遗漏自定义实现。
3. **复用正确的组件。** 语义上是标签/chip 的控件使用 `WwTag`；普通按钮仍是按钮，不要为消除 M3 默认高度而把所有交互按钮伪装成标签。必要时在共享按钮封装中明确紧凑尺寸，并检查各平台的点击/焦点行为。
4. **分清紧贴文字的边框与结构性容器。** 卡片和分区容器的留白按 `WwPad` 层级，不要将所有 `padding`、`spacedBy` 一律改成 1dp。横向 padding 不在本次目标内，除非用户明确要求。
5. **保留交互与内容布局。** 不移除必要的 Switch / IconButton 点击区域；若其最小触控尺寸撑高父行，应优先调整行布局/视觉边框范围，而不是缩小触控目标。多行编辑框由调用方显式指定的最小高度须保留。
6. **保持已确认行为。** 时间线管理弹窗最外层 `fillMaxHeight(0.92f)` 高度必须固定，只压缩内部行和卡片。其他窗口外壳也不能因紧凑控件改动而意外缩小。
7. **逐处核实是否真的生效。** 不能以“接入 token”作为完成标准；找出框高实际由谁决定（文字 lineHeight、内边距、最小尺寸、触控尺寸、父布局间隔），再选择修改处。避免虚报“全局统一”。
8. 修改相关规范文档，说明哪些是紧凑文字框、哪些是结构容器、哪些控件因可访问性或多行输入而保留较大尺寸。

## 建议执行顺序与验收

1. 阅读 `memory/ui-typography-standard.md`，查看当前工作树已有改动，勿覆盖用户/其他模型的未提交内容。
2. 用代码搜索盘点 Compose UI 中硬编码垂直 padding、`Surface` 自绘文本框、Material3 按钮/输入框；分批按高复用组件和页面迁移，避免盲目全局替换。
3. 对每种代表控件核对默认字号和增大字号两种情况下的测量高度：边框应跟随文字行高变化，上下留白仍紧凑；并确认点击、键盘、多行编辑和滚动未回归。
4. 运行 Kotlin 编译（至少 `.\gradlew :desktopApp:compileKotlin`）；若有相关 UI 测试则运行。需要运行时预览/截图对比时覆盖标签、单行表单、搜索框、M3 按钮、卡片、带 Switch 的行和时间线管理弹窗。
5. 最终报告覆盖范围、仍保留较大尺寸的理由和验证结果；不要声称没有逐屏核验的控件都已统一。
