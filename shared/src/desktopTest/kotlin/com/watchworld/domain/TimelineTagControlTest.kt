package com.watchworld.domain

import com.watchworld.model.ChatMessage
import com.watchworld.model.Role
import com.watchworld.model.TimelineBranch
import com.watchworld.model.TimelineDescriptionTagRequest
import com.watchworld.model.TimelineDescriptionTagRequestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T17 验收：`timelineDescriptionTagControl.ts` 的 1:1 直译。
 * 期望字符串全部按 TS 源码手写。
 */
class TimelineTagControlTest {

    private fun branch(id: String, descriptionTag: String? = null, visible: Boolean? = null) =
        TimelineBranch(id = id, descriptionTag = descriptionTag, visible = visible)

    private fun message(id: String, timelineId: String? = null, descriptionTag: String? = null) =
        ChatMessage(id = id, role = Role.USER, timelineId = timelineId, descriptionTag = descriptionTag)

    private fun request(
        id: String,
        target: String,
        status: TimelineDescriptionTagRequestStatus = TimelineDescriptionTagRequestStatus.PENDING,
    ) = TimelineDescriptionTagRequest(
        id = id, targetTimelineId = target, proposedDescriptionTag = "新标签",
        reason = "理由", status = status,
    )

    @Test
    fun controlConstantsMatchTs() {
        assertEquals("<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:", TIMELINE_DESCRIPTION_TAG_CONTROL_OPEN)
        assertEquals("-->", TIMELINE_DESCRIPTION_TAG_CONTROL_CLOSE)
        assertEquals(120, TIMELINE_DESCRIPTION_TAG_MAX_LENGTH)
        assertEquals(240, TIMELINE_DESCRIPTION_TAG_REASON_MAX_LENGTH)
    }

    @Test
    fun stripRemovesCompleteControls() {
        val raw = "正文<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"a\",\"reason\":\"b\"}--> 尾部"
        assertEquals("正文 尾部", stripTimelineDescriptionTagControl(raw))
    }

    @Test
    fun stripDropsEverythingAfterUnclosedMarker() {
        val raw = "可见正文<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"半截"
        assertEquals("可见正文", stripTimelineDescriptionTagControl(raw))
    }

    @Test
    fun stripTrimsTrailingWhitespaceAndLeavesCleanTextUntouched() {
        assertEquals("hello", stripTimelineDescriptionTagControl("hello   \n "))
        assertEquals("", stripTimelineDescriptionTagControl(""))
    }

    @Test
    fun parseExtractsSingleValidProposalAndHidesControl() {
        val raw = "回复正文<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:" +
            "{\"descriptionTag\":\"学园线·结识好友\",\"reason\":\"本轮结识了新朋友\"}-->"
        val result = parseTimelineDescriptionTagControl(raw)
        assertEquals("回复正文", result.visibleReply)
        assertEquals("学园线·结识好友", result.proposal?.proposedDescriptionTag)
        assertEquals("本轮结识了新朋友", result.proposal?.reason)
    }

    @Test
    fun parseIgnoresMalformedInvalidAndDuplicateControls() {
        val broken = parseTimelineDescriptionTagControl("A<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{oops}-->")
        assertEquals("A", broken.visibleReply)
        assertNull(broken.proposal)

        val wrongKeys = parseTimelineDescriptionTagControl(
            "B<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"x\"}-->")
        assertNull(wrongKeys.proposal)

        val array = parseTimelineDescriptionTagControl(
            "C<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:[\"x\",\"y\"]-->")
        assertNull(array.proposal)

        val two = parseTimelineDescriptionTagControl(
            "D<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"a\",\"reason\":\"b\"}-->" +
                "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"c\",\"reason\":\"d\"}-->")
        assertEquals("D", two.visibleReply)
        assertNull(two.proposal)
    }

