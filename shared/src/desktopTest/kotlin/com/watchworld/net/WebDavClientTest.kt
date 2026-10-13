package com.watchworld.net

import com.watchworld.model.ChatSession
import com.watchworld.model.WebDavConfig
import com.watchworld.model.WwJson
import kotlin.io.encoding.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T19 验收：[WebDavClient] 的传输层（PROPFIND/PUT/GET/DELETE/MKCOL + Basic 认证）。
 *
 * 全部对着进程内 [FakeWebDavServer]，不触网。
 */
class WebDavClientTest {

    private val expectedAuth = "Basic " + Base64.encode("user:pass".encodeToByteArray())

    private fun config(url: String, enabled: Boolean = true, syncPath: String? = null, syncRootDir: String? = null) =
        WebDavConfig(
            enabled = enabled,
            url = url,
            username = "user",
            password = "pass",
            syncPath = syncPath,
            syncRootDir = syncRootDir,
        )

    private fun <T> withClient(block: suspend (WebDavClient) -> T): T {
        val http = HttpClientFactory.create()
        return try {
            runBlocking { block(WebDavClient(http)) }
        } finally {
            http.close()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 测试连接
    // -----------------------------------------------------------------------------------------

    @Test
    fun testConnectionRunsPropfindPutDeleteWithBasicAuthAndCleansTempFile() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val result = client.testConnection(config(fake.url))

            assertTrue(result.success, result.message)
            assertEquals("WebDAV 读写测试成功，临时文件已清理。耗时 ${result.latencyMs}ms", result.message)
            assertEquals(listOf("PROPFIND", "PUT", "DELETE"), fake.requests.map { it.method })
            assertEquals("0", fake.requests[0].depth, "PROPFIND 必须带 Depth: 0")
            assertTrue(fake.requests.all { it.authorization == expectedAuth }, "每个请求都要带 Basic 认证")

            val putPath = fake.requests[1].path
            assertContains(putPath, "watchworld-write-test-")
            assertNull(fake.stored(putPath), "临时文件应被 DELETE 清理")
        } finally {
            fake.stop()
        }
    }

