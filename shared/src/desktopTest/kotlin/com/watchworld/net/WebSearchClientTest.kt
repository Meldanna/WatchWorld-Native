package com.watchworld.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T11 联网搜索 / Skill 目录客户端单测。
 *
 * 纯函数（DDG 解析、frontmatter、意图判定、格式化）直接测；网络部分用进程内 `HttpServer`
 * 假装 DDG 与 agent-skills.md oRPC 上游 —— 只 mock 客户端会把「请求头 / `{json}` 包一层 /
 * 状态码分流」这一整层漏掉。
 */
class WebSearchClientTest {

    // -----------------------------------------------------------------------------------------
    // DDG HTML 解析（纯函数）
    // -----------------------------------------------------------------------------------------

    @Test
    fun parseDuckDuckGoHtmlSkipsAdsAndNonHttpLinks() {
        val results = parseDuckDuckGoHtml(SAMPLE_HTML)
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("Kotlin & Docs", first.title)
        assertEquals("https://kotlinlang.org/docs/", first.url)
        assertEquals("Official docs & guides.", first.snippet)
        assertEquals("kotlinlang.org", first.source)

        val second = results[1]
        assertEquals("Example 'quoted'", second.title)
        assertEquals("https://example.com/path?a=1", second.url)
        // JS 把每个标签换成空格再折叠，所以 `</b>.` 会留下一个空格 —— 原样照抄。
        assertEquals("A snippet with tags .", second.snippet)
        assertEquals("example.com", second.source)
    }

    @Test
    fun parseDuckDuckGoHtmlHonorsLimit() {
        val html = ddgHtml(count = 5)
        assertEquals(2, parseDuckDuckGoHtml(html, limit = 2).size)
        assertEquals(5, parseDuckDuckGoHtml(html, limit = 10).size)
    }

    @Test
    fun decodeSearchHtmlHandlesNamedAndNumericEntities() {
        assertEquals("a & b <c> 'd' A", decodeSearchHtml("a &amp; b &lt;c&gt; &#x27;d&#x27; &#65;"))
    }

    @Test
    fun unwrapDuckDuckGoUrlDecodesUddgParam() {
        assertEquals(
            "https://kotlinlang.org/docs/",
            unwrapDuckDuckGoUrl("//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2F&amp;rut=x"),
        )
        // 非 DDG 跳转链原样返回。
        assertEquals("https://example.com/a", unwrapDuckDuckGoUrl("https://example.com/a"))
    }

    @Test
    fun percentDecodeRejectsMalformedSequences() {
        assertEquals("a b", percentDecode("a%20b"))
        assertNull(percentDecode("abc%2"))
        assertNull(percentDecode("abc%zz"))
    }

    // -----------------------------------------------------------------------------------------
    // 意图判定 / 格式化
    // -----------------------------------------------------------------------------------------

    @Test
    fun intentHelpersMatchTheTsPatterns() {
        assertTrue(isSkillSearchIntent("帮我搜索一个 skill"))
        assertTrue(isSkillSearchIntent("查找 github 技能"))
        assertTrue(!isSkillSearchIntent("今天天气怎么样"))
        assertTrue(shouldUseWebSearch("今天天气怎么样"))
        assertTrue(shouldUseWebSearch("最新新闻"))
        assertTrue(!shouldUseWebSearch("你好呀"))
    }

    @Test
    fun formatSearchContextRendersHeadingAndItems() {
        val text = formatSearchContext(
            "kotlin",
            listOf(WebSearchResult("K", "https://k.org", "snippet", "k.org")),
            skillSearch = false,
        )
        assertContains(text, "# 【本轮联网搜索结果】")
        assertContains(text, "检索词：kotlin")
        assertContains(text, "## [1] K")
        assertContains(text, "URL：https://k.org")
        assertEquals("", formatSearchContext("q", emptyList(), skillSearch = false))
    }

