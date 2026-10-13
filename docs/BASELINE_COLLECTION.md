# 迁移前基线采集清单（BASELINE_COLLECTION.md）

> **目的**：在动 Kotlin 之前，采集三类"无法事后补拍"的可验证资产。
> **理由**：这三样东西**读代码推不出来**——它们记录的是"当前真实行为"。
> 迁完之后若没有它们，你**无法判断迁移是否真的变好了**，也**无法验证解析逻辑对不对**。
> **总耗时**：约 60–90 分钟。

---

## 0. 为什么必须现在做

| 资产 | 不采的后果 | 采集耗时 |
|---|---|---|
| **A. 三家 Provider 的真实 SSE 报文** | Kotlin 版解析器写完后**无法验证对错**，只能靠"看起来能跑" | 30 min |
| **B. 首 token 延迟基线** | 迁完后**无法判断真流式是否真的更快**，也无法证明 Worker 方案是否够用 | 15 min |
| **C. 主题截图对照** | Compose 主题重建时**只能凭记忆**，288 行 CSS 变量对应关系会错 | 30 min |

**A 最关键**。`nativeToolChat.ts` 的解析逻辑是对的，但没有真实报文就无法证明 Kotlin 版复刻得对 —— 而流式解析 bug 极难排查（错一个字段就静默丢内容）。

---

## A. 采集三家真实 SSE 报文

### A.1 存放位置

在仓库新建目录（**建议纳入 git，这是测试 fixture**）：

```
E:\WatchWorld\WatchWorld-Native\fixtures\sse\        ← 原生仓（见 FILE_LAYOUT.md §2 决策 2）
├── openai-stream.txt          ← OpenAI 兼容（含 deepseek / openrouter / groq）
├── claude-stream.txt          ← Claude（含 thinking + tool_use）
├── gemini-stream.txt          ← Gemini（含 thought + functionCall）
├── openai-toolcall-stream.txt ← 带工具调用的流（重要！分片累积逻辑）
├── README.md                  ← 记录：模型名、日期、用了什么 prompt
└── archive-v5-sample.json     ← 见 §D
```

### A.2 采集方法（curl，最可靠）

**不要用浏览器抓包** —— 会拿到 gzip 压过的、或被前端处理过的内容。
用 `curl` 直接打上游，`--no-buffer` 保证不缓冲：

**① OpenAI 系（以 deepseek 为例）**

```powershell
# 流式文本
curl.exe -N -X POST https://api.deepseek.com/v1/chat/completions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $env:DEEPSEEK_API_KEY" `
  -d '{\"model\":\"deepseek-chat\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"从1数到5，每个数字后面加一句话\"}]}' `
  -o fixtures\sse\openai-stream.txt
```

**② Claude**

```powershell
curl.exe -N -X POST https://api.anthropic.com/v1/messages `
  -H "Content-Type: application/json" `
  -H "x-api-key: $env:ANTHROPIC_API_KEY" `
  -H "anthropic-version: 2023-06-01" `
  -d '{\"model\":\"claude-sonnet-4-5\",\"max_tokens\":200,\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"从1数到5，每句加一个短句\"}]}' `
  -o fixtures\sse\claude-stream.txt
```

**③ Gemini**（注意 `?alt=sse`，不加会返回 JSON 数组而非 SSE）

```powershell
curl.exe -N -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$env:GEMINI_API_KEY" `
  -H "Content-Type: application/json" `
  -d '{\"contents\":[{\"parts\":[{\"text\":\"从1数到5，每句加一个短句\"}]}]}' `
  -o fixtures\sse\gemini-stream.txt
```

**④ 带工具调用的流（最重要的一份）**

OpenAI 系和 Claude 各采一份。用一个**必定触发工具调用**的 prompt，并提供假工具：

```powershell
# OpenAI 格式，提供一个假工具，明确要求调用
curl.exe -N -X POST https://api.deepseek.com/v1/chat/completions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $env:DEEPSEEK_API_KEY" `
  -d '{\"model\":\"deepseek-chat\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"请调用 get_weather 工具查询北京的天气\"}],\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"description\":\"查询天气\",\"parameters\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}}}]}' `
  -o fixtures\sse\openai-toolcall-stream.txt
```

