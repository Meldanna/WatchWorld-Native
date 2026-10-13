package com.watchworld.net

import com.watchworld.domain.LOAD_SKILL_TOOL_NAME
import com.watchworld.domain.isMcpAvailableToModel
import com.watchworld.model.AgentSkill
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.McpServerConfig
import com.watchworld.model.WwJson
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/*
 * 工具轮次循环（T09）。规格书：`src/lib/nativeToolChat.ts` 的 `sendNativeToolChat`（:194-544）。
 *
 * ⚠️ 1:1 直译。工具名注册规则、`note()` 文案、各家的 history 形状与 tool result 形状、
 *    finish_reason 白名单、`toolCallStepLimit` 的 0=无限语义，一律不改
 *    （MIGRATION_CONSTRAINTS §3.3；docs/KOTLIN_MIGRATION_PLAN.md §6.4）。
 *
 * 与 TS 的**有意差异**（改这里之前先看，别当成漏做）：
 *  1. TS 的 `viaProxy` 分支（走 Cloudflare Worker / 同源 `/api/chat`）整个不要 —— 桌面端是
 *     进程内应用，没有 Worker。`emitPlainReply` 只服务那条路径，因此也一并不要。
 *  2. 非流式分支不搬 `replayAsStream` 回放：那是 Android WebView 拿不到增量时的补偿手段，
 *     桌面端 [ChatStreamClient.streamSse] 已是真流式，再回放只会凭空拖慢。
 *  3. 真 MCP 调用（`callMcpToolNative`）抽成 [McpToolCaller] 注入。T09 只做循环本身，
 *     SSE / StreamableHTTP 的传输实现归 T10；测试注入 mock。
 *  4. 取消（用户按停止）**往外抛** [CancellationException]，已累积的正文由调用方从
 *     [ToolChatResult] 取。TS 是吞掉并 `return visible`，但 Coroutine 的取消语义要求继续传播，
 *     否则 `withTimeout` / 结构化并发都会失效。
 */

/**
 * 真正发起一次 MCP 工具调用的出口。
 *
 * ⚠️ 传进来的是**原始工具名**（[ToolDefinition.originalName]），不是注册名 —— 注册名可能带
 * `_2`/`_3` 后缀，那是本地为了跨服务去重编出来的，上游 MCP 服务不认识
 * （`MIGRATION_CONSTRAINTS.md` §3.3）。
 *
 * T10 的实现要照 `mcpApi.ts:29` 的两条校验做：`status != connected` 或工具不在 `server.tools`
 * 里 → 抛「工具不属于当前可用的 MCP 服务」。
 */
fun interface McpToolCaller {
    suspend fun call(server: McpServerConfig, name: String, args: JsonObject): JsonElement
}

/** 工具循环的流式输出。`onNote` 的文案由 TS 侧写定，调用方只负责显示（T14 接 UI）。 */
data class ToolChatListeners(
    val onChunk: suspend (String) -> Unit = {},
    val onReasoningChunk: suspend (String) -> Unit = {},
    val onNote: suspend (String) -> Unit = {},
)

/**
 * 一条已经解析好的图片（`api.ts:373` 的 `{ dataUrl, mime }`）。
 *
 * ⚠️ 与 [ToolChatMessage.imageIds] 的分工：`imageIds` 是**引用**（由 T08 的
 * `imageContextMode` 过滤后透传），[ToolChatImage] 才是**本体**。
 * 读取本体要访问图片存储（挂起 + 磁盘），因此由 `ChatRequestAssembler.loadImages`
 * 在**进入工具循环之前**一次性补齐 —— 循环里的 [providerMessage] 保持纯函数。
 */
data class ToolChatImage(val dataUrl: String, val mime: String)

/** 一条发给 Provider 的历史消息（`api.ts:342` 的 `{ role, content, imageIds }`）。 */
data class ToolChatMessage(
    val role: String,
    val content: String,
    val imageIds: List<String>? = null,
    /** 图片本体；为 `null` 表示「还没读」或「读不到」（`api.ts:373` 的 `images`）。 */
    val images: List<ToolChatImage>? = null,
)

/**
 * `NativeChatParams`（`apiNative.ts:9`）。
 *
 * ⚠️ [stream] 默认 `true`（TS 的 `stream = true`）；[toolCallStepLimit] 默认 `0` = 不限步数。
 * 采样参数的可空性照抄 TS 的可选字段：`null` 就是「不传这个键」。
 */
data class ToolChatParams(
    val provider: ApiProviderConfig,
    val model: String,
    val messages: List<ToolChatMessage>,
    val systemInstruction: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val maxTokens: Int? = null,
    /** 工具注册的唯一来源：**必须**是 `AssembledContext.availableMcpServers`（T08）。 */
    val activeMcpServers: List<McpServerConfig> = emptyList(),
    /** 同理：**必须**是 `AssembledContext.availableSkills`。 */
    val activeSkills: List<AgentSkill> = emptyList(),
    val toolCallStepLimit: Int = 0,
    val stream: Boolean = true,
    val listeners: ToolChatListeners = ToolChatListeners(),
)

