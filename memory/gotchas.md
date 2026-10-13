# 迁移踩坑记录（gotchas.md）

> 格式：**症状 → 原因 → 对策**。踩到就加，不要攒。
> 迁移完成后这仍是回归资产。

---

## 模板

### YYYY-MM-DD · 一句话症状

- **症状**：看到什么现象
- **原因**：根因（尽量指到 `文件:行`）
- **对策**：怎么修的 / 以后怎么避免
- **相关**：涉及的文件或红线条款

---

## 已踩（按时间倒序）

### 2026-10-09 · 「库还是 v1、没发布过 → 直接改表」—— 开发机上就崩了，且所有测试都测不出来

- **症状**：给 `worldDocuments` 加一列后启动应用，弹窗
  `Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the
  version number. Expected identity hash: ae8df0cd…, found: b290e3ae…`。
  而 `:shared:desktopTest` **全绿**，包括所有 SQLite 往返测试。
- **原因**：改实体时判断「库还是 `version = 1`、从没发布过 → 不用迁移」。这个判断只对**全新的空库**成立。
  开发机上 `%APPDATA%\WatchWorld\watchworld.db` 早就是 v1 结构了 —— 上一次跑 `:desktopApp:run` 时就建好了。
  `openInMemory()` **每次都是新建库**，identity hash 天然对得上，所以测试永远发现不了。
- **对策**：`version = 1 → 2` + `WwMigrations.kt` 的 `MIGRATION_1_2` + `open()` 上 `.addMigrations(...)`，
  并加 `WwDatabaseMigrationTest`（物理造完整 v1 库 → 真 `open` → 迁移 + 哈希校验 → 关库重开）。
  **判据：只要改实体就必须 bump 版本 + 写 Migration。没有例外**（跑过一次应用就等于「已有库文件」）。
  ⚠️ 顺带踩到：`schemas/1.json` 已被「直接改实体」的那次构建**污染**（`legacyGroup` 写进 v1 的 `createSql`、
  `identityHash` 被改），必须按旧库文件的真实结构还原，否则 `version = 1` 与「库是 v1」两个条件同时成立、
  结构却不一致，排查成本翻倍。
- **相关**：`data/db/WwDatabase.kt`、`data/db/WwMigrations.kt`、`shared/schemas/*.json`；
  `tasks/T18-世界书与知识库.md`「心得 1、2」。

### 2026-10-09 · 测 Room 迁移不需要 `room-testing`（它在 KMP 里解析不到）

- **症状**：想用 `MigrationTestHelper` 写迁移测试，加了 `androidx.room:room-testing` 到 `desktopTest`，
  构建失败在依赖解析上（`ModuleVersionResolveException`），而且 Gradle 报错时自己又 NPE，
  真实原因被盖住。
- **原因**：Room 2.8.5 的 `room-testing` **不带 KMP 变体**，`desktopTestRuntimeClasspath` 里解析不出来。
- **对策**：不加依赖，改成自己按**已提交的 `schemas/*.json`** 造库：
  ① 结构契约（`2.json` 的列定义 == 迁移 SQL）；② 物理造完整 v1 库 → 真 `WwDatabase.open` → 读写 → 关库重开。
  造库有三个必须：**替换 `createSql` 里的 `${'$'}{TABLE_NAME}` 占位符**（否则建出名叫 `${TABLE_NAME}` 的表，
  报 `no such table`）、**全部表都建**（少一张报 `Migration didn't properly handle: providers(...)`）、
  **索引也建**（Room 比对包含索引，少一个 `index_sessions_updatedAt` 报 `sessions(...)` 不匹配，
  且报错只给差异片段）。`sessions` 的 `createSql` 与真实列还对不上（TypeConverter），要单独手写。
- **相关**：`desktopTest/data/db/WwDatabaseMigrationTest.kt`；`tasks/T18`「心得 3」。

### 2026-10-09 · PowerShell 写 JSON 会加 BOM，`WwJson` 直接解析失败

- **症状**：迁移测试读 `shared/schemas/1.json` 报
  `Unexpected JSON token at offset 2: Expected quotation mark '"', but had '\uFFFD' instead`。
- **原因**：用 PowerShell 的 `Set-Content -Encoding UTF8` 改过 `1.json`，它写了 **UTF-8 BOM**（`EF BB BF`）；
  `WwJson.parseToJsonElement` 不认 BOM（`Json` 没有配 `allowBom`）。
- **对策**：写文本文件用无 BOM 的编码：
  `[System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))`。
  校验：文件首 3 字节不能是 `239,187,191`。
- **相关**：`shared/schemas/com.watchworld.data.db.WwDatabase/1.json`；`tasks/T18`「心得 2」。

### 2026-10-09 · 「读时迁移」的字段被写库丢掉了 —— 导入老存档静默失去分组

- **症状**：导入用老格式（文档带 `group` 组名字符串）的存档后，**文档全部变成未分组**；
  不报错、不抛异常，库里也没有任何异常痕迹。单测 `WorldLibraryTest` 全绿（纯函数本身是对的）。
- **原因**：`storage.ts:360-373` 的 `group`→`groupId` 迁移发生在**读**的时候
  （`Storage.getWorldDocuments()`）。原生版没有 localStorage 那层中间态，链路是
  `parseArchive → 模型 → Mapper.toEntity → SQLite → 读时才迁移`。
  而 `WorldDocument.toEntity` 第一版写成 `legacyGroup = null`（理由是「`explicitNulls = false` 下
  `null` 等于键消失，正好对应 TS 的解构丢弃」）—— **解构丢弃在读，这里却是写**。
  `null` 一写，`group` 就永远进不了库，`worldDocuments()` 再也看不到它。
- **对策**：`toEntity` **原样带上** `legacyGroup`；迁移是纯函数、返回清理过的新对象，
  写回时该列自然清空，老 `group` 不会复活。
  `StorageRoundTripTest.legacyWorldDocumentGroupNameMigratesToGroupId` 三段一起断言
  （读回是 `groupId` / 库里无残值 / 导出无 `group` 键）。
  **判据：某个字段该不该落库，看「迁移发生在读还是写」，不是「它属不属于新模型」。**
- **相关**：`data/db/Mappers.kt`、`data/repository/ConfigRepository.kt`、`model/Content.kt`；
  `tasks/T18-世界书与知识库.md`「心得 1」。

### 2026-10-09 · 用「期望/实际都打印出来」定位，比反复重跑快得多

