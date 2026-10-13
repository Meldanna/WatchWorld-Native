# T03 · 接真实 Provider：Ktor 硬编码直连真流式

| | |
|---|---|
| 任务号 | T03 |
| 日期 | 2026-10-06 |
| 状态 | ✅ 完工 |
| 阶段 | 阶段 1 · 看见「流式」 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T03 |

## 目标与可见效果

- **目标**：把迁移的**根本目的**落地 —— 用 Ktor `ByteReadChannel` 拿到**真正的增量**，
  而不是 Capacitor `replayAsStream()` 那种「读完了再回放」的假流式
- **可见效果**：真实模型回复逐字出现（配置硬编码，先绕过存档与设置界面）
- **验收标准**（PLAN §8.2，**迁移成败的判据**）：帧间隔呈稳定节奏（上游 300ms/帧 → 实测 250–400ms），
  **非一次性到达**

## 改动

| 文件 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | **改**。加 `ktor = "3.6.0"` / `kotlinxSerialization = "1.9.0"` 与三个库坐标 |
| `shared/build.gradle.kts` | **改**。commonMain 加 `ktor-client-core` + `kotlinx-serialization-json`；desktopMain 加 `ktor-client-okhttp` |
| `net/HttpClientFactory.kt` | **新增**。`HttpClient {}` 靠引擎自动发现取 **OkHttp**（PLAN §2.3）；**不装 `HttpTimeout`** |
| `net/ChatStreamClient.kt` | **新增**。★ `streamSse()` = 真流式原始行流（PLAN §6.1 直译）；`streamAssistantText()` = 最小 OpenAI 兼容路；`ApiException` |
| `net/HardcodedChatProvider.kt` | **新增**。T03 硬编码 Provider：baseUrl/model 写死，**apiKey 只从环境变量取** |
| `model/WwJson.kt` | **新增**。PLAN §4.4 的**最小版**（`ignoreUnknownKeys` + `coerceInputValues`） |
| `platform/PlatformEnv.kt`、`desktopMain/.../PlatformEnv.desktop.kt` | **新增**。`expect/actual` 读环境变量（commonMain 无法访问 env） |
| `ui/chat/ChatScreen.kt` | **改**。接真实 `ChatStreamClient`；错误显示成气泡；`DisposableEffect` 关客户端 |
| `ui/App.kt` | **改**。标题 → T03 |
| `net/MockChatStream.kt`、`commonTest/.../MockChatStreamTest.kt` | **删除**。按 T03 要求清掉 T02 临时脚手架 |
| `desktopTest/.../ChatStreamClientTest.kt` | **新增**。★ PLAN §8.2 帧间隔验收 + 非 2xx 抛错 |

## 心得

- **★ PLAN §6.1 的示例代码已经过时**：它写 `channel.readUTF8Line()`，但 Ktor 3.6.0 把
  `readUTF8Line` 标记为 **deprecated**（"Use readLineStrict or readLine instead"）。
  照抄文档会引入构建警告（本仓标准是零警告）。
- **两个替代品选哪个很关键**，别随手选错：
  - `readLineStrict(maxLength)` —— **会在行过长时抛异常**。SSE 的单个 `data:` 帧可能很大
    （工具调用的大段 JSON），用它会**直接炸在长回复上**，而且现象是"偶发"，极难查。
  - `readLine()` —— 宽松读行，**EOF 返回 `null`**（编译器确认了可空返回）。
    本任务用它，配合 `while (!channel.isClosedForRead)`。
- **不敢装 `HttpTimeout`**：Ktor 的超时插件把**整个请求（含读 body）**算进
  `requestTimeoutMillis`，而流式请求的耗时取决于模型生成多久（可达数分钟）。
  装上就容易在长回复中途把流掐断 —— 那正是「真流式」最怕的失败模式。
  宁可不设超时，等有明确需求时再按「只在 connect 阶段计时」加。
- **引擎用自动发现，不写 `expect/actual`**：`HttpClient {}` 会从 classpath 找引擎，
  于是 `net/` 与 `ui/` 全部留在 commonMain，`HttpClientFactory.kt` 也能放在 PLAN 指定的
  `net/` 下（而不是被迫下沉到 `desktopMain`）。只有 Env 那种**真正平台相关**的东西才用 expect/actual。
- **API Key 绝不写进代码**。「配置写死在代码里」指的是 baseUrl/model，
  密钥一旦硬编码就有提交泄露的风险，所以只从环境变量读，空则 UI 明确报错而不是静默失败。
- **验证的关键不是"测试通过"，而是"测试耗时"**：帧间隔用例耗时 **1.88s** ≈ 6×300ms。
  如果实现里误用了缓冲（`bodyAsText()` 之类），断言会失败；但如果只是断言写松了，
  耗时这 1.88s 才是"确实逐帧到达"的直接证据。做流式验证时**把耗时也当成判据**。
- **T03 的 SSE 累积只是最小版**：够跑通 OpenAI 兼容系，但没有正式 `SseParser`，也没有三家适配。
  刻意不在这里展开 —— 那是 T04，否则会在没有 fixture 的情况下凭记忆写协议，正是 CONSTRAINTS 警告的事。

## 要点

- **`readLine()` 是本仓的流式读行方式**；**不要**改成 `readLineStrict`（长行抛异常），
  **更不要**回到 `readUTF8Line`（3.6.0 已废弃）。等升级 Ktor 时**必须重新核对这个 API**。