/**
 * 一条 AI 消息的产物。[text] 是可见正文（含 `[工具调用第 N 步：…]` 这类 note）。
 *
 * ⚠️ 取消**不**走返回值：`CancellationException` 照常往外抛（结构化并发 / `withTimeout` 依赖它），
 * 已打出来的正文由调用方从它自己的 `onChunk` 累积里取 —— 那是它本来就有的东西。
 */
data class ToolChatResult(val text: String)

/** 工具定义：MCP 工具带 [server]；内置 Skill 工具 [server] 为 `null` 并标 [isBuiltinSkill]。 */
data class ToolDefinition(
    val server: McpServerConfig?,
    val originalName: String,
    val name: String,
    val description: String,
    val schema: JsonElement,
    val isBuiltinSkill: Boolean = false,
)

/** 从流里累积出的一次工具调用。[rawArguments] 为 `null` = 只有 block 起始里的 `input`。 */
data class ToolCall(
    val id: String,
    val name: String,
    val rawArguments: String?,
    val initialArguments: JsonElement?,
)

/** 某个 MCP 工具的参数 schema 不是 JSON 对象时抛出（TS：`工具 xxx 的参数 schema 无效`）。 */
class ToolSchemaException(message: String) : Exception(message)

private const val ROLE_SYSTEM = "system"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val MODEL_ROLE = "model"

/** 纯图片消息的占位文本（`nativeToolChat.ts:267` 的 `'[图片]'`）。 */
private const val IMAGE_PLACEHOLDER_TEXT = "[图片]"
private const val BUILTIN_SKILL_DESCRIPTION =
    "读取指定专业 Skill 的完整执行指引。当用户请求属于某个 Skill 的用途时，先调用本工具传入该 Skill 的 name，再依据返回内容作答。"

/**
 * 跑完一整条 AI 消息（含中途的工具轮次）。
 *
 * ⚠️ 与 TS 一致：**除取消外不抛异常** —— 失败原因会作为一条 `note` 追加进正文，已打出来的字
 * 不会被清掉（工具调用失败时中断整条消息比留一段带错误说明的正文更糟）。
 *
 * @return [ToolChatResult]。被取消时抛 `CancellationException`，此时**没有**返回值 ——
 *         已输出的正文从调用方的 `onChunk` 累积里取。
 */