**⑤ 思维链样本（若你的 Provider 支持）**

DeepSeek-R1 系、Claude 的 extended thinking、Gemini 的 thought 都产出思维链。**各采一份**，用于验证 §3.4 的思维链分流逻辑。

### A.3 采集成功的判据

打开生成的 `.txt`，**必须满足**：

- [ ] 有**多行** `data:` 开头的行（不是一行全量 JSON）
- [ ] 最后一行是 `data: [DONE]`（OpenAI）或含 `message_stop`（Claude）
- [ ] 每行 JSON 里有 `delta` / `content_block_delta` / `candidates` 字样
- [ ] **不同行的内容确实是增量**（同一字段在不同行里是不同片段）

**❌ 踩坑特征**：
- 文件只有 1–2 行 → 上游把 stream 关了，或 curl 缺 `-N`
- 内容是压缩的乱码 → 加了 `Content-Encoding: gzip` 之类，去掉
- 是完整 JSON 对象 → Gemini 忘了 `?alt=sse`

### A.4 写入 `fixtures/sse/README.md`

```markdown
| 文件 | Provider | 模型 | 采集日期 | 备注 |
|---|---|---|---|---|
| openai-stream.txt | deepseek | deepseek-chat | 2026-10-06 | 纯文本流 |
| openai-toolcall-stream.txt | deepseek | deepseek-chat | 2026-10-06 | 含 tool_calls 分片 |
| claude-stream.txt | anthropic | claude-sonnet-4-5 | 2026-10-06 | 含 content_block_* |
| claude-thinking.txt | anthropic | ... | ... | 含 thinking_delta |
| gemini-stream.txt | google | gemini-2.5-flash | 2026-10-06 | alt=sse |
```

**这份 README 比报文本身还重要** —— 三个月后你看不懂这些文件是干什么的。

---


## B. 采集首 token 延迟基线

### B.1 为什么要单独测

你的项目现在有**两条**流式路径，基线必须**分别记录**：

| 路径 | 条件 | 现状 |
|---|---|---|
| Worker 中转 | 设置里填了「API 中转服务器」 | 真流式 |
| 直连 + 回放 | 未填中转 | `replayAsStream()` 伪装，固定约 900ms 才显示完 |

**迁移后目标**：原生直连的真流式应**快于或等于 Worker 路径**。
没有基线，你就说不出这句话。

### B.2 测什么（三个数字）

| 指标 | 定义 | 采集方式 |
|---|---|---|
| **T1 首 token** | 从点发送 → 第一个字出现 | 秒表 / 录屏帧数 |
| **T2 完整输出** | 从点发送 → 回复结束 | 秒表 |
| **T3 帧间隔** | 每个字/块出现的间隔是否均匀 | 录屏观察 |

### B.3 采集步骤

**前置**：启用消息耗时显示（设置 → 显示设置 → 「显示耗时」），这样 `latencyMs` 会记在消息上。

**方法一：秒表法（够用）**

```
1. 起服务：cd E:\WatchWorld\WatchWorld ; npm run dev
2. 浏览器打开 http://localhost:3000
3. 新建窗口，发送固定 prompt（见 B.4）
4. 用手机秒表计时：
   - 按下发送的瞬间开始
   - 第一个字符出现的瞬间 → T1
   - 回复完全停止的瞬间 → T2
5. 重复 3 次，取中位数（不要取平均，首帧易受冷启动影响）
```

**方法二：录屏法（更准，推荐用于 T3）**

```
1. 用 Windows 自带「游戏录制 Win+G」或 OBS 录整个操作过程
2. 录完后逐帧看（推荐 PotPlayer 按 F 逐帧）
3. 记录：发送帧号 F0、首字帧号 F1、末字帧号 F2
4. T1 = (F1-F0)/fps，T2 = (F2-F0)/fps
5. 从逐帧记录里能直接读出帧间隔是否均匀 → T3
```

### B.4 固定测试 prompt（必须每次都一样）