- **症状**：`deleteGroupCascadesToSubtreeAndItsDocuments` 失败，期望 `[旁支, 未分组]`、
  实际 `[根, 旁支, 未分组]`。看起来像 `deleteGroup` 没删干净（`doomed` 算错）。
- **原因**：**实现的 `doomed` 是对的**；是我写期望时记错了归属 —— 文档挂在 `root` 与 `grandChild` 上，
  删的是 `child` 子树，`root` 的文档本就不该被删。
- **对策**：没有反复重跑，而是把 id 与文档归属各打印一次
  （临时 `assertTrue(false, "DIAG ...")`），一眼看到 `根文档 -> wg-…1000` 而 `child = wg-…2000`，
  立刻判定是期望错了。**用完即删**。
  ⚠️ 小坑：`assertEquals(expected, actual, "DIAG …")` 在 `List<String>` 上会解析到
  **`assertEquals(Double, Double, Double)`** 重载（消息被当第三个 double），必须写成
  `assertEquals(expected, actual, message = "…")` 或先用 `assertTrue(false, …)` 打印。
- **相关**：`tasks/T18-世界书与知识库.md`「心得 6」。

### 2026-10-09 · `ConfigRepository.setXxx` 是全量替换：拿内存快照写回会抹掉别人的改动

- **症状**：`deleteGroup` 第一版用内存 `snapshot.documents` 过滤后写回，功能测试能过，
  但设计上会在「内存快照落后于库」时（例如刚导入过存档）把库里的改动一起删掉。
- **原因**：`setWorldGroups` / `setWorldDocuments` 对齐 Web 版的 `Storage.setWorldGroups(整份数组)`，
  是**全量替换**——会删掉不在列表里的条目。内存快照不是权威。
- **对策**：`config.transaction { 读库全量 → 改 → 写回 }`，两个列表都从库里读；
  文档特意取「读接口给的对象」而不是快照里的（读接口已跑过 `group`→`groupId` 迁移）。
- **相关**：`ui/world/WorldViewModel.kt`；`data/db/ConfigDaos.kt` 顶部注释；`tasks/T07`。

### 2026-10-09 · 「正则写错了也看不出来」——补判别性用例才测得出

- **症状**：`CONTROL_CHARACTER_PATTERN = Regex("[\\u0000-\\u0008...\\u007F]")` 这条，**原有用例全绿**，但完全无法证明正则真的在剥控制字符（原用例只喂了普通文字）。
- **原因**：Kotlin 字符串里 `\\u0000` 会先转义成 `\u0000` 再交给正则引擎解释成 NUL；若手抖写成 4 个反斜杠，就变成「匹配字面量 `\u0000` 六个字符」——行为完全不同，但**没有用例能区分**。
- **对策**：补 `sanitizeTimelineTagTextStripsControlCharacters`：喂真实 NUL/BEL/DEL → 断言**消失**；喂 TAB → 断言**保留**（`\u0009` 在 TS 的 C0 区间之外）。
  **凡是「字符串转义 / 正则语义」这类写错了不报错的代码，用例必须让「写对」和「写错」产生不同结果。**
- **相关**：`domain/TimelineTagControl.kt`；`tasks/T17-时间线树.md`「心得 6」。

### 2026-10-09 · 手感「像 bug」的正则 / 循环，凭直觉写用例会被打脸

- **症状**：T17 第一版用例把「回到分支【A1】」「进入时间线：学园线」「这是一个新时间线」当**应当命中**的样本，三条全挂；一度以为是自己 Kotlin 正则写错了。
- **原因**：**TS 原始正则本来就不接受这些写法**。
  - `switchPattern` 里 `(?:【|\[)?` 出现在「时间线/分支」**之前**，所以「回到分支**【**A1**】**」这种「后缀方括号」写法不命中；
  - 显式声明正则 `(?:一个|一条|一个全新的?)?` 只认「一个」「一条」「一个全新的」，**不认「一个新」**（「这是一个新时间线」不命中）；
  - `allocateTimelineCodeTag` 的根分支 `for` 循环里 `num` 每轮重置 + 立即 `return`，**只会用 A 开头**（看着像漏了 `continue`，实为规格）。
- **对策**：出验收样本前，**先把候选串喂给 Node 跑 TS 原始正则**（临时 `scratch-t17.mjs`，用完即删），拿实测结果当期望；对「TS 不接受」的写法补**反证断言**（`assertFalse`），防止后人「顺手放宽」。
- **相关**：`domain/TimelineMemory.kt`；`tasks/T17-时间线树.md`「心得 1、5」。

### 2026-10-09 · Kotlin `JsonPrimitive.content` 把数字读成字符串，`typeof !== 'string'` 必须显式补

- **症状**：`parseTimelineDescriptionTagControl` 收到 `{"descriptionTag":123,"reason":"b"}` 时**采纳**了申请（标签变成 `"123"`），而 TS 版本会丢弃它。
- **原因**：TS 的 `sanitizeText` 第一行是 `if (typeof value !== 'string') return undefined`；Kotlin 侧只写了 `(obj["x"] as? JsonPrimitive)?.content` —— `JsonPrimitive.content` 对**数字/布尔**同样返回字面量字符串，于是类型校验被绕过。
- **对策**：加 `.takeIf { it.isString }`。**凡是「TS 用 `typeof` 判类型」的地方，Kotlin 侧都要显式检查 `isString` / `as? JsonPrimitive`，不能只靠 `.content`。**
- **相关**：`domain/TimelineTagControl.kt`；`tasks/T17-时间线树.md`「心得 4」。

### 2026-10-08 · regenerateInto 与 send 的生成后半段高度重复

- **症状**：`regenerateInto` 几乎是 `send` 的后半段（装配→工具循环→落库→错误处理）的复制。
- **原因**：`send` 还承担 user/assistant 消息创建和标题截断，直接提取公共方法需要拆分这些逻辑，影响已通过 T14 的 8 条用例。
- **对策**：T16 选择「先复制再精简」，保持两处独立。第三个生成入口出现时再提取。
- **相关**：`ui/chat/ChatViewModel.kt`；`tasks/T16-消息版本与图片.md`「心得 1」。

### 2026-10-08 · 版本切换必须同步镜像顶层 latencyMs/tokens

- **症状**：切版本后存档导出的顶层字段仍是旧版本的值。
- **原因**：`api.ts:1412 handleSwitchVersion` 会把当前版本的 latency/tokens 复制到顶层。Kotlin 侧最初漏了这步。
- **对策**：`switchVersion` 里同步设置 `msg.copy(latencyMs = ver?.latencyMs, tokens = ver?.tokens)`。
- **相关**：`ui/chat/ChatViewModel.kt`；`tasks/T16-消息版本与图片.md`「心得 2」。

