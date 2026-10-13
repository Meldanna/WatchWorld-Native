package com.watchworld.domain

import com.watchworld.model.TimelineBranch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T17 验收：`timelineMemory.ts` 的 T17 部分 1:1 直译。
 * 期望值全部按 TS 源码**手写**（不回读 Kotlin 常量），这样常量被改动时测试会失败。
 */
class TimelineMemoryTest {

    private fun branch(
        id: String,
        name: String = "",
        tag: String = "",
        codeTag: String? = null,
        descriptionTag: String? = null,
        visible: Boolean? = null,
        description: String = "",
        plotSummary: String = "",
        keyMilestones: List<String>? = null,
        parentId: String? = null,
    ) = TimelineBranch(
        id = id, name = name, tag = tag, codeTag = codeTag, descriptionTag = descriptionTag,
        visible = visible, color = "indigo", description = description, plotSummary = plotSummary,
        keyMilestones = keyMilestones, parentId = parentId,
    )

    @Test
    fun timelineColorsMatchTsTableAndFallbackToFirst() {
        assertEquals(7, TIMELINE_COLORS.size)
        assertEquals(listOf("indigo", "emerald", "amber", "rose", "cyan", "purple", "teal"),
            TIMELINE_COLORS.map { it.id })
        assertEquals("靛青", TIMELINE_COLORS.first().name)
        assertEquals("bg-indigo-500/10 dark:bg-indigo-500/20", TIMELINE_COLORS.first().bg)
        assertEquals("ring-teal-500/40", TIMELINE_COLORS.last().ring)

        assertEquals("indigo", getTimelineColorConfig(null).id)
        assertEquals("indigo", getTimelineColorConfig("").id)
        assertEquals("indigo", getTimelineColorConfig("nope").id)
        assertEquals("teal", getTimelineColorConfig("teal").id)
    }

    @Test
    fun defaultMainTimelineMatchesTs() {
        val t = createDefaultMainTimeline(now = 111L)
        assertEquals("timeline-main", t.id)
        assertEquals("通用世界观", t.name)
        assertEquals("通用", t.tag)
        assertEquals("通用", t.codeTag)
        assertEquals("通用世界观", t.descriptionTag)
        assertEquals(true, t.visible)
        assertEquals("indigo", t.color)
        assertEquals("核心共享世界观与通用事实设定。所有分支均继承此通用基础。", t.description)
        assertEquals("世界观基石由此展开，通用信息在所有分支共享。", t.plotSummary)
        assertEquals(listOf("世界观核心基石建立"), t.keyMilestones)
        assertTrue(t.messageIds.isEmpty())
        assertEquals(111L, t.createdAt)
        assertEquals(111L, t.updatedAt)
    }

    @Test
    fun rootAllocationPicksFirstFreeLetterNumber() {
        assertEquals("A1", allocateTimelineCodeTag(emptyList(), null))
        assertEquals("A2", allocateTimelineCodeTag(listOf(branch("t", codeTag = "A1")), null))
        assertEquals("A1", allocateTimelineCodeTag(emptyList(), branch("timeline-main", codeTag = "通用")))
        assertEquals("A2", allocateTimelineCodeTag(listOf(branch("t", codeTag = "A1")), branch("x", codeTag = "通用")))
    }

    @Test
    fun subAllocationAppendsIndexToParentCode() {
        val parent = branch("a1", codeTag = "A1")
        assertEquals("A11", allocateTimelineCodeTag(listOf(parent), parent))
        assertEquals("A12", allocateTimelineCodeTag(listOf(parent, branch("a11", codeTag = "A11")), parent))
        assertEquals("A11", allocateTimelineCodeTag(emptyList(), branch("naked")))
    }

    @Test
    fun detectIntentSwitchesOnExactCodeTag() {
        val timelines = listOf(branch("t-a1", name = "学园线", tag = "A1", codeTag = "A1"),
            branch("t-b1", name = "战役", tag = "B1", codeTag = "B1"))
        assertEquals(TimelineAction.SWITCH_EXISTING, detectTimelineIntent("a1", timelines).action)
        assertEquals("t-a1", detectTimelineIntent("A1", timelines).targetTimelineId)
        assertEquals("t-b1", detectTimelineIntent("  B1  ", timelines).targetTimelineId)
    }