    @Test
    fun parseRejectsEmptyAndNonStringFields() {
        assertNull(parseTimelineDescriptionTagControl(
            "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"\",\"reason\":\"b\"}-->").proposal)
        assertNull(parseTimelineDescriptionTagControl(
            "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":\"   \",\"reason\":\"b\"}-->").proposal)
        assertNull(parseTimelineDescriptionTagControl(
            "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{\"descriptionTag\":123,\"reason\":\"b\"}-->").proposal)
    }

    @Test
    fun parseSanitizesControlCharactersAndTruncates() {
        val longTag = "甲".repeat(200)
        val raw = "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:" +
            "{\"descriptionTag\":\"$longTag\",\"reason\":\" 理由 \"}-->"
        val result = parseTimelineDescriptionTagControl(raw)
        assertEquals(120, result.proposal?.proposedDescriptionTag?.length)
        assertEquals("理由", result.proposal?.reason)

        // 真·控制字符（NUL / BEL / DEL）必须被剔掉，只留可见文字。
        // ⚠️ 这条是**判别性**的：若正则里把 `\u0000` 写成了字面量 `\\u0000`，这里会失败。
        val withControls = "<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:" +
            "{\"descriptionTag\":\"\\u0000学\\u0007园\\u007F线\",\"reason\":\"\\u0001理由\\u0002\"}-->"
        val cleaned = parseTimelineDescriptionTagControl(withControls)
        assertEquals("学园线", cleaned.proposal?.proposedDescriptionTag)
        assertEquals("理由", cleaned.proposal?.reason)
    }

    @Test
    fun sanitizeTimelineTagTextStripsControlCharacters() {
        assertEquals("AB", sanitizeTimelineTagText("\u0000A\u0007B\u007F", 10))
        assertEquals("AB", sanitizeTimelineTagText("A\u000BB\u000C", 10))
        // 制表符属于 TS 的 C0 区间**之外**（`\u0000-\u0008` 不含 `\u0009`），所以**不剥**，只被 trim 掉
        assertEquals("A \tB", sanitizeTimelineTagText("  A \tB  ", 10))
        assertNull(sanitizeTimelineTagText("\u0000\u0007", 10))
    }

    @Test
    fun createBuildsPendingRequestWithComputedId() {
        val timelines = listOf(branch("t-a1", descriptionTag = "旧标签"))
        val req = createTimelineDescriptionTagRequest(
            targetTimelineId = "t-a1",
            sourceMessageId = "msg-7",
            proposal = TimelineDescriptionTagProposal("新标签", "因为剧情推进"),
            timelines = timelines,
            timelineMemoryEnabled = true,
            now = 999L,
        )
        assertEquals("timeline-description-tag-999-msg-7", req?.id)
        assertEquals("t-a1", req?.targetTimelineId)
        assertEquals("旧标签", req?.oldDescriptionTag)
        assertEquals("新标签", req?.proposedDescriptionTag)
        assertEquals("因为剧情推进", req?.reason)
        assertEquals("msg-7", req?.sourceMessageId)
        assertEquals(999L, req?.createdAt)
        assertEquals(TimelineDescriptionTagRequestStatus.PENDING, req?.status)
    }

    @Test
    fun createReturnsNullForAllGuardConditions() {
        val timelines = listOf(branch("t-a1", descriptionTag = "旧标签"))
        val proposal = TimelineDescriptionTagProposal("新标签", "理由")
        assertNull(createTimelineDescriptionTagRequest("t-a1", "m", proposal, timelines, false))
        assertNull(createTimelineDescriptionTagRequest("t-a1", "m", null, timelines, true))
        assertNull(createTimelineDescriptionTagRequest("t-a1", "", proposal, timelines, true))
        assertNull(createTimelineDescriptionTagRequest("missing", "m", proposal, timelines, true))
        assertNull(createTimelineDescriptionTagRequest("t-hidden", "m", proposal,
            listOf(branch("t-hidden", visible = false)), true))
        assertNull(createTimelineDescriptionTagRequest("t-a1", "m",
            TimelineDescriptionTagProposal(" 旧标签 ", "理由"), timelines, true))
    }

