# SSE 报文 fixture

> 对应 `../../docs/BASELINE_COLLECTION.md` §A（SSE 报文）与 §D（存档样本）。
> 这些是**真实上游响应的原始行**，Kotlin 版 Provider 适配器的单测（PLAN §8.1）直接读它们。

采集位置：文档原文写 `E:\WatchWorld\WatchWorld\fixtures\sse\`，本仓按 `docs/FILE_LAYOUT.md` §2
决策改为**放在原生仓**（fixture 是原生版长期回归资产，不依赖跨仓相对路径）。采集方法不变。

---

## 采集工具（`../tools/`）

| 脚本 | 作用 |
|---|---|
| `collect-sse-fixtures.sh` | **一键采集**三家 SSE 报文（只采有 Key 的几家），采完自动跑校验；**不打印任何 Key** |
| `check-fixtures.mjs` | **自校验**：把 §A.3 / §D.5 的判据变成可执行检查；Provider 靠**内容嗅探**识别，不依赖文件名 |
| `generate-minimal-archive.ts` | 用 **React 版自身的 `Storage.exportAllData()`** 生成 minimal 存档（保证形状同源） |
| `redact-archive.mjs` | 存档样本脱敏 + 残留扫描（**只替换非空值**，保留「字段存在但值为空」的兼容路径） |

### 采 SSE 报文

**Key 必须写在文件里，不能在终端 `export`** —— 脚本由 Freebuff 的 shell 执行，
你在另一个终端 export 的变量不会被继承。

```bash
# ① 填 Key（本文件被 .gitignore 的 `.env*` 规则忽略）
#    模板见 fixtures/.env.example
notepad fixtures/.env.local

# ② 先零成本自检：只报配置解析结果，不发请求、不回显 Key
bash fixtures/tools/collect-sse-fixtures.sh --dry-run

# ③ 正式采集（只采有 Key 的几家），采完自动跑校验
bash fixtures/tools/collect-sse-fixtures.sh
```

#### 中转站（OpenAI 兼容 + Bearer 鉴权）—— 本仓当前的实际用法

只填这三行即可采到 `openai-*.txt`：

```
WW_FIXTURE_OPENAI_BASE=https://你的中转站域名/v1   # 带不带 /v1、或直接写完整端点都行
WW_FIXTURE_OPENAI_KEY=sk-你的中转站key
WW_FIXTURE_OPENAI_MODEL=中转站支持的模型名
```

> **⚠️ 中转站采不了真实的 Claude / Gemini 协议样本。** 中转站通常只暴露 OpenAI 兼容端点，
> 会把 Claude/Gemini 的响应也归一化成 `choices[].delta` 形状。用那种报文去写 "Claude 适配器"
> 会写出**错的实现，且流式 bug 极难排查**。
> 只有当中转站**原生透传**原生端点时才能采：
> - Claude：`POST {base}/v1/messages`（`x-api-key` + `anthropic-version`；只认 Bearer 时设 `WW_FIXTURE_CLAUDE_AUTH=bearer`）
> - Gemini：`POST {base}/v1beta/models/<model>:streamGenerateContent?alt=sse&key=…`
>
> 校验器现在会做**「文件名 vs 内容」一致性检查**：文件名声称 claude、内容却是 `choices` 形状时**直接判 FAIL**
> （已用模拟的中转站归一化报文实测过，确实会拦下）。

#### 解析器已实测容忍的手写格式

`.env.local` 不需要写得多规范，下列写法都验证过能正确解析：

| 写法 | 例子 |
|---|---|
| 双引号 + 行尾空格 | `WW_FIXTURE_OPENAI_BASE="https://x.ai"␠␠` |
| 单引号 | `WW_FIXTURE_CLAUDE_BASE='https://x.ai/v1'` |
| 行尾注释 | `WW_FIXTURE_OPENAI_KEY=sk-xxx␠␠# 注释` |
| `export` 前缀 | `export WW_FIXTURE_OPENAI_MODEL=gpt-4o-mini` |
| Windows CRLF 换行 | 记事本直接保存 |

可选覆盖：`WW_FIXTURE_CLAUDE_MODEL` / `WW_FIXTURE_CLAUDE_USER_AGENT` / `WW_FIXTURE_GEMINI_MODEL`；
设 `WW_FIXTURE_WITH_THINKING=1` 额外采 Claude 思维链样本。

