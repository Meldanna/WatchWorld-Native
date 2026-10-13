package com.watchworld.domain

import com.watchworld.model.ChatMessage
import com.watchworld.model.Role
import com.watchworld.model.TimelineBranch

/*
 * `src/lib/timelineMemory.ts` 中上下文拼装要用到的部分：
 *   T08：`DEFAULT_MAIN_TIMELINE_ID`:74、`rearrangeMessagesForAi`:146、`formatMessageWithFloorAndTag`:226；
 *   T17：`TIMELINE_COLORS`:3、`getTimelineColorConfig`:69、`createDefaultMainTimeline`:76、
 *        `allocateTimelineCodeTag`:98、`detectTimelineIntent`:264、`enrichTimelineProgression`:347、
 *        `buildTimelineTreePromptContext`:399。
 *
 * ⚠️ 1:1 直译，排序比较器的每一个分支都对应 TS 一行；JS 的 `Array.prototype.sort` 是稳定排序，
 *    Kotlin 的 `sortedWith` 同样稳定，相等元素保持原相对顺序。
 * ⚠️ 字符串字面量（时间线树提示词、里程碑关键词）逐字照抄，改一个字都会影响模型行为
 *    （MIGRATION_CONSTRAINTS §4.3）。
 */

/** `timelineMemory.ts:74`。⚠️ 存档里的字面量，不许改（`isCommonMessage` 三重判定之一）。 */
const val DEFAULT_MAIN_TIMELINE_ID: String = "timeline-main"

/** 「通用」标签字面量（`codeTag == "通用"`）。 */
const val COMMON_CODE_TAG: String = "通用"

/**
 * 发给 AI 的消息正文：assistant 取当前版本（越界退回 `versions[0]`），空串再退回顶层 `content`；
 * 其它角色只用顶层 `content`。对应 TS 的
 * `(m.versions[m.currentVersionIndex] || m.versions[0])?.content || m.content`。
 */
internal fun messageContentForAi(message: ChatMessage): String {
    if (message.role != Role.ASSISTANT) return message.content
    val version = message.versions.getOrNull(message.currentVersionIndex) ?: message.versions.firstOrNull()
    return version?.content?.ifEmpty { null } ?: message.content
}

/** `timelineMemory.ts:146 rearrangeMessagesForAi`：纯程序逻辑，不调用 AI。 */
fun rearrangeMessagesForAi(
    currentBranch: TimelineBranch?,
    allTimelines: List<TimelineBranch>,
    allMessages: List<ChatMessage>,
): List<ChatMessage> {
    if (allMessages.isEmpty()) return emptyList()

    // 1. 自由多选：可见性完全由各分支自身的 visible 开关决定
    val visibleBranchIds = allTimelines.filter { it.visible != false }.map { it.id }.toSet()

    // 记忆树层级深度（带环保护：同一次顶层调用内共用 guard，与 TS 默认参数一致）
    val branchDepth = mutableMapOf<String, Int>()
    fun depthOf(branch: TimelineBranch, guard: MutableSet<String> = mutableSetOf()): Int {
        branchDepth[branch.id]?.let { return it }
        if (branch.id in guard) return 0
        guard += branch.id
        val parentId = branch.parentId
        val parent = if (!parentId.isNullOrEmpty() && parentId != branch.id) {
            allTimelines.firstOrNull { it.id == parentId }
        } else null
        val depth = if (parent != null) depthOf(parent, guard) + 1 else 0
        branchDepth[branch.id] = depth
        return depth
    }
    allTimelines.forEach { depthOf(it) }

    val mainBranch = allTimelines.firstOrNull {
        it.id == DEFAULT_MAIN_TIMELINE_ID || it.codeTag == COMMON_CODE_TAG
    }
    val isMainVisible = mainBranch?.let { it.visible != false } ?: true

    // 2. 取出属于可见分支的消息
    val included = allMessages.filter { m ->
        when {
            m.isCommon == true || m.codeTag == COMMON_CODE_TAG || m.timelineId == DEFAULT_MAIN_TIMELINE_ID -> isMainVisible
            m.timelineId.isNullOrEmpty() -> isMainVisible // 未分配分支的旧消息视为通用
            else -> m.timelineId in visibleBranchIds
        }
    }

    // 3. 排序：通用 → 浅层 → 深层 → 当前分支（同层按楼号，再按时间戳）
    val currentBranchId = currentBranch?.id
    return included.sortedWith { a, b ->
        val isCurrentA = a.timelineId == currentBranchId
        val isCurrentB = b.timelineId == currentBranchId
        if (isCurrentA && !isCurrentB) return@sortedWith 1
        if (!isCurrentA && isCurrentB) return@sortedWith -1

        val orderA = branchDepth[a.timelineId.orEmpty()] ?: 0
        val orderB = branchDepth[b.timelineId.orEmpty()] ?: 0
        if (orderA != orderB) return@sortedWith orderA.compareTo(orderB)

        val floorA = a.floorNumber ?: 0
        val floorB = b.floorNumber ?: 0
        if (floorA != floorB) return@sortedWith floorA.compareTo(floorB)

        a.timestamp.compareTo(b.timestamp)
    }
}

