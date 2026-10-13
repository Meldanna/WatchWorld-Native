package com.watchworld.domain

import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.AppPreferences
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.DefaultModelConfig
import com.watchworld.model.MessageVersion
import com.watchworld.model.Role
import com.watchworld.model.TimelineBranch
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** T20 验收：角色关系分析的纯逻辑（分支解析 / 上下文格式 / 模型回退链 / 兜底文案）。 */
class RoleAnalysisTest {

    @Test
    fun currentBranchInfoPrefersCodeTagAndDescriptionTag() {
        val session = ChatSession(
            id = "s",
            activeTimelineId = "t2",
            timelines = listOf(
                TimelineBranch(id = "t1", tag = "通用", name = "通用世界观"),
                TimelineBranch(id = "t2", codeTag = "A12", descriptionTag = "学园线·结识好友", tag = "A12", name = "学园线"),
            ),
        )
        assertEquals(BranchInfo("A12", "学园线·结识好友"), currentBranchInfo(session))
    }

    @Test
    fun currentBranchInfoFallsBackToTagNameThenDefaults() {
        val branch = TimelineBranch(id = "t1", tag = "B1", name = "分支一")
        assertEquals(BranchInfo("B1", "分支一"), currentBranchInfo(ChatSession(id = "s", timelines = listOf(branch))))
        assertEquals(BranchInfo("通用", "主线"), currentBranchInfo(ChatSession(id = "s")))
    }

    @Test
    fun buildRoleAnalysisContextFormat() {
        val messages = listOf(
            ChatMessage(id = "u", role = Role.USER, content = "你好", floorNumber = 1, codeTag = "通用"),
            ChatMessage(
                id = "a",
                role = Role.ASSISTANT,
                floorNumber = 2,
                codeTag = "A1",
                versions = listOf(MessageVersion(content = "回复")),
            ),
        )
        assertEquals("[通用] #1 用户: 你好\n[A1] #2 AI: 回复", buildRoleAnalysisContext(messages))
    }

    @Test
    fun buildRoleAnalysisUserPromptWrapsBranchAndContext() {
        val prompt = buildRoleAnalysisUserPrompt(BranchInfo("A1", "学园线"), "#1 用户: 你好")
        assertContains(prompt, "【当前时间线分支】：A1 · 学园线")
        assertContains(prompt, "【当前时间线重排上下文记录】：")
        assertContains(prompt, "#1 用户: 你好")
        assertContains(prompt, "请基于上述记录，生成一份角色关系与深层心理学动态分析报告。")
    }

    @Test
    fun buildRoleAnalysisFallbackMentionsBranchAndUnavailable() {
        val fallback = buildRoleAnalysisFallback(BranchInfo("A1", "学园线"))
        assertContains(fallback, "### 📊 角色关系分析")
        assertContains(fallback, "**分析分支**：`A1 · 学园线`")
        assertContains(fallback, "分析功能暂不可用，请检查 API 配置。")
    }

    @Test
    fun dropHiddenAnalysisMessagesOnlyRemovesExplicitlyHiddenReports() {
        fun analysis(id: String, visibility: AnalysisVisibility?): ChatMessage = ChatMessage(
            id = id, role = Role.ASSISTANT, floorNumber = 1, isAnalysis = true,
            analysisVisibility = visibility,
            versions = listOf(MessageVersion(content = id)),
        )
        val plain = ChatMessage(id = "plain", role = Role.USER, content = "hi", floorNumber = 2)
        val messages = listOf(
            analysis("hidden", AnalysisVisibility.HIDDEN),
            analysis("visible", AnalysisVisibility.VISIBLE),
            analysis("legacy", null),
            plain,
        )
        assertEquals(
            listOf("visible", "legacy", "plain"),
            dropHiddenAnalysisMessages(messages).map { it.id },
        )
    }

    @Test
    fun buildAnalysisDocTitleUsesFloorAndCodeTag() {
        assertEquals("心理博弈分析·#7 (A1)", buildAnalysisDocTitle(7, "A1"))
        assertEquals("心理博弈分析·#1 (分析)", buildAnalysisDocTitle(null, null))
    }

    @Test
    fun resolveAnalysisModelFallbackChain() {
        val session = ChatSession(id = "s", model = "session-model")
        val prefs = AppPreferences(defaultModels = DefaultModelConfig(analysis = "ana-model"))
        val provider = ApiProviderConfig(id = "p", name = "P", type = ApiProviderType.OPENAI, defaultModel = "provider-model")
        assertEquals("session-model", resolveAnalysisModel(session, prefs, provider))
        assertEquals("ana-model", resolveAnalysisModel(session.copy(model = null), prefs, provider))
        assertEquals("provider-model", resolveAnalysisModel(session.copy(model = null), AppPreferences(), provider))
    }
}