suspend fun ChatStreamClient.runToolChat(params: ToolChatParams, mcpCaller: McpToolCaller): ToolChatResult {
    val kind = providerKind(params.provider.type)
    // 与 TS 一致：ollama 是本地服务，不要求 Key。
    val apiKey = params.provider.apiKey.orEmpty()
    if (apiKey.isBlank() && params.provider.type != ApiProviderType.OLLAMA) {
        throw ApiException(0, "请先配置 ${params.provider.name} API Key")
    }
    val limit = params.toolCallStepLimit
    if (limit < 0) throw ApiException(0, "工具调用步数必须是非负整数")

    val tools = registerTools(params.activeMcpServers, params.activeSkills)
    val url = chatUrl(params.provider, kind, params.model, params.stream)
    val headers = requestHeaders(params.provider, kind, apiKey)
    val system = params.systemInstruction?.ifEmpty { null }
        ?: params.messages.firstOrNull { it.role == ROLE_SYSTEM }?.content
    val history = params.messages
        .filter { it.role != ROLE_SYSTEM }
        .map { providerMessage(it, kind) }
        .toMutableList()
    // 只有 OpenAI 系把 system 当普通消息放在最前面；Claude / Gemini 各有自己的字段。
    if (kind == ProviderKind.OPENAI && !system.isNullOrEmpty()) {
        history.add(
            0,
            JsonObject(mapOf("role" to JsonPrimitive(ROLE_SYSTEM), "content" to JsonPrimitive(system))),
        )
    }

    var visible = ""

    val append: suspend (String) -> Unit = { text ->
        visible += text
        if (text.isNotEmpty()) params.listeners.onChunk(visible)
    }
    // `note()` 文案与 TS 逐字一致（PLAN §6.4 的验收点之一）。
    val note: suspend (String) -> Unit = { text ->
        append((if (visible.isNotEmpty()) "\n\n" else "") + "[$text]")
        params.listeners.onNote(visible)
    }
    // 思维链只用于展示：跨轮用空行拼接，不进入 history，也不影响 visible。
    var fullReasoning = ""
    val pushReasoning: suspend (String) -> Unit = { value ->
        if (value.isNotEmpty()) {
            fullReasoning = if (fullReasoning.isNotEmpty()) "$fullReasoning\n\n$value" else value
            params.listeners.onReasoningChunk(fullReasoning)
        }
    }

    var steps = 0
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val round = Round(kind, steps, visible, append, pushReasoning)

            if (params.stream) {
                streamEvents(
                    url = url,
                    headers = headers,
                    // ⚠️ 每轮都要新建 adapter：Claude 的 block 表是**跨事件**的有状态对象，
                    //    复用上一轮的那一份会把上一轮的 block 当成已开始。
                    adapter = ProviderStreamAdapters.forProvider(kind),
                    body = requestBody(params, kind, history, tools, system, stream = true).toString(),
                ).collect { event -> round.receive(event) }
            } else {
                val raw = postJson(
                    url = url,
                    headers = headers,
                    body = requestBody(params, kind, history, tools, system, stream = false).toString(),
                )
                pseudoEvents(kind, raw).forEach { event -> round.receive(event) }
            }
            fullReasoning = round.roundReasoning

            currentCoroutineContext().ensureActive()
            val reason = round.reason
            if (reason == null || reason !in allowedFinishReasons(kind)) {
                note(
                    if (reason != null) "模型停止：$reason；未执行本轮工具"
                    else "响应意外中断：未收到完成标记，未执行本轮工具",
                )
                return ToolChatResult(visible)
            }

            val calls = round.calls()
            when (kind) {
                ProviderKind.OPENAI -> {
                    if (calls.isNotEmpty() && reason != "tool_calls") {
                        note("工具调用未正常结束，已停止")
                        return ToolChatResult(visible)
                    }
                    if (calls.isNotEmpty()) history += round.openAiAssistantMessage()
                }
                ProviderKind.CLAUDE -> {
                    if (calls.isNotEmpty() && reason != "tool_use") {
                        note("工具调用未正常结束，已停止")
                        return ToolChatResult(visible)
                    }
                    if (calls.isNotEmpty()) {
                        history += JsonObject(
                            mapOf(
                                "role" to JsonPrimitive(ROLE_ASSISTANT),
                                "content" to JsonArray(round.sortedBlocks()),
                            ),
                        )
                    }
                }
                ProviderKind.GEMINI -> if (calls.isNotEmpty()) {
                    history += JsonObject(
                        mapOf("role" to JsonPrimitive(MODEL_ROLE), "parts" to JsonArray(round.parts.toList())),
                    )
                }
            }

            if (calls.isEmpty()) {
                if (round.roundText.isBlank()) note("模型未返回最终正文")
                return ToolChatResult(visible)
            }

            steps++
            note(
                "工具调用第 $steps 步：" +
                    calls.joinToString("、") { call ->
                        tools.firstOrNull { it.name == call.name }?.originalName ?: "未知工具"
                    },
            )

            val results = mutableListOf<JsonObject>()
            for (call in calls) {
                currentCoroutineContext().ensureActive()
                val executed = executeToolCall(call, tools, params.activeSkills, mcpCaller)
                if (executed.isError) note("工具返回错误，已记录到结果")
                val serialized = WwJson.encodeToString(JsonElement.serializer(), executed.result)
                results += toolResultMessage(kind, call, serialized, executed.isError)
            }

            currentCoroutineContext().ensureActive()
            if (limit > 0 && steps >= limit) {
                note("已完成 $steps 步工具调用，达到本窗口上限，已停止本条 AI 消息")
                return ToolChatResult(visible)
            }
            when (kind) {
                ProviderKind.OPENAI -> history += results
                ProviderKind.CLAUDE -> history += JsonObject(
                    mapOf("role" to JsonPrimitive(ROLE_USER), "content" to JsonArray(results.toList())),
                )
                ProviderKind.GEMINI -> history += JsonObject(
                    mapOf("role" to JsonPrimitive(ROLE_USER), "parts" to JsonArray(results.toList())),
                )
            }
        }
    } catch (error: CancellationException) {
        // 对应 TS 的 AbortError 分支：停止生成不是错误，已经打出来的正文照常保留。
        // ⚠️ 但这里**不能**补 `note("已停止生成")`：协程已被取消，`onChunk` / `onNote`
        //    这些 suspend 回调会立刻再抛一次 CancellationException，反而是 bug。
        //    调用方（T14 的 ChatViewModel）自己在 catch 里补这句提示 —— 它也知道是它按的停止。
        throw error
    } catch (error: Exception) {
        note("请求失败：${error.message ?: error.toString()}")
        return ToolChatResult(visible)
    }
}

// ---------------------------------------------------------------------------------------------
// 非流式响应 → 伪事件（`nativeToolChat.ts:120-170`）
// ---------------------------------------------------------------------------------------------

/**
 * 把非流式的完整响应归一化成与流式逐帧等价的伪事件，让同一套累积逻辑通吃。
 * **只做形状转换，不改变任何字段语义。**
 *
 * ⚠️ 这条路径不搬 TS 的 `replayAsStream` 回放（见文件头「有意差异 2」）：桌面端本来就能真流式，
 * `stream = false` 是调用方明确要求的一次性返回，不需要再演一遍逐字出现。
 */
