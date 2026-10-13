# T04 · SSE 解析与三家 Provider

| | |
|---|---|
| 任务号 | T04 |
| 日期 | 2026-10-06 |
| 状态 | 🚧 进行中 |
| 阶段 | 阶段 2 · 让流式「对」 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T04 |

> **后续任务不阻塞**：T04 当前未完成的 Gemini 真实流 fixture 与 Claude/Gemini 应用内实时请求验收，不影响后续功能开发。执行工作计划时，不必等待 T04 全部验收完成；T04 不是后续任务的阻塞条件，可按计划继续推进。

## 目标与可见效果

- **目标**：以 Kotlin 复刻 SSE 报文边界语义，并把 OpenAI、Claude、Gemini 的协议增量归一到统一事件。
- **可见效果**：UI 继续只显示正文增量；解析层同时保留思维链、工具调用、结束原因等事件，供 T09 接续消费。
- **验收标准**：DeepSeek/OpenAI-compatible 真实 fixture 与在线 smoke test 通过；Claude 用户确认的纯净样本通过其严格格式校验，且协议样例测试通过；Gemini 协议样例测试通过；Claude 缺少 `content_block_start` 时抛错；Gemini `thought == true` 进入思维链事件而非正文；`[DONE]` 与 `message_stop` 都能结束流。无需为测试购买 Gemini 额度。

## 改动

| 文件 | 改动 |
|---|---|
| `shared/src/commonMain/kotlin/com/watchworld/net/SseParser.kt` | 新增无第三方依赖的增量 SSE parser：多行 `data:`、EOF 最后一帧、错误帧与双结束条件。 |
| `shared/src/commonMain/kotlin/com/watchworld/net/ProviderStreamAdapter.kt` | 新增统一事件模型及 OpenAI / Claude / Gemini 协议 adapter；Claude 按 block index 校验 delta 起始事件。 |
| `shared/src/commonMain/kotlin/com/watchworld/net/ChatStreamClient.kt` | 把旧 OpenAI-only 行累积替换成 parser + adapter；保留原始 `streamSse` 与 UI 正文流接口。 |
| `shared/src/desktopTest/kotlin/com/watchworld/net/SseProviderAdapterTest.kt` | 覆盖 SSE 边界、OpenAI 真 fixture、Claude 错误边界和 Gemini 思维链/工具调用协议样例。 |
| `shared/src/desktopTest/kotlin/com/watchworld/net/DeepSeekLiveSmokeTest.kt` | 新增默认跳过的 opt-in 在线 smoke test；使用本机环境变量验证 DeepSeek-compatible 真流。 |
| `fixtures/sse/README.md`、`fixtures/.env.example`、`fixtures/tools/run-deepseek-live.ps1` | 增加官方 DeepSeek Key 配置位和 Windows opt-in 在线测试入口；强制绕过 Gradle 测试缓存，保证在线验证真实执行。 |
| `WORKPLAN.md` | 标记 T04 进行中。 |
| `memory/function-map.md` | 更新 Kotlin 协议层入口，并恢复 React 规格 §C。 |

## 心得

- 真实传输 `streamSse` 与协议解析要分层：parser 只接逐行 Flow，因此不会缓冲整个 HTTP body，也能用纯协议测试验证状态机。
- `[DONE]` / `message_stop` 必须在 parser 层停止上游 Flow，而不只是忽略后续事件；否则没有结束帧的持续连接可能一直占用请求。
- Claude adapter 必须按每个流实例化，因为 block-start 状态跨事件保存；不能把有状态 adapter 做成全局单例。
- Claude 用户已确认其样本纯度可信，并且轻微格式问题会被其严格检查拒绝；本任务接受该格式校验结论。由于原始样本尚未落入仓库，自动化回归仍由 Claude 协议样例测试覆盖。
- Gemini 模型列表请求成功只能证明 Key 与 Gemini API 连通；只有生成请求成功并取得真实流，才能验证真实 Provider 流式行为。

## 要点

