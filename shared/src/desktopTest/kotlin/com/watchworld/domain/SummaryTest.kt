package com.watchworld.domain

import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.AppPreferences
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.DefaultModelConfig
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T20 验收：前文总结的纯逻辑（范围 / 逐楼文本 / 模型回退链）。 */
class SummaryTest {

    private fun user(floor: Int, content: String, codeTag: String? = null) =
        ChatMessage(id = "u$floor", role = Role.USER, content = content, floorNumber = floor, codeTag = codeTag)

    private fun assistant(floor: Int, content: String, codeTag: String? = null, content2: String? = null) =
        ChatMessage(
            id = "a$floor",
            role = Role.ASSISTANT,
            content = "",
            floorNumber = floor,
            codeTag = codeTag,
            versions = listOf(MessageVersion(content = content), MessageVersion(content = content2 ?: content)),
            currentVersionIndex = 0,
        )

    @Test
    fun summaryRangeCountsPendingAndHidden() {
        val session = ChatSession(
            id = "s",
            messages = listOf(user(1, "a"), assistant(2, "b"), user(3, "c"), assistant(4, "d")),
            lastSummarizedFloor = 2,
        )
        val range = summaryRange(session)
        assertEquals(3, range.fromFloor)
        assertEquals(4, range.maxFloor)
        assertEquals(2, range.pendingCount)
        assertEquals(2, range.hiddenCount)
    }

    @Test
    fun summaryRangeOnEmptySession() {
        val range = summaryRange(ChatSession(id = "s"))
        assertEquals(1, range.fromFloor)
        assertEquals(0, range.maxFloor)
        assertEquals(0, range.pendingCount)
        assertEquals(0, range.hiddenCount)
    }

    @Test
    fun unsummarizedMessagesKeepsFloorsAfterLastSummarized() {
        val session = ChatSession(
            id = "s",
            messages = listOf(user(1, "a"), assistant(2, "b"), user(3, "c")),
            lastSummarizedFloor = 2,
        )
        assertEquals(listOf("u3"), unsummarizedMessages(session).map { it.id })
    }

    @Test
    fun buildSummaryTranscriptFollowsRoleAsymmetryAndTagPrefix() {
        val transcript = buildSummaryTranscript(
            listOf(user(1, "你好", codeTag = "通用"), assistant(2, "回复", codeTag = "A1")),
        )
        assertEquals("#1 [通用] 用户：你好\n\n#2 [A1] AI：回复", transcript)
        // 没有 codeTag 时不写方括号段。
        assertEquals("#1 用户：你好", buildSummaryTranscript(listOf(user(1, "你好"))))
    }

    @Test
    fun buildSummaryTranscriptReadsCurrentVersionNotFirst() {
        val msg = ChatMessage(
            id = "a",
            role = Role.ASSISTANT,
            floorNumber = 1,
            versions = listOf(MessageVersion(content = "旧版"), MessageVersion(content = "新版")),
            currentVersionIndex = 1,
        )
        assertEquals("#1 AI：新版", buildSummaryTranscript(listOf(msg)))
    }

    @Test
    fun buildSummaryRequestContainsDelimiterAndPrompt() {
        val request = buildSummaryRequest("请总结", "#1 用户：a")
        assertContains(request, "请总结")
        assertContains(request, "===== 待总结对话 =====")
        assertContains(request, "#1 用户：a")
    }

    @Test
    fun resolveSummaryModelPrefersOverrideThenSessionThenPreferencesThenProvider() {
        val session = ChatSession(id = "s", model = "session-model")
        val prefs = AppPreferences(defaultModels = DefaultModelConfig(summary = "pref-model"))
        val provider = ApiProviderConfig(id = "p", name = "P", type = ApiProviderType.OPENAI, defaultModel = "provider-model")

        assertEquals("override", resolveSummaryModel("override", session, prefs, provider))
        assertEquals("session-model", resolveSummaryModel(null, session, prefs, provider))
        assertEquals(
            "pref-model",
            resolveSummaryModel(null, session.copy(model = null), prefs, provider),
        )
        assertEquals(
            "provider-model",
            resolveSummaryModel(null, session.copy(model = null), AppPreferences(), provider),
        )
        assertTrue(resolveSummaryModel(null, session.copy(model = null), AppPreferences(), null).isEmpty())
    }
}
