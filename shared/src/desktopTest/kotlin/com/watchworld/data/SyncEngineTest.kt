package com.watchworld.data

import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.createSyncState
import com.watchworld.data.repository.isSyncConfigured
import com.watchworld.data.repository.pullChanges
import com.watchworld.data.repository.pushChanges
import com.watchworld.model.ChatSession
import com.watchworld.model.WebDavConfig
import com.watchworld.model.WwJson
import com.watchworld.net.FakeWebDavServer
import com.watchworld.net.HttpClientFactory
import com.watchworld.net.SyncManifest
import com.watchworld.net.SyncSessionMeta
import com.watchworld.net.WebDavClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T19 验收：[SyncEngine] 的增量 push/pull 语义（时间戳较新者胜）。
 *
 * 假 WebDAV 上游 + 内存 SQLite；图片同步属 T21，不在本套用例范围。
 */
class SyncEngineTest {

    private fun config(url: String, enabled: Boolean = true, autoSync: Boolean = true) = WebDavConfig(
        enabled = enabled,
        url = url,
        username = "user",
        password = "pass",
        autoSync = autoSync,
    )

    private fun payloadOf(session: ChatSession): String = buildJsonObject {
        put("kind", "session")
        put("payload", WwJson.encodeToJsonElement(session))
    }.toString()

    private fun manifestJson(vararg entries: Pair<String, Long>): String = WwJson.encodeToString(
        SyncManifest(
            updatedAt = 1L,
            sessions = entries.associate { (id, updatedAt) -> id to SyncSessionMeta(updatedAt = updatedAt) },
        ),
    )

    private fun <T> withClient(block: suspend (WebDavClient) -> T): T {
        val http = HttpClientFactory.create()
        return try {
            runBlocking { block(WebDavClient(http)) }
        } finally {
            http.close()
        }
    }

    @Test
    fun isSyncConfiguredRequiresEnabledAndCompleteCredentials() {
        assertTrue(isSyncConfigured(config("https://dav/x")))
        assertFalse(isSyncConfigured(null))
        assertFalse(isSyncConfigured(config("https://dav/x", enabled = false)))
        assertFalse(isSyncConfigured(WebDavConfig(enabled = true, url = "", username = "u", password = "p")))
        assertFalse(isSyncConfigured(WebDavConfig(enabled = true, url = "u", username = "", password = "p")))
        assertFalse(isSyncConfigured(WebDavConfig(enabled = true, url = "u", username = "u", password = null)))
    }

    @Test
    fun pushUploadsNewSessionAndSecondPushIsNoop() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val state = createSyncState()
            val session = ChatSession(id = "s1", title = "一", updatedAt = 1000L)

            val first = pushChanges(config(fake.url), listOf(session), state, client, now = { 1000L })
            assertEquals(1, first.pushed)
            assertEquals("已上传 1 个窗口 / 0 张图片", first.message)
            assertContains(fake.stored("/dav/guanjie/windows/s1.json").orEmpty(), "\"kind\":\"session\"")
            assertContains(fake.stored("/dav/guanjie/index.json").orEmpty(), "\"s1\"")

            val second = pushChanges(config(fake.url), listOf(session), state, client, now = { 2000L })
            assertEquals(0, second.pushed)
            assertEquals("无改动", second.message)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pushOnlyUploadsSessionsNewerThanManifest() = withClient { client ->
        val fake = FakeWebDavServer()
        fake.store("/dav/guanjie/index.json", manifestJson("s1" to 1000L, "s2" to 1000L))
        try {
            val state = createSyncState()
            val sessions = listOf(
                ChatSession(id = "s1", title = "旧", updatedAt = 1000L),
                ChatSession(id = "s2", title = "新", updatedAt = 2000L),
            )
            val outcome = pushChanges(config(fake.url), sessions, state, client, now = { 3000L })
            assertEquals(1, outcome.pushed)
            assertContains(outcome.message, "已上传 1 个窗口")
            // s1 没有变化（本地 <= 清单），不应被上传。
            assertEquals(null, fake.stored("/dav/guanjie/windows/s1.json"))
            assertContains(fake.stored("/dav/guanjie/windows/s2.json").orEmpty(), "s2")
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pullDownloadsNewerSessions() = withClient { client ->
        val fake = FakeWebDavServer()
        val remote = ChatSession(id = "s9", title = "远端", updatedAt = 5000L, webSearchEnabled = true)
        fake.store("/dav/guanjie/index.json", manifestJson("s9" to 5000L))
        fake.store("/dav/guanjie/windows/s9.json", payloadOf(remote))
        try {
            val state = createSyncState()
            val (pulled, outcome) = pullChanges(config(fake.url), emptyList(), state, client)

            assertEquals(1, outcome.pulled)
            assertEquals("已拉取 1 个窗口", outcome.message)
            assertEquals("s9", pulled.single().id)
            // 归一化第 ⑤ 条：只有严格 true 才保持 true。
            assertEquals(true, pulled.single().webSearchEnabled)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pullSkipsWhenLocalIsAtLeastAsNew() = withClient { client ->
        val fake = FakeWebDavServer()
        fake.store("/dav/guanjie/index.json", manifestJson("s1" to 5000L))
        fake.store("/dav/guanjie/windows/s1.json", payloadOf(ChatSession(id = "s1", updatedAt = 5000L)))
        try {
            val state = createSyncState()
            val local = listOf(ChatSession(id = "s1", title = "本地", updatedAt = 9000L))
            val (pulled, outcome) = pullChanges(config(fake.url), local, state, client)
            assertTrue(pulled.isEmpty())
            assertEquals("无更新", outcome.message)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pullWithoutManifestReturnsNoUpdates() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val state = createSyncState()
            val (pulled, outcome) = pullChanges(config(fake.url), emptyList(), state, client)
            assertTrue(pulled.isEmpty())
            assertEquals("无更新", outcome.message)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pulledUpdatedAtSurvivesReplaceAll() = withClient { client ->
        val fake = FakeWebDavServer()
        val remote = ChatSession(id = "s9", title = "远端", updatedAt = 5000L)
        fake.store("/dav/guanjie/index.json", manifestJson("s9" to 5000L))
        fake.store("/dav/guanjie/windows/s9.json", payloadOf(remote))
        val db = WwDatabase.openInMemory()
        // 故意用一个会「盖 updatedAt」的 now：App 的合并必须走 replaceAll（不盖戳），
        // 否则远端时间戳会被改成本机时间，下一轮又反向推送。
        val repository = SessionRepository(db, now = { 999_999L })
        try {
            val local = runBlocking { repository.all() }
            val (pulled, _) = pullChanges(config(fake.url), local, createSyncState(), client)
            val merged = local.associateBy { it.id }.toMutableMap()
            pulled.forEach { merged[it.id] = it }
            runBlocking { repository.replaceAll(merged.values.toList()) }

            val stored = runBlocking { repository.all() }.single()
            assertEquals("s9", stored.id)
            assertEquals(5000L, stored.updatedAt, "replaceAll 不许盖 updatedAt")
        } finally {
            db.close()
            fake.stop()
        }
    }
}