/**
 * `timelineMemory.ts:226 formatMessageWithFloorAndTag`：`[A1·学园线] #4 正文`。
 * ⚠️ `floorNumber` 为 0 时 TS 视为假值、不写楼号前缀，这里同样处理。
 */
fun formatMessageWithFloorAndTag(
    message: ChatMessage,
    allTimelines: List<TimelineBranch>,
    /** 正文。ContextBuilder 传入**已过正则**的正文，使时间线模式下正则仍生效（T08 方案 A，偏离 TS）。 */
    content: String = messageContentForAi(message),
): String {
    val floor = message.floorNumber
    val floorPrefix = if (floor != null && floor != 0) "#$floor" else ""
    val timeline = allTimelines.firstOrNull { it.id == message.timelineId }

    var tagDisplay = COMMON_CODE_TAG
    val codeTag = message.codeTag
    if (!codeTag.isNullOrEmpty()) {
        val desc = message.descriptionTag
        tagDisplay = if (!desc.isNullOrEmpty()) "$codeTag·$desc" else codeTag
    } else if (timeline != null) {
        val code = timeline.codeTag?.ifEmpty { null } ?: timeline.tag.ifEmpty { null } ?: COMMON_CODE_TAG
        val desc = timeline.descriptionTag?.ifEmpty { null } ?: timeline.name
        tagDisplay = if (desc.isNotEmpty() && desc != code) "$code·$desc" else code
    }

    return "[$tagDisplay] $floorPrefix $content".trim()
}

/* =============================================================================================
 * T17：时间线树 + 编号分配 + 意图识别 + 剧情推进 + 树提示词
 * =========================================================================================== */

/**
 * `timelineMemory.ts:3 TIMELINE_COLORS` 的一个色板条目。
 *
 * ⚠️ `bg` / `border` / `text` / `badge` / `ring` 在 TS 里是 **Tailwind class 字符串**
 *   （`bg-indigo-500/10 dark:bg-indigo-500/20`）。UI 层直接消费它们；纯逻辑层不解释这些值。
 */
data class TimelineColorConfig(
    val id: String,
    val name: String,
    val bg: String,
    val border: String,
    val text: String,
    val badge: String,
    val ring: String,
)

