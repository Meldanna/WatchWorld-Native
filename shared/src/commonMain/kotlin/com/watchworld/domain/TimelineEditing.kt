package com.watchworld.domain

import com.watchworld.model.ChatSession
import com.watchworld.model.TimelineBranch

/*
 * `src/components/TimelineModal.tsx` 里的会话级写操作，1:1 直译（T24）。
 *
 * TimelineModal 把「点按钮 → `onUpdateSession(updater)`」这一类逻辑就地写在组件里。原生侧
 * 拆成两层：本文件只做「旧会话 → 新会话」的**纯变换**（可脱离数据库单测），落库与状态回流
 * 交给 `ChatViewModel`（MIGRATION_CONSTRAINTS §5：UI 不直接操作 DAO）。
 *
 * ⚠️ 字符串字面量逐字照抄 —— 默认剧情脉络、默认区分信息、里程碑格式都会进存档。
 * ⚠️ 「无变化」的调用点**返回传入的同一个实例**，调用方据此跳过写库（见 `ChatViewModel`）。
 */

/**
 * 新建 / 编辑分支的表单载荷（`TimelineModal.tsx:69-76` 的 `formXxx` 状态组）。
 *
 * 与 [TimelineBranch] 有意分开：这是**用户正在输入、还没保存**的草稿，保存失败时要原样保留，
 * 而 `TimelineBranch` 是已落库的实体。
 */
data class TimelineDraft(
    /** `null` = 新建分支；非空 = 正在编辑该分支。 */
    val editingId: String? = null,
    val codeTag: String = "",
    /** 供用户辨认的中文名字（**不含**编号前缀）。 */
    val name: String = "",
    val descriptionTag: String = "",
    val color: String = TIMELINE_COLORS.first().id,
    val parentId: String? = null,
    val description: String = "",
    val plotSummary: String = "",
) {
    /** 保存按钮的可用条件（`TimelineModal.tsx:881`：编号与中文名都不为空）。 */
    val canSave: Boolean get() = name.isNotBlank() && codeTag.isNotBlank()
}

/**
 * `TimelineModal.tsx:141 handleStartCreate`：新建分支的初值。
 *
 * 编号由 [allocateTimelineCodeTag] 自动分配，代表色取**第一个还没被用过的**色板项。
 */
fun newTimelineDraft(
    timelines: List<TimelineBranch>,
    parentId: String? = null,
    activeTimelineId: String? = null,
): TimelineDraft {
    val parentKey = parentId?.ifEmpty { null } ?: activeTimelineId?.ifEmpty { null } ?: ""
    val parentBranch = timelines.firstOrNull { it.id == parentKey }
    val usedColors = timelines.mapNotNull { it.color.ifEmpty { null } }.toSet()
    val nextColor = TIMELINE_COLORS.firstOrNull { it.id !in usedColors } ?: TIMELINE_COLORS.first()
    return TimelineDraft(
        editingId = null,
        codeTag = allocateTimelineCodeTag(timelines, parentBranch),
        name = "新分支",
        descriptionTag = "",
        color = nextColor.id,
        parentId = parentKey,
        description = "",
        plotSummary = "自该分化节点起展开新剧情。",
    )
}

/**
 * `TimelineModal.tsx:158 handleStartEdit`：编辑现有分支的初值。
 *
 * ⚠️ 中文名要先把已有的编号前缀剥掉，否则「A1·学园线」会被拼成「A1·A1·学园线」。
 */
fun editTimelineDraft(branch: TimelineBranch): TimelineDraft {
    val currentCode = branch.codeTag?.ifEmpty { null } ?: branch.tag
    val displayName = NAME_CODE_PREFIX(currentCode).replace(branch.name, "").trim()
    return TimelineDraft(
        editingId = branch.id,
        codeTag = currentCode,
        name = displayName.ifEmpty { branch.name },
        descriptionTag = branch.descriptionTag.orEmpty(),
        color = branch.color,
        parentId = branch.parentId?.ifEmpty { null },
        description = branch.description,
        plotSummary = branch.plotSummary,
    )
}

