package com.watchworld.domain

import com.watchworld.model.AnalysisVisibility
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.AppPreferences
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role

/**
 * 心理动力学角色关系分析（T20）—— `api.ts:664 analyzeRoleRelationships` +
 * `App.tsx:1924 handleTriggerRoleAnalysis` 的纯逻辑部分。
 *
 * 这是一次**独立的一次性模型调用**（不写入聊天工具循环、不注入工具），
 * 结果作为一条 `isAnalysis = true` 的 assistant 消息插入当前窗口。
 */

/** `api.ts:672` 的分析 system 提示词（逐字）。 */
const val ROLE_ANALYSIS_SYSTEM_INSTRUCTION: String =
    "你是一位精通角色心理学、人际动力学和剧情推演的资深世界观角色分析顾问。\n" +
        "你的任务是根据当前时间线重排后的上下文记录，输出一份专业、客观、逻辑严密的「角色关系与心理动态深度分析报告」。\n" +
        "请以以下结构输出 Markdown：\n" +
        "1. 核心人物心理动态分析（核心驱动力、真实防御机制、隐秘软肋）\n" +
        "2. 双边/多边关系动态与权势平衡（控制与制约、信任度与博弈策略）\n" +
        "3. 隐性张力积累与烈度演化趋势（在当前分支中的烈度变化细节）\n" +
        "4. 潜在引爆点与剧情推演预测（后续可能激发或缓解的事件触发点）\n\n" +
        "请保持精细深入，直击人性深层，莫流于表面的套话。"

/** 分析消息的 `codeTag` / `descriptionTag`（`App.tsx:1960-1962`）。 */
const val ROLE_ANALYSIS_CODE_TAG: String = "分析"
const val ROLE_ANALYSIS_DESCRIPTION_TAG: String = "角色心理博弈"

/** 分析报告归档到的世界观分组 id（`App.tsx:1912` 硬编码 `wg-analysis`）。 */
const val ROLE_ANALYSIS_GROUP_ID: String = "wg-analysis"

/** 生成中的占位正文（`App.tsx:1957`）。 */
const val ROLE_ANALYSIS_PLACEHOLDER: String = "正在启动角色关系分析 Agent 进行深层心理学研判..."

/**
 * 剔除「仅自己可见」的角色关系分析报告。
 *
 * 徽章文案是「仅自己可见 (不占AI上下文)」—— 本函数让这句话**真正成立**：
 * 被隐藏的分析报告不得进入送往模型的任何上下文（正式对话、前文总结、下一次分析）。
 *
 * ⚠️ 只有 `analysisVisibility` **明确为 [AnalysisVisibility.HIDDEN]** 才剔除：
 *    `null`（老存档没这个字段）= 可见，与 UI 的 `?? VISIBLE` 一致。
 * ⚠️ 报告本体仍留在会话里（UI 照常展示），只是不发给模型。
 */
fun dropHiddenAnalysisMessages(messages: List<ChatMessage>): List<ChatMessage> =
    messages.filterNot { it.isAnalysis == true && it.analysisVisibility == AnalysisVisibility.HIDDEN }

/** 当前分支的 `(code, name)`：`codeTag || tag || '通用'`、`descriptionTag || name || '主线'`。 */
data class BranchInfo(val code: String, val name: String)

fun currentBranchInfo(session: ChatSession): BranchInfo {
    val timelines = session.timelines.orEmpty()
    val active = timelines.firstOrNull { it.id == session.activeTimelineId } ?: timelines.firstOrNull()
    val code = active?.codeTag?.takeIf { it.isNotEmpty() }
        ?: active?.tag?.takeIf { it.isNotEmpty() }
        ?: "通用"
    val name = active?.descriptionTag?.takeIf { it.isNotEmpty() }
        ?: active?.name?.takeIf { it.isNotEmpty() }
        ?: "主线"
    return BranchInfo(code, name)
}

/**
 * 分析用的上下文快照（`App.tsx:1938-1945`）。
 * 格式：`[<codeTag||通用>] #<楼号||1> <用户|AI>: <正文>`，条与条之间换行。
 */
fun buildRoleAnalysisContext(messages: List<ChatMessage>): String = messages.joinToString("\n") { message ->
    val text = if (message.role == Role.ASSISTANT) {
        message.versions.getOrNull(message.currentVersionIndex)?.content?.takeIf { it.isNotEmpty() }
            ?: message.versions.firstOrNull()?.content?.takeIf { it.isNotEmpty() }
            ?: message.content
    } else {
        message.content
    }
    val tag = message.codeTag?.takeIf { it.isNotEmpty() } ?: "通用"
    val who = if (message.role == Role.USER) "用户" else "AI"
    "[$tag] #${message.floorNumber ?: 1} $who: $text"
}

/** 分析请求的 user 正文（`api.ts:682`）。 */
fun buildRoleAnalysisUserPrompt(branch: BranchInfo, contextText: String): String =
    "【当前时间线分支】：${branch.code} · ${branch.name}\n\n" +
        "【当前时间线重排上下文记录】：\n$contextText\n\n" +
        "请基于上述记录，生成一份角色关系与深层心理学动态分析报告。"

/**
 * 调用失败时的兜底报告（`api.ts:717`）。
 * ⚠️ Web 版的 `analyzeRoleRelationships` **自己 catch** 并返回这段文案，因此上层几乎不会抛。
 */
fun buildRoleAnalysisFallback(branch: BranchInfo): String =
    "### 📊 角色关系分析\n" +
        "**分析分支**：`${branch.code} · ${branch.name}`\n\n" +
        "分析功能暂不可用，请检查 API 配置。"

/**
 * 分析所用模型（`App.tsx:1998-2002`）：
 * `session.model || preferences.defaultModels.analysis || provider.defaultModel`。
 */
fun resolveAnalysisModel(
    session: ChatSession,
    preferences: AppPreferences,
    provider: ApiProviderConfig?,
): String = sequenceOf(
    session.model,
    preferences.defaultModels.analysis,
    provider?.defaultModel,
).firstOrNull { !it.isNullOrBlank() }.orEmpty()

/** 归档为世界观文档时的标题（`App.tsx:1907`）。 */
fun buildAnalysisDocTitle(floorNumber: Int?, codeTag: String?): String =
    "心理博弈分析·#${floorNumber ?: 1} (${codeTag ?: ROLE_ANALYSIS_CODE_TAG})"