```
从1数到20，每个数字单独一行，每行后面跟一句不超过10个字的短句描述。
```

**为什么用这个**：
- 输出长度适中（约 300–400 字），能体现流式的持续过程
- 内容**可预测**，便于判断是否被截断
- 产出**稳定的多帧**，便于观察帧间隔

### B.5 记录表（填到 `fixtures\baseline-latency.md`）

```markdown
# 首 token 延迟基线（迁移前）

采集日期：2026-10-06
测试 prompt：「从1数到20，每个数字单独一行，每行后面跟一句不超过10个字的短句描述。」
网络环境：<家庭宽带 / 4G / 5G>
模型：<provider / model 名>

## 路径 1：Worker 中转（真流式）

| 轮次 | T1 首token | T2 完整 | T3 帧间隔观察 |
|---|---|---|---|
| 1 | __ ms | __ ms | 逐字/顿挫/突发 |
| 2 | __ ms | __ ms | |
| 3 | __ ms | __ ms | |
| **中位数** | **__ ms** | **__ ms** | |

## 路径 2：直连 + 回放（未配中转）

| 轮次 | T1 首token | T2 完整 | T3 帧间隔观察 |
|---|---|---|---|
| 1 | __ ms | __ ms | 约900ms匀速完成 |
| 2 | __ ms | __ ms | |
| 3 | __ ms | __ ms | |
| **中位数** | **__ ms** | **__ ms** | |

## 参照基线（历史实测，来自 Worker 验证）

假上游每 300ms 推一帧，经 Worker 转发后实测帧间隔：
`311, 310, 313, 309, 310, 311, 314` ms（平均 311ms，贴近上游节奏）
→ 这说明 Worker 中转路径的真流式是成立的。

## 迁移后对比（留给未来填）

| 指标 | 迁移前（Worker） | 迁移后（Kotlin 原生） | 结论 |
|---|---|---|---|
| T1 | | | |
| T2 | | | |
| T3 帧间隔 | | | |
```

### B.6 成功判据

- [ ] 两条路径**各有 3 次**记录
- [ ] T1 有具体数字（不是"很快"这种描述）
- [ ] T3 有**主观描述**（"逐字" / "一阵一阵" / "突现"），因为体感本身是验收标准
- [ ] 记录了网络环境与模型（不同网络/模型延迟差异极大，不记就失去意义）

---

## C. 主题截图对照

### C.1 为什么要截

Compose 无 CSS 变量，`src/index.css` 的 288 行变量要逐个映射到 Compose 属性。
**光读 CSS 无法知道"哪个变量控制哪里"** —— 因为变量在多个组件里被复用。

截图是**唯一能验证"我映射对了"的证据**。

### C.2 截什么（最小集）

不需要全截。**截这几处就能覆盖 90% 的变量**：

| # | 界面 | 覆盖的变量 |
|---|---|---|
| 1 | 聊天主界面（有消息、含工具栏） | 背景、气泡、文字主副色、边框、强调色、按钮 |
| 2 | 侧边栏抽屉（含会话列表、分组） | 面板色、选中态、分割线、图标色 |
| 3 | 输入框区域（含模型选择、发送按钮） | 输入框底、占位符色、按钮激活/禁用态 |
| 4 | 任一设置弹窗（推荐 Provider 弹窗） | 弹窗底、遮罩、标题、表单控件、滚动条 |
| 5 | 消息气泡细节（含时间/耗时/模型名） | 小字色、角标、次要信息 |

### C.3 截几套主题

**至少 3 套**，覆盖明暗两极：

1. 一套**浅色**内置主题
2. 一套**深色**内置主题
3. **你自己的 DIY 主题**（`guanjie_custom_themes`，如果有）

### C.4 存放位置

```
E:\WatchWorld\WatchWorld-Native\fixtures\theme-baseline\   ← 原生仓（见 FILE_LAYOUT.md §2 决策 2）
├── light-chat.png
├── light-sidebar.png
├── light-input.png
├── light-settings.png
├── light-message-detail.png
├── dark-chat.png
├── dark-sidebar.png
├── ...
├── custom-*.png
└── README.md          ← 见 C.5
```

