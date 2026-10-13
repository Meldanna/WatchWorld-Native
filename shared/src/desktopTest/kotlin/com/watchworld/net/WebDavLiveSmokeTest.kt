package com.watchworld.net

import com.watchworld.model.WebDavConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 在线 smoke：对真实 WebDAV 服务走通「测试连接 → 推送备份 → 拉回同一份」。
 *
 * 默认跳过（CI / 无网环境不应因外部服务抖动而红）。真机验证：
 * ```
 * WW_T19_LIVE=1 WW_T19_WEBDAV_URL=https://dav.jianguoyun.com/dav/ \
 *   WW_T19_WEBDAV_USER=you@example.com WW_T19_WEBDAV_PASSWORD=应用密码 \
 *   ./gradlew :shared:desktopTest --tests "*WebDavLiveSmokeTest"
 * ```
 * 坚果云必须用**应用专用密码**。为避免覆盖真实备份，smoke 用独立文件路径。
 */
class WebDavLiveSmokeTest {

    @Test
    fun liveWebDavConnectionThenPushAndPull() = runBlocking {
        assumeTrue(
            "Set WW_T19_LIVE=1 (and WW_T19_WEBDAV_URL/USER/PASSWORD) to run the opt-in WebDAV smoke test",
            System.getenv("WW_T19_LIVE") == "1",
        )
        val url = System.getenv("WW_T19_WEBDAV_URL").orEmpty()
        val config = WebDavConfig(
            enabled = true,
            url = url,
            username = System.getenv("WW_T19_WEBDAV_USER").orEmpty(),
            password = System.getenv("WW_T19_WEBDAV_PASSWORD"),
            syncPath = "/watchworld-native-smoke.json",
        )
        val http = HttpClientFactory.create()
        val client = WebDavClient(http)
        try {
            val test = client.testConnection(config)
            assertTrue(test.success, "在线 WebDAV 连接失败：${test.message}")

            val marker = "{\"smoke\":\"${System.currentTimeMillis()}\"}"
            val push = client.pushBackup(config, marker)
            assertTrue(push.success, "推送失败：${push.message}")

            val pull = client.pullBackup(config)
            assertTrue(pull.success, "拉取失败：${pull.message}")
            assertEquals(marker, pull.remoteData)
        } finally {
            http.close()
        }
    }
}