private fun pseudoEvents(kind: ProviderKind, data: JsonElement): List<ProviderStreamEvent> {
    val payload = data as? JsonObject ?: return emptyList()
    val adapter = ProviderStreamAdapters.forProvider(kind)

    if (kind == ProviderKind.GEMINI) {
        val blocked = (payload["promptFeedback"] as? JsonObject)?.get("blockReason") != null
        val frames = if (blocked) {
            listOf(buildJsonObject { put("promptFeedback", payload["promptFeedback"]!!) })
        } else {
            listOf(buildJsonObject { put("candidates", payload["candidates"] ?: JsonArray(emptyList())) })
        }
        return frames.flatMap(adapter::adapt)
    }

    if (kind == ProviderKind.CLAUDE) {
        val frames = mutableListOf<JsonObject>()
        var index = 0
        for (value in (payload["content"] as? JsonArray).orEmpty()) {
            val block = value as? JsonObject ?: continue
            val blockIndex = index
            index++
            val blockType = block.stringOrNull("type")
            frames += buildJsonObject {
                put("type", "content_block_start")
                put("index", JsonPrimitive(blockIndex))
                putJsonObject("content_block") {
                    block.forEach { (key, element) -> put(key, element) }
                    // 正文走下面的 text_delta，起始块里的 text 先清空，避免重复。
                    if (blockType == "text") put("text", "")
                }
            }
            val text = block.stringOrNull("text")
            val thinking = block.stringOrNull("thinking")
            val partial = when {
                blockType == "text" && !text.isNullOrEmpty() -> buildJsonObject {
                    put("type", "text_delta")
                    put("text", text)
                }
                blockType == "thinking" && !thinking.isNullOrEmpty() -> buildJsonObject {
                    put("type", "thinking_delta")
                    put("thinking", thinking)
                }
                blockType == "tool_use" -> buildJsonObject {
                    put("type", "input_json_delta")
                    // 非流式返回的 input 是完整对象，这里塞进 partial_json（TS 原样）。
                    put("partial_json", (block["input"] ?: JsonObject(emptyMap())).toString())
                }
                // `redacted_thinking`：加密思维链无法展示，也不回放，保持静默。
                else -> null
            }
            if (partial != null) {
                frames += buildJsonObject {
                    put("type", "content_block_delta")
                    put("index", JsonPrimitive(blockIndex))
                    put("delta", partial)
                }
            }
        }
        frames += buildJsonObject {
            put("type", "message_delta")
            putJsonObject("delta") { put("stop_reason", payload["stop_reason"] ?: JsonNull) }
        }
        return frames.flatMap(adapter::adapt)
    }

    val choice = (payload["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: JsonObject(emptyMap())
    val message = choice["message"] as? JsonObject ?: JsonObject(emptyMap())
    val frame = buildJsonObject {
        putJsonArray("choices") {
            addJsonObject {
                choice["finish_reason"]?.let { put("finish_reason", it) }
                putJsonObject("delta") {
                    message["content"]?.let { put("content", it) }
                    message["reasoning_content"]?.let { put("reasoning_content", it) }
                    message["tool_calls"]?.let { put("tool_calls", it) }
                }
            }
        }
    }
    return adapter.adapt(frame)
}

private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

// ---------------------------------------------------------------------------------------------
// 工具注册（`nativeToolChat.ts:200-234`）
// ---------------------------------------------------------------------------------------------

/**
 * 注册工具清单。**原始工具名优先，只有跨服务重名才追加 `_2`/`_3`**，并把最终名记在
 * [ToolDefinition.originalName] 上（字段名有点绕：`originalName` 存的是**原始名**，
 * `name` 存的是**注册名**）。
 *
 * 为什么不能用带前缀的变体：注入 system 的 `mcpContext` 用的是原始工具名，模型照着 system 里的
 * 名字调用，而 API 侧只认注册名 —— 不一致就会报「未授权的工具」（CONSTRAINTS §3.3）。
 * ⚠️ 改这里必须同步改 `ContextBuilder` 的 MCP 能力清单。
 */
fun registerTools(
    activeMcpServers: List<McpServerConfig>,
    activeSkills: List<AgentSkill>,
): List<ToolDefinition> {
    // 与 ContextBuilder 用同一套判定（isMcpAvailableToModel）：清单里写的服务和这里注册的必须
    // 出自同一份过滤结果，否则模型会调用一个「system 里有、注册表里没有」的工具。
    val usedNames = mutableSetOf<String>()
    val tools = activeMcpServers
        .filter(::isMcpAvailableToModel)
        .flatMap { server ->
            server.tools.map { tool ->
                // ⚠️ 先绑定成局部变量再判断：`schema !is JsonObject` 之后编译器才能把它窄化成
                //    JsonObject 传进 ToolDefinition（直接写在一行里会因 elvis 而窄化失败）。
                val schema: JsonElement = tool.parametersSchema?.let { WwJson.parseToJsonElement(it) }
                    ?: emptyObjectSchema()
                if (schema !is JsonObject) throw ToolSchemaException("工具 ${tool.name} 的参数 schema 无效")
                var uniqueName = tool.name
                var suffix = 2
                while (uniqueName in usedNames) {
                    uniqueName = "${tool.name}_$suffix"
                    suffix++
                }
                usedNames += uniqueName
                ToolDefinition(
                    server = server,
                    originalName = tool.name,
                    name = uniqueName,
                    description = "${server.name} / ${tool.name}: ${tool.description.orEmpty()}",
                    schema = schema,
                )
            }
        }
        .toMutableList()

    // 内置 Skill 加载工具：清单已在 system 中常驻，正文按需由模型调用本工具拉取。
    // 它不是 MCP 工具（server 为 null），在执行分支里单独拦截。
    if (activeSkills.isNotEmpty()) {
        tools += ToolDefinition(
            server = null,
            originalName = LOAD_SKILL_TOOL_NAME,
            name = LOAD_SKILL_TOOL_NAME,
            description = BUILTIN_SKILL_DESCRIPTION,
            schema = skillToolSchema(),
            isBuiltinSkill = true,
        )
    }
    return tools
}

/** TS 的 `{ type: 'object', properties: {} }`。 */
private fun emptyObjectSchema(): JsonElement = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private fun skillToolSchema(): JsonElement = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("name") {
            put("type", "string")
            put("description", "要加载的 Skill 名称，必须与能力清单中的名称完全一致")
        }
    }
    putJsonArray("required") { add(JsonPrimitive("name")) }
}

