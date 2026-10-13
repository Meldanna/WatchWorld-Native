# WatchWorld 迁移约束清单（MIGRATION_CONSTRAINTS.md）

> **用途**：开新窗口让模型改 Kotlin 原生代码时，**把本文件内容一并贴给模型**。
> **目的**：防止模型"自作聪明地改良"导致存档不兼容、协议不一致、流式退化。
> **配套**：`KOTLIN_MIGRATION_PLAN.md`（技术方案）。本文件只讲**不许动什么**。

---

## 0. 给模型的开场指令（直接复制这段）

```
你正在把 E:\WatchWorld\WatchWorld 的 React/TS 项目迁移到 Kotlin + Compose Multiplatform。

【第一原则】这是「逐行对照迁移」，不是「重写」。TS 代码是规格书，不是参考实现。

【硬约束】
1. 字段名、函数职责、执行顺序 —— 一律不得改动。
2. 你认为某处"应该重构/改名/换个数据结构" —— 先列出来问，不要直接改。
3. 遇到 TS 里看着别扭的命名（codeTag / descriptionTag / guanjie_ 前缀）—— 原样保留。
4. 每一处"明显可以简化"的地方，先假设它是有意为之，去注释里找原因。

【判断依据】本文件的下述规则优先于你的工程直觉。冲突时以本文件为准。
```

---

## 1. 存档格式：绝对不可改（🔴 最高优先级）

### 1.1 导出 JSON 的顶层结构

`exportAllData()` 的产物是**跨版本契约**，被 WebDAV 远端备份与导入功能依赖。

**顶层字段名（一个都不能错）**：

```json
{
  "version": 5,
  "exportTime": <number>,
  "providers": [...], "groups": [...], "agents": [...], "prompts": [...],
  "regexRules": [...], "knowledgeBase": [...],
  "theme": {...},              // ⚠️ 单数！不是 themes
  "uiMode": "...",
  "mcpServers": [...], "skills": [...],
  "displaySettings": {...},
  "webdavConfig": {...},
  "worldDocuments": [...],     // ⚠️ 复数
  "worldGroups": [...],
  "sessions": [...],
  "appPreferences": {...}
}
```

**易错点（模型高频犯错处）**：
- `theme` 是**单数**（映射到 `omnichat_theme_palette`，值是单个 `ThemePalette` 对象）
- `worldDocuments` / `worldGroups` 是**复数**
- `version: 5` 不可改数字，除非同时升级 `importAllData` 的兼容分支

### 1.2 `redactSecrets` 行为（导出时必须保留）

```
exportAllData({ redactSecrets: true }) 时：
  - 所有 providers[].apiKey 替换为空字符串 ''
  - webdavConfig.password 替换为空字符串 ''
用途：推到 WebDAV 远端备份时不泄露密钥。换设备恢复后须重新填写。
```

⚠️ **不要"顺手"改成 `null`**。是空字符串 `''`，恢复逻辑按空串判断。

### 1.3 字段可空性语义（三态，不是两态）

TS 里 `undefined` 与 `false` / `0` / `''` **语义不同**。Kotlin 侧用 `null` 表达 `undefined`。

| 字段 | undefined 的含义 | 必须保持 |
|---|---|---|
| `streamingEnabled` | **视为开启流式**（老存档兼容） | `Boolean?`，`null` ≠ `false` |
| `toolCallStepLimit` | 继承全局设置 | `Int?`；显式 `0` = 无限制 |
| `uiRenderLimit` | `0` = 全部渲染 | 默认值 `0`，不是 `null` |
| `aiContextCustomLimit` | 配合 `latest_custom` 用，默认 `3` | — |
| `imageContextMode` | 默认 `'once'` | `enum?` |

⚠️ **序列化配置必须让 `null` 保持"未设置"语义**（`explicitNulls = false`）。
**不得**把 `streamingEnabled = null` 归一化成 `false` —— 这会让所有老存档突然变成非流式。

---


## 2. 存档迁移的 6 条兼容逻辑（必须逐条搬）

`storage.ts` 的 `normalizeChatSession()` 是**老存档能正确加载的前提**。迁移到 Kotlin 时必须**一条不漏**：