- **红线（CONSTRAINTS §3.2）**：`net/` 下禁止 `bodyAsText()`（当前仅"错误体"这一处例外）、
  `body<String>()`、`flow{}.buffer()`、额外 `withContext(Dispatchers.IO)`。
  唯一正确的读法：`preparePost(...).execute { response.body<ByteReadChannel>().readLine() }`。
- **`streamSse` 是 scoped**：`execute {}` 出块即释放连接，body 只在块内可读。
  别把 `response` 往外传。
- **版本只在 `gradle/libs.versions.toml` 改**：`ktor 3.6.0` / `kotlinxSerialization 1.9.0`。
  ⚠️ **kotlinx-serialization 的编译器插件尚未启用** —— T03 只用 `JsonElement` 读写，不需要
  `@Serializable`；**T05 定义数据类时才要加** `org.jetbrains.kotlin.plugin.serialization`。
- **`WwJson` 少了 `explicitNulls = false`**（§4.4 的完整配置），因为三态语义要到 T05 有数据类才有意义。
  **T05 负责补齐**，别以为已经完整。
- **`HardcodedChatProvider` 与已删除的 `MockChatStream` 同类：临时件**。
  T15（设置界面）/ T05（存档）落地后应删除，配置改由 `Storage.providers` 提供。
  环境变量名：`WW_T03_API_KEY`（必需）、`WW_T03_BASE_URL`、`WW_T03_MODEL`。
- **`HttpClientFactory` 刻意不设超时**。若将来要加，务必只加 **connect** 阶段，
  不要把 request/整体超时算进流式读 body 的时间。
- **帧间隔测试放在 `desktopTest`，不能进 `commonTest`**：它需要 JDK 的
  `com.sun.net.httpserver.HttpServer` 起假上游。`commonTest` 里放的是纯逻辑（如 `SplitBlocksTest`）。
- **T04 会替换 `streamAssistantText` 内部的 SSE 累积逻辑**（改为 `net/SseParser.kt` + 三家适配器 + fixture 单测）；
  **`streamSse` 这个原始行流是稳定接口，保留不动**。

## 验收结果

- [x] `./gradlew :desktopApp:build` 成功，**零警告**
- [x] `./gradlew :shared:desktopTest` 通过（6 个用例）
- [x] ★ **帧间隔用例耗时 1.88s ≈ 6×300ms**，`gaps.all { it in 250..400 }` 通过 → `帧间隔贴近上游节奏`
- [x] 非 2xx 上游 → 抛 `ApiException(401)`（错误不被吞掉）
- [x] 应用启动出窗口（`tasklist /V` 见 `WatchWorld Native`）
- [x] **真实模型端到端验证**：2026-10-06 通过配置的 OpenAI 兼容中转站完成 Ktor smoke test；多次正文增量、间隔与总耗时断言通过。首次用 `gpt-4o-mini` 被中转站拒绝，改用已成功采集 fixture 的模型后通过。测试时仅在 Gradle 进程环境中复用 fixture Key；`.env.local` 的 `WW_T03_API_KEY` 仍为空。
- [x] 现有 React 仓 `E:\WatchWorld\WatchWorld\` **零改动**

## 遗留问题 / 下一步

- **真实模型链路已通过 OpenAI 兼容中转验证**，但当前 `.env.local` 没有 `WW_T03_API_KEY`，`WW_T03_BASE_URL` 也为空；手测应用前需把这些值放入启动进程环境。`gpt-4o-mini` 经该中转请求失败，fixture 中已验证可用的模型通过了 smoke test。
- **Claude 原生协议尚未验证成功**：默认模型 `claude-sonnet-4-5` 返回 HTTP 503；指定 `claude-opus-4-8` 后返回 `model_not_found`。用户确认反代号池额度已耗尽；额度恢复后再重试。用中转站 OpenAI 模型列表中的 DeepSeek ID 探测时没有正文增量，不能当作 Claude fixture。
- **没有超时、没有重试、没有错误重连**；取消（停止按钮）时靠协程取消退出 `execute` 块释放连接，
  但**未显式验证**连接确实被及时释放。
- **`streamAssistantText` 只支持 OpenAI 兼容系**，且 SSE 累积是简化版（无多行 data 的完整语义、
  无 `message_stop` 等）—— **T04 完整化**。
- 决策 #2（Markdown 库）仍未定，与 T03 无关。
- **下一步是 T04**：`net/SseParser.kt` + 三家 Provider 归一化 + `fixtures/sse/` 单测。
  OpenAI 兼容文本流与工具调用 fixture 已通过校验；Claude 原生 fixture 仍待获得可用模型并成功采集。

## 相关红线

- **`MIGRATION_CONSTRAINTS.md` §3.2（真流式实现红线）**：**已严格遵守**。
  全仓无 `bodyAsText()`（仅错误路径一处，符合"拿完整报错"的意图）、无 `body<String>()`、
  无 `flow{}.buffer()`、无额外 `withContext`。
- **§3.1（SSE 语义）**：**部分遵守** —— 已保留「空行派发 / `data:` 累积 / `[DONE]` 结束 / 错误帧抛异常」，
  但**双结束条件与三家归一化要到 T04**。本任务刻意不做全，以免无 fixture 时凭记忆写协议。
- 未触碰存档字段名 / `version: 5` / 存储键前缀 / system 字符串。
