# Kotlin 迁移 · 前置准备一览（kotlin-migration.md）

> ⚠️ **进度权威在 `../WORKPLAN.md` 的 §一 完成记录**，本文件不重复记录任务进度。
> 本文件只回答一个问题：**"还差什么才能开工 / 进入某一阶段？"**

---

## 尚未完成的前置准备（`docs/BASELINE_COLLECTION.md`）

这些是**无法事后补拍**的资产，缺了就无法验证迁移是否变好。

| 项 | 内容 | 落在哪 | 被谁需要 | 状态 |
|---|---|---|---|---|
| **D** | 存档样本 `archive-v5-rich/minimal/legacy.json`（已脱敏） | `fixtures/sse/` | T06 往返测试 | ✅ **三份齐了**（2026-10-07）。rich = 2 会话 / 31 消息 / 28 版本的真实导出，覆盖清单 #1–#6、#8–#12、#14、#16；仍缺 #7 子分支 / #13 推入通用 / #15 带 token 的 MCP。⚠️ 采集时**连续认错两次文件**（当天导出三次、名只差后缀） |
| **A** | 三家 SSE 报文（+ 工具调用流） | `fixtures/sse/` | T04 Provider 单测 | 🟡 **OpenAI 兼容文本流 + 工具调用已采集并通过校验**；Claude 原生样本待确认可用模型 ID；Gemini 尚无 Key |
| **B** | 首 token 延迟基线（两条路径各 3 次） | `fixtures/baseline-latency.md` | T03 真流式验收对比 | [ ] |
| **C** | 主题截图 + 色值标注（≥2 套） | `fixtures/theme-baseline/` | T12 主题走查 | [ ] |

### 采集工具（`fixtures/tools/`）—— 2026-10-06 就绪

| 脚本 | 作用 |
|---|---|
| `collect-sse-fixtures.sh` | 一键采集三家 SSE（只采有 Key 的），采完自动校验；**不打印 Key** |
| `check-fixtures.mjs` | 把 §A.3 / §D.5 判据变成可执行检查（Provider 靠内容嗅探；已用三类样本自测，非空壳）。**含「文件名 vs 内容」一致性守卫**：文件名声称 claude、内容却是 `choices` 形状 → FAIL（阻止中转站归一化报文被当成 Claude fixture） |
| `generate-minimal-archive.ts` | 用 React 自身 `Storage.exportAllData()` 生成 minimal（保证形状同源） |
| `redact-archive.mjs` | **密钥**脱敏 + 残留扫描（只替换非空值，保留「字段存在但值为空」路径） |
| `scrub-archive-messages.mjs` | **对话内容**脱敏（抹消息正文，保留全部结构；`--include-text` 可选抹其它自由文本） |

```bash
# A：采 SSE（密钥写在 fixtures/.env.local，不用 export）
bash fixtures/tools/collect-sse-fixtures.sh          # 先 --dry-run 可零成本验证 Key 有没有读到
bash fixtures/tools/collect-sse-fixtures.sh --dry-run
# D-rich：应用里导出后，先脱密钥、再抹正文
node fixtures/tools/redact-archive.mjs <导出的.json> fixtures/sse/archive-v5-rich.json
node fixtures/tools/scrub-archive-messages.mjs fixtures/sse/archive-v5-rich.json fixtures/sse/archive-v5-rich.json
```

⚙️ **密钥放在 `fixtures/.env.local`**（已建，已被 `.gitignore` 忽略）：
采集脚本是被 Freebuff 的 shell 执行的，**在别的终端 `export` 不会被继承**。

🔌 **中转站用法（2026-10-06 实测）**：用户的密钥都是中转站的，走 OpenAI 兼容 + Bearer 即可，填
`WW_FIXTURE_OPENAI_BASE` / `WW_FIXTURE_OPENAI_KEY` / `WW_FIXTURE_OPENAI_MODEL` 三行。
`.env.local` 的解析器已容忍手工格式：双/单引号、行尾空格、行尾注释、`export` 前缀、CRLF（均已实测）。
⚠️ Claude 只有在中转站返回 Anthropic 原生事件时才能作为 `claude-*` fixture。省略
`WW_FIXTURE_CLAUDE_MODEL` 会使用 `claude-sonnet-4-5`；指定 `claude-opus-4-8` 后返回
`model_not_found`；用户确认原因是反代号池额度耗尽。DeepSeek 模型 ID 虽能返回 HTTP 200，
但没有正文 delta，也不能作 fixture。额度恢复后用现有 Claude 配置重试。