```kotlin
// 对应 storage.ts:86-110
// rawToolLimit：从原始 JSON 对象里直接取出的 toolCallStepLimit（JsonElement?），
// 因为老存档里可能是 -1 / NaN / 字符串，不能先反序列化成 Int? 再判断。
fun normalizeChatSession(raw: ChatSession, rawToolLimit: JsonElement?): ChatSession = raw.copy(
    // ① 删除遗留字段 connectedSkillIds（老版本用的，现已由 skillIds 取代）
    //    → Kotlin 侧直接不声明该字段，或用 ignoreUnknownKeys 忽略

    // ② 从 connectedKnowledgeIds 里剔除内置产品指南 ID
    connectedKnowledgeIds = raw.connectedKnowledgeIds?.filter { it != "kb-quick-notes" } ?: emptyList(),

    // ③ timelineDescriptionTagRequests 非法值 → 空队列
    timelineDescriptionTagRequests = raw.timelineDescriptionTagRequests ?: emptyList(),

    // ④ toolCallStepLimit：非有限数字 → null；合法 → max(0, floor(n))
    toolCallStepLimit = (rawToolLimit as? JsonPrimitive)
        ?.takeIf { !it.isString }            // 对应 typeof === 'number'
        ?.doubleOrNull
        ?.takeIf { it.isFinite() }           // 对应 Number.isFinite
        ?.let { maxOf(0, floor(it).toInt()) },

    // ⑤ webSearchEnabled：非严格 true → false
    webSearchEnabled = raw.webSearchEnabled == true,

    // ⑥ streamingEnabled：非 boolean → null（视为流式）
    streamingEnabled = raw.streamingEnabled,   // Boolean? 原样保留三态
)
```

**为什么每条都不能省**：
- ② 内置指南已迁出知识库，不剔除会让老用户看到幽灵条目
- ④ `toolCallStepLimit` 老存档可能有 `-1` / `NaN` / 字符串，不归一会导致工具循环异常
- ⑤⑥ 老存档可能字段缺失或类型不对，不归一会让行为不可预测

---

## 3. 流式协议：不可改的语义

### 3.1 SSE 解析状态机（`nativeToolChat.ts:73-92`）

```
空行                          → 派发累积的 data
"data:" 开头                  → 累积（去掉 "data:" 和其后的一个空格）
payload == "[DONE]"           → ended = true，不派发
event.type == "message_stop"  → 派发后 ended = true
event.error / type == "error" → 抛异常
```

⚠️ **双结束条件必须都保留**：OpenAI 系用 `[DONE]`，Claude 用 `message_stop`。
⚠️ **不得引入第三方 SSE 库替换** —— 现有语义经生产验证，换库会引入行为差异，而流式 bug 极难排查。

### 3.2 真流式实现红线

```kotlin
// ✅ 正确：scoped execute + 逐行读
client.preparePost(url) { ... }.execute { response ->
    val ch = response.body<ByteReadChannel>()
    while (!ch.isClosedForRead) emit(ch.readUTF8Line() ?: break)
}

// ❌ 禁止：任何会缓冲整个响应的写法
response.bodyAsText()          // 一次性读完，流式作废
response.body<String>()        // 同上
flow { ... }.buffer()          // 缓冲破坏增量
withContext(Dispatchers.IO)    // 多套一层，会破坏引擎 dispatcher 语义
```

**验收标准**：帧间隔应贴近上游节奏（上游 300ms/帧 → 实测 250–400ms），**而非一次性到达**。
参照基线：Worker 验证时实测 `311, 310, 313, 309, 310, 311, 314` ms。

### 3.3 工具名注册规则（`nativeToolChat.ts:200-214`）

```
1. 默认使用「原始工具名」（不是加前缀的变体）
2. 仅当跨服务重名时，追加 _2 / _3 后缀
3. 最终名记在 originalName 上，供 callMcpToolNative 回填真实工具名
```

**为什么不能反过来（不能加前缀）**：
> 工具名必须与注入 system 的清单完全一致（`api.ts` 的 `mcpContext` 用的是**原始工具名**），
> 否则模型会照着 system 里的名字调用，而 API 侧只认注册名 → 报「未授权的工具」。