    @Test
    fun detectIntentSwitchesOnPhrasePattern() {
        val timelines = listOf(branch("t-a1", name = "学园线", tag = "A1", codeTag = "A1"))
        // ⚠️ 这批输入是**照着 TS 正则实测**选的（见 tasks/T17-时间线树.md「心得」），
        //    不是随手写的自然语句 —— TS 的 switchPattern 里 `(?:【|\[)?` 在「时间线/分支」**之前**，
        //    所以「回到分支【A1】」「位于 [A1]」这类写法**在 TS 里就不命中**。
        for (text in listOf("切换到A1", "回到A1", "切回A1", "转到A1", "进入A1",
            "重回A1", "位于A1", "处于A1", "切换到 A1", "回到分支A1",
            "进入时间线：学园线", "转到【学园线】")) {
            val r = detectTimelineIntent(text, timelines)
            assertTrue(r.triggered, "应命中：$text")
            assertEquals(TimelineAction.SWITCH_EXISTING, r.action)
            assertEquals("t-a1", r.targetTimelineId)
        }
        // 反证：TS 正则不接受的写法必须**不命中**（否则等于偷偷放宽了协议）
        for (text in listOf("位于 [A1]", "回到分支【A1】", "分支【A1】", "随便说说")) {
            assertFalse(detectTimelineIntent(text, timelines).triggered, "不应命中：$text")
        }
    }

    @Test
    fun detectIntentCreatesNewFromExplicitDeclaration() {
        val r = detectTimelineIntent("这是一个时间线：叫 五年后要塞战役", emptyList())
        assertTrue(r.triggered)
        assertEquals(TimelineAction.CREATE_NEW, r.action)
        assertEquals("五年后要塞战役", r.newTimelineName)
        assertEquals("五年后要", r.newTimelineTag)
        assertEquals("叫 五年后要塞战役", r.initialDescription)

        // ⚠️ 「这是一个**新**时间线」在 TS 里**不命中**：`(?:一个|一条|一个全新的?)?`
        //    不包含「一个新」这个组合。这条是反证，防止有人「顺手」把正则放宽。
        assertFalse(detectTimelineIntent("这是一个新时间线：叫 五年后要塞战役", emptyList()).triggered)
    }

    @Test
    fun detectIntentCreatesNewFromBracketTagAndSwitchesWhenExisting() {
        val created = detectTimelineIntent("【时间线：IF线·大学时代】开始吧", emptyList())
        assertTrue(created.triggered)
        assertEquals(TimelineAction.CREATE_NEW, created.action)
        assertEquals("IF线·大学时代", created.newTimelineName)
        assertEquals("IF线·", created.newTimelineTag)
        assertEquals("通过标签【IF线·大学时代】明确创建的新时间线。", created.initialDescription)

        val timelines = listOf(branch("t-x", name = "IF线·大学时代", tag = "IF线"))
        val switched = detectTimelineIntent("【时间线：IF线·大学时代】", timelines)
        assertEquals(TimelineAction.SWITCH_EXISTING, switched.action)
        assertEquals("t-x", switched.targetTimelineId)
    }

    @Test
    fun detectIntentReturnsFalseForBlankAndUnrelatedText() {
        assertFalse(detectTimelineIntent("   ", emptyList()).triggered)
        assertFalse(detectTimelineIntent("今天天气不错", emptyList()).triggered)
        assertNull(detectTimelineIntent("今天天气不错", emptyList()).action)
    }

    @Test
    fun enrichAddsMilestoneAndAppendsExchangeLog() {
        val b = branch("t", description = "学园线", plotSummary = "", keyMilestones = emptyList())
        val r = enrichTimelineProgression(b, "她决定离开这里", "好的。")
        assertEquals("学园线\n• 本分支因果节点（当前共 1 个关键里程碑）。", r.updatedDescription)
        assertEquals("用户: 她决定离开这里... | 回复: 好的。...", r.updatedPlotSummary)
        assertEquals(listOf("她决定离开这里"), r.newMilestones)
    }

    @Test
    fun enrichKeepsOnlyLastEightPlotLinesAndTenMilestones() {
        val existing = (1..10).joinToString("\n") { "line$it" }
        val b = branch("t", description = "已有描述", plotSummary = existing, keyMilestones = emptyList())
        val r = enrichTimelineProgression(b, "普通一句话", "普通回复")
        val lines = r.updatedPlotSummary.split("\n")
        assertEquals(8, lines.size)
        assertEquals("line4", lines.first())
        assertEquals("用户: 普通一句话... | 回复: 普通回复...", lines.last())

        val tagged = branch("t", description = "已经有 关键里程碑 字样的描述")
        assertEquals("已经有 关键里程碑 字样的描述",
            enrichTimelineProgression(tagged, "她决定走", "好的。").updatedDescription)

        val many = branch("t", keyMilestones = (1..11).map { "m$it" })
        val r2 = enrichTimelineProgression(many, "触发决定", "好的。")
        assertEquals(10, r2.newMilestones.size)
        assertEquals("m2", r2.newMilestones.first())
    }