// ---------------------------------------------------------------------------------------------
// 工具执行（`nativeToolChat.ts:490-532`）
// ---------------------------------------------------------------------------------------------

private class ToolExecution(val result: JsonElement, val isError: Boolean)

/**
 * 执行一次工具调用。
 *
 * ⚠️ 错误**不外抛**：转成 `{ "error": "..." }` 交给模型，并把 `isError` 带到 tool result 上
 * —— 模型看到错误会自己换个思路，直接中断整条消息反而更差。
 * 只有取消要往外抛。
 */
private suspend fun executeToolCall(
    call: ToolCall,
    tools: List<ToolDefinition>,
    boundSkills: List<AgentSkill>,
    mcpCaller: McpToolCaller,
): ToolExecution = try {
    val tool = tools.firstOrNull { it.name == call.name }
        ?: throw ApiException(0, "模型请求了未授权的工具")
    val args = decodeArguments(call)
    if (tool.isBuiltinSkill) {
        // 内置 load_skill：按名称返回 Skill 正文，不产生额外网络请求。
        val requestedName = args["name"].stringOrNull()?.trim().orEmpty()
        val match = boundSkills.firstOrNull { it.name == requestedName }
            ?: boundSkills.firstOrNull { it.name.equals(requestedName, ignoreCase = true) }
            ?: throw ApiException(
                0,
                "未找到名为“$requestedName”的 Skill。可用的 Skill 有：" +
                    boundSkills.joinToString("、") { it.name },
            )
        // 正文只在本轮工具结果中出现，不写入会话历史之外的持久状态。
        ToolExecution(
            result = buildJsonObject {
                put("skill", match.name)
                put("description", match.description)
                put("instruction", match.systemInstruction ?: match.systemInstructionInjection)
            },
            isError = false,
        )
    } else {
        val server = tool.server ?: throw ApiException(0, "模型请求了未授权的工具")
        val result = mcpCaller.call(server, tool.originalName, args)
        ToolExecution(result, isError = (result as? JsonObject)?.get("isError").boolOrNull() == true)
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    ToolExecution(
        result = buildJsonObject { put("error", error.message ?: error.toString()) },
        isError = true,
    )
}

/**
 * TS：`typeof call.args === 'string' ? JSON.parse(call.args) : call.args`，随后要求必须是对象。
 *
 * - [ToolCall.rawArguments] 非 null（OpenAI 的 arguments 分片 / Claude 的 `input_json_delta`）
 *   → 解析它；
 * - 为 null（Claude 只有 block 起始的 `input`）→ 直接用那个对象；
 * - 解析失败或不是对象 → 「工具参数必须为 JSON 对象」，作为工具错误回给模型。
 */
private fun decodeArguments(call: ToolCall): JsonObject {
    val parsed: JsonElement = if (call.rawArguments != null) {
        runCatching { WwJson.parseToJsonElement(call.rawArguments) }.getOrNull()
            ?: throw ApiException(0, "工具参数必须为 JSON 对象")
    } else {
        call.initialArguments ?: throw ApiException(0, "工具参数必须为 JSON 对象")
    }
    return parsed as? JsonObject ?: throw ApiException(0, "工具参数必须为 JSON 对象")
}

/** 各家的 tool result 形状（`nativeToolChat.ts:529-531`），逐字段照抄。 */
private fun toolResultMessage(kind: ProviderKind, call: ToolCall, serialized: String, isError: Boolean): JsonObject =
    when (kind) {
        ProviderKind.OPENAI -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", call.id)
            put("content", serialized)
        }
        ProviderKind.CLAUDE -> buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", call.id)
            put("content", serialized)
            put("is_error", isError)
        }
        ProviderKind.GEMINI -> buildJsonObject {
            putJsonObject("functionResponse") {
                put("name", call.name)
                // TS：`...(call.id ? { id: call.id } : {})` —— 空 id 不写这个键。
                if (call.id.isNotEmpty()) put("id", call.id)
                put("response", buildJsonObject { put("result", WwJson.parseToJsonElement(serialized)) })
            }
        }
    }