/**
 * `TimelineModal.tsx:38 formatParentBranchLabel`。
 *
 * 把分支显示成「编号 · 中文名」：先从事先拼好的标签里抠出 `A12` 这样的编号，再把分支名里
 * 重复的编号前缀剥掉。**抠不出编号时原样返回名字或标签**（老存档里 `tag` 可能是「主线」）。
 */
fun formatTimelineParentLabel(timeline: TimelineBranch): String {
    val rawName = timeline.name.trim()
    val rawTag = (timeline.codeTag?.ifEmpty { null } ?: timeline.tag).trim()
    val codeTag = CODE_TAG_PREFIX.find(rawTag)?.groupValues?.get(1)?.uppercase() ?: ""
    if (codeTag.isEmpty()) return rawName.ifEmpty { rawTag }

    val cleanName = NAME_CODE_PREFIX(codeTag).replace(rawName, "").trim()
    return if (cleanName.isNotEmpty()) "$codeTag · $cleanName" else codeTag
}

/** `TimelineModal.tsx:609` 的卡片标题：`A12·学园线`（编号与中文名之间是**中点**，不补空格）。 */
fun timelineDisplayTitle(timeline: TimelineBranch): String {
    val code = timeline.codeTag?.ifEmpty { null } ?: timeline.tag
    val cleanName = NAME_CODE_PREFIX(code).replace(timeline.name, "")
    return "$code·$cleanName"
}

/** `TimelineModal.tsx:82-83`：当前聚焦分支（`activeTimelineId` → 首条 → `timeline-main` 两级回退）。 */
fun resolveActiveTimeline(session: ChatSession): TimelineBranch? {
    val timelines = session.timelines.orEmpty()
    val id = session.activeTimelineId?.ifEmpty { null }
        ?: timelines.firstOrNull()?.id
        ?: DEFAULT_MAIN_TIMELINE_ID
    return timelines.firstOrNull { it.id == id } ?: timelines.firstOrNull()
}

/** `TimelineModal.tsx:94 handleToggleMemory`：总开关本身只翻一个布尔。 */
fun setTimelineMemoryEnabled(session: ChatSession, enabled: Boolean, now: Long): ChatSession =
    session.copy(timelineMemoryEnabled = enabled, updatedAt = now)

/** `TimelineModal.tsx:102 handleSelectActiveTimeline`。 */
fun setActiveTimelineBranch(session: ChatSession, timelineId: String, now: Long): ChatSession =
    session.copy(activeTimelineId = timelineId, updatedAt = now)

/**
 * `TimelineModal.tsx:110 handleToggleVisibility`。
 *
 * ⚠️ TS 是**取反**（`!(t.visible !== false)`）；这里收显式目标值，调用方传
 *    `branch.visible == false` 即完全等价，且便于「设为某值」的调用点复用。
 * ⚠️ 只影响发给 AI 的上下文，屏幕始终完整展示（PRD 4.6）。
 */
fun setTimelineBranchVisibility(
    session: ChatSession,
    timelineId: String,
    visible: Boolean,
    now: Long,
): ChatSession = session.copy(
    timelines = session.timelines.orEmpty().map { timeline ->
        if (timeline.id != timelineId) timeline
        else timeline.copy(visible = visible, updatedAt = now)
    },
    updatedAt = now,
)

/** `TimelineModal.tsx:126 handleSetAllVisibility`。⚠️ 自由多选，**不动** `activeTimelineId`。 */
fun setAllTimelineBranchesVisibility(session: ChatSession, visible: Boolean, now: Long): ChatSession =
    session.copy(
        timelines = session.timelines.orEmpty().map { it.copy(visible = visible, updatedAt = now) },
        updatedAt = now,
    )

/**
 * `TimelineModal.tsx:175 handleSaveTimeline`：新建或更新一个分支。
 *
 * 三种连带写入（漏掉任何一种都会让界面与 AI 上下文对不上）：
 *   1. 分支自身的 `name` / `tag` / `codeTag` / `descriptionTag` / `color` / `parentId` /
 *      `description` / `plotSummary`；
 *   2. **归属该分支的全部消息**的 `timelineTag` / `codeTag` / `descriptionTag` 缓存；
 *   3. 新建时把 `activeTimelineId` 切到新分支。
 *
 * ⚠️ 中文名为空 → **原样返回**（TS 的 `if (!displayName) return`）。保存按钮本身就禁用了，
 *    这里是第二道闸门。
 */