    // ---------------------------------------------------------------- approve / reject

    @Test
    fun approveUpdatesRequestTimelineAndMessageCaches() {
        val timelines = listOf(branch("t-a1", descriptionTag = "旧标签"),
            branch("t-b1", descriptionTag = "别动我"))
        val messages = listOf(message("m1", timelineId = "t-a1", descriptionTag = "旧标签"),
            message("m2", timelineId = "t-b1", descriptionTag = "别动我"),
            message("m3", timelineId = null))
        val requests = listOf(request("req-1", "t-a1"))

        val result = approveTimelineDescriptionTagRequest(requests, timelines, messages, "req-1", true, now = 5L)

        assertTrue(result.resolved)
        assertEquals(TimelineDescriptionTagRequestStatus.APPROVED, result.requests.single().status)
        assertEquals(5L, result.requests.single().resolvedAt)
        assertEquals("新标签", result.timelines.first { it.id == "t-a1" }.descriptionTag)
        assertEquals(5L, result.timelines.first { it.id == "t-a1" }.updatedAt)
        assertEquals("别动我", result.timelines.first { it.id == "t-b1" }.descriptionTag)
        assertEquals("新标签", result.messages.first { it.id == "m1" }.descriptionTag)
        assertEquals("别动我", result.messages.first { it.id == "m2" }.descriptionTag)
        assertNull(result.messages.first { it.id == "m3" }.descriptionTag)
    }

    @Test
    fun approveIsNoOpWhenMemoryDisabledOrRequestOrBranchMissing() {
        val timelines = listOf(branch("t-a1"))
        val requests = listOf(request("req-1", "t-a1"))
        val messages = listOf(message("m1", timelineId = "t-a1"))

        // 记忆关闭 → 原样返回
        val off = approveTimelineDescriptionTagRequest(requests, timelines, messages, "req-1", false)
        assertFalse(off.resolved)
        assertEquals(TimelineDescriptionTagRequestStatus.PENDING, off.requests.single().status)

        // 无此申请
        assertFalse(approveTimelineDescriptionTagRequest(requests, timelines, messages, "nope", true).resolved)

        // 申请已处理
        val resolved = listOf(request("req-1", "t-a1", TimelineDescriptionTagRequestStatus.APPROVED))
        assertFalse(approveTimelineDescriptionTagRequest(resolved, timelines, messages, "req-1", true).resolved)

        // 目标分支不可见 / 不存在
        assertFalse(approveTimelineDescriptionTagRequest(requests, listOf(branch("t-a1", visible = false)),
            messages, "req-1", true).resolved)
        assertFalse(approveTimelineDescriptionTagRequest(requests, emptyList(), messages, "req-1", true).resolved)
    }

    @Test
    fun rejectOnlyChangesStatusAndIgnoresMemoryFlag() {
        val timelines = listOf(branch("t-a1", descriptionTag = "旧标签"))
        val messages = listOf(message("m1", timelineId = "t-a1", descriptionTag = "旧标签"))
        val requests = listOf(request("req-1", "t-a1"))

        // 即使记忆关闭也照常拒绝（TS 源码如此）
        val result = rejectTimelineDescriptionTagRequest(requests, timelines, messages, "req-1", now = 8L)
        assertTrue(result.resolved)
        assertEquals(TimelineDescriptionTagRequestStatus.REJECTED, result.requests.single().status)
        assertEquals(8L, result.requests.single().resolvedAt)
        assertEquals("旧标签", result.timelines.single().descriptionTag)
        assertEquals("旧标签", result.messages.single().descriptionTag)

        assertFalse(rejectTimelineDescriptionTagRequest(requests, timelines, messages, "nope").resolved)
    }
}