### T04 DeepSeek 在线验证

DeepSeek 官方 API 使用 OpenAI 兼容 SSE，可真实验证 T04 的 parser 与 OpenAI adapter，无需 Claude/Gemini Key。Windows 显式运行 `powershell -ExecutionPolicy Bypass -File fixtures/tools/run-deepseek-live.ps1` 才会联网。脚本从被忽略的 `fixtures/.env.local` 读取 `WW_T04_DEEPSEEK_API_KEY`（或当前进程的 `DEEPSEEK_API_KEY`），不输出 Key。本次官方 `api.deepseek.com` / `deepseek-flash` 在线请求通过；脚本强制禁用 Gradle 测试缓存，确保每次确实发送 API 请求。

### 采/生成存档样本

```bash
# minimal：全新安装的导出（已生成）
cd E:/WatchWorld/WatchWorld
npx tsx E:/WatchWorld/WatchWorld-Native/fixtures/tools/generate-minimal-archive.ts

# rich：在应用里「设置 → 备份与同步 → 导出全部数据」，拿到 JSON 后过三个脚本
cd E:/WatchWorld/WatchWorld-Native
node fixtures/tools/redact-archive.mjs         <导出的.json> /tmp/rich-1.json   # ① 凭据 + 身份
node fixtures/tools/scrub-archive-messages.mjs /tmp/rich-1.json /tmp/rich-2.json # ② 对话派生文本
node fixtures/tools/neutralize-endpoints.mjs   /tmp/rich-2.json fixtures/sse/archive-v5-rich.json  # ③ 个人端点
```

⚠️ **三步都要跑，而且分属三类不同的东西**（互不重叠，少一步就漏一类）：

| 脚本 | 负责 | 不管 |
|---|---|---|
| `redact-archive.mjs` | 凭据（`apiKey`/`password`/`authToken`）+ 身份（`username`） | URL、对话内容 |
| `scrub-archive-messages.mjs` | 对话派生文本：消息正文、`summaries[].content`、`timelines[].plotSummary` | 凭据、URL |
| `neutralize-endpoints.mjs` | 个人端点：`webdavConfig.url`、`mcpServers[].url`（含路径里的账号 slug） | `providers[].baseUrl`（**故意**不动） |

> 📌 **这个「三类分工」是踩出来的**：一开始只有前两个脚本，于是 2026-10-07 那次真实导出里
> ①`username` 里的**邮箱**、②`summaries[].content` 里那段 **803 字的对话总结**、
> ③`/mcp/andunya` 里的**账号名**，三者全都一路通过。它们看着分属不同对象，
> 但用同一句话就能归到一起：**「这段内容是不是从你这个人或这次对话来的？」**
> —— 是，就不该进仓库。别再按「字段属于哪个对象」判断。

#### rich 的先「聊出内容」再导出 —— 采集清单

⚠️ **2026-10-06 那次导出之所以作废，是因为导出时浏览器是全新状态**（结果与 minimal 逐字段等价、0 条消息）。
存档是**从 localStorage 读的**，所以必须用**你平时真正在用的那个源**导出，不是新开的无痕窗口。

📌 **现状（2026-10-07）**：`archive-v5-rich.json` 已入库，取自同一天**最后一次**导出
（2 会话 / 31 消息 / 28 版本），下表 **#1–#6、#8–#12、#14、#16 的大部分**都已覆盖。
**仍缺三项：#7 子分支、#13 推入通用、#15 带 token 的 MCP**（外加 #12 的 `aiContextCustomLimit`、
#16 的 `agents[].memory`）—— 这几项暂由合成用例 `fieldsMissingFromTheFixturesSurviveRoundTrip` 顶着。

导出前逐项做一遍。下表是**合成**用例目前替真实样本顶着的地方
（legacy 是**旧版**形状、minimal 是**空档**、rich 已覆盖大半）：

