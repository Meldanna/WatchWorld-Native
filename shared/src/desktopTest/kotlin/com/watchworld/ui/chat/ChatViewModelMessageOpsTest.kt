package com.watchworld.ui.chat

import com.sun.net.httpserver.HttpServer
import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.model.Agent
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import com.watchworld.net.ChatStreamClient
import com.watchworld.net.HttpClientFactory
import com.watchworld.net.McpClient
import com.watchworld.net.WebSearchClient
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatViewModelMessageOpsTest {
    private companion object {
        var clock = 2_000_000_000_000L
        fun tick(): Long { clock += 1_000; return clock }
        fun sse(text: String): String = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"$text\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
            append("data: [DONE]\n\n")
        }
    }

    private class Upstream(payload: String) {
        private val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        init {
            srv.createContext("/v1/chat/completions") { ex ->
                ex.responseHeaders.add("Content-Type", "text/event-stream")
                ex.sendResponseHeaders(200, 0)
                ex.responseBody.use { it.write(payload.toByteArray()); it.flush() }
            }; srv.start()
        }
        val base get() = "http://127.0.0.1:${srv.address.port}/v1"
        fun stop() = srv.stop(0)
    }

    private class E(val db: WwDatabase, val up: Upstream) {
        val sr = SessionRepository(db) { tick() }
        private val cr = ConfigRepository(db)
        private val http = HttpClientFactory.create()
        val vm = ChatViewModel(
            sessionRepository = sr, settingsRepository = SettingsRepository(db),
            configRepository = cr, chatClient = ChatStreamClient(http),
            searchClient = WebSearchClient(http), mcpClient = McpClient(http),
            now = { tick() })
        suspend fun seed() {
            cr.setProviders(listOf(ApiProviderConfig(id="p", name="f",
                type=ApiProviderType.OPENAI, baseUrl=up.base, apiKey="k", defaultModel="m")))
            cr.setAgents(listOf(Agent(id="a", name="b", systemPrompt="hi")))
        }
        fun s() = ChatSession(id="s-${tick()}", title="t", agentId="a", providerId="p", model="m")
        fun close() { http.close() }
    }

    private fun go(payload: String, b: suspend (E) -> Unit) {
        val up = Upstream(payload); val db = WwDatabase.openInMemory(); val e = E(db, up)
        try { runBlocking { b(e) } } finally { e.close(); up.stop(); db.close() }
    }

    @Test fun switchVersionPersists() = go(sse("x")) { e ->
        val s = e.sr.create(e.s().copy(messages = listOf(ChatMessage(
            id = "m1", role = Role.ASSISTANT,
            versions = listOf(
                MessageVersion(content = "v0", latencyMs = 100, tokens = 10),
                MessageVersion(content = "v1", latencyMs = 200, tokens = 20),
                MessageVersion(content = "v2", latencyMs = 300, tokens = 30)),
            currentVersionIndex = 0, latencyMs = 100, tokens = 10))))
        e.vm.open(s); e.vm.switchVersion("m1", 2)
        val m = assertNotNull(e.sr.byId(s.id)).messages.single()
        assertEquals(2, m.currentVersionIndex)
        assertEquals(300L, m.latencyMs); assertEquals(30, m.tokens)
    }

    @Test fun switchVersionClamps() = go(sse("x")) { e ->
        val s = e.sr.create(e.s().copy(messages = listOf(ChatMessage(
            id = "m1", role = Role.ASSISTANT,
            versions = listOf(MessageVersion(content = "a"), MessageVersion(content = "b")),
            currentVersionIndex = 0))))
        e.vm.open(s); e.vm.switchVersion("m1", 99)
        assertEquals(1, assertNotNull(e.sr.byId(s.id)).messages.single().currentVersionIndex)
    }

    @Test fun deleteMessagePersists() = go(sse("x")) { e ->
        val s = e.sr.create(e.s().copy(messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, content = "a"),
            ChatMessage(id = "a1", role = Role.ASSISTANT, versions = listOf(MessageVersion(content = "b"))),
            ChatMessage(id = "u2", role = Role.USER, content = "c"))))
        e.vm.open(s); e.vm.deleteMessage("a1")
        assertEquals(listOf("u1", "u2"), assertNotNull(e.sr.byId(s.id)).messages.map { it.id })
    }

    @Test fun editResendTruncates() = go(sse("new")) { e ->
        e.seed()
        val s = e.sr.create(e.s().copy(messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, content = "old", floorNumber = 1),
            ChatMessage(id = "a1", role = Role.ASSISTANT, floorNumber = 2,
                versions = listOf(MessageVersion(content = "ans"))),
            ChatMessage(id = "u2", role = Role.USER, content = "f", floorNumber = 3))))
        e.vm.open(s); e.vm.editAndResend("u1", "edited")
        val st = assertNotNull(e.sr.byId(s.id))
        assertEquals("edited", st.messages.first().content)
        assertTrue(st.messages.size >= 2)
        assertEquals(Role.ASSISTANT, st.messages.last().role)
    }

    @Test fun regenerateAddsVersion() = go(sse("regen")) { e ->
        e.seed()
        val s = e.sr.create(e.s().copy(messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, content = "hi", floorNumber = 1),
            ChatMessage(id = "a1", role = Role.ASSISTANT, floorNumber = 2,
                versions = listOf(MessageVersion(content = "orig")), currentVersionIndex = 0))))
        e.vm.open(s); e.vm.regenerate("a1")
        val m = assertNotNull(e.sr.byId(s.id)).messages.last()
        assertEquals("a1", m.id); assertEquals(2, m.versions.size)
        assertEquals(1, m.currentVersionIndex)
        assertTrue(m.versions[1].content.contains("regen"))
        assertEquals("orig", m.versions[0].content)
    }

    @Test fun regenerateUserNoOp() = go(sse("x")) { e ->
        val s = e.sr.create(e.s().copy(messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, content = "hi"))))
        e.vm.open(s); e.vm.regenerate("u1")
        assertEquals(1, assertNotNull(e.sr.byId(s.id)).messages.size)
    }
}