/** `timelineMemory.ts:3`：7 套标签色板，**顺序即优先级**（默认取第 0 项 indigo）。 */
val TIMELINE_COLORS: List<TimelineColorConfig> = listOf(
    TimelineColorConfig(
        id = "indigo", name = "靛青",
        bg = "bg-indigo-500/10 dark:bg-indigo-500/20",
        border = "border-indigo-500/30",
        text = "text-indigo-600 dark:text-indigo-400",
        badge = "bg-indigo-600 text-white",
        ring = "ring-indigo-500/40",
    ),
    TimelineColorConfig(
        id = "emerald", name = "翠绿",
        bg = "bg-emerald-500/10 dark:bg-emerald-500/20",
        border = "border-emerald-500/30",
        text = "text-emerald-600 dark:text-emerald-400",
        badge = "bg-emerald-600 text-white",
        ring = "ring-emerald-500/40",
    ),
    TimelineColorConfig(
        id = "amber", name = "琥珀",
        bg = "bg-amber-500/10 dark:bg-amber-500/20",
        border = "border-amber-500/30",
        text = "text-amber-600 dark:text-amber-400",
        badge = "bg-amber-600 text-white",
        ring = "ring-amber-500/40",
    ),
    TimelineColorConfig(
        id = "rose", name = "玫瑰",
        bg = "bg-rose-500/10 dark:bg-rose-500/20",
        border = "border-rose-500/30",
        text = "text-rose-600 dark:text-rose-400",
        badge = "bg-rose-600 text-white",
        ring = "ring-rose-500/40",
    ),
    TimelineColorConfig(
        id = "cyan", name = "青蓝",
        bg = "bg-cyan-500/10 dark:bg-cyan-500/20",
        border = "border-cyan-500/30",
        text = "text-cyan-600 dark:text-cyan-400",
        badge = "bg-cyan-600 text-white",
        ring = "ring-cyan-500/40",
    ),
    TimelineColorConfig(
        id = "purple", name = "幻紫",
        bg = "bg-purple-500/10 dark:bg-purple-500/20",
        border = "border-purple-500/30",
        text = "text-purple-600 dark:text-purple-400",
        badge = "bg-purple-600 text-white",
        ring = "ring-purple-500/40",
    ),
    TimelineColorConfig(
        id = "teal", name = "松石",
        bg = "bg-teal-500/10 dark:bg-teal-500/20",
        border = "border-teal-500/30",
        text = "text-teal-600 dark:text-teal-400",
        badge = "bg-teal-600 text-white",
        ring = "ring-teal-500/40",
    ),
)

/** `timelineMemory.ts:69 getTimelineColorConfig`：未命中（含 `null`/空串）一律退回第 0 项。 */
fun getTimelineColorConfig(colorId: String?): TimelineColorConfig =
    TIMELINE_COLORS.firstOrNull { it.id == colorId } ?: TIMELINE_COLORS.first()

/**
 * `timelineMemory.ts:76 createDefaultMainTimeline`。
 * ⚠️ `createdAt` / `updatedAt` 由调用方传入（TS 用 `Date.now()` 两次调用；commonMain 没有对等物）。
 */
fun createDefaultMainTimeline(now: Long): TimelineBranch = TimelineBranch(
    id = DEFAULT_MAIN_TIMELINE_ID,
    name = "通用世界观",
    tag = COMMON_CODE_TAG,
    codeTag = COMMON_CODE_TAG,
    descriptionTag = "通用世界观",
    visible = true,
    color = "indigo",
    description = "核心共享世界观与通用事实设定。所有分支均继承此通用基础。",
    plotSummary = "世界观基石由此展开，通用信息在所有分支共享。",
    keyMilestones = listOf("世界观核心基石建立"),
    messageIds = emptyList(),
    createdAt = now,
    updatedAt = now,
)

/**
 * `timelineMemory.ts:98 allocateTimelineCodeTag`：分配 `A1` / `A12` / `B1` 这类编号标签。
 *
 * ⚠️ 根分支的字面量循环在 TS 里看着像 bug（每轮 `num` 都从 1 重来、命中即 return），
 *   但这就是规格：字母按 A→Z 逐个试探，**第一个字母立即返回**（`existingCodes` 里没有
 *   该字母 + 1 时给 `A1`，有 `A1` 则给 `A2`）。**不要「修」成遍历所有字母找最小空闲**。
 */
fun allocateTimelineCodeTag(
    existingTimelines: List<TimelineBranch>,
    parentBranch: TimelineBranch? = null,
): String {
    val existingCodes = existingTimelines.mapNotNull { it.codeTag?.ifEmpty { null } }.toSet()

    if (parentBranch == null ||
        parentBranch.id == DEFAULT_MAIN_TIMELINE_ID ||
        parentBranch.codeTag == COMMON_CODE_TAG
    ) {
        // 根分支：分配 A1, B1, C1...（⚠️ 首个字母即返回，见上方注释）
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for (letter in letters) {
            var num = 1
            while ("$letter$num" in existingCodes) num++
            return "$letter$num"
        }
        return "T${existingTimelines.size}"
    }

    // 子分支：基于父分支分配（例如 A1 -> A11, A12, A13...）
    val parentCode = parentBranch.codeTag?.ifEmpty { null } ?: "A1"
    var subIndex = 1
    while ("$parentCode$subIndex" in existingCodes) subIndex++
    return "$parentCode$subIndex"
}