| # | 要在应用里做什么 | 覆盖的冷门字段 | 现状 |
|---|---|---|---|
| 1 | 至少一个窗口**聊出 5 条以上**消息 | 有内容的 `sessions[].messages` | ✅ rich（31 条） |
| 2 | 对某条助手消息**滚动重答一次**（产生第 2 版并切换过去） | `currentVersionIndex > 0`、`versions[]` 多元素 | ✅ rich（**7 条**多版本，最多 4 版；且暴露了 `versions[0]` 可以是空串） |
| 3 | 用支持思维链的模型回一轮 | `versions[].reasoning` | ✅ rich（9 个版本带思维链） |
| 4 | 生成一次**前文总结** | `summaries`、`lastSummarizedFloor` | ✅ rich（`summaries=1`、`lastSummarizedFloor=29`） |
| 5 | 跑一次「**分析角色关系**」 | `isAnalysis` / `analysisVisibility` / `analysisBranchCode` | ✅ rich |
| 6 | 让模型提出一次**描述标签变更，并保持不审批** | 非空的 `timelineDescriptionTagRequests` | ✅ rich（1 条待审批） |
| 7 | 造出一个**子分支**（`parentId` 非空） | `timelines[].parentId`（`forkMessageId` 见注） | ⬜ **仍缺** |
| 8 | 窗口里**勾选一篇世界书文档** | `connectedWorldDocIds` | ✅ rich（3 篇） |
| 9 | 窗口里**启用一条正则规则** | `regexRuleIds` | ✅ rich（2 条） |
| 10 | 发一条**带图片**的消息 | `imageIds`、`imageContextMode` | ✅ rich |
| 11 | **关掉某个窗口的流式开关**，另开一个窗口保持开启 | 显式 `streamingEnabled: false` 与 `true` 并存 | ✅ rich（两会话一关一开） |
| 12 | 改一次窗口级**工具轮数上限 / 上下文可见性 / 标签显示模式 / 联网开关** | `toolCallStepLimit` / `aiContextCustomLimit` / `tagDisplayMode` / `webSearchEnabled` | 🟡 rich 有 `toolCallStepLimit=0` / `tagDisplayMode=desc` / `webSearchEnabled=true`；**`aiContextCustomLimit` 仍缺** |
| 13 | 用一次「**推入通用**」 | `isCommonPushed`、`systemPromptInjectedCommon` | ⬜ **仍缺** |
| 14 | 保留**两个以上会话** | 多 session（`pinned` 见注） | ✅ rich（2 个） |
| 15 | 配一个带 **token 的 MCP 服务**并测一次连接 | `authToken` / `headers` / `lastError` / `serverVersion` | 🟡 rich 有 1 个 MCP（18 个工具、`serverVersion`），但**没有 `authToken`/`headers`** |
| 16 | 给某个顾问填一次**长期记忆**并绑定一个 Skill | `agents[].memory` / `skillIds` | 🟡 rich 有 `skillIds`；**`agents[].memory` 仍缺** |

> ⚠️ **有两个字段永远采不到 —— 它们是死字段**（2026-10-07 全仓核实：只出现在 `src/types.ts` 的类型声明里，
> 没有任何 UI / handler / 业务逻辑写它们）：
>
> | 字段 | 结论 |
> |---|---|
> | `ChatSession.pinned` | **产品里根本没有「置顶」功能**（用户 2026-10-07 确认）。纯历史遗留 |
> | `TimelineBranch.forkMessageId` | 没有任何代码写它；分支的父子关系只靠 `parentId`（写入点在 `App.tsx:920`） |
>
> 它们只能由合成用例覆盖（`ArchiveRoundTripTest` 里已经覆盖了），**别试图采集**。
> 清单里**其余字段已逐个核实确有写入方**，都是「待采」而不是「采不到」—— 别把这两种情况混为一谈：
> 前者是待办，后者要标成「受限于上游功能，等修好再采」，否则以后翻到这张表会以为是漏做的。

**3–4、6、7、10 价值最高** —— 覆盖的是结构最复杂、最容易迁错的字段
（思维链、总结的楼层区间、未审批队列、嵌套分支、图片引用 id）。

