# T10 · MCP 客户端

| | |
|---|---|
| 任务号 | T10 |
| 日期 | 2026-10-07 |
| 状态 | ✅ 完工（真机在线 smoke 通过） |
| 阶段 | 阶段 4 · 接回完整对话链路 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T10 |

## 目标与可见效果

- **目标**：从零实现 MCP 客户端（**无官方 Kotlin SDK**），实现 T09 定义的 `McpToolCaller`。
- **可见效果**：MCP 工具真的能连通并调用 —— `WW_T10_LIVE_MCP=1` 在线 smoke 对真机
  `https://github.windlife.site/mcp/andunya` 跑通 `initialize → tools/list → tools/call`。
- **验收标准**：`initialize → tools/list → tools/call` 三步；降级可只做后两步。

## 改动

| 文件 | 改动 |
|---|---|
| `shared/.../net/McpClient.kt` | **新增**（约 660 行）。★ 从零实现的 MCP 客户端：`McpClient : McpToolCaller`（每次调用建连、用完即关）；`testConnection(McpServerConfig) → McpConnectionTestResult`；`McpSession`（id 分配 / 握手 / `nextId` / `mcp-session-id` / `mcp-protocol-version` / `tools/list` 翻页）；**两种 transport** —— `StreamableHttpTransport`（单端点 POST，JSON/SSE 双响应分流、202、`DELETE` 终止）与 `SseTransport`（旧式 HTTP+SSE：GET 事件流 + `event: endpoint` + POST 202 + 响应走 GET 流）；宽松 SSE 帧解析；URL 校验；`explainMcpError` 5 分支 |
| `shared/src/desktopTest/.../net/McpClientTest.kt` | **新增 13 个用例**：三步顺序与请求头、服务端降级版本被记住、handshake 不带 session id / 后续带上 / 关闭发 DELETE、SSE 形状响应、`tools/list` 翻页、`description` 回退、404 文案、URL 三条校验、`tools/call` 参数与结果、`isError` 透传、两条可用性校验先于任何请求、JSON-RPC 错误原文、旧式 SSE 传输、`explainMcpError` 5 分支 |
| `shared/src/desktopTest/.../net/McpLiveSmokeTest.kt` | **新增**。在线 smoke（`assumeTrue(System.getenv("WW_T10_LIVE_MCP") == "1")`，地址可用 `WW_T10_LIVE_MCP_URL` 覆盖）；默认跳过 |
| `fixtures/mcp/{tools-list,tools-call-list-repos}.json` | 真机响应原文（从 `.tmp-t10-probe/` 移入） |

## 心得

### 1. 超时必须转成普通异常，不能用 `withTimeout`

`kotlinx.coroutines.withTimeout` 抛的是 `TimeoutCancellationException`，它**是** `CancellationException`。
T09 的 `executeToolCall` 里 `catch (CancellationException) { throw error }` 会把它当成「用户按了停止」往外抛，
整条 AI 消息会被误判为取消。所以这里一律用 **`withTimeoutOrNull`**，超时返回 `null` 后自己抛 `ApiException`。
这也是「MCP 连接超时（15 秒）」能作为普通错误文案回到模型/用户的前提。

### 2. 不能复用 `SseParser` 解析 MCP 的 SSE

`SseParser` 把 `data:` 帧当成模型流：解析失败抛 `StreamProtocolException`，命中 `error` 帧直接抛 `ApiException`。
MCP 的 SSE 响应里会有**服务端通知帧**（无 `id`、可能是 `notifications/…`）甚至非 JSON 数据，
拿那套解析会误伤。于是 `McpClient` 内自带一个**更宽松**的 `readSseFrames`：非 JSON 帧丢弃、无 `id` 的帧忽略、
只按 `id` 匹配自己的响应。旧式 SSE 的 `event: endpoint` 更是 URL 而非 JSON，也必须单独走一条读取分支。

### 3. 服务端可能拒绝传进来的版本，也可能不给 session id

真机的 `initialize` 把 `2025-06-18` 降级成 `2024-11-05`，且**响应头没有 `mcp-session-id`**。
所以客户端必须：发自己实测过的版本 → **接受服务端返回的版本**并写进后续请求的 `mcp-protocol-version`；
session id **拿到了才带**，拿不到就不能依赖它（终止会话的 `DELETE` 也随之跳过）。

### 4. `MockWebServer`/只 mock 客户端都会漏掉整层

测试用进程内 `HttpServer` 假装真上游，因为要断言的恰恰是「POST 的请求头、JSON 形状、响应 Content-Type 分流」。
`SseTransport` 的假上游**必须用线程池 executor**：GET handler 会一直阻塞到测试结束，
`HttpServer` 默认单线程 executor 会让后续 POST 永远排不上队。

### 5. 「两种 transport」都从零写

TS 用官方 SDK 的 `SSEClientTransport` / `StreamableHTTPClientTransport`；没有 SDK 只能两份都写。
旧式 SSE 用「常驻 GET + `event: endpoint` + POST 202 + 响应走 GET 流」的表达，靠
`Channel` + 后台 reader 协程把异步响应桥回请求-响应模型。

## 要点

- **客户端身份**：`call` 用 `watchworld-android-tools`，`testConnection` 用 `watchworld-android-mcp-client`，
  版本都 `0.2.5`（`mcpApi.ts:61-67` / `:105`）。