// ---------------------------------------------------------------------------------------------
// 请求装配（`nativeToolChat.ts:235-333`）
// ---------------------------------------------------------------------------------------------

private val DEFAULT_BASE_URLS: Map<ApiProviderType, String> = mapOf(
    ApiProviderType.OPENAI to "https://api.openai.com/v1",
    ApiProviderType.DEEPSEEK to "https://api.deepseek.com/v1",
    ApiProviderType.OPENROUTER to "https://openrouter.ai/api/v1",
    ApiProviderType.GROQ to "https://api.groq.com/openai/v1",
    ApiProviderType.OLLAMA to "http://localhost:11434/v1",
    ApiProviderType.CLAUDE to "https://api.anthropic.com/v1",
    ApiProviderType.GEMINI to "https://generativelanguage.googleapis.com/v1beta",
)

/** TS 的 `kind`：gemini → gemini、claude → claude，其余（含 custom）全当 OpenAI 兼容。 */
private fun providerKind(type: ApiProviderType): ProviderKind = when (type) {
    ApiProviderType.GEMINI -> ProviderKind.GEMINI
    ApiProviderType.CLAUDE -> ProviderKind.CLAUDE
    else -> ProviderKind.OPENAI
}

/**
 * 补全后的 base URL + 该家的路径。
 *
 * ⚠️ Gemini 的 key 走 **query 参数**（`?key=`），不是请求头；这一点与另两家不同。
 * ⚠️ 非流式时 Gemini 的路径要换掉（`:streamGenerateContent?alt=sse` → `:generateContent`）。
 */
private fun chatUrl(provider: ApiProviderConfig, kind: ProviderKind, model: String, stream: Boolean): String {
    val base = (
        provider.baseUrl?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URLS[provider.type]
            ?: DEFAULT_BASE_URLS.getValue(ApiProviderType.OPENAI)
        ).trimEnd('/')
    return when (kind) {
        ProviderKind.GEMINI -> {
            val method = if (stream) "streamGenerateContent?alt=sse&" else "generateContent?"
            "$base/models/${model.encodeURLPathPart()}:$method" +
                "key=${provider.apiKey.orEmpty().encodeURLParameter()}"
        }
        ProviderKind.CLAUDE -> if (base.endsWith("/messages")) base else "$base/messages"
        ProviderKind.OPENAI -> if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }
}

private fun requestHeaders(provider: ApiProviderConfig, kind: ProviderKind, apiKey: String): Map<String, String> {
    val headers = linkedMapOf("Content-Type" to "application/json")
    headers.putAll(provider.customHeaders.orEmpty())
    when (kind) {
        ProviderKind.CLAUDE -> {
            headers["x-api-key"] = apiKey
            headers["anthropic-version"] = "2023-06-01"
        }
        ProviderKind.OPENAI -> if (apiKey.isNotEmpty()) headers["Authorization"] = "Bearer $apiKey"
        ProviderKind.GEMINI -> Unit // key 在 URL 上
    }
    return headers
}

/**
 * 历史消息 → 各家 wire 形状（`nativeToolChat.ts:266-278`）。
 *
 * 图片按 TS 逐字拼装：Gemini 用 `inlineData`、Claude 用 base64 `source`、
 * OpenAI 用 `image_url`（dataURL 整串）。有 `imageIds` 但本体没读出来时只发文本，
 * 纯图片消息补 `[图片]` 占位（`nativeToolChat.ts:267` 的 `m.content || '[图片]'`）。
 */