### 2026-10-08 · Compose Row 的位置参数顺序：Arrangement 不是 Modifier

- **症状**：`Row(Arrangement.spacedBy(6.dp), Modifier.fillMaxWidth())` 报 `Argument type mismatch: actual type is 'Arrangement.HorizontalOrVertical', but 'Modifier' was expected`。
- **原因**：`Row` 的签名是 `Row(modifier: Modifier, horizontalArrangement, verticalAlignment, content)`。第一个位置参数是 `modifier`，不是 arrangement。把 arrangement 放到第一个位置，编译器把它当 modifier。
- **对策**：用命名参数 `Row(modifier = ..., horizontalArrangement = ...)` 或严格按位置顺序 `Row(Modifier, Arrangement.spacedBy(8.dp))`。**Column 同理**。
- **相关**：`ui/settings/SettingsPanels.kt`；`tasks/T15-设置类弹窗.md`「心得 3」。

### 2026-10-08 · 取消后写库：第一条挂起点就把部分正文丢了

- **症状**：用户按「停止」后，屏幕上已显示的半截正文与「[已停止生成]」**没有落库**；不报错，只是重启后那段没了。
- **原因**：协程已被取消，`catch (CancellationException)` 里调 `sessionRepository.save()` —— 里面第一个挂起点（Room 查询/写入切换调度器）立刻再抛一次 `CancellationException`，写操作整个作废。
  与 2026-10-07 那条「取消路径里补一句提示，反而又抛一次」是**同一条纪律**，只是这次踩在数据库写上而不是回调上。
- **对策**：取消分支里的落库包 `withContext(NonCancellable) { … }`。`ChatViewModelTest.stoppingKeepsPartialTextAndMarksTheMessageStopped` 守住。
- **相关**：`ui/chat/ChatViewModel.kt`；`tasks/T14-会话与侧边栏.md`「心得 2」。

### 2026-10-08 · 给离屏像素测试写「反证样本」时，反证本身的期望就是错的

- **症状**：`ChatMessageBubbleRenderTest` 里那条「只有顶层 `content`、`versions` 为空 → 不该渲染出文字」一直失败，看起来像 `MessageBubble` 取错了字段。
- **原因**：**TS 的兜底是规格的一部分** —— `api.ts:328` 是 `(m.versions[i] || m.versions[0])?.content || m.content`，`versions` 为空时**本来就应该**回落到顶层 `content`。我把「兜底」误当成了「bug」。
- **对策**：判别性样本要挑**只有一种实现能通过**的输入 —— 改成「`versions[0]` 空、正文在 `versions[1]`、`currentVersionIndex = 1`」（只读 `versions.first()` 或只读顶层都会空白）；兜底行为另立一条用例**正面断言**。
- **相关**：`desktopTest/ui/chat/ChatMessageBubbleRenderTest.kt`；`tasks/T14-会话与侧边栏.md`「心得 4」。

### 2026-10-08 · 任务的「上游失败」与「装配失败」混进同一个 error 字段，测试直接顶回来

- **症状**：上游返回 500 时，断言 `state.error != null` 失败 —— 实际是 `null`，但界面上确实有错误文字。
- **原因**：`runToolChat` **除取消外不抛**，它把失败原因写成正文里的一条 note（`[请求失败：{上游原文}]`）。于是上游失败根本到不了 `ChatViewModel` 的 `catch`；`catch` 只接得住「自己装配时炸的」（没 Provider / 没 Agent）。
- **对策**：两条路径给**不同的落点** —— 上游失败进**正文**（用户要能对着那条消息继续对话），装配失败进 `state.error`（它没有消息可以承载原因）。两条各有专门用例。
- **相关**：`ui/chat/ChatViewModel.kt`；`tasks/T14-会话与侧边栏.md`「心得 1」。

### 2026-10-08 · 改 `SettingsRepository` 的兜底默认值，把 T07 的往返测试打成假失败

- **症状**：把 `displaySettings()` / `appPreferences()` 的兜底从「模型自身默认值」换成 `DEFAULT_*` 常量后，`StorageRoundTripTest` 里三份真实存档的往返断言全挂，看起来像存档解析坏了。
- **原因**：那两个默认值同时被两处用到 —— 全新安装的默认设置，以及 `ArchiveStore.snapshot()` 在**老存档缺这两个字段**时的补齐。测试里的 `withStorageDefaults` 手写着旧默认值，于是「期望」与「实际」两边的兜底不一致。
- **对策**：测试的补齐函数改成**引用同一份常量**（`com.watchworld.data.DEFAULT_*`），不再手抄。**改 `DefaultData.kt` 的值时必须同步改 `StorageRoundTripTest.withStorageDefaults`。**
- **相关**：`data/DefaultData.kt`；`data/repository/SettingsRepository.kt`；`desktopTest/data/StorageRoundTripTest.kt`。

### 2026-10-08 · CssColors 把 CSS 的 `/ 0.9` 当成了 0.9% 不透明度

- **症状**:深色模式下 AI 气泡近乎全透明(与 Web 版不符)。浅色 E2E 截图、快照对拍(只比字符串)都发现不了。
- **原因**:`oklch(L% C H / A)` 的 A 在 CSS 里**无单位是 0..1 比例**(`/ 0.9` = 90%),带 `%` 才是百分数。`CssColors.parse` 把抓到的 `0.9` 直接当百分数传给 `OklchColorMath.toSrgbInt(alphaPercent)` → `0.9/100×255 ≈ 2`。theme.ts 的 `--m1-bubble-dark = oklch(20.8% 0.042 265.755 / 0.9)` 正好命中。
- **对策**:区分有无 `%` —— 带 `%` 按百分数、无单位 ×100 折算;`CssColorsTest` 的 alpha 用例守住(无单位 `.9`/`0.9`、`90%`、`1`/`100%`/`0.5`)。**新增解析器时,凡「比例 vs 百分数」的 CSS 数值先问单位。**
- **相关**:`ui/theme/CssColors.kt`;`ui/theme/CssColorsTest.kt`。

### 2026-10-07 · 主题色对拍:Tailwind v4 的 oklch 值与 v3 hex 不是同一套颜色

