package com.watchworld.net

import com.watchworld.model.WwJson
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SseProviderAdapterTest {

    @Test
    fun parserCombinesMultilineDataAndStopsAtDone() = runBlocking {
        var reachedAfterDone = false
        val lines = flow {
            emit(": keepalive")
            emit("event: message")
            emit("data: {\"type\":")
            emit("data: \"message_start\"}")
            emit("")
            emit("data: [DONE]")
            emit("")
            reachedAfterDone = true
            emit("data: {\"ignored\":true}")
        }

        val parsed = SseParser.parse(lines).toList()
        assertEquals(1, parsed.size)
        assertEquals("message_start", parsed.single()["type"]?.toString()?.trim('"'))
        assertFalse(reachedAfterDone, "parser must cancel upstream as soon as [DONE] is seen")
    }

    @Test
    fun parserDispatchesFinalFrameAtEofAndHonorsMessageStop() = runBlocking {
        var reachedAfterStop = false
        val lines = flow {
            listOf(
                "data: {\"type\":\"content_block_start\",\"index\":0}",
                "",
                "data: {\"type\":\"message_stop\"}",
                "",
            ).forEach { emit(it) }
            reachedAfterStop = true
            emit("data: {\"type\":\"message_start\"}")
        }
        val parsed = SseParser.parse(lines).toList()

        assertEquals(2, parsed.size)
        assertEquals("message_stop", parsed.last()["type"]?.toString()?.trim('"'))
        assertFalse(reachedAfterStop, "message_stop must stop upstream after its own event is emitted")
    }

    @Test
    fun parserSurfacesMalformedJsonAndProviderErrorFrames() = runBlocking {
        val malformed = assertFailsWith<StreamProtocolException> {
            SseParser.parse(listOf("data: {not-json}", "").asFlow()).toList()
        }
        assertTrue(malformed.message.orEmpty().contains("JSON"))

        val apiError = assertFailsWith<ApiException> {
            SseParser.parse(
                listOf("data: {\"type\":\"error\",\"error\":{\"message\":\"denied\"}}", "").asFlow(),
            ).toList()
        }
        assertEquals(0, apiError.statusCode)
        assertEquals("denied", apiError.message)
    }

    @Test
    fun openAiFixtureNormalizesTextAndChunkedToolArguments() {
        runBlocking {
            val adapter = ProviderStreamAdapters.forProvider(ProviderKind.OPENAI)
            val textEvents = SseParser.parse(readFixture("openai-stream.txt").asFlow())
                .toList()
                .flatMap(adapter::adapt)
            assertTrue(textEvents.filterIsInstance<ProviderStreamEvent.TextDelta>().any { it.text.isNotEmpty() })

            val toolEvents = SseParser.parse(readFixture("openai-toolcall-stream.txt").asFlow())
                .toList()
                .flatMap(adapter::adapt)
                .filterIsInstance<ProviderStreamEvent.ToolCallDelta>()
                .mapNotNull { it.arguments }
            assertTrue(toolEvents.size >= 2, "real fixture must contain multiple argument chunks")
            assertIs<kotlinx.serialization.json.JsonObject>(
                WwJson.parseToJsonElement(toolEvents.joinToString("")),
            )
        }
    }

    @Test
    fun claudeAdapterRequiresBlockStartAndSeparatesThinkingFromText() {
        val adapter = ProviderStreamAdapters.forProvider(ProviderKind.CLAUDE)
        assertFailsWith<StreamProtocolException> {
            adapter.adapt(json("""{"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"x"}}"""))
        }

        assertEquals(
            listOf(ProviderStreamEvent.TextDelta("hi")),
            adapter.adapt(json("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":"hi"}}""")),
        )
        assertEquals(
            emptyList(),
            adapter.adapt(json("""{"type":"content_block_start","index":1,"content_block":{"type":"thinking","thinking":""}}""")),
        )
        assertEquals(
            listOf(ProviderStreamEvent.ReasoningDelta("thinking")),
            adapter.adapt(json("""{"type":"content_block_delta","index":1,"delta":{"type":"thinking_delta","thinking":"thinking"}}""")),
        )
    }

    @Test
    fun geminiThoughtPartDoesNotBecomeVisibleText() {
        val adapter = ProviderStreamAdapters.forProvider(ProviderKind.GEMINI)
        val events = adapter.adapt(
            json(
                """{"candidates":[{"content":{"parts":[{"text":"internal","thought":true},{"text":"visible"},{"functionCall":{"name":"lookup","args":{"city":"Tokyo"}},"thoughtSignature":"sig"}]},"finishReason":"STOP"}]}""",
            ),
        )

        assertEquals(
            listOf(
                ProviderStreamEvent.ReasoningDelta("internal"),
                ProviderStreamEvent.TextDelta("visible"),
                ProviderStreamEvent.ToolCallDelta(
                    index = 2,
                    name = "lookup",
                    initialArguments = json("""{"city":"Tokyo"}"""),
                    thoughtSignature = "sig",
                ),
                ProviderStreamEvent.Finished("STOP"),
            ),
            events,
        )
    }

    private fun json(source: String) = WwJson.parseToJsonElement(source).jsonObject

    private fun readFixture(name: String): List<String> {
        val start = File(System.getProperty("user.dir"))
        val fixture = generateSequence(start) { it.parentFile }
            .map { File(it, "fixtures/sse/$name") }
            .firstOrNull(File::isFile)
            ?: error("Fixture not found from ${start.absolutePath}: fixtures/sse/$name")
        return fixture.readLines()
    }
}
