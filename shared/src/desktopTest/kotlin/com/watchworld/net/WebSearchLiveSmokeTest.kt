package com.watchworld.net

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 在线 smoke：对真实 DuckDuckGo 与 agent-skills.md 目录跑通检索。
 *
 * 默认跳过（外网 + 上游限流会抖动）。真机验证：
 * ```
 * WW_T11_LIVE=1 ./gradlew :shared:desktopTest --tests "*WebSearchLiveSmokeTest"
 * ```
 * 范式与 `DeepSeekLiveSmokeTest` / `McpLiveSmokeTest` 一致：`System.getenv` + `assumeTrue`。
 */
class WebSearchLiveSmokeTest {

    @Test
    fun liveDuckDuckGoSearchReturnsRealResults() = runBlocking {
        assumeTrue("Set WW_T11_LIVE=1 to run the opt-in live web-search smoke test", System.getenv("WW_T11_LIVE") == "1")

        val http = HttpClientFactory.create()
        try {
            val results = WebSearchClient(http).searchWeb("Kotlin coroutines", limit = 5)
            assertTrue(results.isNotEmpty(), "DuckDuckGo 应返回至少一条结果")
            assertTrue(results.all { it.url.startsWith("http") }, "URL 都应是 http(s)：$results")
            assertTrue(results.none { it.source == "duckduckgo.com" }, "广告位应被过滤：$results")
        } finally {
            http.close()
        }
    }

    @Test
    fun liveSkillDirectorySearchReturnsEntries() = runBlocking {
        assumeTrue("Set WW_T11_LIVE=1 to run the opt-in live skill-search smoke test", System.getenv("WW_T11_LIVE") == "1")

        val http = HttpClientFactory.create()
        try {
            val entries = WebSearchClient(http).searchAgentSkills("pdf", page = 1, pageSize = 5)
            assertTrue(entries.isNotEmpty(), "agent-skills.md 应返回至少一条 Skill")
            assertTrue(entries.all { it.id.count { ch -> ch == '/' } >= 2 }, "id 应是 owner/repo/skillName：$entries")
        } finally {
            http.close()
        }
    }
}