private fun providerMessage(message: ToolChatMessage, kind: ProviderKind): JsonObject {
    val images = message.images.orEmpty()
    // `[图片]` 占位文案与 TS 逐字一致。
    val text = message.content.ifEmpty { if (message.imageIds.isNullOrEmpty()) "" else IMAGE_PLACEHOLDER_TEXT }
    return when (kind) {
        ProviderKind.GEMINI -> buildJsonObject {
            put("role", if (message.role == ROLE_ASSISTANT) MODEL_ROLE else ROLE_USER)
            putJsonArray("parts") {
                add(buildJsonObject { put("text", text) })
                images.forEach { image ->
                    addJsonObject {
                        putJsonObject("inlineData") {
                            put("mimeType", image.mime)
                            put("data", base64Payload(image.dataUrl))
                        }
                    }
                }
            }
        }
        ProviderKind.CLAUDE -> buildJsonObject {
            put("role", message.role)
            putJsonArray("content") {
                add(buildJsonObject { put("type", "text"); put("text", text) })
                images.forEach { image ->
                    addJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64")
                            put("media_type", image.mime)
                            put("data", base64Payload(image.dataUrl))
                        }
                    }
                }
            }
        }
        ProviderKind.OPENAI -> if (images.isEmpty()) {
            buildJsonObject { put("role", message.role); put("content", text) }
        } else {
            buildJsonObject {
                put("role", message.role)
                putJsonArray("content") {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", image.dataUrl) }
                        }
                    }
                }
            }
        }
    }
}

/** dataURL 的 base64 段（`img.dataUrl.split(',')[1]`）；没有逗号时退回整串。 */
private fun base64Payload(dataUrl: String): String {
    val comma = dataUrl.indexOf(',')
    return if (comma < 0) dataUrl else dataUrl.substring(comma + 1)
}

/**
 * 请求体（`nativeToolChat.ts:319-333`）。采样参数的键名逐字照抄：
 * OpenAI 是 `top_p` / `frequency_penalty`，Claude 是 `top_k` / `max_tokens`（默认 4096），
 * Gemini 走 `generationConfig`。
 *
 * ⚠️ TS 在 Gemini 分支**只传** temperature / topP / topK / maxOutputTokens，惩罚项不传 —— 照抄。
 * ⚠️ Gemini 的流式/非流式由 URL 区分，body 里没有 `stream` 键（TS 同样没有）。
 */
private fun requestBody(
    params: ToolChatParams,
    kind: ProviderKind,
    history: List<JsonObject>,
    tools: List<ToolDefinition>,
    system: String?,
    stream: Boolean,
): JsonObject = buildJsonObject {
    when (kind) {
        ProviderKind.GEMINI -> {
            put("contents", JsonArray(history))
            if (!system.isNullOrEmpty()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                }
            }
            putJsonObject("generationConfig") {
                params.temperature?.let { put("temperature", it) }
                params.topP?.let { put("topP", it) }
                params.topK?.let { put("topK", it) }
                params.maxTokens?.let { put("maxOutputTokens", it) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { tool ->
                                addJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parametersJsonSchema", tool.schema)
                                }
                            }
                        }
                    }
                }
            }
        }
        ProviderKind.CLAUDE -> {
            put("model", params.model)
            put("messages", JsonArray(history))
            put("stream", stream)
            if (!system.isNullOrEmpty()) put("system", system)
            params.temperature?.let { put("temperature", it) }
            params.topP?.let { put("top_p", it) }
            params.topK?.let { put("top_k", it) }
            put("max_tokens", params.maxTokens ?: 4096)
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.schema)
                        }
                    }
                }
            }
        }
        ProviderKind.OPENAI -> {
            put("model", params.model)
            put("messages", JsonArray(history))
            put("stream", stream)
            params.temperature?.let { put("temperature", it) }
            params.topP?.let { put("top_p", it) }
            params.frequencyPenalty?.let { put("frequency_penalty", it) }
            params.presencePenalty?.let { put("presence_penalty", it) }
            params.maxTokens?.let { put("max_tokens", it) }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.schema)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun allowedFinishReasons(kind: ProviderKind): List<String> = when (kind) {
    ProviderKind.OPENAI -> listOf("stop", "tool_calls")
    ProviderKind.CLAUDE -> listOf("end_turn", "stop_sequence", "tool_use")
    ProviderKind.GEMINI -> listOf("STOP")
}

// ---------------------------------------------------------------------------------------------
// 单轮累积（TS 里是 `while` 循环体内的一批局部变量，这里收成一个类）
// ---------------------------------------------------------------------------------------------