- **症状**:想用「7 个预设的 primaryHex 必须等于色阶 600 档解回来的 sRGB」当测试向量,全部对不上(如 emerald 差 5/3/3、violet 差 4/24/17)。
- **原因**:Tailwind **v4 的 oklch 表是重新调的**,与 v3 时代的 hex 同名不同色:emerald-600 v4 表 `oklch(59.6% 0.145 163.225)` 按 CSS Color 4 逆向解回 **#009966**,而 v3 表是 #059669(仅色度 C 不同,L/h 一致)。React 仓 theme.ts 两者混用——色阶用 v4 oklch(浏览器实际渲染的色),primaryHex 用 v3 hex(仅列表圆点展示)。**浏览器才是 ground truth**,Compose 侧必须也按 oklch 解色才能与 Web 观感一致。
- **对策**:向量测试改为「v4 oklch → 期望 = 浏览器渲染值」(`OklchVectorTest`,DevTools 可复核);正向用 hex→oklch 手算参考值守住矩阵。往返(hex→oklch→sRGB→hex ±1/255)是自洽性检查,与 v4 表不可往返无关。
- **相关**:`ui/theme/OklchVectorTest.kt`;`ui/theme/OklchColorMath.kt`。

### 2026-10-07 · theme.ts 模板串里同一行混用两种数字精度(L 是 toString,C/H 是 toFixed)

- **症状**:buildScale 输出对快照总差一位:L 多出尾零(`95.0000` vs `95`),C 又少尾零(`0.068` vs `0.0680`),H 偶发少尾零(`64.6` vs `64.60`)。
- **原因**:theme.ts 模板是 `` `oklch(${L}% ${c.toFixed(4)} ${h.toFixed(2)})` `` —— **L 是 number 隐式 toString(去尾零)**,只有 C/H 走 `toFixed`(固定小数位、**保留**尾零;node 实证 `(0.068).toFixed(4)==="0.0680"`、`(95).toFixed(4)==="95.0000"`)。同一模板两种精度,凭直觉猜必错;family m2 直乘路径的 L/h 则是字符串原样透传。
- **对策**:Kotlin 拆成两个助手:`toCssNumber`(toString 语义,去尾零)喂 L;`toFixedCss4/2`(固定位不丢尾零,JS toFixed 的 half-up)喂 C/H。以基线快照逐字对拍为最终裁决(`ThemeSnapshotTest`,现 22 套 × 24 变量全等;fixture 自 2026-10-13 起由 `ThemeBaselineGenerator` 从本侧导出)。
- **相关**:`ui/theme/ThemeScale.kt`;`ui/theme/ThemeVars.kt`;`fixtures/theme-baseline/snapshots.json`。

### 2026-10-07 · 注释里写 `/*`，编译报「Unclosed comment」在一百多行之外

- **症状**：`McpClient.kt` 编译报 `Syntax error: Unclosed comment`（定位在文件最后一行），外加一串“不相干”的错（`Suspension functions can only be called within coroutine body`、`Unresolved reference 'McpTransportException'`）。
- **原因**：KDoc 里写了 `` `notifications/*` `` —— Kotlin **支持嵌套块注释**，这个 `/*` 又开了一层，把后面所有代码都吞进注释里，错误定位全被带到 EOF。
- **对策**：注释里不要出现字面量 `/*`（写成 `notifications/…`）。看到「Unclosed comment 且在 EOF」先搜注释里的 `/*`。
- **相关**：`net/McpClient.kt`；T11 又踩一次 —— `net/WebSearchClient.kt` 里写了 `` `/api/*` ``，同样报 Unclosed comment。凡在注释里写通配路径（`/api/*`、`notifications/*`）都是地雷。

### 2026-10-07 · 移植 `new URL(x, base)` 时只判 http(s)，把 `mailto:` 当成了相对路径

- **症状**：`unwrapDuckDuckGoUrl` 把 `mailto:someone@example.com` 拼成了 `https://duckduckgo.com/mailto:someone@example.com`，于是这个非网页链接没被过滤掉。
- **原因**：JS `new URL('mailto:a@b', base)` 会保留 `mailto:` scheme（它已是绝对 URL）；我最初只判 `^https?://`，其余一律套 base。
- **对策**：先判「有没有**任意** scheme」（`^[a-zA-Z][a-zA-Z0-9+.\-]*:`），有就原样、没有再套 base；是否属于网页链接交给上层的 http(s) 判定。
- **相关**：`net/WebSearchClient.kt` `unwrapDuckDuckGoUrl`。

### 2026-10-07 · 移植 JS 字符串处理时，测试期望按直觉写而没按 JS 语义

- **症状**：`stripHtml` 与实体解码的用例挂了，但实现是对的 —— 期望值写错。`A snippet with <b>tags</b>.` 实际输出 `A snippet with tags .`（标签换成空格后留下一个空格）；`&#65;` 是字符 `A` 不是 `65`。
- **原因**：JS `value.replace(/<[^>]+>/g, ' ')` 把**每个**标签换成空格再折叠，标点前会留空格；十进制实体解的是码位。
- **对策**：移植 JS 字符串逻辑时，先按 JS 语义手推期望值（必要时在浏览器/Node 里跑一遍），再写断言；两侧不一致时先怀疑期望值。
- **相关**：`net/WebSearchClient.kt` `stripSearchHtml` / `decodeSearchHtml`。

### 2026-10-07 · `withTimeout` 的超时异常被工具循环当成「用户停止」

- **症状**：MCP 调用超时本应回一句「MCP 连接超时（15 秒）」，实际整条 AI 消息被当作取消外抛，正文只剩已输出部分。
- **原因**：`kotlinx.coroutines.withTimeout` 抛 `TimeoutCancellationException`，它**是** `CancellationException`；T09 `ToolLoop.kt executeToolCall` 的 `catch (CancellationException) { throw error }`（有意保留取消语义）会把它继续外抛。
- **对策**：MCP 客户端一律用 `withTimeoutOrNull`，超时后自己抛 `ApiException(0, "…")`（普通异常，T09 会转成 `{error}` 交给模型）。
- **相关**：`net/McpClient.kt` `withTimeoutApi`；`tasks/T09-工具轮次循环.md`「心得 1」。

### 2026-10-07 · 假上游的常驻 GET 流把 POST 饿死

- **症状**：旧式 SSE 测试一直超时，POST `/messages` 根本没进 handler。
- **原因**：`com.sun.net.httpserver.HttpServer` 的 `executor = null` 是**单线程**；SSE 测试的 GET handler 会阻塞到测试结束，后面的 POST 永远排不上队。
- **对策**：SSE 假上游改用 `Executors.newFixedThreadPool(n)`，并在 `stop()` 里 `shutdownNow()`。不阻塞的假上游仍可用默认单线程。
- **相关**：`desktopTest/net/McpClientTest.kt` `FakeSseMcpServer`。