### C.5 关键：`fixtures\theme-baseline\README.md`

**这份 README 是截图的价值所在**。每张图必须标注来源：

```markdown
# 主题视觉基线

截取日期：2026-10-06　环境：Chrome / 1440x900 / 100% 缩放

## light-chat.png
- 主题：<主题名>（theme id: <从 localStorage omnichat_theme_palette 读>）
- 关键色值（从 DevTools 取）：
  - 页面背景：#______  ← 对应 CSS 变量 --______
  - 气泡（助手）：#______  ← --______
  - 气泡（用户）：#______  ← --______
  - 主文字：#______  ← --______
  - 次要文字：#______  ← --______
  - 边框：#______  ← --______
  - 强调色：#______  ← --______
```

**取色值的方法**：F12 → 选中元素 → 在 Styles 面板找到用了 `var(--xxx)` 的属性 →
记下变量名与**计算后的实际色值**。这一步是"把 CSS 变量和视觉对应起来"的关键。

### C.6 更省事的替代方案

如果嫌手动取色麻烦，可以**导出 CSS 变量快照**代替：

```js
// 浏览器控制台执行，把当前所有主题变量导出为 JSON
copy(JSON.stringify(
  Object.fromEntries(
    [...document.styleSheets]
      .flatMap(s => { try { return [...s.cssRules] } catch { return [] } })
      .filter(r => r.selectorText === ':root' || r.selectorText === 'html')
      .flatMap(r => [...r.style])
      .filter(p => p.startsWith('--'))
      .map(p => [p, getComputedStyle(document.documentElement).getPropertyValue(p).trim()])
  ), null, 2
))
```

把输出存成 `fixtures\theme-baseline\css-vars-<主题名>.json`。

**⚠️ 但注意**：快照只给你「变量名 → 色值」，**不告诉你这个变量用在哪儿**。
所以**截图 + README 标注**仍不可替代，两者都要。

### C.7 成功判据

- [ ] 至少 2 套主题（建议 3 套）各 5 张截图
- [ ] README 里每张图都标了**主题名 + 关键色值 + 对应变量名**
- [ ] 或有 `css-vars-*.json` 快照
- [ ] 记录了**截取环境**（浏览器/分辨率/缩放），否则截图无法复现

---

## D. 附赠：存档样本（强烈建议一并采集）

这个不在原计划的三项里，但**成本 2 分钟、价值极高**。

### D.1 为什么需要

`MIGRATION_CONSTRAINTS.md` §1/§2 说「v5 存档必须字节级兼容」。
但**"兼容"要用什么验证？** —— 没有真实样本，Kotlin 版的往返测试只能造数据，
而**造的数据不会包含你数据里的那些历史遗留字段**（那才是真正会炸的地方）。

### D.2 采集方法

```
1. 启动应用，打开设置 → 备份与同步
2. 点「导出全部数据」
3. 得到 JSON 文件 → 存为 fixtures\sse\archive-v5-sample.json
```

### D.3 ⚠️ 必须脱敏

导出的 JSON 里**含明文 API Key 和 WebDAV 密码**。存进 git 前必须处理：

```powershell
# 用项目自带的脱敏导出（如果 UI 有该选项），或手动替换
# 检查是否残留：
Select-String -Path fixtures\sse\archive-v5-sample.json -Pattern 'sk-|Bearer |password'
```

**替换成占位符**（保持字段结构不变，只换值）：

```json
{ "apiKey": "REDACTED_TEST_KEY", "password": "REDACTED_TEST_PASSWORD" }
```

**⚠️ 不要删掉这些字段** —— 字段必须存在（哪怕值是占位符），否则测不出"字段存在但值为空"的兼容路径。

### D.4 最好导两份

| 文件 | 内容 | 用途 |
|---|---|---|
| `archive-v5-rich.json` | 你**真实使用**的完整数据（脱敏后） | 测真实场景：大量会话、时间线树、世界书 |
| `archive-v5-minimal.json` | 全新安装未做任何配置的导出 | 测边界：空数组、缺失可选字段 |