> 导出后**先脱敏再提交**，三个脚本都要跑（分工见上面的表格）。
> 跑完请**自己再扫一遍** —— 脚本只找它认得的东西，而「认得」这个清单是靠踩坑长出来的：
>
> ```bash
> # 期望全部为 0（1tim 那条是 providers[].baseUrl，见下）
> for pat in "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}" "sk-[A-Za-z0-9]\{8,\}" "用户:" "windlife" "jianguoyun"; do
>   printf "%-46s %s\n" "$pat" "$(grep -oE "$pat" fixtures/sse/archive-v5-*.json | wc -l)"
> done
> ```
>
> ⚠️ 别图省事用 `scrub-archive-messages.mjs --include-text` —— 它会连**已验证为内置默认值**的长文本
> （世界书正文、Skill 指令、提示词）一起抹掉，把样本变瘦。**窄修比广抹好。**
>
> 📌 **保留了什么、为什么**（这些是**有意**留下的，不是漏抹）：
>
> | 留下 | 理由 |
> |---|---|
> | `providers[].baseUrl`（含 `https://ai.1tim.com/v1`） | 服务端点而非账号标识；且 Provider 配置（T04/T15）要靠真实形状的 baseUrl 测请求拼接 |
> | 会话标题（如「随便聊聊」）、Provider/分组名 | **配置标签**，不是对话转写。且标题要测非默认值 |
> | `timelines[].descriptionTag` = `主线·军工部海伦娜` | 这是**剧情标签**，但「海伦娜」「军工」两个词都出自应用**自带的**默认世界书文档 —— 不含额外身份信息；`·` 还是标签嵌套的分隔符，正需要真实样本 |
>
> 判断口径只有一句：**「这段内容是不是从『你这个人』或『这次对话』来的？」**
> 是 → 必须处理；否（配置标签、公共端点、内置默认值）→ 可以留。

---

## 当前状态（更新于 2026-10-07）

| 文件 | Provider | 状态 |
|---|---|---|
| `archive-v5-minimal.json` | — | ✅ **已生成**：React 自身导出，`version=5`，1 session（`messages` 为空、含 `timelines`） |
| `archive-v5-legacy.json` | — | ✅ **已生成**：来自 2026-10-01 的真实旧导出（**16 个顶层字段**，缺 `worldGroups`/`appPreferences`）。消息正文已抹，结构全保留 |
| `archive-v5-rich.json` | — | ✅ **已入库（2026-10-07）**：来自 `Downloads/watchworld-backup-2026-10-06 (2).json`（同一天**第三次**导出，用户逐轮补齐功能后重导）。**2 会话 / 31 消息 / 28 版本**，含 7 条多版本滚动重答消息、9 个带思维链的版本、前文总结、待审批标签队列、图片引用、世界书与正则绑定、两个会话一关一开的流式开关。<br>⚠️ 更早的两次导出（`… (1).json` 6 条消息、`…json` 无总结）都已被它**取代** —— 三次同名、只差后缀，差别巨大，别再认错 |
| `openai-stream.txt` | OpenAI 兼容 | ✅ 已采集并通过校验 |
| `openai-toolcall-stream.txt` | 同上 | ✅ 已采集并通过增量与工具参数分片校验 |
| `claude-stream.txt` | Anthropic 原生 | ✅ 用户确认外部样本纯度可信且通过严格格式校验；原始文件未放入本仓 |
| `claude-toolcall-stream.txt` | Anthropic 原生 | ⬜ 仓库尚无可重放的 Claude 工具调用样本 |
| `claude-thinking.txt` | Anthropic 原生 | ⬜ 尚未采集 |
| `gemini-stream.txt` | Google 原生 | ⬜ 未配置可用 Key |
| DeepSeek 官方 API 在线 smoke test | DeepSeek 原生 OpenAI 兼容 | ✅ `api.deepseek.com` / `deepseek-flash`，真实 parser + adapter 请求通过 |

### 本次采集来源

| 文件 | 日期 | 模型 | 备注 |
|---|---|---|---|
| `openai-stream.txt` | 2026-10-06 | `deepseek-v4.1-flash` | OpenAI 兼容中转；真实多帧增量 |
| `openai-toolcall-stream.txt` | 2026-10-06 | `deepseek-v4.1-flash` | OpenAI 兼容中转；含 `get_weather` 参数分片 |
| `archive-v5-legacy.json` | 2026-10-01 | gemini / claude 混合 | 源 `Downloads/omnichat-backup-2026-10-01.json`（`exportTime=1790845299137` 已逐字段核对一致）；5 条消息、16 个顶层字段 |
| `archive-v5-rich.json` | 2026-10-06 | `claude-sonnet-4-6` / `deepseek-v4.1-flash` | 源 `Downloads/watchworld-backup-2026-10-06 (2).json`（`exportTime=1791310214190`）；2 会话、31 消息、28 版本、18 个顶层字段。**唯一有真实历史的样本** |
| `archive-v5-minimal.json` | 2026-10-06 | — | 由 React 自身 `Storage.exportAllData()` 生成（**不是**人工导出），新装空档 |