fun applyTimelineDraft(session: ChatSession, draft: TimelineDraft, now: Long): ChatSession {
    val displayName = draft.name.trim()
    if (displayName.isEmpty()) return session

    val codeTag = draft.codeTag.trim().uppercase().ifEmpty { "A1" }
    val fullName = "$codeTag·$displayName"
    val descriptionTag = draft.descriptionTag.trim()
    val description = draft.description.trim()
        .ifEmpty { "在「$fullName」分支下的独立设定，区别于其他时间线。" }
    val plotSummary = draft.plotSummary.trim()
    val timelines = session.timelines.orEmpty()

    val editingId = draft.editingId
    if (editingId == null) {
        val newId = "timeline-$now"
        val branch = TimelineBranch(
            id = newId,
            name = displayName,
            tag = codeTag,
            codeTag = codeTag,
            descriptionTag = descriptionTag,
            visible = true,
            color = draft.color,
            description = description,
            plotSummary = plotSummary.ifEmpty { "新时间线分支已建立，等待探索。" },
            keyMilestones = listOf("分化创建时间线【$fullName】"),
            messageIds = emptyList(),
            parentId = draft.parentId?.ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
        return session.copy(
            timelines = timelines + branch,
            activeTimelineId = newId,
            updatedAt = now,
        )
    }

    val nextTimelines = timelines.map { timeline ->
        if (timeline.id != editingId) timeline
        else timeline.copy(
            name = displayName,
            tag = codeTag,
            codeTag = codeTag,
            descriptionTag = descriptionTag,
            color = draft.color,
            parentId = draft.parentId?.ifEmpty { null },
            description = description,
            // 剧情脉络留空时**沿用旧值**（TS：`formPlotSummary.trim() || t.plotSummary`）。
            plotSummary = plotSummary.ifEmpty { timeline.plotSummary },
            updatedAt = now,
        )
    }
    val nextMessages = session.messages.map { message ->
        if (message.timelineId != editingId) message
        else message.copy(timelineTag = fullName, codeTag = codeTag, descriptionTag = descriptionTag)
    }
    return session.copy(timelines = nextTimelines, messages = nextMessages, updatedAt = now)
}

/**
 * `TimelineModal.tsx:259 handleDeleteTimeline`：删除分支，消息回落到第一条剩余分支。
 *
 * ⚠️ **只剩一个分支时不删**（TS 的 `timelines.length <= 1` 守卫）—— 返回同一实例，
 *    调用方据此跳过写库。
 * ⚠️ `timelineTag` 回落用的是剩余分支的 `tag`（不是 `codeTag`），空串再兜「主线」。
 *    TS 源码如此，照抄。
 */
fun deleteTimelineBranch(session: ChatSession, timelineId: String, now: Long): ChatSession {
    val timelines = session.timelines.orEmpty()
    if (timelines.size <= 1) return session

    val remaining = timelines.filterNot { it.id == timelineId }
    val fallback = remaining.firstOrNull()
    val fallbackId = fallback?.id ?: DEFAULT_MAIN_TIMELINE_ID
    val fallbackTag = fallback?.tag?.ifEmpty { null } ?: "主线"

    val nextMessages = session.messages.map { message ->
        if (message.timelineId != timelineId) message
        else message.copy(timelineId = fallbackId, timelineTag = fallbackTag)
    }
    return session.copy(
        timelines = remaining,
        activeTimelineId = if (session.activeTimelineId == timelineId) fallbackId else session.activeTimelineId,
        messages = nextMessages,
        updatedAt = now,
    )
}

/** `^([A-Z]+\d+)`（`i`）：从标签里抠出 `A12` 这样的编号前缀。 */
private val CODE_TAG_PREFIX: Regex = Regex("^([A-Za-z]+\\d+)")

/** `^<编号>[·\s_-]*`（`i`）：剥掉名字里重复的编号前缀。 */
private fun NAME_CODE_PREFIX(codeTag: String): Regex =
    Regex("^${escapeRegex(codeTag)}[·\\s_-]*", RegexOption.IGNORE_CASE)