### 2026-10-07 · 取消路径里补一句提示，反而又抛一次 `CancellationException`

- **症状**：用户按停止后，想在 `catch (CancellationException)` 里 `note("已停止生成")`，结果 note 没写进去，异常从 catch 里又冒出来一次。
- **原因**：`note()` 内部调 `onChunk` / `onNote`，它们是 suspend 回调；协程已被取消，任何挂起点都会立刻再抛 `CancellationException`（`net/ToolCallLoop.kt` `runToolChat` 的 catch 段）。
- **对策**：取消分支只 `throw error`，不调任何 suspend 回调；「已停止生成」由调用方（T14 `ChatViewModel`）在自己的 catch 里补，已输出正文从调用方的 `onChunk` 累积里取。`cancellationKeepsPartialTextAndPropagates` 守住。
- **相关**：`tasks/T09-工具轮次循环.md`「心得 1」。

### 2026-10-07 · Claude 第二轮工具调用解析错位

- **症状**：Claude 工具轮次的第二轮起，内容块对不上号（把上一轮的块当成已开始）。
- **原因**：Claude 的 adapter 里 block 表是**跨事件**的有状态对象；工具循环复用上一轮的 adapter 实例，旧状态带进了新一轮。
- **对策**：每轮都 `ProviderStreamAdapters.forProvider(kind)` 新建 adapter，不缓存。
- **相关**：`net/ToolCallLoop.kt` `runToolChat` 流式分支；CONSTRAINTS §3.4。

### 2026-10-07 · Gemini 的 tool result 写成了字符串

- **症状**：照 OpenAI / Claude 的写法把工具结果序列化成字符串塞进 Gemini，请求不报错，但模型拿到的是一段被转义的文本而不是结构化结果。
- **原因**：OpenAI `content`、Claude `tool_result.content` 是字符串化 JSON；Gemini 是 `functionResponse.response.result`，`result` 是**对象本身**（TS：`response: { result }`）。
- **对策**：Gemini 分支对序列化结果再 `parseToJsonElement` 放进 `result`；`geminiRoundsUseFunctionCallAndFunctionResponseParts` 断言 `result` 是 `JsonObject`。
- **相关**：`net/ToolCallLoop.kt` `toolResultMessage`；`tasks/T09-工具轮次循环.md`「心得 3」。

### 2026-10-07 · clean 后编译报 `shrunk-classpath-snapshot.bin` 找不到 / 日志文件被占用

- **症状**：`clean` 后 `:shared:compileKotlinDesktop` 失败，`Compilation in Kotlin daemon has failed … shrunk-classpath-snapshot.bin (系统找不到指定的路径)`；随后 `--stop` 再跑，又出现 `daemon has been stopped` 和 `ww-t08-build.log` 被另一进程占用。
- **原因**：残留的 Gradle/Kotlin daemon 持有旧的增量编译状态与重定向的日志句柄，与 `clean` 冲突。不是代码问题。
- **对策**：`.\gradlew.bat --stop`，确认无残留 `java.exe`（`Get-CimInstance Win32_Process` 查 gradle 命令行，必要时 `Stop-Process`），换一个新日志文件名再 `clean` + 全量构建。
- **相关**：T08 方案 A 复验。

### 2026-10-07 · 约束文档写的 system 段落顺序与 `api.ts` 源码不一致

- **症状**：照 CONSTRAINTS §4.1 / PLAN §6.6 拼「正典 → 知识库 → MCP → Skill」，与 Web 版实际发出的 system 不同，无任何报错。
- **原因**：`api.ts:311-321` 真实顺序是 head → 正典 → **MCP → Skill → 知识库** → 联网 → 楼号约定 → extraSystemTail → 时间线树（源码注释：利于 Prefix Cache）。文档是概括，写错了。
- **对策**：以源码为准（TS 是规格书），`systemSectionsFollowApiTsOrder` 钉整串；2026-10-07 已按源码修正 CONSTRAINTS §4.1 与 PLAN §6.6。**凡涉及顺序/字面量，先读源码再信文档。**
- **相关**：`domain/ContextBuilder.kt`；`tasks/T08-上下文拼装.md`「心得 1」。

### 2026-10-07 · 用户正则直接交给 Kotlin `Regex.replace` 会静默改变输出

- **症状**（设计时发现）：同一条规则在两版结果不同：`$&` / `$$` / `$<name>` 不被识别或抛错，反斜杠被当转义，无 `g` 标志时 Kotlin 仍全部替换。
- **原因**：JS 与 Kotlin 替换串语法、`g` 语义不同；TS 还有 `rule.flags || 'g'`。
- **对策**：`RegexProcessor.kt` 自己按 ECMAScript GetSubstitution 展开替换串，`global` 显式控制替换次数，非法 pattern/flags 跳过该条。`regexRulesApplyOnOutputScopeWithJsSemantics` 守住。
- **相关**：`domain/RegexProcessor.kt`。

### 2026-10-07 · JS 假值：`floorNumber == 0`、`slice(-0)`、空串回退

- **症状**（设计时发现）：直译成 `!= null` 判断会让 `#0` 前缀出现、`latest_custom` 且 limit=0 时一条助手消息都不留。
- **原因**：TS 的 `!m.floorNumber`、`arr.slice(-0)`（= 全部）、`a || b`（空串也回退）都是假值语义。
- **对策**：逐处按 JS 真值对齐（见 `ContextBuilder.kt` / `TimelineMemory.kt` 注释）；可空 String 用 `?.ifEmpty { null } ?:` 表达 `||`。
- **相关**：`tasks/T08-上下文拼装.md`「心得 4」。

### 2026-10-07 · 时间线读回来是 `[]`：单列主键让后写的会话把同名分支「抢」走了

- **症状**：`StorageRoundTripTest` 用 rich 样本往返，第一个会话的 `timelines` 读回 `[]`；合成用例全过，无任何报错。
- **原因**：`TimelineEntity` 原为 `@PrimaryKey val id`，而时间线 id **只在会话内唯一**（两个会话都有 `timeline-main`）。逐会话 `@Upsert` 时，第二个会话把第一个会话的同名行改写成自己的 `sessionId`。
- **对策**：`messages` / `timelines` / `summaries` 三张子表一律用复合主键 `primaryKeys = ["sessionId", "id"]`（库仍是未发布的 `version = 1`，无需 Migration）；按行更新/删除子表的查询必须同时带 `sessionId`。由 `sameChildIdsInDifferentSessionsDoNotCollide` 守住。拆表前逐个确认「这个 id 的唯一范围是全局还是父对象内」。
- **相关**：`data/db/SessionEntities.kt`；`tasks/T07-Room存储层.md`「心得 1」。

