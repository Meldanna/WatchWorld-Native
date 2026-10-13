package com.watchworld.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException

/**
 * T05 数据模型验收：用**真实 v5 存档样本**反序列化，断言形状与 TS 侧一致。
 *
 * 样本见 `fixtures/sse/README.md`：
 * - `archive-v5-legacy.json` —— 旧版真实导出，多测三件事：遗留字段 `connectedSkillIds`、
 *   顶层缺 `worldGroups`/`appPreferences`、消息 `versions` 的位置按角色不对称。
 * - `archive-v5-minimal.json` —— 全新安装的空档，覆盖「空数组 / 可选字段缺失」边界。
 *
 * ⚠️ 本测试只做「读进来不炸 + 关键形状对」。`normalizeChatSession` 的 6 条兼容逻辑
 *    （含 `toolCallStepLimit` 从原始 JSON 数值归一化）**归 T06**，此处不测 —— 但会断言
 *    「未经归一化时 `streamingEnabled` 仍是 `null`」，防止有人把三态提前拍平。
 */
class ArchiveV5ModelTest {

    private fun fixture(name: String): ArchiveV5 {
        val start = File(System.getProperty("user.dir"))
        val file = generateSequence(start) { it.parentFile }
            .map { File(it, "fixtures/sse/$name") }
            .firstOrNull { it.isFile }
            ?: error("Fixture not found from ${start.absolutePath}: fixtures/sse/$name")
        return WwJson.decodeFromString(file.readText())
    }

    private fun jsonString(value: String) = "\"$value\""

    @Test
    fun legacyArchiveParsesAndDropsLegacyFields() {
        val archive = fixture("archive-v5-legacy.json")

        assertEquals(5, archive.version)

        // 顶层缺 worldGroups / appPreferences：不是「空数组」而是「字段不存在」
        assertTrue(archive.worldGroups.isEmpty())
        assertNull(archive.appPreferences)
        assertEquals("emerald", archive.theme)

        // providers：type 全部能落到 enum 上（legacy 里含 gemini/openai/deepseek/claude/ollama）
        assertEquals(5, archive.providers.size)
        assertTrue(archive.providers.any { it.type == ApiProviderType.GEMINI })
        assertTrue(archive.providers.any { it.type == ApiProviderType.OLLAMA })

        // knowledgeBase 里那条 kb-quick-notes 读得进来（是否剔除归 T06 的 normalize 层）
        assertTrue(archive.knowledgeBase.any { it.id == "kb-quick-notes" })

        // 遗留字段 connectedSkillIds 不在模型里 → 被 ignoreUnknownKeys 丢掉，不抛错
        val session = archive.sessions.single()
        assertEquals("session-welcome", session.id)
        assertEquals(AiContextVisibilityFilter.ALL, session.aiContextVisibility)

        // legacy 的时间线：parentId 字段整个不存在 → null
        val main = assertNotNull(session.timelines).single()
        assertEquals("timeline-main", main.id)
        assertNull(main.parentId)
    }

    @Test
    fun legacyMessageContentLocationIsAsymmetricPerRole() {
        val session = fixture("archive-v5-legacy.json").sessions.single()
        assertEquals(5, session.messages.size)

        val assistant = session.messages.first { it.role == Role.ASSISTANT }
        assertEquals(1, assistant.versions.size)
        assertTrue(assistant.versions.first().content.isNotBlank())
        assertNotNull(assistant.latencyMs)

        val user = session.messages.first { it.role == Role.USER }
        // ⚠️ user 消息的 versions 是空数组，正文只在顶层 content
        assertTrue(user.versions.isEmpty(), "user 消息不得假设 versions[0] 存在")
        assertTrue(user.content.isNotBlank())
        assertNull(user.latencyMs)

        // 楼号 / 分支标签是 AI 上下文的锚点，必须原样读到
        assertTrue(session.messages.all { it.floorNumber != null })
        assertTrue(session.messages.all { it.timelineId == "timeline-main" })
        assertEquals("通用", user.codeTag)
    }

    @Test
    fun minimalArchiveParsesWithEmptyCollections() {
        val archive = fixture("archive-v5-minimal.json")

        assertEquals(5, archive.version)
        assertEquals(1, archive.sessions.size)
        // 新装导出的 INITIAL_SESSION.messages 就是空数组
        assertTrue(archive.sessions.single().messages.isEmpty())
        val prefs = assertNotNull(archive.appPreferences)
        assertEquals(15.0, prefs.chatFontSize)
        assertEquals(0, prefs.defaultSession.toolCallStepLimit)
        assertEquals(UiMode.DARK, WwJson.decodeFromString<UiMode>(jsonString(archive.uiMode!!)))
    }

    /**
     * 三态红线：`streamingEnabled` 未经 normalize 时**必须保持 null**。
     * 一旦有人给它加了 `= false` 默认值，这条测试会失败 —— 那就是老存档集体变非流式的那个 bug。
     */
    @Test
    fun unnormalizedStreamingFlagKeepsThreeState() {
        val legacy = fixture("archive-v5-legacy.json").sessions.single()
        assertNull(legacy.streamingEnabled, "老存档没有该字段 → 必须是 null（视为流式），不能拍成 false")

        val minimal = fixture("archive-v5-minimal.json").sessions.single()
        // 显式的 false 要能读到（与上面的 null 区分开）
        assertEquals(false, minimal.webSearchEnabled)
    }

    @Test
    fun unknownProviderTypeFallsBackToCustomInsteadOfThrowing() {
        val json = """
            {"id":"p1","name":"x","type":"some_future_type","defaultModel":"m"}
        """.trimIndent()
        val provider = WwJson.decodeFromString<ApiProviderConfig>(json)
        assertEquals(ApiProviderType.CUSTOM, provider.type, "未知 provider 类型应退回 CUSTOM（coerceInputValues）")
    }

    @Test
    fun requiredFieldsAreStillStrict() {
        // 缺 id：不给默认值，必须抛错 —— 静默补默认值会掩盖真的损坏的存档
        assertFailsWith<SerializationException> {
            WwJson.decodeFromString<McpTool>("""{"description":"无 name"}""")
        }
    }

    /**
     * 往返：`JSON → 模型 → JSON` 后**再解一次**必须得到同样的模型。
     * 这条不比对原始文本（`explicitNulls=false` / `encodeDefaults=false` 本就会改写形状），
     * 只保证模型自身没有丢信息。
     */
    @Test
    fun archiveSurvivesRoundTrip() {
        for (name in listOf("archive-v5-legacy.json", "archive-v5-minimal.json")) {
            val first = fixture(name)
            val encoded = WwJson.encodeToString(first)
            val second = WwJson.decodeFromString<ArchiveV5>(encoded)
            assertEquals(first, second, "$name 往返后模型应完全一致")
        }
    }

    @Test
    fun roundTripDoesNotMaterializeNullsIntoTheArchive() {
        val legacy = fixture("archive-v5-legacy.json")
        val text = WwJson.encodeToString(legacy)
        // 老存档里压根没有的字段，不能因为往返就凭空补出 false
        assertTrue(
            !text.contains("\"streamingEnabled\":false"),
            "缺失的流式开关不得被写成 false",
        )
    }
}