⚠️ 改这里会**同时**影响 `api.ts` 的 `mcpContext` 拼装，两处必须同步改，否则必炸。

### 3.4 三家 Provider 的差异点（照抄，勿凭记忆重写）

| Provider | 文本增量 | 思维链 | 工具调用 | 结束 |
|---|---|---|---|---|
| OpenAI 系 | `choices[0].delta.content` | `delta.reasoning_content` | `delta.tool_calls[]` 分片累积 | `[DONE]` / `finish_reason` |
| Claude | `content_block_delta.delta.text`（`type=="text_delta"`） | `delta.thinking` | `input_json_delta.partial_json` 分片拼接 | `message_stop` |
| Gemini | `candidates[0].content.parts[].text` | 同字段 + `part.thought==true` | `parts[].functionCall` | `finishReason` |

**Claude 强制要求**：
- `content_block_start` 先建 block 记录；`content_block_delta` 找不到 block 时**必须抛错**（原文案：`Claude 流缺少内容块起始事件`）
- `signature_delta` 必须累积到 block 上
- `input_json_delta.partial_json` 是**字符串分片**，需按 index 累积后再整体 parse

**Gemini 强制要求**：
- `part.thought == true` 的文本走**推思维链**分支，**不进入正文、不回放上下文**
- `promptFeedback.blockReason` 必须捕获（安全拦截）

---


## 4. 上下文拼装：顺序与边界不可改（`api.ts`）

### 4.1 执行顺序（严格）

```
1. 按 aiContextVisibility 过滤历史消息
2. 过滤"已推入通用"的楼层（pushedCommonFloors）
3. 若 timelineMemoryEnabled → rearrangeMessagesForAi()
4. 拼 system 上下文（顺序固定，以 api.ts:311-321 源码为准）：
   head（Agent 人格 + 窗口指令 + 长期记忆）→ 世界观正典 → MCP 能力清单 → Skill 清单 → 知识库 → 本轮联网结果 → 楼号引用约定 → extraSystemTail（通用缓存 + 前文总结）→ 时间线树提示词
5. 应用正则规则（regexRules）
6. 图片按 imageContextMode 决定保留与否
```

### 4.2 `pushedCommonFloors` 过滤（`api.ts:133-153`）

**为什么存在**（原文注释）：
> 已经"推入通用"的消息正文已存在于 system 尾部缓存中，不能再作为普通历史消息重复发送。

**实现要求**：
```kotlin
// 必须用同一正则
val pushedFloors = Regex("\\[来自\\s+#(\\d+)\\]")
    .findAll(extraSystemTail)
    .mapNotNull { it.groupValues[1].toIntOrNull() }
    .toSet()

// 三重判定，缺一不可
val isCommonMessage = m.isCommon == true ||
    m.codeTag == "通用" ||
    m.timelineId == "timeline-main"
```

⚠️ **不要"优化"这个过滤**。它的精妙处在于：**撤回推入后 `extraSystemTail` 不再有标记，消息自动恢复**。换成别的判断方式会破坏这个自愈特性。

### 4.3 system 段落标题（字符串必须逐字一致）

```
"\n\n# 【世界观·正典设定】:\n"
"\n\n# 【外部参考资料·知识库】:\n"
"\n\n# 【已验证可用的 MCP 服务能力】:\n"
```

⚠️ 这些字符串是**模型理解上下文的锚点**。改标点、改"·"、改冒号，都会影响模型行为。

---

## 5. 三向同步纪律

```
                 ┌──────────────────────┐
                 │   Repository (单一入口) │
                 └──────────┬───────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
    本地 SQLite        WebDavClient        JsonArchive
                                          (v5 导入/导出)
```

**规则**：
1. **不绕过 Repository** —— UI 层不许直接碰 DAO
2. **写操作走 `suspend fun`**，同一事务内更新 `updatedAt`（同步引擎靠它判断增量）
3. **`version: 5` 不可改**，除非同步升级导入兼容分支
4. **三个出口的数据必须一致** —— 改任一处格式，另两处必须同步

---

## 6. 命名：原样保留，不要"改良"

以下是**看着别扭但必须保留**的命名：