### 2026-10-07 · commonMain 里 `withTransaction` 解析不到

- **症状**：照 Android 习惯写 `db.withTransaction { }`，commonMain 编译报未解析引用。
- **原因**：`androidx.room.withTransaction` 在 room-ktx 里，**只有 Android**；Room KMP 没有它。
- **对策**：用 `useWriterConnection { it.immediateTransaction { ... } }`，已封装为 `WwDatabase.withTransaction {}`（`data/db/WwTransaction.kt`），上层统一调它。
- **相关**：`data/db/WwTransaction.kt`。

### 2026-10-07 · `expect object` 带来 Beta 警告，破坏零警告

- **症状**：平台层写成 `expect object PlatformStorage`，编译出 expect/actual classes are in Beta 警告。
- **原因**：expect/actual 类在 Kotlin 2.4 仍是 Beta。
- **对策**：一律用**顶层 `expect fun` / `expect val`**（`appDataDir` / `createSqliteDriver` / `sqliteQueryDispatcher`，与 `platformEnv`、`currentTimeMillis` 同写法）。
- **相关**：`platform/PlatformStorage.kt`。

### 2026-10-07 · Room 的 KSP 配置写错名字，静默不生成代码

- **症状**：DAO 实现找不到 / `WwDatabase` 构造报错，报错位置离真正原因很远。
- **原因**：KSP 配置名跟着 KMP **目标名**走。目标叫 `desktop` → 配置叫 **`kspDesktop`**；写 `kspJvm` / `kspCommonMainMetadata` 不会生成代码。
- **对策**：`dependencies { add("kspDesktop", libs.androidx.room.compiler) }`。T22 加 Android 目标时对应配置是 `kspAndroid`。
- **相关**：`shared/build.gradle.kts`。

### 2026-10-07 · `databaseBuilder(name = "watchworld.db")` 把库建在当前工作目录

- **症状**：库文件出现在启动目录而不是 `%APPDATA%\WatchWorld`。
- **原因**：KMP 版 Room 在 JVM/Android 上把 `name` **当路径用**，裸名字就是相对路径。
- **对策**：传**文件全路径**（`appDataDir()` + `WwDatabase.FILE_NAME`）；`fileBackedDatabaseSurvivesCloseAndReopen` 断言库文件落在给定路径。
- **相关**：`data/db/WwDatabase.kt` `open()`；2026-10-06「相对路径写文件跑到另一个盘」同类。

### 2026-10-07 · Gradle 下载间歇性 TLS 错误

- **症状**：构建偶发 `TLS` / `handshake` / `Could not GET` / `Could not download`，同一命令重跑就过。
- **原因**：网络侧间歇问题，不是依赖坐标错误。
- **对策**：输出匹配上述关键字时重试（最多 12 次）；PowerShell 循环里写 `${i}:` 不要写 `$i:`（后者是作用域语法，解析报错）。真正的坐标错误每次都会以同样的 `Could not find` 失败，不会被重试掩盖。
- **相关**：本次 T07 回归命令。

### 2026-10-07 · 脱敏漏了三样东西，全都通过了检查 —— 因为判据是「字段叫什么名字」

- **症状**：真实导出经「擦密钥 + 抹正文」两个脚本处理后，仍带着
  ① `webdavConfig.username` 里的**真邮箱**、② `summaries[].content` 里 **803 字的对话结构化总结**、
  ③ `mcpServers[].url` 里的**账号段**（`/mcp/andunya`）。三个脚本的残留扫描**全部报告通过**。
  同一类问题当天还漏了第四样：`timelines[].plotSummary`（自动生成的「`用户: … | 回复: …`」日志）。
- **原因**：四次都是同一个思维错误 —— **用「这个字段属于哪个对象、叫什么名字」判断要不要脱敏**：
  - `SECRET_KEYS` 只列了 `apiKey`/`password`/`authToken`，**用户名不是密钥** → 没人往 `username` 看；
  - 抹除脚本只遍历 `session.messages`，**总结不是消息** → `summaries[].content` 在遍历范围之外；
  - 两个脚本一个管凭据、一个管对话，**URL 不归任何一个管** → 端点里的账号段无人负责。
- **对策**：**换判据** —— 问「**这段内容是不是从『用户这个人』或『这次对话』来的？**」
  是就处理，否（配置标签、公共端点、内置默认值）才可留。并把它固化成三个互不重叠的职责：
  | 脚本 | 负责 |
  |---|---|
  | `redact-archive.mjs` | 凭据（`apiKey`/`password`/`authToken`）+ 身份（`username`、邮箱扫描） |
  | `scrub-archive-messages.mjs` | 对话派生文本：消息正文、`summaries[].content`、`timelines[].plotSummary` |
  | `neutralize-endpoints.mjs` | 个人端点：`webdavConfig.url`、`mcpServers[].url`（含路径账号段） |
  采样本要**跑满三个**，跑完再自己 grep 一遍（命令见 `fixtures/sse/README.md`）。
- **附带教训**：**「已核对过没问题」必须逐字段成立**。legacy 样本的 README 当年写着「其余长文本经核对
  全是内置默认值」，那句话写下当天就是错的。可复现的核对法是**逐字节比对两份样本的非会话文本字段**，
  而不是读一遍觉得没事。
- **相关**：`fixtures/tools/*.mjs`；`fixtures/sse/README.md`「三步分工」；
  `tasks/T06-v5存档往返.md`「心得 6」。

### 2026-10-07 · 导出 v5 存档时 `version` 整个消失了（`encodeDefaults = false` 的连带效应）

- **症状**：T06 写 `exportArchive`，测试断言导出里有 `"version": 5` 却失败 —— 产物里**根本没有 `version` 这个键**，
  连 `worldGroups` 也不见了。没有任何报错，因为「省略字段」是 `encodeDefaults = false` 的正常行为。
- **原因**：`encodeDefaults = false` 会略过**等于字段默认值**的键，而恰好：
  - `ArchiveV5.version` 的默认值就是 **`5`** → `copy(version = 5)` 编码出来还是「等于默认值」→ 被略过；
  - `worldGroups` 在 legacy 存档里缺字段 → 解码成 `emptyList()` → 也等于默认值 → 被略过。

  `version` 是**明文规定不可改、跨版本契约、升级兼容分支靠它判断**的字段，就这么无声无息地没了。
  Web 版不会这样：`exportAllData` 是**显式的对象字面量**，17 个顶层键一个不少。
