package com.watchworld.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `parseMcpText` 的体检（对标 `src/lib/configTextImporter.ts:78-104`）。
 *
 * 守的是这几条容易写错的地方：
 * - 键名中英混排 + 大小写不敏感；
 * - 「`键: 值` 行」与「`## 键` 段落」两种写法都要认；
 * - 围栏代码块与 `-` / `*` 列表前缀要剥掉；
 * - `stdio` 与缺 URL 必须**报错**而不是给出半成品配置。
 */
class ConfigTextImporterTest {

    @Test
    fun parsesInlineKeyValueForm() {
        val server = parseMcpText(
            """
            名称: 我的自建服务
            url: https://example.com/mcp
            type: sse
            token: abc123
            描述: 自家机房的工具服务
            """.trimIndent(),
            "fallback-name.md",
        )

        assertEquals("我的自建服务", server.name)
        assertEquals("https://example.com/mcp", server.url)
        assertEquals("abc123", server.authToken)
        assertEquals("自家机房的工具服务", server.description)
        assertTrue(server.id.startsWith("mcp-"))
        // 类型解析成枚举，而不是原样字符串。
        assertEquals(com.watchworld.model.McpTransportType.SSE, server.type)
    }

    @Test
    fun parsesMarkdownHeadingFormAndStripsFences() {
        val server = parseMcpText(
            """
            # 服务名称
            GitHub MCP

            ## URL
            ```text
            https://api.githubcopilot.com/mcp/
            ```

            ## 连接类型
            - streamable_http
            """.trimIndent(),
            "ignored.md",
        )

        assertEquals("GitHub MCP", server.name)
        assertEquals("https://api.githubcopilot.com/mcp/", server.url)
        assertEquals(com.watchworld.model.McpTransportType.STREAMABLE_HTTP, server.type)
    }

    @Test
    fun defaultsTypeToStreamableHttpAndNameToFileName() {
        // ⚠️ 只认 `键: 值` / `键: 值` 与 `## 键` 段落，**不认** `键=值`（参考版同样不认）。
        val server = parseMcpText("url: https://example.com/mcp", "我的配置.txt")

        assertEquals(com.watchworld.model.McpTransportType.STREAMABLE_HTTP, server.type)
        assertEquals("我的配置", server.name)
        assertEquals("从本地文本文件导入的 MCP 服务。", server.description)
        assertNull(server.authToken)
    }

    @Test
    fun rejectsStdioTransport() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseMcpText("名称: 本地服务\ntype: stdio\nurl: http://localhost:3000", "x.md")
        }
        assertTrue("Stdio" in error.message.orEmpty(), "应明确提示不再支持 Stdio，实际：${error.message}")
    }

    @Test
    fun rejectsMissingUrlAndEmptyContent() {
        assertFailsWith<IllegalArgumentException> { parseMcpText("名称: 只有名字", "x.md") }
        assertFailsWith<IllegalArgumentException> { parseMcpText("   \n  ", "x.md") }
    }

    @Test
    fun ignoresBomAndCrlf() {
        val server = parseMcpText("﻿名称: BOM 服务\r\nURL: https://example.com/mcp\r\n", "x.md")

        assertEquals("BOM 服务", server.name)
        assertEquals("https://example.com/mcp", server.url)
    }

    // ────────────────────────────── parseSkillText ──────────────────────────────

    @Test
    fun parsesSkillWithKeywordsToolsAndInstruction() {
        val skill = parseSkillText(
            """
            名称: 方案规划
            icon: 🧭
            描述: 把模糊需求拆成可执行步骤
            触发词: 分析, 规划, 方案
            所需工具: read_file, web_search
            ## 系统指令
            【已激活专属技能】请按结构化方案输出分步执行指南。
            """.trimIndent(),
            "fallback.md",
        )

        assertEquals("方案规划", skill.name)
        assertEquals("🧭", skill.icon)
        assertEquals("把模糊需求拆成可执行步骤", skill.description)
        assertEquals(listOf("分析", "规划", "方案"), skill.triggerKeywords)
        assertEquals(listOf("read_file", "web_search"), skill.toolsNeeded)
        assertEquals("【已激活专属技能】请按结构化方案输出分步执行指南。", skill.systemInstructionInjection)
        // 两个同义字段必须一起写。
        assertEquals(skill.systemInstructionInjection, skill.systemInstruction)
        assertEquals(false, skill.isBuiltin)
    }

    @Test
    fun skillFallsBackToDefaultsAndRequiresInstruction() {
        val skill = parseSkillText("## 系统指令\n照着做。", "我的技能.txt")

        assertEquals("我的技能", skill.name)
        assertEquals("⚡", skill.icon)
        assertEquals("从本地文本文件导入的技能。", skill.description)
        assertEquals(listOf("通用"), skill.triggerKeywords, "没写触发词时回退「通用」")
        assertNull(skill.toolsNeeded, "没写所需工具时留 null，而不是空列表")

        // 没有系统指令的 Skill 对模型毫无作用 → 必须报错，不能静默落库。
        val error = assertFailsWith<IllegalArgumentException> {
            parseSkillText("名称: 只有名字", "x.md")
        }
        assertTrue("系统指令" in error.message.orEmpty(), "应提示缺系统指令，实际：${error.message}")
    }
}
