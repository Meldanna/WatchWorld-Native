package com.watchworld.net

import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 在线 smoke：对真实 MCP 服务走通 `initialize → tools/list → tools/call`。
 *
 * 默认跳过，避免 CI / 无网环境因为外部服务抖动而红。真机验证时：
 * ```
 * WW_T10_LIVE_MCP=1 ./gradlew :shared:desktopTest --tests "*McpLiveSmokeTest"
 * ```
 * 可用 `WW_T10_LIVE_MCP_URL` 覆盖默认地址。范式与 `DeepSeekLiveSmokeTest` 一致：
 * 用 `System.getenv`（不是 `platformEnv`）+ JUnit `assumeTrue`。
 */
class McpLiveSmokeTest {

    @Test
    fun liveMcpServerAnswersInitializeToolsListAndToolsCall() = runBlocking {
        assumeTrue(
            "Set WW_T10_LIVE_MCP=1 to run the opt-in live MCP smoke test",
            System.getenv("WW_T10_LIVE_MCP") == "1",
        )
        val url = System.getenv("WW_T10_LIVE_MCP_URL")?.takeIf { it.isNotBlank() }
            ?: "https://github.windlife.site/mcp/andunya"

        val http = HttpClientFactory.create()
        val client = McpClient(http)
        try {
            // initialize → tools/list
            val discovered = client.testConnection(
                McpServerConfig(id = "live", name = "live", url = url),
            )
            assertTrue(discovered.success, "在线 MCP 连接失败：${discovered.error}")
            assertTrue(discovered.tools.isNotEmpty(), "应至少发现一个工具")

            // tools/call（挑一个只读工具，避免对真实仓库产生副作用）
            val target = discovered.tools.firstOrNull { it.name == "list_repos" } ?: discovered.tools.first()
            val connected = McpServerConfig(
                id = "live",
                name = "live",
                url = url,
                status = McpServerStatus.CONNECTED,
                tools = discovered.tools,
            )
            val callResult = client.call(connected, target.name, buildJsonObject {})
            val content = (callResult as? JsonObject)?.get("content")
            assertTrue(content is JsonArray, "tools/call 应返回 content 数组，实际：$callResult")
        } finally {
            http.close()
        }
    }
}