- `SseParser.parse()` 输出 JSON object payload；`[DONE]` 不发射，`message_stop` 发射后终止。
- 空行派发累积的所有 `data:` 行（以换行拼接）；EOF 时也派发未以空行结束的最后一帧。
- 顶层 `error` 或 `type == "error"` 抛 `ApiException`；JSON 非 object 或格式错误抛 `StreamProtocolException`，不静默跳过。
- `ProviderStreamEvent` 现有事件包括正文、思维链、工具调用增量、签名增量和结束原因；UI 当前仍只消费 `TextDelta`。
- T04 目前仅 OpenAI 兼容请求接入 `ChatStreamClient`；Claude/Gemini adapter 已可单测，但真实请求配置、设置 UI 及真实 fixture 未完成。
- 仓库内可重放的真实 fixture 仍为 `openai-stream.txt` 与 `openai-toolcall-stream.txt`；Claude 外部样本的纯度/格式通过状态来自用户确认，尚未提交样本文本，不可写成仓库内 fixture。
- **Gemini 验证（2026-10-08）**：使用本机 `fixtures/.env.local` 中配置的 `WW_FIXTURE_GEMINI_KEY` 调用 `GET /v1beta/models` 成功，首批返回 50 个模型且有下一页；`gemini-2.5-flash:generateContent` 返回 404（模型不再向新用户开放），改用列表中的 `gemini-3.8-flash:generateContent` 后收到 HTTP 403 `PERMISSION_DENIED`（`Your project has been denied access. Please contact support.`）。因此 API 网络连接和模型列表权限已验证，但生成访问被拒绝，未能采集 Gemini 真实流 fixture；没有保存响应内容或 Key。
- **DeepSeek 验证位**：可用已配置的 DeepSeek-compatible endpoint 验证 parser + OpenAI adapter 的端到端真流；Key 只从本机环境变量 `WW_T03_API_KEY` 读取，模型/base 可用 `WW_T03_MODEL` / `WW_T03_BASE_URL` 覆盖，严禁把 Key 填进源码或此文档。

## 验收结果

- [x] `:shared:desktopTest` 通过，包含现有真流式节奏测试及新 SSE/adapter 测试。
- [x] `:desktopApp:build` 成功。
- [x] OpenAI 两份真实 fixture 可解析；工具参数多帧累积结果可解析为 JSON object。
- [x] Claude 缺失 block-start 抛错；Gemini thought 文本不进入正文 delta。
- [x] Claude 外部样本纯度及严格格式校验通过（用户确认）；仓库内仍没有该样本文件，自动回归使用协议样例。
- [x] OpenAI 兼容中转上的 DeepSeek 模型在线真流 smoke test 通过（`deepseek-v4.1-flash`）。
- [x] DeepSeek 官方 API 在线真流 smoke test：`api.deepseek.com` / `deepseek-flash` 实际请求成功，测试耗时约 2.1 秒。
- [ ] Gemini 真实流 fixture 单测通过（模型列表请求已成功；生成请求对 `gemini-3.8-flash` 返回 HTTP 403 `PERMISSION_DENIED`，当前无法采集真实流）。
- [ ] 从应用配置中切换到 Claude/Gemini 并完成实时请求验收。

## 遗留问题 / 下一步

- Claude 样本的纯度与严格格式校验已由用户确认通过；仓库尚未保存原始样本，adapter 自动化仍依赖协议样例。Gemini Key 可读取模型列表，但生成权限被 API 拒绝；协议级测试已覆盖 `thought` 分流，真实 fixture 仍待有权限时采集。
- 当前 `HardcodedChatProvider` 与请求体仍只覆盖 OpenAI 兼容格式；真实 Claude/Gemini 请求装配是否并入 T04，需结合设置/数据模型任务安排，不能把 adapter 单测等同于端到端支持。
- DeepSeek 实测入口：`fixtures/tools/run-deepseek-live.ps1` 显式运行时优先读取忽略文件中的 `WW_T04_DEEPSEEK_API_KEY`，否则读取当前进程的 `DEEPSEEK_API_KEY`；默认请求 `https://api.deepseek.com/v1` 的 `deepseek-chat`，不显示密钥。官方端点 `api.deepseek.com` 上使用用户配置的 `deepseek-flash` 实际验证通过（约 2.1 秒）；测试脚本带 `--no-build-cache --rerun-tasks`，不会把 Gradle 缓存结果误认作在线成功。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §3.1：保留空行派发、`data:` 累积、`[DONE]`、`message_stop` 与错误帧抛错；不引入第三方 SSE 库。
- `MIGRATION_CONSTRAINTS.md` §3.2：原始网络流仍使用 scoped `execute {}` 与 `ByteReadChannel.readLine()`；不缓冲响应。