- **对策**：导出的**顶层**先铺一层 `encodeDefaults = true` 的骨架再叠加实际值
  （`Json(WwJson) { encodeDefaults = true }.encodeToJsonElement(ArchiveV5())`）。见 `ArchiveRepository.exportJson` / `defaultsJson`。
  ⚠️ **只铺顶层**：`encodeDefaults` 递归生效，拿它编码整份存档会把每个嵌套实体里等于默认值的字段全写出来，
  而 Web 版在嵌套实体上是 spread 语义（老存档没有的键就一直没有）。
- **教训（不止适用于存档）**：`encodeDefaults = false` 下，**任何「默认值恰好等于业务上必须存在的值」的字段都会静默消失**。
  改动 `ArchiveV5` / `ChatSession` 的默认值前，先想一遍「这个默认值会不会让某个必须存在的键在导出时被略过」。
- **相关**：`data/repository/ArchiveRepository.kt`；`tasks/T06-v5存档往返.md`「心得 2」。

### 2026-10-07 · 归一化只做「解码前」那一层，三条规则静默失效

- **症状**：T06 里 `sanitizeSessionJson` 写好了、测试却显示 `connectedKnowledgeIds` 里的 `kb-quick-notes`
  根本没被滤掉，`webSearchEnabled` 也没落成 `false`。解码没报错，字段也在，就是**规则没生效**。
- **原因**：老存档归一化**不是一件事，是两件**，解决的是不同问题：

  | 问题 | 例子 | 必须在哪里解决 |
  |---|---|---|
  | **解不开** | `connectedKnowledgeIds` 是字符串、`streamingEnabled` 是 `"maybe"` | 解码**前**（Kotlin 解码器类型不符直接抛，`coerceInputValues` 不管这种） |
  | **解开了但不对** | 数组里含 `kb-quick-notes`、`webSearchEnabled` 是 `false` 而非缺失 | 解码**后**（要模型的语义） |

  只做解码前，就只拿到第 ①⑥ 条；第 ②③⑤ 条需要模型才能判断，于是全漏。
- **对策**：`parseArchive` 是三步 —— `normalizeArchiveJson`（解码前）→ 解码 → `normalizeArchive`（解码后）。
  **缺任何一步都不会报错，只会静默少做几条规则。**
- **相关**：`data/migration/ArchiveNormalizer.kt`、`SessionNormalizer.kt`；`ArchiveRoundTripTest` 里有三条用例专门盯这个。

### 2026-10-07 · 归一化不幂等，会导致「每导出一次就丢一次 `toolCallStepLimit`」

- **症状**：还没踩到，是设计时发现的雷：`exportArchive` 会对**已经归一化过**的会话再跑一遍
  `normalizeChatSession`；若第 ④ 条的输入被当成「没有原始值」，合法的 `toolCallStepLimit = 5`
  会在每次导出时被抹成 `null` —— 无报错，只有工具循环行为悄悄变了。
- **原因**：第 ④ 条的判据要的是**原始 `JsonElement`**（老存档里可能是 `-1`/`NaN`/字符串），
  第二次归一化时那个原始值已经不存在了，若默认当 `null` 处理就等于「非法值」。
- **对策**：`normalizeChatSession` 的 `rawToolLimit` 默认参数用**模型自身的值回推**等价输入
  （`session.toolCallStepLimit?.let { JsonPrimitive(it) }`），使归一化幂等。
  改这个签名前先看 `normalizeChatSessionIsIdempotent`。
- **相关**：`data/migration/SessionNormalizer.kt`；`tasks/T06-v5存档往返.md`「心得 3」。

### 2026-10-06 · `explicitNulls = false` 挡不住「显式传入的 null」

- **症状**：以为配了 `explicitNulls = false` 就等于「所有 null 都不写回 JSON」，于是没给可空字段写默认值，
  结果往返时存档里凭空多出 `"someKey": null`，与 Web 版导出的形状对不上。
- **原因**：`explicitNulls = false` 只处理两种情况 —— ① 因**等于默认值**而省略；② 显式 null 但**该字段有默认值**。
  **字段没有默认值时，显式传入的 `null` 仍会被编码成 `"key": null`**。
- **对策**：本仓约定 —— **`@Serializable` 数据类里每个可空字段都必须给 `= null` 默认值**。
  复查命令（找「可空但没默认值」的字段，应输出为空）：
  ```bash
  grep -rnE 'val [A-Za-z0-9_]+: [A-Za-z0-9_.<>]+\?, *$' shared/src/commonMain/kotlin/com/watchworld/model/
  ```
- **相关**：`model/WwJson.kt`（配置说明已写进注释）；`tasks/T05-数据模型.md`「心得」。

### 2026-10-06 · Ktor 3.6.0 里 `readUTF8Line()` 已废弃；而它的替代品之一会炸在长行上

- **症状**：按 PLAN §6.1 的示例写 `channel.readUTF8Line()`，构建报
  `'suspend fun ByteReadChannel.readUTF8Line(max: Int = ...)' is deprecated. Use readLineStrict or readLine instead.`
- **原因**：Ktor 3.6.0 把 `readUTF8Line` 标记为废弃；而 PLAN §6.1 的示例代码早于这个变更。
- **对策**：两个替代品**必须选对**：
  - `readLineStrict(maxLength)` —— **行超过 maxLength 会抛异常**。SSE 的单个 `data:` 帧可能很大
    （工具调用的大段 JSON），用它会在长回复上偶发炸掉，极难查。**本仓不用**。
  - `readLine()` —— 宽松读行，**EOF 返回 `null`**。本仓用这个，配合 `while (!channel.isClosedForRead)`。
- **相关**：`net/ChatStreamClient.kt`；`docs/KOTLIN_MIGRATION_PLAN.md` §6.1（示例已过时，改代码时以本仓为准）。

### 2026-10-06 · 用相对路径写文件，新文件跑到了另一个盘，构建却报 BUILD SUCCESSFUL

- **症状**：在 `E:\WatchWorld\WatchWorld-Native` 里做 T02，`./gradlew :desktopApp:build`
  报 `BUILD SUCCESSFUL`，但 `compileKotlinDesktop` 是 `UP-TO-DATE`（新建的 6 个 .kt 根本没参与编译）。
