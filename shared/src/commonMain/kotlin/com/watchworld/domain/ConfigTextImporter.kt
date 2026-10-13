package com.watchworld.domain

import com.watchworld.model.AgentSkill
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpTransportType
import com.watchworld.platform.currentTimeMillis
import kotlin.random.Random

/*
 * 本地文本配置导入（对标 `src/lib/configTextImporter.ts`，104 行）。
 *
 * 认两种写法，键名中英皆可：
 * 1. 行内键值：`名称: 我的服务` / `- url: https://…`（前缀 `-`/`*` 会被剥掉）
 * 2. Markdown 标题段：`## URL` 换行后跟内容
 *
 * ⚠️ 与参考版一致：**不支持 `stdio`**，出现即报错（参考版已经废弃本地 Stdio）。
 */

/** 去掉 BOM 与 CRLF，并 trim（`normalizeText`）。 */
private fun normalizeConfigText(text: String): String =
    text.removePrefix("﻿").replace("\r\n", "\n").trim()

/** 剥掉围栏代码块与列表前缀（`stripMarkdown`）。 */
private fun stripMarkdown(value: String): String = value
    .replace(Regex("^```[^\\n]*\\n?", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\n?```$", RegexOption.IGNORE_CASE), "")
    .replace(Regex("^[-*]\\s+"), "")
    .trim()

/**
 * 按候选键名找值：先试 `键: 值` 行，再试 `## 键` 段落（`valueOf`）。
 *
 * ⚠️ 与参考版的两点刻意不同：
 * 1. **空值按「没找到」处理**。JS 里 `""` 是 falsy，所以参考版 `valueOf(...) || 默认值` 会回退；
 *    Kotlin 只判 null 不回退，会拿空串当有效值（曾导致 url 解析成空串）。
 * 2. 段落写法用 `\z`（输入末尾）而不是 `$` 收尾。JS 的 `$` 在 `m` 标志下**会在行尾**就满足，
 *    于是「`## URL` 紧跟围栏代码块」这种写法只捕获到 ` ```text ` 这一行；参考版因此会把
 *    url 解析成空串并报「未找到 URL」。这里按用户预期修掉。
 */
private fun configValueOf(text: String, labels: List<String>): String? {
    labels.forEach { label ->
        val escaped = Regex.escape(label)
        val patterns = listOf(
            Regex("^\\s*(?:[-*]\\s*)?(?:$escaped)\\s*[:：]\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            Regex("^#{1,6}\\s*(?:$escaped)\\s*$\\n+([\\s\\S]*?)(?=\\n#{1,6}\\s|\\z)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
        )
        patterns.forEach { pattern ->
            val match = pattern.find(text)
            val captured = match?.groupValues?.getOrNull(1)?.trim()
            if (!captured.isNullOrEmpty()) {
                val stripped = stripMarkdown(captured)
                if (stripped.isNotEmpty()) return stripped
            }
        }
    }
    return null
}

/** 逗号/分号/换行分隔的列表值（`listOf`），逐项剥掉列表前缀。 */
private fun configListOf(text: String, labels: List<String>): List<String> {
    val value = configValueOf(text, labels) ?: return emptyList()
    return value.split(Regex("[,，;；\\n]+"))
        .map { it.replace(Regex("^[-*]\\s*"), "").trim() }
        .filter { it.isNotEmpty() }
}

/** 文件名去扩展名，作为兜底名称（`fileBaseName`）。 */
private fun fileBaseName(fileName: String): String =
    fileName.replace(Regex("\\.[^.]+$"), "").trim().ifEmpty { "导入配置" }

/** 生成带随机后缀的 id（参考版用 `Date.now()` + `Math.random().toString(36)`）。 */
private fun importedId(prefix: String): String =
    "$prefix-${currentTimeMillis()}-${Random.nextInt(36 * 36 * 36).toString(36)}"

/**
 * 从文本解析一个 MCP 服务配置（`parseMcpText`）。
 *
 * @throws IllegalArgumentException 内容为空、含 `stdio`、或找不到 URL 时。
 */
fun parseMcpText(text: String, fileName: String): McpServerConfig {
    val source = normalizeConfigText(text)
    require(source.isNotEmpty()) { "文件内容为空。" }

    val name = configValueOf(source, listOf("name", "名称", "服务名称", "MCP Name")) ?: fileBaseName(fileName)
    val rawType = (configValueOf(source, listOf("type", "transport", "类型", "连接类型")) ?: "streamable_http").lowercase()
    require(rawType != "stdio") { "不再支持本地 Stdio MCP，请改用远程 HTTP 或 SSE 服务。" }
    val type = if (rawType == "sse") McpTransportType.SSE else McpTransportType.STREAMABLE_HTTP
    val url = configValueOf(source, listOf("url", "endpoint", "地址", "端点"))
    val description = configValueOf(source, listOf("description", "描述", "服务说明"))
        ?: "从本地文本文件导入的 MCP 服务。"
    val authToken = configValueOf(source, listOf("authToken", "token", "Bearer Token", "访问令牌", "密钥"))

    require(!url.isNullOrBlank()) { "未找到远程 MCP URL。" }

    return McpServerConfig(
        id = importedId("mcp"),
        name = name,
        type = type,
        url = url,
        authToken = authToken,
        description = description,
    )
}

/**
 * 从文本解析一个 Skill（`parseSkillText`）。
 *
 * ⚠️ **注入指令是必填**：没有「系统指令」段落就报错 —— 一个没有注入文本的 Skill
 *    对模型毫无作用，静默落库只会让用户以为导入成功了。
 *
 * @throws IllegalArgumentException 内容为空、或找不到系统指令时。
 */
fun parseSkillText(text: String, fileName: String): AgentSkill {
    val source = normalizeConfigText(text)
    require(source.isNotEmpty()) { "文件内容为空。" }

    val name = configValueOf(source, listOf("name", "名称", "技能名称", "Skill Name")) ?: fileBaseName(fileName)
    val description = configValueOf(source, listOf("description", "描述", "技能说明"))
        ?: "从本地文本文件导入的技能。"
    val icon = configValueOf(source, listOf("icon", "图标", "emoji")) ?: "⚡"
    val triggerKeywords = configListOf(source, listOf("triggerKeywords", "keywords", "触发词", "关键词"))
    val toolsNeeded = configListOf(source, listOf("toolsNeeded", "tools", "所需工具", "MCP工具"))
    val instruction = configValueOf(
        source,
        listOf(
            "systemInstructionInjection",
            "systemInstruction",
            "instruction",
            "系统指令",
            "注入指令",
            "执行指令",
        ),
    )
    require(instruction != null) { "未找到技能系统指令。请添加“系统指令：...”或“## 系统指令”段落。" }

    return AgentSkill(
        id = importedId("skill"),
        name = name,
        // 参考版 `icon.slice(0, 4)`：emoji 最长也就几个码元，防手滑粘一整段进去。
        icon = icon.take(4),
        description = description,
        triggerKeywords = triggerKeywords.ifEmpty { listOf("通用") },
        systemInstructionInjection = instruction,
        // 两个字段在参考版里同义（TS 注释写的就是 alias），导入时写同一个值。
        systemInstruction = instruction,
        toolsNeeded = toolsNeeded.ifEmpty { null },
        isBuiltin = false,
    )
}