| 现有命名 | 不要改成 | 原因 |
|---|---|---|
| `codeTag` | `branchCode` | 存档字段 |
| `descriptionTag` | `branchLabel` | 存档字段 |
| `guanjie_*` 存储键前缀 | `watchworld_*` | **老存档全丢** |
| `omnichat_*` 存储键前缀 | `watchworld_*` | **老存档全丢** |
| `isCommon` | `isShared` | 存档字段 |
| `floorNumber` | `messageIndex` | 存档字段 + AI 上下文标记 |
| `LOAD_SKILL_TOOL_NAME` | `load_skill` | 必须与注入 system 的名字一致 |
| `kb-quick-notes` | `builtin-guide` | 兼容逻辑里的硬编码 ID |

⚠️ **存储键前缀尤其致命**：`guanjie_` / `omnichat_` 混用是历史遗留，但**改任何一个都等于清空用户数据**。

---

## 7. 迁移期间**不要做**的事

| # | 禁止 | 原因 |
|---|---|---|
| 1 | 删除 `src/` 下的 React 代码 | 原生版可用前 Web 版仍是备份；主题走查需并排对比 |
| 2 | 删除 `toPseudoEvents()` / `replayAsStream()` | 未配中转时的**设计好的降级路径**，不是遗留垃圾 |
| 3 | 在迁移期间给 Web 版加新功能 | 两边永远追不上 |
| 4 | "顺手"重构 `ChatSession` 的 64 字段 | 每个字段都对应存档，改名即破兼容 |
| 5 | 把 `ChatSession` 拆成多表（除 `messages`/`timelines`/`summaries`） | 配置类字段拆表会让迁移成本与出错率暴涨 |
| 6 | 换掉 SSE 解析逻辑 | 见 §3.1 |
| 7 | 改 `version: 5` 而不改导入兼容分支 | 老存档无法恢复 |

---

## 8. 每次改动的自检清单

模型每次交付前必须回答：

- [ ] 我有没有**改字段名**？（改名 = 存档不兼容）
- [ ] 我有没有**改存储键**？（改键 = 数据丢失）
- [ ] 我有没有**改执行顺序**？（顺序 = 行为）
- [ ] 我有没有**改字符串字面量**（system 标题 / 工具名 / 正则）？
- [ ] 我改的东西**是否需要同步改另一处**？（如工具名 ↔ `mcpContext`）
- [ ] 如果我判断某处"应该重构"，我**是否先问了**而不是直接改？
- [ ] 存档往返测试**是否仍然通过**？

---

## 9. 快速定位表

| 要改的东西 | 必读的源码 | 对应方案章节 |
|---|---|---|
| 数据模型 | `src/types.ts` | `KOTLIN_MIGRATION_PLAN.md` §4 |
| 存档导入导出 | `src/lib/storage.ts`（`exportAllData` / `importAllData` / `normalizeChatSession`） | §5.4 |
| 流式传输 | `src/lib/nativeToolChat.ts` (22-118) | §6.1 §6.2 |
| Provider 适配 | `src/lib/nativeToolChat.ts` (354-395) | §6.3 |
| 工具循环 | `src/lib/nativeToolChat.ts` (196-214, 397+) | §6.4 |
| 上下文拼装 | `src/lib/api.ts` (109-250) | §6.6 |
| 联网搜索 | `server.ts` (44-121) + `src/lib/webSearch.ts` | §6.5 |
| MCP | `src/lib/mcpApi.ts` + `server.ts`（mcp/test） | §6.7 |
| WebDAV | `src/lib/webdavSync.ts` + `syncEngine.ts` | §6.8 |
| 主题 | `src/lib/theme.ts` + `src/index.css` | §7.2 |

---

## 10. 一句话总结

**迁移的成功标准不是"代码更漂亮"，而是"行为完全一致 + 真流式"。**

任何"让代码更漂亮"的改动，只要碰到 §1–§6 的任何一条，就是在拿存档兼容性和协议正确性做交换。

有疑问时：**先问，不要改。**

---

**本文件与 `KOTLIN_MIGRATION_PLAN.md` 冲突时，以本文件的"不许改"条款为准。**