/** `TimelineDetectionResult.action` 的两个字面量值。 */
enum class TimelineAction(val wireValue: String) {
    CREATE_NEW("create_new"),
    SWITCH_EXISTING("switch_existing"),
}

/** `timelineMemory.ts:255 TimelineDetectionResult`。 */
data class TimelineDetectionResult(
    val triggered: Boolean,
    val action: TimelineAction? = null,
    val targetTimelineId: String? = null,
    val newTimelineName: String? = null,
    val newTimelineTag: String? = null,
    val initialDescription: String? = null,
)

/** `timelineMemory.ts:284`：正则元字符转义（JS `/[.*+?^${}()|[\]\\]/g` → `'\\$&'`）。 */
private val REGEX_META_CHARS: Regex = Regex("[.*+?^${'$'}()|\\[\\]\\\\]")

/**
 * `timelineMemory.ts:284`：正则元字符转义（JS `/[.*+?^${}()|[\]\\]/g` → `'\\$&'`）。
 * ⚠️ `internal` 而非 `private`：[TimelineEditing] 剥编号前缀时用的是同一条转义。
 */
internal fun escapeRegex(value: String): String = REGEX_META_CHARS.replace(value) { "\\${it.value}" }

/**
 * `timelineMemory.ts:267 detectTimelineIntent`：识别用户输入里的分支切换/新建声明。
 *
 * 三种触发方式（**顺序不可换**）：
 *   1. 整串等于某个 `codeTag`（忽略大小写）或匹配「切换/回到/…」句式 → 切到已有分支；
 *   2. 「这是一个时间线…」句式 → 新建；
 *   3. `【时间线：xxx】` 方括号标签 → 命中已有则切换，否则新建。
 */
fun detectTimelineIntent(text: String, existingTimelines: List<TimelineBranch>): TimelineDetectionResult {
    val clean = text.trim()
    if (clean.isEmpty()) return TimelineDetectionResult(triggered = false)

    // 1. 用户输入编号直接切换（例如输入 "A12"、"切到A1"、"进入B1"）
    for (t in existingTimelines) {
        val codeTag = t.codeTag
        if (!codeTag.isNullOrEmpty() && clean.equals(codeTag, ignoreCase = true)) {
            return TimelineDetectionResult(
                triggered = true, action = TimelineAction.SWITCH_EXISTING, targetTimelineId = t.id,
            )
        }

        val escapedTag = escapeRegex(codeTag?.ifEmpty { null } ?: t.tag)
        val escapedName = escapeRegex(t.name)
        val switchPattern = Regex(
            "(?:切换|回到|切回|转到|进入|重回|位于|处于)(?:到|回)?(?:【|\\[)?(?:时间线|分支)?" +
                "(?:[:：\\s])*(?:$escapedTag|$escapedName)(?:】|\\])?",
            RegexOption.IGNORE_CASE,
        )
        if (switchPattern.containsMatchIn(clean)) {
            return TimelineDetectionResult(
                triggered = true, action = TimelineAction.SWITCH_EXISTING, targetTimelineId = t.id,
            )
        }
    }

    // 2. 检测显式声明："这是一个时间线..." / "这是一个新时间线..."
    val explicitMatch = Regex(
        "(?:这|此)(?:个|条)?(?:是|算作|作为|当成|开启)?(?:一个|一条|一个全新的?)?" +
            "时间线[：:\\s]*(.*)",
        RegexOption.IGNORE_CASE,
    ).find(clean)
    if (explicitMatch != null) {
        val remainder = explicitMatch.groupValues[1].trim()
        val nameMatch = Regex("^(?:叫|名为|称作|：|:)?\\s*([^\\s,，。]+)").find(remainder)
        val branchName = nameMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
            ?: remainder.take(15).ifEmpty { "新时间线" }
        return TimelineDetectionResult(
            triggered = true,
            action = TimelineAction.CREATE_NEW,
            newTimelineName = branchName,
            newTimelineTag = branchName.take(4),
            initialDescription = remainder.ifEmpty { "根据用户意图开启的新世界观时间线。" },
        )
    }

    // 3. 检测方括号标签声明：【时间线：xxx】
    val bracketMatch = Regex("[【\\[](?:时间线|分支|IF线)[：:\\s]*([^】\\]]+)[】\\]]", RegexOption.IGNORE_CASE)
        .find(clean)
    if (bracketMatch != null) {
        val rawName = bracketMatch.groupValues[1].trim()
        val existing = existingTimelines.firstOrNull {
            it.name == rawName || it.tag == rawName || it.codeTag == rawName
        }
        return if (existing != null) {
            TimelineDetectionResult(
                triggered = true, action = TimelineAction.SWITCH_EXISTING, targetTimelineId = existing.id,
            )
        } else {
            TimelineDetectionResult(
                triggered = true,
                action = TimelineAction.CREATE_NEW,
                newTimelineName = rawName,
                newTimelineTag = rawName.take(4),
                initialDescription = "通过标签【$rawName】明确创建的新时间线。",
            )
        }
    }

    return TimelineDetectionResult(triggered = false)
}

