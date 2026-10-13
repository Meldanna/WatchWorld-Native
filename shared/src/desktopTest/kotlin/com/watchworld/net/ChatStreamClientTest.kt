package com.watchworld.net

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * **PLAN §8.2：真流式验收标准** —— 「这是迁移成败的判据」。
 *
 * 判据不是"收到了内容"，而是**帧间隔贴近上游节奏**：
 * 如果实现里误用了 `bodyAsText()` / `body<String>()` / `flow{}.buffer()`，
 * 所有帧会在同一瞬间一起到达，`gaps` 就会全是 0。
 *
 * 这里用 JDK 自带的 `com.sun.net.httpserver.HttpServer` 起假上游
 * （每 300ms 推一帧，与当初 Worker 验证用的节奏一致），因此本测试放在
 * `desktopTest`（需要 JDK 网络栈），不进 `commonTest`。
 *
 * 参照基线：`311, 310, 313, 309, 310, 311, 314` ms。
 */
class ChatStreamClientTest {

    private val frameIntervalMs = 300L
    private val frameCount = 7

    /** 起一个每 [frameIntervalMs] 推一帧的假 SSE 上游，返回 server 与 url。 */
    private fun startFakeUpstream(): Pair<HttpServer, String> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            // 长度 0 = chunked：必须走分块传输，才可能真的增量到达。
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                repeat(frameCount) { index ->
                    if (index > 0) Thread.sleep(frameIntervalMs)
                    val frame = "data: {\"choices\":[{\"delta\":{\"content\":\"帧$index\"}}]}\n\n"
                    out.write(frame.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                out.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
        server.executor = null
        server.start()
        return server to "http://127.0.0.1:${server.address.port}/chat"
    }

    @Test
    fun frameIntervalsFollowUpstreamPaceInsteadOfArrivingAtOnce() = runBlocking {
        val (server, url) = startFakeUpstream()
        val client = ChatStreamClient(HttpClientFactory.create())
        try {
            val marks = mutableListOf<Long>()
            val start = TimeSource.Monotonic.markNow()

            client.streamSse(url).collect { line ->
                // 只记内容帧；末尾的 [DONE] 紧随最后一帧到达，会把间隔算成 0。
                if (line.contains("\"content\"")) {
                    marks += start.elapsedNow().inWholeMilliseconds
                }
            }

            val gaps = marks.zipWithNext { a, b -> b - a }
            assertEquals(frameCount, marks.size, "应收到 $frameCount 个内容帧")
            assertTrue(gaps.size >= 5, "应收到多帧而非一次性到达：$gaps")
            assertTrue(gaps.all { it in 250..400 }, "帧间隔应贴近 ${frameIntervalMs}ms：$gaps")
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun nonSuccessStatusThrowsApiException() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/boom") { exchange ->
            val body = "{\"error\":\"nope\"}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.executor = null
        server.start()

        val client = ChatStreamClient(HttpClientFactory.create())
        try {
            val url = "http://127.0.0.1:${server.address.port}/boom"
            val error = runCatching { client.streamSse(url).collect { } }.exceptionOrNull()
            val apiError = assertIs<ApiException>(error, "应抛 ApiException，实际：$error")
            assertEquals(401, apiError.statusCode)
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