> ⚠️ **A 尚未完整**：已可用的 OpenAI fixtures 可用于对应解析/适配测试；Claude 与 Gemini 的
> 适配单测必须等各自真实协议样本，不能用中转站归一化报文凑数。
> **D 已齐**：T06（2026-10-07）用 `legacy + minimal + rich` 三份跑往返。legacy 是**旧版**真实导出；
> minimal 是**空档**；rich 是**当前格式的真实导出**（2 会话 / 31 消息 / 28 版本，含多版本滚动重答、
> 思维链、前文总结、待审批标签队列、图片引用、世界书/正则绑定、一关一开的流式开关）。
> 仍缺 5 项（子分支 / 推入通用 / 带 token 的 MCP / `aiContextCustomLimit` / `agents[].memory`），
> 暂由 `ArchiveRoundTripTest` 的**合成**用例顶着，逐项状态见 `../fixtures/sse/README.md`。
> 🚨 **采样本必读 —— 脱敏要跑满三个脚本，少一个就漏一类**：
> `redact-archive.mjs`（凭据+身份，含 `username`/邮箱）/ `scrub-archive-messages.mjs`（对话派生文本：
> 消息正文 + `summaries[].content` + `timelines[].plotSummary`）/ `neutralize-endpoints.mjs`（个人端点）。
> 判据是「**这段内容是不是从人或对话来的**」，**不是**「字段叫什么名字」—— 后者一天内漏了四样东西。

**时间紧就只做 D + A**（`BASELINE_COLLECTION.md` §E.1：D 成本最低价值最高，A 最关键）。

## 采集阻塞关系

```
T04 SSE 解析/Provider 单测  ← A（必须）
T03 真流式验收（对比基线）  ← B（强烈建议）
T06 v5 存档往返测试         ← D（必须）  ✅ 2026-10-07 已用 legacy+minimal 完工；rich 待补
T12 主题视觉走查            ← C（必须，否则只能凭记忆）
```

即：**A 与 D 是硬前置**，B 与 C 是"缺了会难受"。

## 未决决策（`docs/KOTLIN_MIGRATION_PLAN.md` §11）

| # | 决策 | 建议 | 定否 |
|---|---|---|---|
| 1 | 存储方案 | Room KMP | [ ] |
| 2 | Markdown 库 | compose-markdown | [x] 已定 2026-10-08：mikepenz `multiplatform-markdown-renderer` 0.45.0（0.42+ 原生流式 `StreamingMarkdownState`） |
| 3 | 是否保留 Worker | 保留作搜索回退 | [ ] |
| 4 | 推进顺序 | 桌面优先 | [x] 已定 |
| 5 | 旧 React 版 | 保留到 P4 结束 | [x] 已定 |
| 6 | 功能范围 | 全量对等 | [ ] |

## 变更记录

- 2026-10-08　**决策 #2 定案**：Markdown 库选 mikepenz `multiplatform-markdown-renderer`（+ `-m3`）0.45.0 ——
  Compose 原生/KMP，且 0.42+ 自带 `StreamingMarkdownState`（追加式，只重解析不稳定尾部），正对 PLAN §7.3。
  已实测与本仓 Compose MP 1.12.1 / Kotlin 2.4.20 编译兼容。T13 见 `../tasks/T13-Markdown流式渲染.md`。

- 2026-10-07　**T06 完工**（`legacy + minimal + rich` 三份真实存档往返全过）：**D 改标 ✅ 已齐** ——
  rich 其实当天就导出成功了（`Downloads/watchworld-backup-2026-10-06 (1).json`），是文档**看错了文件**
  （同名两次导出，无内容的是第一次）。同日补记两条必须知道的实现约束 —— ① 存档归一化**分解码前/解码后两层**，
  少一层会静默漏规则；② `normalizeChatSession` 的 `rawToolLimit` 默认参数**必须**用模型自身值回推
  （幂等，否则导出会吃掉 `toolCallStepLimit`）。另发现并修掉一处脱敏漏洞：`timelines[].plotSummary`
  含用户原话，而脱敏脚本默认不处理它。详见 `../tasks/T06-v5存档往返.md`、`gotchas.md`、`../fixtures/sse/README.md`。
- 2026-10-06　采集脚本适配**中转站**（OpenAI 兼容 + Bearer）；实测 `.env.local` 解析器容忍引号/行尾空格/注释/export/CRLF；
  校验器新增「文件名 vs 内容」一致性守卫；A 改标 🟡。
  ⚠️ 核实：用户 2026-10-06 那次导出与 minimal **逐字段等价**（0 消息），rich 仍未拿到。
- 2026-10-06　已采集并校验 OpenAI 兼容文本流与工具调用流；Claude 默认模型 503、指定 Opus ID 因号池额度耗尽不可用，
  已列出的 DeepSeek ID 无正文增量；补齐自定义 Claude User-Agent、采集失败隔离、工具参数分片校验和非零失败退出。
- 2026-10-06　新增「采集工具」小节；D 标记为 🟡（minimal 已生成，rich 待导出）。
- 2026-10-06　建立；同日收敛为"前置准备一览"，进度移交 `../WORKPLAN.md`。