/** `timelineMemory.ts:354 enrichTimelineProgression` 的返回值。 */
data class TimelineProgression(
    val updatedDescription: String,
    val updatedPlotSummary: String,
    val newMilestones: List<String>,
)

/** `timelineMemory.ts:363` 里程碑关键词，**顺序即优先级**（第一个命中的 `break`）。 */
private val MILESTONE_KEYWORDS: List<String> =
    listOf("决定", "导致", "发现", "改变", "爆发", "杀死", "揭开", "建立")

/**
 * `timelineMemory.ts:350 enrichTimelineProgression`：按对话推进分支描述与里程碑。
 *
 * ⚠️ `userMessage.length < 50` 决定从哪一侧取句子（短用户消息从用户话里找，否则从 AI 回复里找）。
 * ⚠️ `updatedPlotSummary` 只保留**最后 8 条**；`newMilestones` 只保留**最后 10 条**。
 */
fun enrichTimelineProgression(
    branch: TimelineBranch,
    userMessage: String,
    aiReply: String,
): TimelineProgression {
    val currentDesc = branch.description
    val currentPlot = branch.plotSummary
    val milestones = branch.keyMilestones.orEmpty().toMutableList()

    for (kw in MILESTONE_KEYWORDS) {
        if (userMessage.contains(kw) || aiReply.contains(kw)) {
            val source = if (userMessage.length < 50) userMessage else aiReply
            val sentence = source.split(Regex("[。！？\\n]")).firstOrNull { it.contains(kw) }
            if (sentence != null && sentence.trim().length > 4) {
                val cleanSnippet = sentence.trim().take(36)
                if (cleanSnippet !in milestones) {
                    milestones += cleanSnippet
                    break
                }
            }
        }
    }

    val previousSnippets =
        if (currentPlot.isNotEmpty()) currentPlot.split("\n").toMutableList() else mutableListOf()
    previousSnippets += "用户: ${userMessage.take(30)}... | 回复: ${aiReply.take(35)}..."
    val updatedPlotSummary = previousSnippets.takeLast(8).joinToString("\n")

    var updatedDescription = currentDesc
    if (milestones.isNotEmpty() && !currentDesc.contains("关键里程碑")) {
        updatedDescription = "$currentDesc\n• 本分支因果节点（当前共 ${milestones.size} 个关键里程碑）。"
    }

    return TimelineProgression(
        updatedDescription = updatedDescription.trim(),
        updatedPlotSummary = updatedPlotSummary.trim(),
        newMilestones = milestones.takeLast(10),
    )
}