    @Test
    fun testConnectionRejectsIncompleteConfigBeforeAnyRequest() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val cases = listOf(
                WebDavConfig(url = "", username = "u", password = "p"),
                WebDavConfig(url = fake.url, username = "", password = "p"),
                WebDavConfig(url = fake.url, username = "u", password = null),
            )
            for (case in cases) {
                val result = client.testConnection(case)
                assertEquals(false, result.success)
                assertEquals("请填写完整的 WebDAV 地址、用户名和应用密码", result.message)
            }
            assertTrue(fake.requests.isEmpty(), "校验失败不应发任何请求")
        } finally {
            fake.stop()
        }
    }

    @Test
    fun testConnectionReportsDirectoryFailure() = withClient { client ->
        val fake = FakeWebDavServer(propfindStatus = 401)
        try {
            val result = client.testConnection(config(fake.url))
            assertEquals(false, result.success)
            assertContains(result.message, "WebDAV 目录访问失败 (401)")
            assertEquals(listOf("PROPFIND"), fake.requests.map { it.method })
        } finally {
            fake.stop()
        }
    }

    @Test
    fun testConnectionReportsUploadFailure() = withClient { client ->
        val fake = FakeWebDavServer(putStatus = 403)
        try {
            val result = client.testConnection(config(fake.url))
            assertEquals(false, result.success)
            assertContains(result.message, "目录可访问，但上传测试失败 (403)")
            assertContains(result.message, "目标路径 /dav/watchworld-write-test-")
        } finally {
            fake.stop()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 全量备份 push / pull
    // -----------------------------------------------------------------------------------------

    @Test
    fun pullBackupMissingFileIsSuccessNotFailure() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val result = client.pullBackup(config(fake.url))
            assertTrue(result.success)
            assertEquals("远程尚无备份文件，后续将自动推送本地数据", result.message)
            assertNull(result.remoteData)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pullBackupReturnsRemoteText() = withClient { client ->
        val fake = FakeWebDavServer()
        fake.store("/dav/guanjie_backup.json", "{\"version\":5}")
        try {
            val result = client.pullBackup(config(fake.url))
            assertTrue(result.success, result.message)
            assertEquals("成功拉取远程数据", result.message)
            assertEquals("{\"version\":5}", result.remoteData)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pushBackupPutsBodyAtBackupPath() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val result = client.pushBackup(config(fake.url), "{\"a\":1}")
            assertTrue(result.success, result.message)
            assertEquals("同步成功", result.message)
            assertNotNull(result.timestamp)
            assertEquals("{\"a\":1}", fake.stored("/dav/guanjie_backup.json"))
        } finally {
            fake.stop()
        }
    }

    @Test
    fun pushAndPullRejectWhenDisabledOrIncomplete() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            assertEquals(
                "WebDAV 未启用或配置不完整",
                client.pushBackup(config(fake.url, enabled = false), "{}").message,
            )
            assertEquals(
                "WebDAV 未启用或配置不完整",
                client.pullBackup(config(fake.url, enabled = false)).message,
            )
            assertEquals(
                "WebDAV 未启用或配置不完整",
                client.pushBackup(config(""), "{}").message,
            )
            assertTrue(fake.requests.isEmpty())
        } finally {
            fake.stop()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 增量同步原语
    // -----------------------------------------------------------------------------------------

    @Test
    fun manifestRoundTrip() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val manifest = SyncManifest(
                updatedAt = 1234L,
                sessions = mapOf("s1" to SyncSessionMeta(updatedAt = 99L, title = "对话一")),
            )
            val push = client.pushManifest(config(fake.url), manifest)
            assertTrue(push.success, push.message)

            val fetched = client.fetchManifest(config(fake.url))
            assertEquals(manifest, fetched)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun sessionFileRoundTrip() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            val session = ChatSession(id = "session-1", title = "标题", updatedAt = 42L)
            val push = client.pushSessionFile(config(fake.url), session)
            assertTrue(push.success, push.message)

            val payload = client.fetchSessionFile(config(fake.url), session.id)
            assertNotNull(payload)
            assertEquals(session, WwJson.decodeFromJsonElement<ChatSession>(payload))
            // 存的是 {kind, payload} 包裹体，路径经过 safeFileName。
            assertContains(fake.stored("/dav/guanjie/windows/session-1.json").orEmpty(), "\"kind\":\"session\"")
        } finally {
            fake.stop()
        }
    }

    @Test
    fun ensureCollectionsRunsThreeMkcolAndIgnoresAlreadyExisting() = withClient { client ->
        val fake = FakeWebDavServer()
        try {
            client.ensureCollections(config(fake.url))
            assertEquals(listOf("/dav/guanjie/", "/dav/guanjie/windows/", "/dav/guanjie/media/"), fake.requests.map { it.path })
            assertEquals(listOf("MKCOL", "MKCOL", "MKCOL"), fake.requests.map { it.method })

            // 目录已存在时服务端返回 405，客户端必须忽略而不是报错（不抛即通过）。
            client.ensureCollections(config(fake.url))
        } finally {
            fake.stop()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 纯函数：路径拼接
    // -----------------------------------------------------------------------------------------

    @Test
    fun backupFileUrlAndSyncRootFollowWebPaths() {
        val plain = WebDavConfig(url = "https://dav.jianguoyun.com/dav/")
        assertEquals("https://dav.jianguoyun.com/dav/guanjie_backup.json", backupFileUrl(plain))
        assertEquals("https://dav.jianguoyun.com/dav/guanjie/", syncRoot(plain))

        val custom = WebDavConfig(
            url = "https://example.com/dav",
            syncPath = "backup/x.json",
            syncRootDir = "/ww/",
        )
        assertEquals("https://example.com/dav/backup/x.json", backupFileUrl(custom))
        assertEquals("https://example.com/dav/ww/", syncRoot(custom))
    }

    @Test
    fun safeFileNameAndUrlPath() {
        assertEquals("a_b_c", safeFileName("a/b c"))
        assertEquals("session-1.json", safeFileName("session-1.json"))
        assertEquals("/dav/x", urlPath("https://host/dav/x"))
        assertEquals("/", urlPath("https://host"))
    }
}