private class Round(
    private val kind: ProviderKind,
    private val steps: Int,
    private val visibleBefore: String,
    private val append: suspend (String) -> Unit,
    private val pushReasoning: suspend (String) -> Unit,
) {
    var reason: String? = null
        private set
    var roundText: String = ""
        private set
    var roundReasoning: String = ""
        private set

    private var separated = false
    private val openCalls = sortedMapOf<Int, OpenCall>()
    private val blocks = sortedMapOf<Int, JsonObject>()
    private val jsonInputs = mutableMapOf<Int, String>()
    val parts = mutableListOf<JsonElement>()

    suspend fun receive(event: ProviderStreamEvent) {
        when (event) {
            is ProviderStreamEvent.TextDelta -> text(event.text)
            is ProviderStreamEvent.ReasoningDelta -> {
                roundReasoning += event.text
                pushReasoning(event.text)
            }
            is ProviderStreamEvent.ToolCallDelta -> accumulate(event)
            is ProviderStreamEvent.SignatureDelta -> {
                val block = blocks[event.index] ?: return
                val prior = block["signature"].stringOrNull().orEmpty()
                blocks[event.index] = JsonObject(block + ("signature" to JsonPrimitive(prior + event.signature)))
            }
            is ProviderStreamEvent.Finished -> event.reason?.takeIf { it.isNotEmpty() }?.let { reason = it }
        }
    }

    /** TS 的 `text()`：工具轮次之间补一个空行，正文同时进 roundText 与 visible。 */
    private suspend fun text(value: String) {
        if (value.isEmpty()) return
        if (!separated && steps > 0 && visibleBefore.isNotEmpty()) append("\n\n")
        separated = true
        roundText += value
        append(value)
    }

    private fun accumulate(event: ProviderStreamEvent.ToolCallDelta) {
        when (kind) {
            ProviderKind.CLAUDE -> {
                // content_block_start 里的 tool_use：把 id / name / input 存进 block 表，
                // partial_json 分片随后按 index 累积（最后整体解析，见 sortedBlocks）。
                if (event.initialArguments != null) {
                    blocks[event.index] = buildJsonObject {
                        put("type", "tool_use")
                        event.id?.let { put("id", it) }
                        event.name?.let { put("name", it) }
                        put("input", event.initialArguments)
                    }
                }
                event.arguments?.let { jsonInputs[event.index] = jsonInputs[event.index].orEmpty() + it }
            }
            ProviderKind.GEMINI -> parts += buildJsonObject {
                putJsonObject("functionCall") {
                    if (event.id != null) put("id", event.id)
                    if (event.name != null) put("name", event.name)
                    put("args", event.initialArguments ?: JsonObject(emptyMap()))
                }
                // Gemini 思考内容可能带 thoughtSignature，回填时带回去。
                event.thoughtSignature?.let { put("thoughtSignature", it) }
            }
            ProviderKind.OPENAI -> {
                // 按 index 把 name / arguments 的**分片串起来**（TS 用的是 `+=`）。
                val call = openCalls.getOrPut(event.index) { OpenCall() }
                event.id?.let { if (call.id.isEmpty()) call.id = it }
                event.name?.let { call.name += it }
                event.arguments?.let { call.arguments = call.arguments.orEmpty() + it }
            }
        }
    }

    fun calls(): List<ToolCall> = when (kind) {
        ProviderKind.OPENAI -> openCalls.values.map {
            ToolCall(id = it.id, name = it.name, rawArguments = it.arguments, initialArguments = null)
        }
        ProviderKind.CLAUDE -> blocks.mapNotNull { (index, block) ->
            if (block["type"].stringOrNull() != "tool_use") return@mapNotNull null
            ToolCall(
                id = block["id"].stringOrNull().orEmpty(),
                name = block["name"].stringOrNull().orEmpty(),
                rawArguments = jsonInputs[index],
                initialArguments = block["input"],
            )
        }
        ProviderKind.GEMINI -> parts.mapNotNull { part ->
            val functionCall = (part as? JsonObject)?.get("functionCall") as? JsonObject ?: return@mapNotNull null
            ToolCall(
                id = functionCall["id"].stringOrNull().orEmpty(),
                name = functionCall["name"].stringOrNull().orEmpty(),
                rawArguments = null,
                initialArguments = functionCall["args"] ?: JsonObject(emptyMap()),
            )
        }
    }

    /**
     * OpenAI 的 assistant 回合：累积出的 tool_calls **原样**回填（含首帧带来的 `type: "function"`）。
     * 不回填 `type` 的话，第二轮请求里模型会收到形状不完整的 tool_calls。
     */
    fun openAiAssistantMessage(): JsonObject = buildJsonObject {
        put("role", ROLE_ASSISTANT)
        if (roundText.isEmpty()) put("content", JsonNull) else put("content", roundText)
        putJsonArray("tool_calls") {
            openCalls.values.forEach { call ->
                addJsonObject {
                    put("id", call.id)
                    put("type", call.type)
                    putJsonObject("function") {
                        put("name", call.name)
                        put("arguments", call.arguments.orEmpty())
                    }
                }
            }
        }
    }

    /** Claude 的 assistant 回合：blocks 按 index 回填；`partial_json` 解析失败按 `{}` 处理（TS 原样）。 */
    fun sortedBlocks(): List<JsonElement> = blocks.map { (index, block) ->
        val parsed = jsonInputs[index]?.let { runCatching { WwJson.parseToJsonElement(it) }.getOrNull() }
        if (parsed is JsonObject) JsonObject(block + ("input" to parsed))
        else JsonObject(block + ("input" to JsonObject(emptyMap())))
    }

    private class OpenCall {
        var id: String = ""
        val type: String = "function"
        var name: String = ""
        var arguments: String? = null
    }
}

// ---------------------------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------------------------

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.boolOrNull(): Boolean? = stringOrNull()?.toBooleanStrictOrNull()
