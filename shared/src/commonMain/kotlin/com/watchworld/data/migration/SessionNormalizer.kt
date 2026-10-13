package com.watchworld.data.migration

import com.watchworld.model.ChatSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.floor

/** 已下线的内置产品指南 ID（`storage.ts:100`）。老版本会把它自动连到每个窗口。 */
const val LEGACY_BUILTIN_KNOWLEDGE_ID: String = "kb-quick-notes"

/** 遗留字段：老版本用它连接 Skill，已被 `skillIds` 取代（`storage.ts:87`）。 */
const val LEGACY_CONNECTED_SKILL_IDS: String = "connectedSkillIds"

/** 老存档里可能出现的、`streamingEnabled` 之外的布尔三态字段。 */
private const val KEY_WEB_SEARCH_ENABLED = "webSearchEnabled"
private const val KEY_STREAMING_ENABLED = "streamingEnabled"
private const val KEY_TOOL_CALL_STEP_LIMIT = "toolCallStepLimit"
private const val KEY_CONNECTED_KNOWLEDGE_IDS = "connectedKnowledgeIds"
private const val KEY_TIMELINE_DESCRIPTION_TAG_REQUESTS = "timelineDescriptionTagRequests"

/**
 * `storage.ts:86-110 normalizeChatSession()` —— **老存档能正确加载的前提**。
 * 见 `MIGRATION_CONSTRAINTS.md` §2。
 *
 * 六条兼容逻辑，以及各自**在本仓由谁执行**（这一点必须清楚，否则会漏）：
 *
 * | # | 规则 | 由谁执行 |
 * |---|---|---|
 * | ① | 删除遗留字段 `connectedSkillIds` | **模型 + 解码**：[ChatSession] 故意不声明该字段，`WwJson.ignoreUnknownKeys = true` 直接丢弃 |
 * | ② | `connectedKnowledgeIds` 剔除内置指南 ID，非数组 → 空 | 本函数（[sanitizeSessionJson] 先把非数组抹成缺失） |
 * | ③ | `timelineDescriptionTagRequests` 非法值 → 空队列 | 本函数（同上） |
 * | ④ | `toolCallStepLimit` 非有限数字 → `null`；合法 → `max(0, floor(n))` | 本函数，但**输入必须是原始 `JsonElement`** |
 * | ⑤ | `webSearchEnabled` 非严格 `true` → `false` | 本函数（[sanitizeSessionJson] 先把非 boolean 抹成缺失） |
 * | ⑥ | `streamingEnabled` 非 boolean → `null`（三态，视为流式） | [sanitizeSessionJson]（模型层只能是 `Boolean?`，非 boolean 在解码前就被抹掉） |
 *
 * ### 为什么 ④ 不能「先反序列化成 `Int?` 再判断」
 *
 * 老存档里它可能是 `-1` / `NaN` / 字符串。`ChatSession.toolCallStepLimit` 是 `Int?`：
 * - 字符串会**解码失败**（不是归一化）；
 * - `-1` 能解码，但直接用它会让工具循环行为异常。
 *
 * 因此调用方必须**从原始 JSON 里取出这个字段**再传进来（见 [sanitizeSessionJson] 与
 * `ArchiveNormalizer`），不能只传解码后的模型。
 *
 * ### 默认参数不是「可省略」
 *
 * `rawToolLimit` 省略时，用**模型自身的值**回推一个等价输入。这使得本函数**幂等**：
 * 已经归一化过的会话再归一化一次结果不变（`ArchiveRepository.export` 依赖这一点）。
 * 若改成省略即 `null`，第二次归一化会把合法的 `toolCallStepLimit = 5` 抹成 `null`。
 *
 * @param rawToolLimit 原始 JSON 里的 `toolCallStepLimit` 字段；缺失传 `null`。
 */
fun normalizeChatSession(
    session: ChatSession,
    rawToolLimit: JsonElement? = session.toolCallStepLimit?.let { JsonPrimitive(it) },
): ChatSession = session.copy(
    // ② 内置产品指南已迁出知识库。只移除明确的内置 ID，不动用户自己选的其他条目。
    connectedKnowledgeIds = session.connectedKnowledgeIds.filter { it != LEGACY_BUILTIN_KNOWLEDGE_ID },
    // ③ 老数据没有这个字段；非法值当作「没有待审批请求」，而不是抛错。
    timelineDescriptionTagRequests = session.timelineDescriptionTagRequests ?: emptyList(),
    // ④ 非有限数字 → null（继承全局）；合法 → max(0, floor(n))。
    toolCallStepLimit = toolCallStepLimitFrom(rawToolLimit),
    // ⑤ 老存档可能是 undefined / 字符串 / 数字。只有严格 true 才算授权。
    webSearchEnabled = session.webSearchEnabled == true,
    // ⑥ streamingEnabled 在这里**故意原样保留**：模型层已经是 Boolean?，非 boolean 的值
    //    在 sanitizeSessionJson 里就没了。写成 `session.streamingEnabled` 只是为了标出
    //    「这条规则在本函数里没有额外动作」，别误以为漏了。
)