- **超时**：初始化/连接 15s（文案「MCP 连接超时（15 秒）」），`tools/call` 120s，`tools/list` 默认 60s；
  其它超时文案统一 `Request timed out`。数值与文案都是 `mcpApi.ts` 定的，别改。
- **URL 校验有三条不同文案，不能合并**：`call` 的协议错 → `MCP 地址必须为 HTTP 或 HTTPS`；
  `testConnection` 的协议错 → `远程 MCP 地址只允许 http 或 https`；格式错 → `MCP 地址格式不正确`；
  空 → `MCP 地址不能为空`；内嵌凭据 → `MCP URL 不允许内嵌用户名或密码`。
- **`explainMcpError` 5 分支的顺序不能变**（localhost → cleartext → 网络 → 404/405 → 兜底），文案逐字；
  它是 `internal`，专门给测试用。
- **工具清单落成 `model/McpTool`**（`name` / `description` / `parametersSchema`）：
  `description` 空则回退 `MCP 工具方法: <name>`；`parametersSchema` 是 `inputSchema` 的 JSON 字符串，
  缺省为 `{"type":"object"}`。
- **`McpToolCaller` 的前两条校验先于任何网络请求**：`status != connected` 或工具不在 `server.tools`
  → `工具不属于当前可用的 MCP 服务`（`mcpApi.ts:29-36`）。
- **`withTimeoutOrNull` 的返回值不能用 `?:` 直接兜底**：block 必须是**非空**返回类型，超时才由 `null` 判定。
- **每次调用建连、用完即关**；有 session 时关闭前发 `DELETE`，失败只上报不外抛。
- **`McpClient` 不持有/不关闭 `HttpClient`**，连接池由调用方（T14/T15）统一管理。

## 验收结果

- [x] `initialize → tools/list → tools/call` 三步齐全（`testConnectionRunsInitializeThenToolsList…`、`callSendsArgumentsAndReturnsToolResult`）。
- [x] 服务端降级版本被接受并记住：后续请求带 `mcp-protocol-version: 2024-11-05`。
- [x] JSON 与 `text/event-stream` 两种响应都解析（`sseFormattedResponseIsParsed`）。
- [x] `mcp-session-id`「有则带、无则不带」：handshake 不带、后续带、关闭发 `DELETE`。
- [x] `tools/list` 按 `nextCursor` 翻页；`202` 无响应体按「已接受」处理。
- [x] `explainMcpError` 5 分支逐条命中；URL 五条校验文案正确。
- [x] **真机在线 smoke 通过**：`WW_T10_LIVE_MCP=1 :shared:desktopTest --tests "*McpLiveSmokeTest"` → 1/1 通过（2.53s）。
- [x] `clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿、**零警告**；
      T06 `ArchiveRoundTripTest` 18/18、T07 `StorageRoundTripTest` 10/10 继续通过。

验证命令与输出：

```
> ./gradlew clean
> ./gradlew :desktopApp:build :shared:desktopTest --rerun-tasks --console=plain
  → BUILD SUCCESSFUL in 31s，19 actionable tasks: 19 executed；grep -i warning = 0 行
  → 读 shared/build/test-results/desktopTest/*.xml：
    ArchiveRoundTripTest 18/0 · StorageRoundTripTest 10/0 · ContextBuilderTest 18/0 · ArchiveV5ModelTest 8/0
    ChatStreamClientTest 2/0 · SseProviderAdapterTest 6/0 · ToolCallLoopTest 21/0 · SplitBlocksTest 4/0
    McpClientTest 13/0 · DeepSeekLiveSmokeTest 1（跳过）· McpLiveSmokeTest 1（跳过）
  → 合计 102 tests / 100 passed / 2 skipped / 0 failed / 0 errors
> WW_T10_LIVE_MCP=1 ./gradlew :shared:desktopTest --tests "*McpLiveSmokeTest" --rerun-tasks
  → BUILD SUCCESSFUL；McpLiveSmokeTest tests=1 skipped=0 failures=0（真机 initialize→tools/list→tools/call 全通）
```

## 遗留问题 / 下一步

- **旧式 SSE 传输没有真服务验证**：只用进程内假上游跑过协议形状。真机目标是 StreamableHTTP；
  若将来遇到只提供旧式 SSE 的服务，先按假上游那套排查。
- **不主动 `ping`**：`initialize` 后只发 `notifications/initialized`。收到服务端主动请求 / 通知一律忽略
  （不阻塞主流程即可）；SDK 会对服务端 `ping` 回 `{}`，这里没做，真机也未见触发。
- **版本降级只实测了 `2025-06-18`**：没测发 `2025-11-25` 时真机的行为，所以客户端固定发实测过的 `2025-06-18`。
- **未处理 `notifications/tools/list_changed`**：工具清单变化后的刷新时机归 T15 设置界面。
- **未接 UI / 仓储**：T15 设置界面拿 `McpConnectionTestResult` 写回 `McpServerConfig`
  （`status` / `tools` / `lastLatencyMs` / `lastError` / `serverName` / `serverVersion`），本任务只返回结果。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §3.3：`McpToolCaller` 收原始工具名、可用性判定与 `ContextBuilder` 一致 —— 本任务的两条校验守住了这一点。
- `MIGRATION_CONSTRAINTS.md` §6：`explainMcpError` / 超时 / 成功文案逐字照抄，不改标点。
- `MIGRATION_CONSTRAINTS.md` §8 自检：未改字段名 / 存储键 / `version`（本任务不碰存储层）；存档与存储往返测试仍通过。