- **原因**：**会话的「项目根」是打开客户端时所在的仓**（本次是 `C:\Users\Lavinia\WatchWorld`），
  **与 `docs/FILE_LAYOUT.md` §1 认定的工作区 `E:\WatchWorld` 无关**。
  `write_file` / `str_replace` 的**相对路径一律相对会话根**，于是写到了 C 盘的另一个仓。
- **对策**：
  1. 给本仓写文件一律用**绝对路径** `E:\WatchWorld\WatchWorld-Native\...`（已实测可用）；
     读文件同样可用绝对路径。
  2. **构建前先 `find` 确认文件真的在 E:**，别只信 `BUILD SUCCESSFUL`：
     目标文件没变时 Gradle 会报 `UP-TO-DATE`，看起来像“成功”。
  3. 误写的文件用 `mv` 搬到 E:，并 `rm -rf` 清掉误建目录（C 盘 React 仓本身没有 `shared/`）。
- **相关**：`docs/FILE_LAYOUT.md` §1；这就是「打开的文件夹与项目无关」的具体代价。

### 2026-10-06 · 窗口一启动就硬崩，退出码 -1073740791，无任何异常输出

- **症状**：`./gradlew :desktopApp:run` 构建成功，应用进程 1–2 秒内消失，Gradle 只报
  `finished with non-zero exit value -1073740791`；**不打印堆栈、不留 hs_err 文件**，看起来像业务代码炸了。
- **原因**：`-1073740791` = `0xC0000409` = `STATUS_STACK_BUFFER_OVERRUN`，崩在 **skiko 的渲染后端**。
  本机（Windows 11 + Temurin 21）的 skiko 默认 Direct3D 后端建窗即崩。
- **对策**：换渲染后端。已验证 `OPENGL` 与 `SOFTWARE` 都正常；
  `desktopApp/build.gradle.kts` 已把它变成开关 `-PwwRender=opengl|software|direct3d|default`，**默认 `opengl`**。
- **排查手法**（下次遇到类似的"无输出硬崩"直接照用）：
  ```bash
  MSYS_NO_PATHCONV=1 /c/Windows/System32/tasklist.exe /V /FI "IMAGENAME eq java.exe" | grep -i "WatchWorld Native"
  jstack <pid> | grep -E '"AWT-EventQueue|"main"|skiko'
  ```
  第 2 条看到 `AWT-EventQueue-0` + `skiko-dispatcher` 线程存活 = 窗口真的建起来了（而不是卡死）。
- **相关**：`tasks/T01-建KMP工程与空窗口.md`；⚠️ 别把这类崩溃当成 Compose 代码错误去查。

### 2026-10-06 · Could not find org.jetbrains.compose.material3:material3:1.12.1

- **症状**：把 `compose.runtime/.foundation/.material3/.ui` 四个已弃用 DSL 访问器改成
  `version.ref = composeMultiplatform` 的显式坐标后，构建挂在这条：`Could not find ...material3:1.12.1`。
  （而 runtime/foundation/ui 的 1.12.1 是存在的，所以报错看起来莫名其妙。）
- **原因**：**Compose 的产物版本是分裂的**。material3 有独立版本线，1.12.1 插件配的是 **material3 1.9.0**，
  与插件版本号无关；runtime / foundation / ui 才跟着插件版本走。
- **对策**：**先让 DSL 跑通一次，再把真实版本抄下来**，不要凭插件版本猜：
  ```bash
  ./gradlew :shared:dependencies --configuration desktopCompileClasspath | grep org.jetbrains.compose | sort -u
  ```
  `libs.versions.toml` 里单独立了 `composeMaterial3` 版本项。
- **相关**：升级 Compose MP 时**必须**重跑上面这条命令重新核对 material3，否则会重犯。

---

## 预置提醒（还没踩，但已知是雷）

### 预置 · 消息正文的存放位置是「按角色不对称」的

- **症状**：迁移后用户消息丢失正文，或 `versions[0]` 空指针崩溃。
- **原因**：真实存档里（`fixtures/sse/archive-v5-legacy.json` 实测）：
  - **assistant** 消息：`versions` 长度 1，正文在 `versions[0].content`，带 `latencyMs`；
  - **user** 消息：`versions` 是**空数组**，正文在**顶层 `content`**，**没有 `latencyMs`**。
- **对策**：`versions` 必须允许空数组；取值时先判角色与数组非空；`latencyMs` 是可空字段。
  不要写 `message.versions.first()` 这种假设。
- **相关**：`MIGRATION_CONSTRAINTS.md` §1；fixtures/sse/README.md「legacy 的独有价值」。

### 预置 · 流式变成"一次性到达"

- **症状**：帧间隔远小于上游节奏，甚至只收到一帧
- **原因**：误用了 `response.bodyAsText()` / `body<String>()`，或 `flow{}.buffer()`、多套了一层 `withContext(Dispatchers.IO)`
- **对策**：只允许 `preparePost().execute{} + ByteReadChannel.readUTF8Line()`；用 PLAN §8.2 的帧间隔测试守住
- **相关**：`MIGRATION_CONSTRAINTS.md` §3.2

### 预置 · 老存档突然全部变成"非流式"

- **症状**：导入老存档后不再逐字输出
- **原因**：把 `streamingEnabled = null` 归一化成了 `false`
- **对策**：保持 `Boolean?` 三态；`explicitNulls = false`
- **相关**：`MIGRATION_CONSTRAINTS.md` §1.3

### 预置 · 模型报"未授权的工具"

- **症状**：工具调用被上游拒绝
- **原因**：注册名与注入 system 的 `mcpContext` 清单不一致（如加了前缀）
- **对策**：用**原始工具名**，仅重名加 `_2/_3`，两处必须同步改
- **相关**：`MIGRATION_CONSTRAINTS.md` §3.3

### 预置 · Claude 流解析静默丢内容

- **症状**：正文缺一段，不报错
- **原因**：`content_block_delta` 找不到 `content_block_start` 时没抛错，静默跳过
- **对策**：缺失必须抛「Claude 流缺少内容块起始事件」
- **相关**：`MIGRATION_CONSTRAINTS.md` §3.4

### 预置 · 老用户看到幽灵知识库条目

- **症状**：知识库里出现已下线的内置指南
- **原因**：漏了 `normalizeChatSession` 里剔除 `kb-quick-notes` 那一步
- **对策**：6 条兼容逻辑逐条搬，一条不漏
- **相关**：`MIGRATION_CONSTRAINTS.md` §2