/**
 * 把原始 session 的 `JsonObject` 揉成「一定能解码成 [ChatSession]」的形状。
 *
 * TS 侧靠 `Array.isArray(...)` / `typeof ... === 'boolean'` 在运行时兜住这些字段，Kotlin 的
 * `@Serializable` 解码则**类型不符就抛**（`coerceInputValues` 只管 `null` 与未知 enum，不管
 * 数组收到字符串这类类型错配）。所以必须在解码前按同样的判据清洗一遍。
 *
 * ⚠️ **只做「类型」清洗，不做「语义」归一**：语义仍由 [normalizeChatSession] 一处负责，
 * 避免两处实现漂移。[toolCallStepLimit] 是唯一例外 —— 它必须在这里就把非法值抹掉，
 * 否则字符串会直接炸掉整份存档的解码；抹掉后 [normalizeChatSession] 会拿到原始的
 * `JsonElement` 补上 `-1 → 0` 这类真正需要数值判断的规则。
 *
 * @return 清洗后的对象；字段缺失等价于「该字段是 null」，与 TS 的 `undefined` 一致。
 */
fun sanitizeSessionJson(raw: JsonObject): JsonObject {
    var result = raw
    // ① 遗留字段：模型没声明它，解码时本来就会丢；这里显式删掉，让「清洗后的 JSON」
    //    本身就是干净的（导出/回写时不再出现）。
    result = result.withoutKey(LEGACY_CONNECTED_SKILL_IDS)
    // ②③ 非数组 → 抹成缺失，解码得 null，再由 normalizeChatSession 归一成空队列。
    result = result.keepingArrayOnly(KEY_CONNECTED_KNOWLEDGE_IDS)
    result = result.keepingArrayOnly(KEY_TIMELINE_DESCRIPTION_TAG_REQUESTS)
    // ④ 非有限数字（字符串 / 布尔 / NaN / 越界）→ 抹成缺失；合法值就地压成归一化后的 Int。
    result = result.keepingNormalizedToolLimit(KEY_TOOL_CALL_STEP_LIMIT)
    // ⑤⑥ 非 boolean → 抹成缺失（= null）。webSearchEnabled 随后由本层之外的第 ⑤ 条变成 false。
    result = result.keepingBooleanOnly(KEY_WEB_SEARCH_ENABLED)
    result = result.keepingBooleanOnly(KEY_STREAMING_ENABLED)
    return result
}

/**
 * `storage.ts:91-94` 的第 ④ 条判据：
 * `typeof n === 'number' && Number.isFinite(n) ? Math.max(0, Math.floor(n)) : undefined`
 *
 * 逐项对应：
 * - `typeof n === 'number'` → 非 `JsonPrimitive`、或 `isString`、或 `JsonNull` 都不算数字；
 * - `Number.isFinite` → `NaN` / `Infinity` 一律出局（`doubleOrNull` 对 `"NaN"` 会给出 `NaN`）；
 * - `Math.floor` + `Math.max(0, …)` → 向下取整并钳到 `>= 0`。
 *
 * 越界（如 `1e30`）在 TS 里没有 Int 上限这回事，Kotlin 里钳到 [Int.MAX_VALUE]，
 * 否则 `.toInt()` 会溢出成负数 —— 那比钳住危险得多。
 */
fun toolCallStepLimitFrom(raw: JsonElement?): Int? {
    val primitive = raw as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    val value = primitive.doubleOrNull ?: return null
    if (!value.isFinite()) return null
    val floored = floor(value)
    return when {
        floored <= 0.0 -> 0
        floored >= Int.MAX_VALUE.toDouble() -> Int.MAX_VALUE
        else -> floored.toInt()
    }
}

private fun JsonObject.withoutKey(key: String): JsonObject =
    if (key in this) JsonObject(this - key) else this

private fun JsonObject.keepingArrayOnly(key: String): JsonObject {
    val value = this[key] ?: return this
    return if (value is JsonArray) this else JsonObject(this - key)
}

private fun JsonObject.keepingBooleanOnly(key: String): JsonObject {
    val value = this[key] ?: return this
    val isBoolean = value is JsonPrimitive && value !is JsonNull && value.booleanOrNull != null
    return if (isBoolean) this else JsonObject(this - key)
}

private fun JsonObject.keepingNormalizedToolLimit(key: String): JsonObject {
    if (key !in this) return this
    val normalized = toolCallStepLimitFrom(this[key])
    return if (normalized == null) {
        JsonObject(this - key)
    } else {
        JsonObject(this + (key to JsonPrimitive(normalized)))
    }
}