    @Test
    fun formatSearchContextUsesEmptySnippetPlaceholder() {
        val text = formatSearchContext("q", listOf(WebSearchResult("T", "https://x", "", "x")), skillSearch = true)
        assertContains(text, "# 【Agent Skill 目录搜索结果】")
        assertContains(text, "摘要：无摘要")
    }

    @Test
    fun formatSkillDirectoryContextRendersEntries() {
        val text = formatSkillDirectoryContext(
            "pdf",
            listOf(
                SkillDirectoryEntry(
                    id = "a/b/pdf",
                    name = "PDF",
                    description = "读 PDF",
                    repoId = "a/b",
                    repoStars = 12,
                    previewUrl = "https://agent-skills.md/skills/a/b/pdf",
                ),
            ),
        )
        assertContains(text, "# 【Agent Skill 目录搜索结果】")
        assertContains(text, "## [1] PDF")
        assertContains(text, "标识：a/b/pdf")
        assertContains(text, "来源：a/b（★ 12）")
        assertContains(text, "描述：读 PDF")
        assertEquals("", formatSkillDirectoryContext("q", emptyList()))
    }

    @Test
    fun parseSkillFrontmatterOnlyReadsTopLevelKeys() {
        val parsed = parseSkillFrontmatter(
            """
            ---
            name: pdf-tools
            description: "Read PDFs"
            metadata: { "emoji": "📄", name: nested }
            tags: [a, b]
            ---
            body
            """.trimIndent(),
        )
        assertEquals("pdf-tools", parsed.name)
        assertEquals("Read PDFs", parsed.description)
    }

    // -----------------------------------------------------------------------------------------
    // 网络（假上游）
    // -----------------------------------------------------------------------------------------