**两份都要**。rich 测"复杂数据能否搬对"，minimal 测"空数据不炸"。

### D.5 成功判据

- [ ] 有 `archive-v5-*.json` 文件
- [ ] `version` 字段为 `5`
- [ ] 无明文密钥残留（用上面的 `Select-String` 检查过）
- [ ] 至少含 1 个有消息的 session
- [ ] 至少含 1 个带 timelines 的 session（如果有时间线记忆功能在使用）

---

## E. 执行顺序与总检查表

### E.1 建议顺序（按性价比）

```
1. D 存档样本        （2 min，价值极高）★ 先做
2. A SSE 报文        （30 min，最关键的 fixture）★ 最重要
3. B 延迟基线        （15 min）
4. C 主题截图        （30 min）
```

**如果时间紧**：只做 **D + A**。这两项直接决定迁移能否验证；
B 和 C 是"锦上添花"，缺了会难受但不致命。

### E.2 总检查表

```
fixtures/
├── sse/
│   ├── README.md                       [ ] 已建，标注模型/日期
│   ├── openai-stream.txt               [ ] 多行 data:，以 [DONE] 结尾
│   ├── openai-toolcall-stream.txt      [ ] 含 tool_calls 分片
│   ├── claude-stream.txt               [ ] 含 content_block_delta
│   ├── claude-thinking.txt             [ ] 含 thinking_delta（可选）
│   ├── gemini-stream.txt               [ ] 含 candidates[].content.parts
│   ├── archive-v5-rich.json            [ ] 已脱敏，version:5
│   └── archive-v5-minimal.json         [ ] 已脱敏，version:5
├── theme-baseline/
│   ├── README.md                       [ ] 每图标注主题名+色值+变量名
│   ├── light-*.png (5 张)              [ ] 聊天/侧栏/输入/设置/气泡细节
│   ├── dark-*.png (5 张)               [ ] 同上
│   ├── custom-*.png (5 张)             [ ] 可选
│   └── css-vars-*.json                 [ ] 可选，变量快照
└── baseline-latency.md                 [ ] 两条路径各 3 次记录
```

### E.3 采集完记得做的事

1. **把这批 fixture 纳入 git**（它们是测试资产，不是临时文件）

   ⚠️ 检查 `.gitignore` 里 `.env*` 规则**不会**误伤 `fixtures/sse/archive-v5-*.json`
   （那个规则只匹配 `.env` 开头的文件，不冲突，但**存脱敏后的文件仍然必须**）

2. **在记忆里记一笔**：更新 `memory/kotlin-migration.md` 的「尚未完成的前置准备」一节，
   把已完成的项划掉

3. **回到 `KOTLIN_MIGRATION_PLAN.md` §8.1**：
   那行「三家 Provider 适配单测 —— 用**真实抓包样本**作 fixture」
   现在有了 `fixtures/sse/`，这条可以真实落地了

---

## F. 常见问题

**Q: 我没有全部三家的 API Key 怎么办？**
只采你有 key 的那几家。**优先级：OpenAI 系 > Claude > Gemini**
（因为 OpenAI 格式最通用，很多中转站都用它）。缺的那家，Kotlin 版先不写适配器。

**Q: 报文里有我的 prompt，会不会泄露隐私？**
测试 prompt 是我给的通用文本（"从1数到5"），不涉及你的真实对话。
**但存档样本（§D）含你的真实会话内容** —— 如果介意，先用一个全新安装的干净档导出。

**Q: 截图存了多少张？太占空间。**
PNG 每张 200KB–1MB，15 张也就 5–15MB。**可以接受**，且这是必要成本。
若嫌大，可转 JPG（质量 85）—— 但**取色请以原始 PNG 为准**，JPG 有色偏。

**Q: 这些 fixture 迁移完成后还有用吗？**
有用。它们是**回归测试资产**：以后改解析逻辑、改存档格式，都要靠它们验证没改坏。
这也是为什么建议纳入 git。

---

**采集完成后，就可以开新窗口启动 `KOTLIN_MIGRATION_PLAN.md` 的 P0 了。**


