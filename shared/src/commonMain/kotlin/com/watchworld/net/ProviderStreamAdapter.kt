package com.watchworld.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ProviderKind {
    OPENAI,
    CLAUDE,
    GEMINI,
}

/** Protocol-neutral deltas consumed by chat UI now and the tool loop in T09. */
sealed interface ProviderStreamEvent {
    data class TextDelta(val text: String) : ProviderStreamEvent
    data class ReasoningDelta(val text: String) : ProviderStreamEvent
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val arguments: String? = null,
        val initialArguments: JsonElement? = null,
        val thoughtSignature: String? = null,
    ) : ProviderStreamEvent
    data class SignatureDelta(val index: Int, val signature: String) : ProviderStreamEvent
    data class Finished(val reason: String? = null) : ProviderStreamEvent
}

interface ProviderStreamAdapter {
    fun adapt(event: JsonObject): List<ProviderStreamEvent>
}

object ProviderStreamAdapters {
    fun forProvider(provider: ProviderKind): ProviderStreamAdapter = when (provider) {
        ProviderKind.OPENAI -> OpenAiStreamAdapter()
        ProviderKind.CLAUDE -> ClaudeStreamAdapter()
        ProviderKind.GEMINI -> GeminiStreamAdapter()
    }
}

private class OpenAiStreamAdapter : ProviderStreamAdapter {
    override fun adapt(event: JsonObject): List<ProviderStreamEvent> {
        val choice = event["choices"]?.asArray()?.firstOrNull()?.asObject() ?: return emptyList()
        val delta = choice["delta"]?.asObject().orEmpty()
        val output = mutableListOf<ProviderStreamEvent>()

        delta["content"]?.asString()?.takeIf(String::isNotEmpty)?.let {
            output += ProviderStreamEvent.TextDelta(it)
        }
        delta["reasoning_content"]?.asString()?.takeIf(String::isNotEmpty)?.let {
            output += ProviderStreamEvent.ReasoningDelta(it)
        }
        delta["tool_calls"]?.asArray()?.forEachIndexed { fallbackIndex, value ->
            val call = value.asObject() ?: return@forEachIndexed
            val function = call["function"]?.asObject().orEmpty()
            val id = call["id"]?.asString()
            val name = function["name"]?.asString()
            val arguments = function["arguments"]?.asString()
            if (id != null || name != null || arguments != null) {
                output += ProviderStreamEvent.ToolCallDelta(
                    index = call["index"]?.asInt() ?: fallbackIndex,
                    id = id,
                    name = name,
                    arguments = arguments,
                )
            }
        }
        choice["finish_reason"]?.asString()?.takeIf(String::isNotEmpty)?.let {
            output += ProviderStreamEvent.Finished(it)
        }
        return output
    }
}

private class ClaudeStreamAdapter : ProviderStreamAdapter {
    private val startedBlocks = mutableSetOf<Int>()

    override fun adapt(event: JsonObject): List<ProviderStreamEvent> {
        val output = mutableListOf<ProviderStreamEvent>()
        val type = event["type"]?.asString()

        when (type) {
            "content_block_start" -> {
                val index = event["index"]?.asInt()
                    ?: throw StreamProtocolException("Claude content_block_start is missing index")
                val block = event["content_block"]?.asObject()
                    ?: throw StreamProtocolException("Claude content_block_start is missing content_block")
                startedBlocks += index
                when (block["type"]?.asString()) {
                    "text" -> block["text"]?.asString()?.takeIf(String::isNotEmpty)?.let {
                        output += ProviderStreamEvent.TextDelta(it)
                    }
                    "thinking" -> block["thinking"]?.asString()?.takeIf(String::isNotEmpty)?.let {
                        output += ProviderStreamEvent.ReasoningDelta(it)
                    }
                    "tool_use" -> output += ProviderStreamEvent.ToolCallDelta(
                        index = index,
                        id = block["id"]?.asString(),
                        name = block["name"]?.asString(),
                        initialArguments = block["input"],
                    )
                }
            }
            "content_block_delta" -> {
                val index = event["index"]?.asInt()
                    ?: throw StreamProtocolException("Claude content_block_delta is missing index")
                if (index !in startedBlocks) {
                    throw StreamProtocolException("Claude stream is missing content_block_start for index $index")
                }
                val delta = event["delta"]?.asObject().orEmpty()
                when (delta["type"]?.asString()) {
                    "text_delta" -> delta["text"]?.asString()?.takeIf(String::isNotEmpty)?.let {
                        output += ProviderStreamEvent.TextDelta(it)
                    }
                    "thinking_delta" -> delta["thinking"]?.asString()?.takeIf(String::isNotEmpty)?.let {
                        output += ProviderStreamEvent.ReasoningDelta(it)
                    }
                    "input_json_delta" -> delta["partial_json"]?.asString()?.takeIf(String::isNotEmpty)?.let {
                        output += ProviderStreamEvent.ToolCallDelta(index = index, arguments = it)
                    }
                    "signature_delta" -> delta["signature"]?.asString()?.takeIf(String::isNotEmpty)?.let {
                        output += ProviderStreamEvent.SignatureDelta(index, it)
                    }
                }
            }
            "message_delta" -> {
                event["delta"]?.asObject()?.get("stop_reason")?.asString()?.let {
                    output += ProviderStreamEvent.Finished(it)
                }
            }
            "message_stop" -> output += ProviderStreamEvent.Finished()
        }
        return output
    }
}

private class GeminiStreamAdapter : ProviderStreamAdapter {
    override fun adapt(event: JsonObject): List<ProviderStreamEvent> {
        val feedback = event["promptFeedback"]?.asObject()
        feedback?.get("blockReason")?.asString()?.takeIf(String::isNotEmpty)?.let {
            return listOf(ProviderStreamEvent.Finished(it))
        }

        val candidate = event["candidates"]?.asArray()?.firstOrNull()?.asObject() ?: return emptyList()
        val output = mutableListOf<ProviderStreamEvent>()
        val parts = candidate["content"]?.asObject()?.get("parts")?.asArray().orEmpty()
        parts.forEachIndexed { toolIndex, value ->
            val part = value.asObject() ?: return@forEachIndexed
            val text = part["text"]?.asString()
            if (!text.isNullOrEmpty()) {
                output += if (part["thought"]?.asBoolean() == true) {
                    ProviderStreamEvent.ReasoningDelta(text)
                } else {
                    ProviderStreamEvent.TextDelta(text)
                }
            }
            part["functionCall"]?.asObject()?.let { functionCall ->
                output += ProviderStreamEvent.ToolCallDelta(
                    index = toolIndex,
                    id = functionCall["id"]?.asString(),
                    name = functionCall["name"]?.asString(),
                    initialArguments = functionCall["args"],
                    thoughtSignature = part["thoughtSignature"]?.asString(),
                )
            }
        }
        candidate["finishReason"]?.asString()?.takeIf(String::isNotEmpty)?.let {
            output += ProviderStreamEvent.Finished(it)
        }
        return output
    }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asInt(): Int? = asString()?.toIntOrNull()

private fun JsonElement?.asBoolean(): Boolean? = asString()?.toBooleanStrictOrNull()

private fun JsonObject.asObject(): JsonObject = this

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