/**
 * `timelineMemory.ts:399 buildTimelineTreePromptContext`：发给 AI 的时间线树背景提示。
 *
 * ⚠️ 不可见分支（`visible === false`）的任何元数据都不得进入 system/context：活动分支不可见时
 *    **整段返回空串**；父分支与其他平行分支也各自按 `visible !== false` 过滤。
 * ⚠️ `allMessages` 在 TS 里被声明但从未使用（历史遗留）。Kotlin 侧保留形参以对齐签名，
 *    由 `@Suppress("UNUSED_PARAMETER")` 抑制未使用警告（零警告红线）。
 * ⚠️ 整段字符串**逐字照抄**，一个标点都不能改（模型理解上下文的锚点）。
 */
@Suppress("UNUSED_PARAMETER")
fun buildTimelineTreePromptContext(
    activeTimeline: TimelineBranch,
    allTimelines: List<TimelineBranch>,
    allMessages: List<ChatMessage>,
): String {
    // 不可见分支的任何元数据都不得进入 system/context。
    if (activeTimeline.visible == false) return ""

    val parentId = activeTimeline.parentId
    val parentTimeline = if (!parentId.isNullOrEmpty()) {
        allTimelines.firstOrNull { it.id == parentId && it.visible != false }
    } else null

    val otherTimelines = allTimelines.filter { it.id != activeTimeline.id && it.visible != false }
    val otherSection = if (otherTimelines.isNotEmpty()) {
        otherTimelines.joinToString("\n") { t ->
            "- 分支【${t.codeTag?.ifEmpty { null } ?: t.tag}·${t.descriptionTag?.ifEmpty { null } ?: t.name}】: ${t.description.take(60)}"
        }
    } else {
        "暂无其他可见平行分支。"
    }

    val activeCode = activeTimeline.codeTag?.ifEmpty { null } ?: activeTimeline.tag
    val activeDesc = activeTimeline.descriptionTag?.ifEmpty { null } ?: activeTimeline.name
    val parentNode = if (parentTimeline != null) {
        "分化自父分支【${parentTimeline.codeTag?.ifEmpty { null } ?: parentTimeline.tag}·${parentTimeline.descriptionTag?.ifEmpty { null } ?: parentTimeline.name}】"
    } else {
        "根主线节点（通用）"
    }

    return """
# 【时间线记忆树 · 分支重组上下文】
后台记忆整理中间件已自动重排：
- 当前激活分支：【$activeCode · $activeDesc】
- 分支层级溯源：$parentNode
- 核心区分信息/世界观特征：
  ${activeTimeline.description}
- 本时间线因果剧情沉淀：
  ${activeTimeline.plotSummary.ifEmpty { "本分支剧情展开中..." }}

【其他可见平行时间线（用于对比辨析，保持分立，切勿混淆事实）】：
$otherSection

【AI回复规范 (PRD 六)】：
1. 你的回复必须严格基于当前时间线【$activeCode】的因果发展；
2. “描述标签”是仅供 AI 识别当前分支特征、事件、行为或代号的短标签，不是分支名称，也不得修改编号、名称、父子关系或可见性；
3. 当用户明确要求补充或修改当前时间线的描述标签时，你必须先根据当前可见信息提出一个具体方案和简短理由，并在普通回复中自然向用户汇报；若本轮剧情产生了显著的新事件、行为、特征或代号，你也可以主动提出方案，但不得宣称已经写入；
4. 提出方案时，在回复末尾附加一次且仅一次变更申请：<!--WATCHWORLD_TIMELINE_DESCRIPTION_TAG:{"descriptionTag":"建议的新描述标签","reason":"申请理由"}-->；JSON 必须严格合法，且只能包含 descriptionTag 与 reason 两个非空字符串字段；
5. 你无权直接修改描述标签。应用只会把合法申请保存为待用户批准的申请；只有用户在时间线窗口点击同意后，应用才执行写入。申请只能作用于系统指定的当前分支【$activeCode】，协议中禁止携带 branchId 或要求修改其他分支；
6. 无需变更时禁止输出申请标记。普通回复不得解释、引用或展示该控制标记，也不要把自然语言“新分支建议”当成申请协议；
7. 若用户提出开启新分歧线，可以用普通自然语言提出建议，但不得借此修改现有其他分支；
8. 若用户需要总结与正文分离，可用 <总结>概括</总结> 与 <正文>详情</正文> 标签包裹，配合正则系统精简历史 Token。
"""
}