⚠️ **rich 里的占位符**（都是脱敏留下的，不是脏数据）：

| 占位符 | 出现在 | 由谁写 |
|---|---|---|
| `REDACTED_TEST_KEY` | `providers[].apiKey`、`webdavConfig.password` | `redact-archive.mjs` |
| `REDACTED_TEST_USER` | `webdavConfig.username`、MCP 端点路径里的账号段 | `redact-archive.mjs` / `neutralize-endpoints.mjs` |
| `REDACTED_TEST_HOST` | `webdavConfig.url`、`mcpServers[].url` | `neutralize-endpoints.mjs` |
| `[[REDACTED_MESSAGE_BODY len=N]]` | 消息正文 / `versions[].content` / `reasoning` / `summaries[].content` / `timelines[].plotSummary` | `scrub-archive-messages.mjs` |

> 两个 `apiKey` 占位符是**非空**的，而 legacy 那份的 `apiKey` 是**空串** —— 这是**故意**的：
> 空串与非空串是两条不同的兼容路径，两份合起来把两条都测到（见 `redact-archive.mjs` 开头「只替换非空值」）。

采集脚本会把基础地址自动规范化为 `/v1/messages`，并附带配置的 Bearer 鉴权和 `User-Agent`。默认模型 `claude-sonnet-4-5` 曾返回 HTTP 503；配置 `claude-opus-4-8` 后，中转站明确返回 `model_not_found`（该账号/分组没有可用渠道）。用 `/v1/models` 列出的 DeepSeek ID 探测时虽返回 HTTP 200，但没有 `content_block_delta` 正文，因此不能作为有效 Claude fixture。HTTP 错误响应放在被 `.gitignore` 忽略的 `fixtures/sse/_rejected/`，不会被默认校验器扫描或提交。

`fixtures/.env.example` 已提供 `WW_FIXTURE_CLAUDE_MODEL` 配置项；需要填中转站 Claude 兼容模式实际支持的模型 ID，不能假设 OpenAI 模型列表中的 ID 可用于 Anthropic 协议。
用户确认 `model_not_found` 的原因是反代号池额度已耗尽；额度恢复后可用当前本地配置重试。

**Claude 样本验收补充**：用户确认其外部样本纯度可信，格式不正确会被严格校验拒绝，因此接受其 Claude 文本样本已通过格式/纯度检查。该原始样本文件尚未复制到本仓；上表的 `claude-stream.txt` 行记录的是外部样本验收结论，不代表此路径下已有文件。

> ⚠️ **minimal 里没有"有消息的 session"是正常的** —— 新装导出的 `INITIAL_SESSION.messages` 就是 `[]`。
> `BASELINE_COLLECTION.md` §D.5 那条判据对它不适用；它的用途正是**测边界（空数组、空消息）**。

### `archive-v5-legacy.json` 的独有价值（T05/T06 必看）

它是**旧版形状的真实导出**，比 minimal/rich 各多测出三件真实存在的事：

1. **`connectedSkillIds` 遗留字段**（值 `["skill-timeline-analysis","skill-role-relationship"]`）——
   正是 `normalizeChatSession` ① 要剔除的那个（`MIGRATION_CONSTRAINTS.md` §2）。
2. **顶层缺 `worldGroups` 与 `appPreferences`** —— 测「字段不存在」而不是「字段为空」。
3. **消息 `versions` 存放位置不对称**（最容易被朴素迁移写错）：

   | 角色 | `versions` | 正文在哪 | `latencyMs` |
   |---|---|---|---|
   | assistant | 长度 1 | `versions[0].content` | 有 |
   | user | **空数组 `[]`** | **顶层 `content`** | **无（可选字段）** |

   → Kotlin 侧 `versions` 必须允许空数组；user 消息不得假设 `versions[0]` 存在。

