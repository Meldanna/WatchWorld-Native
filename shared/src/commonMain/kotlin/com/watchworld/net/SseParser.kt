package com.watchworld.net

import com.watchworld.model.WwJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Malformed SSE payloads are protocol failures; callers must not silently discard them. */
class StreamProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses SSE data lines without buffering the response.
 *
 * Empty lines dispatch accumulated data, including the final unterminated frame at EOF.
 * `[DONE]` and Claude's `message_stop` both stop upstream collection immediately.
 */
object SseParser {

    fun parse(lines: Flow<String>): Flow<JsonObject> = flow {
        val state = State()
        lines.transformWhile { line ->
            val frame = state.accept(line)
            frame?.payload?.let { emit(it) }
            frame?.terminal != true
        }.collect { emit(it) }

        if (!state.ended) {
            state.finish()?.payload?.let { emit(it) }
        }
    }

    private data class Frame(val payload: JsonObject?, val terminal: Boolean)

    private class State {
        private val data = mutableListOf<String>()
        var ended = false
            private set

        fun accept(rawLine: String): Frame? {
            if (ended) return null
            val line = rawLine.removeSuffix("\r")
            return when {
                line.isEmpty() -> dispatch()
                line.startsWith("data:") -> {
                    data += line.removePrefix("data:").removePrefix(" ")
                    null
                }
                else -> null
            }
        }

        fun finish(): Frame? = if (ended) null else dispatch()

        private fun dispatch(): Frame? {
            if (data.isEmpty()) return null

            val payload = data.joinToString("\n")
            data.clear()
            if (payload.trim() == "[DONE]") {
                ended = true
                return Frame(payload = null, terminal = true)
            }

            val json = try {
                WwJson.parseToJsonElement(payload).jsonObject
            } catch (error: SerializationException) {
                throw StreamProtocolException("SSE data frame is not a JSON object", error)
            } catch (error: IllegalStateException) {
                throw StreamProtocolException("SSE data frame is not a JSON object", error)
            } catch (error: IllegalArgumentException) {
                throw StreamProtocolException("SSE data frame is not a JSON object", error)
            }

            throwIfStreamError(json)
            val terminal = json["type"]?.jsonPrimitive?.contentOrNull == "message_stop"
            if (terminal) ended = true
            return Frame(payload = json, terminal = terminal)
        }

        private fun throwIfStreamError(payload: JsonObject) {
            val errorValue = payload["error"]
            val isError = errorValue != null ||
                payload["type"]?.jsonPrimitive?.contentOrNull == "error"
            if (!isError) return

            val message = (errorValue as? JsonObject)
                ?.get("message")
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: (errorValue as? JsonPrimitive)?.contentOrNull
                ?: "模型返回流式错误"
            throw ApiException(0, message)
        }
    }
}