    @Test
    fun enrichPicksSentenceFromAiReplyWhenUserMessageIsLong() {
        val longUser = "这是一段很长的用户消息".repeat(10)
        assertTrue(longUser.length >= 50)
        val r = enrichTimelineProgression(branch("t"), longUser, "他发现了秘密。更多内容。")
        assertEquals(listOf("他发现了秘密"), r.newMilestones)
    }

    @Test
    fun invisibleActiveTimelineReturnsEmptyPrompt() {
        assertEquals("", buildTimelineTreePromptContext(branch("t", visible = false), emptyList(), emptyList()))
    }

    @Test
    fun treePromptContainsVerbatimSections() {
        val active = branch(
            "a1", name = "学园线", tag = "A1", codeTag = "A1", descriptionTag = "学园线",
            description = "校园世界观", plotSummary = "已结识好友", parentId = "timeline-main",
        )
        val main = branch("timeline-main", name = "通用世界观", tag = "通用", codeTag = "通用",
            descriptionTag = "通用世界观", description = "通用设定")
        val prompt = buildTimelineTreePromptContext(active, listOf(main, active), emptyList())

        assertTrue(prompt.startsWith("\n# 【时间线记忆树 · 分支重组上下文】\n"))
        assertTrue(prompt.contains("- 当前激活分支：【A1 · 学园线】"))
        assertTrue(prompt.contains("- 分支层级溯源：分化自父分支【通用·通用世界观】"))
        assertTrue(prompt.contains("- 核心区分信息/世界观特征：\n  校园世界观"))
        assertTrue(prompt.contains("- 本时间线因果剧情沉淀：\n  已结识好友"))
        assertTrue(prompt.contains("【其他可见平行时间线（用于对比辨析，保持分立，切勿混淆事实）】：\n- 分支【通用·通用世界观】: 通用设定"))
        assertTrue(prompt.contains("【AI回复规范 (PRD 六)】："))
        assertTrue(prompt.contains("1. 你的回复必须严格基于当前时间线【A1】的因果发展；"))
        assertTrue(prompt.endsWith("8. 若用户需要总结与正文分离，可用 <总结>概括</总结> 与 <正文>详情</正文> 标签包裹，配合正则系统精简历史 Token。\n"))
    }

    @Test
    fun treePromptUsesRootNodeAndPlaceholderWhenNoParentOrPlot() {
        val active = branch("timeline-main", name = "通用世界观", tag = "通用", codeTag = "通用", descriptionTag = "通用世界观")
        val prompt = buildTimelineTreePromptContext(active, listOf(active), emptyList())
        assertTrue(prompt.contains("- 分支层级溯源：根主线节点（通用）"))
        assertTrue(prompt.contains("- 本时间线因果剧情沉淀：\n  本分支剧情展开中..."))
        assertTrue(prompt.contains("【其他可见平行时间线（用于对比辨析，保持分立，切勿混淆事实）】：\n暂无其他可见平行分支。"))
    }

    @Test
    fun treePromptExcludesInvisibleSiblingsAndInvisibleParent() {
        val active = branch("a1", codeTag = "A1", parentId = "hidden-parent")
        val hiddenSibling = branch("b1", codeTag = "B1", description = "秘密分支", visible = false)
        val hiddenParent = branch("hidden-parent", codeTag = "H0", description = "隐藏父级", visible = false)
        val visibleSibling = branch("c1", codeTag = "C1", description = "可见分支")
        val prompt = buildTimelineTreePromptContext(active, listOf(hiddenParent, hiddenSibling, visibleSibling, active), emptyList())

        assertTrue(prompt.contains("- 分支层级溯源：根主线节点（通用）"))
        assertFalse(prompt.contains("秘密分支"))
        assertTrue(prompt.contains("可见分支"))
    }

    @Test
    fun treePromptTruncatesSiblingDescriptionToSixtyChars() {
        val longDesc = "x".repeat(80)
        val active = branch("a1", codeTag = "A1")
        val sibling = branch("b1", codeTag = "B1", description = longDesc)
        val prompt = buildTimelineTreePromptContext(active, listOf(active, sibling), emptyList())
        assertTrue(prompt.contains("- 分支【B1·】: ${"x".repeat(60)}"))
        assertFalse(prompt.contains("x".repeat(61)))
    }
}