> 正文已用 `scrub-archive-messages.mjs` 抹除（占位符 `[[REDACTED_MESSAGE_BODY len=N]]`，保留长度线索）。
> **其余长文本（agents.systemPrompt / prompts / knowledgeBase / skills / worldDocuments）
> 经核对全是内置默认值，非用户隐私**，故按约定未动。
>
> ⚠️ **2026-10-07 更正**：`timelines[].plotSummary` **不在**上一句的「其余长文本」之列 ——
> 它是应用自动生成的「`用户: … | 回复: …`」日志，**含用户原话**。它是**逐字比对了 rich 与 minimal 的
> 全部非会话文本字段后才发现的**（两者逐字节相同 → 说明是默认值；只有 `plotSummary` 不是）。
> 已把 legacy 与 rich 两份的 `plotSummary` 一并抹掉，`grep -c "用户:" fixtures/sse/archive-v5-*.json` 现为全 0。
> **教训**：「核对过是默认值」这句话要**逐字段**成立，别按字段名的直觉归类。
>
> 🔧 **当天就把这个教训变成了工具改动**（同一类问题在 `username` 上又犯了一次，见上面「三步分工」的表）：
> `scrub-archive-messages.mjs` 现在**默认**就抹 `summaries[].content` 与 `timelines[].plotSummary`
> （判据从「字段属于哪个对象」换成「这段文本是不是从对话派生的」），不再需要手工补刀。

**T04 的硬前置是 `openai-*` 与 `claude-*` 这两组**（T04 写 SSE 解析器 + 三家归一化 + fixture 单测）。
`archive-v5-rich.json` 曾是 **T06 的硬前置**：**2026-10-07 已补上**（见上表），T06 现在用
**legacy + minimal + rich** 三份跑往返。rich 仍未覆盖的字段列在上面清单的「⬜ / 🟡」行。

---

## 采集判据（BASELINE_COLLECTION §A.3）—— 已脚本化

`node fixtures/tools/check-fixtures.mjs` 会逐条检查：

- [ ] 有**多行** `data:` 开头的行（不是一行全量 JSON）
- [ ] 末行是 `data: [DONE]`（OpenAI）或含 `message_stop`（Claude）或含 `finishReason`（Gemini）
- [ ] 每行 JSON 里有 `delta` / `content_block_delta` / `candidates` 字样
- [ ] **不同行的内容确实是增量**：帧数 ≥ 3、去重后 ≥ 2 种、且不能"一帧就是全文"
- [ ] **文件名与内容一致**：文件名声称 claude/gemini、内容却是 `choices` 形状 → 判 FAIL（中转站归一化）

踩坑特征：只有 1–2 行 → 上游关了 stream 或 curl 缺 `-N`；乱码 → 带了 gzip；完整 JSON 对象 → Gemini 忘加 `?alt=sse`；
文件名是 claude 但内容是 `choices` → **中转站把原生协议归一化了，绝对不能当 Claude fixture**。

> 该校验器已用「合格 / 一次性到达 / 多行但每行全量」三类样本自测过，
> **不是永远 PASS 的空壳**：那个「多行但每行都是全文」的陷阱会被它精确判 FAIL（帧数=3 去重=1）。

---

## ⚠️ 存档样本必须脱敏

`archive-v5-*.json` 含明文 API Key 与 WebDAV 密码。**用 `redact-archive.mjs` 处理**，它会：

1. 把 `apiKey` / `password` / `authToken` / `accessToken` 等**非空值**替换为 `REDACTED_TEST_KEY`；
2. **保留空值不动**（`''` 是 T06 必须测的兼容路径，填成占位符反而破坏它）；
3. 再扫一遍 `sk-` / `sk-ant-` / `AIza` / `Bearer` / 非占位符 `password|apiKey`，有残留就**拒绝写入**并退出码 1。

手动复核（等价命令）：

```bash
grep -nE 'sk-|Bearer |password' fixtures/sse/archive-v5-*.json
```

---

## 采集来源记录（填这里）

| 文件 | Provider | 模型 | 采集日期 | 备注 |
|---|---|---|---|---|
| openai-stream.txt | | | | |
| openai-toolcall-stream.txt | | | | |
| claude-stream.txt | | | | |
| claude-toolcall-stream.txt | | | | |
| claude-thinking.txt | | | | |
| gemini-stream.txt | | | | |

> 这张表比报文本身还重要 —— 三个月后没人看得懂这些文件是干什么的。

---

## 采集完成后的收尾

1. **纳入 git**（它们是回归资产，不是临时文件）。
   `.gitignore` 的 `.env*` 规则只匹配 `.env` 开头的文件，**不会**误伤 `archive-v5-*.json`。
2. 在 `memory/kotlin-migration.md` 的「尚未完成的前置准备」里把对应项划掉。
3. 回到 `KOTLIN_MIGRATION_PLAN.md` §8.1 —— 有了真实样本，那行「用真实抓包样本作 fixture」才真正可落地。