    @Test
    fun searchWebFetchesHtmlWithUserAgentAndParses() = runBlocking {
        val fake = FakeWebServer(html = ddgHtml(count = 2))
        val http = HttpClientFactory.create()
        try {
            val results = searchWebAt(http, fake.htmlEndpoint, "kotlin coroutines", limit = 6)
            assertEquals(listOf("Result 0", "Result 1"), results.map { it.title })
            assertEquals("https://example.com/0", results[0].url)

            val request = fake.recorded.single { it.path == "/html/" }
            assertContains(request.query, "q=kotlin")
            assertContains(request.query, "coroutines")
            assertContains(request.userAgent.orEmpty(), "Chrome/124")
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun searchWebClampsLimitToOneToTenAndTruncatesQuery() = runBlocking {
        val fake = FakeWebServer(html = ddgHtml(count = 12))
        val http = HttpClientFactory.create()
        try {
            assertEquals(10, searchWebAt(http, fake.htmlEndpoint, "x", limit = 20).size)
            assertEquals(1, searchWebAt(http, fake.htmlEndpoint, "x", limit = 0).size)

            searchWebAt(http, fake.htmlEndpoint, "a".repeat(600), limit = 6)
            val query = fake.recorded.last { it.path == "/html/" }.query
            assertEquals(500, query.removePrefix("q=").length)
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun searchWebThrowsWhenNoResultsAndOnNonSuccess() = runBlocking {
        val empty = FakeWebServer(html = "<html><body>nothing here</body></html>")
        val http = HttpClientFactory.create()
        try {
            val emptyError = assertFailsWith<ApiException> { searchWebAt(http, empty.htmlEndpoint, "q", 6) }
            assertEquals("联网搜索失败：未取得有效搜索结果，请稍后重试或换关键词", emptyError.message)
        } finally {
            http.close()
            empty.stop()
        }

        val limited = FakeWebServer(html = "challenge", htmlStatus = 202)
        val http2 = HttpClientFactory.create()
        try {
            assertEquals("联网搜索失败：上游限流，请稍后重试", assertFailsWith<ApiException> { searchWebAt(http2, limited.htmlEndpoint, "q", 6) }.message)
        } finally {
            http2.close()
            limited.stop()
        }

        val boom = FakeWebServer(html = "boom", htmlStatus = 500)
        val http3 = HttpClientFactory.create()
        try {
            assertEquals("联网搜索失败 (500)", assertFailsWith<ApiException> { searchWebAt(http3, boom.htmlEndpoint, "q", 6) }.message)
        } finally {
            http3.close()
            boom.stop()
        }
    }

    @Test
    fun searchAgentSkillsSendsJsonEnvelopeAndParsesItems() = runBlocking {
        val fake = FakeWebServer(
            skillSearch = skillSearchJson(
                skillItem(id = "a/b/pdf", name = "PDF", description = "读 PDF", tags = listOf("pdf"), stars = 12),
                skillItem(id = "  ", name = "bad"),
            ),
        )
        val http = HttpClientFactory.create()
        try {
            val entries = searchAgentSkillsAt(http, fake.rpcBase, "pdf", page = 1, pageSize = 8)
            assertEquals(1, entries.size)
            val entry = entries.single()
            assertEquals("a/b/pdf", entry.id)
            assertEquals("PDF", entry.name)
            assertEquals(listOf("pdf"), entry.tags)
            assertEquals(12, entry.repoStars)
            assertEquals("https://agent-skills.md/skills/a/b/pdf", entry.previewUrl)

            val request = fake.recorded.single { it.path == "/rpc/skills/search" }
            val envelope = parseJson(request.body).jsonObject
            assertTrue(envelope.containsKey("json"), "oRPC 请求体必须包一层 { json: ... }")
            val payload = envelope.getValue("json").jsonObject
            assertEquals("pdf", payload["query"]?.jsonPrimitive?.content)
            assertEquals(1, payload["page"]?.jsonPrimitive?.content?.toInt())
            assertEquals(8, payload["pageSize"]?.jsonPrimitive?.content?.toInt())
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun searchAgentSkillsSurfacesNonSuccessAndMalformedEnvelope() = runBlocking {
        val failing = FakeWebServer(skillSearchStatus = 502)
        val http = HttpClientFactory.create()
        try {
            assertEquals("Skill 目录搜索失败 (502)", assertFailsWith<ApiException> { searchAgentSkillsAt(http, failing.rpcBase, "q") }.message)
        } finally {
            http.close()
            failing.stop()
        }

        val malformed = FakeWebServer(skillSearch = """{"nope":1}""")
        val http2 = HttpClientFactory.create()
        try {
            assertEquals(
                "Skill 目录返回了无法识别的数据格式",
                assertFailsWith<ApiException> { searchAgentSkillsAt(http2, malformed.rpcBase, "q") }.message,
            )
        } finally {
            http2.close()
            malformed.stop()
        }
    }

    @Test
    fun previewSkillMdParsesFrontmatterAndPath() = runBlocking {
        val content = "---\nname: pdf-tools\ndescription: Read PDFs\n---\n# Body"
        val fake = FakeWebServer(markdown = markdownJson(content))
        val http = HttpClientFactory.create()
        try {
            val preview = previewSkillMdAt(http, fake.rpcBase, "a/b/pdf-tools")
            assertEquals("a/b/pdf-tools", preview.id)
            assertEquals("skills/x/SKILL.md", preview.path)
            assertEquals(content, preview.content)
            assertEquals("pdf-tools", preview.frontmatterName)
            assertEquals("Read PDFs", preview.frontmatterDescription)
            assertEquals("https://agent-skills.md/skills/a/b/pdf-tools", preview.previewUrl)

            val body = fake.recorded.single { it.path == "/rpc/skills/markdown" }.body
            assertTrue(parseJson(body).jsonObject.containsKey("json"))
        } finally {
            http.close()
            fake.stop()
        }
    }

    @Test
    fun previewSkillMdRejectsShortIdAndEmptyContent() = runBlocking {
        val fake = FakeWebServer(markdown = markdownJson(""))
        val http = HttpClientFactory.create()
        try {
            assertEquals(
                "Skill 标识格式不正确，应为 owner/repo/skillName",
                assertFailsWith<ApiException> { previewSkillMdAt(http, fake.rpcBase, "a/b") }.message,
            )
            assertEquals(
                "该 Skill 没有可用的 SKILL.md 内容",
                assertFailsWith<ApiException> { previewSkillMdAt(http, fake.rpcBase, "a/b/c") }.message,
            )
        } finally {
            http.close()
            fake.stop()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 脚手架
    // -----------------------------------------------------------------------------------------

    private fun parseJson(text: String) = com.watchworld.model.WwJson.parseToJsonElement(text)

    private fun ddgHtml(count: Int): String = buildString {
        repeat(count) { index ->
            append(
                """<div class="result"><h2 class="result__title">""" +
                    """<a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2F$index">Result $index</a>""" +
                    """</h2><div class="result__snippet">Snippet $index</div></div>""",
            )
        }
    }

    private fun skillSearchJson(vararg items: JsonObject): String =
        buildJsonObject {
            put("json", buildJsonObject { put("items", JsonArray(items.toList())) })
        }.toString()

    private fun markdownJson(content: String): String = buildJsonObject {
        put(
            "json",
            buildJsonObject {
                put("path", "skills/x/SKILL.md")
                put("size", content.length)
                put("content", content)
            },
        )
    }.toString()

    private fun skillItem(
        id: String,
        name: String? = null,
        description: String? = null,
        tags: List<String> = emptyList(),
        stars: Int? = null,
    ): JsonObject = buildJsonObject {
        put("id", id)
        name?.let { put("name", it) }
        description?.let { put("description", it) }
        put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
        stars?.let { put("repoStars", it) }
    }

    private val SAMPLE_HTML = """
        <div class="result results_links results_links_deep web-result">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2F&amp;rut=xyz">Kotlin &amp; Docs</a>
          </h2>
          <a class="result__snippet" href="/l/?uddg=x">Official docs &amp; guides.</a>
        </div>
        <div class="result result--ad">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/y.js?ad_provider=foo">Sponsored</a>
          </h2>
        </div>
        <div class="result results_links">
          <h2 class="result__title">
            <a class="result__a" href="/l/?uddg=https%3A%2F%2Fexample.com%2Fpath%3Fa%3D1">Example &#x27;quoted&#x27;</a>
          </h2>
          <div class="result__snippet">A snippet with <b>tags</b>.</div>
        </div>
        <div class="result">
          <a class="result__a" href="mailto:someone@example.com">Mail link</a>
        </div>
    """.trimIndent()
}

private data class RecordedRequest(
    val method: String,
    val path: String,
    val query: String,
    val body: String,
    val userAgent: String?,
)

/** 假上游：`/html/`（DDG）、`/rpc/skills/search`、`/rpc/skills/markdown`（oRPC）。 */
private class FakeWebServer(
    html: String = "",
    val htmlStatus: Int = 200,
    skillSearch: String = """{"json":{"items":[]}}""",
    val skillSearchStatus: Int = 200,
    markdown: String = """{"json":{"content":"x"}}""",
    val markdownStatus: Int = 200,
) {
    val recorded = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    private val htmlBody = html
    private val skillSearchBody = skillSearch
    private val markdownBody = markdown
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/html/") { exchange -> send(exchange, htmlStatus, htmlBody, "text/html") }
        server.createContext("/rpc/skills/search") { exchange -> send(exchange, skillSearchStatus, skillSearchBody, "application/json") }
        server.createContext("/rpc/skills/markdown") { exchange -> send(exchange, markdownStatus, markdownBody, "application/json") }
        server.executor = null
        server.start()
    }

    val htmlEndpoint: String get() = "http://127.0.0.1:${server.address.port}/html/"

    val rpcBase: String get() = "http://127.0.0.1:${server.address.port}/rpc"

    fun stop() = server.stop(0)

    private fun send(exchange: HttpExchange, status: Int, body: String, contentType: String) {
        val requestBody = if (exchange.requestMethod == "GET") {
            ""
        } else {
            exchange.requestBody.readBytes().decodeToString()
        }
        recorded += RecordedRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            query = exchange.requestURI.query ?: "",
            body = requestBody,
            userAgent = exchange.requestHeaders.getFirst("User-Agent"),
        )
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
