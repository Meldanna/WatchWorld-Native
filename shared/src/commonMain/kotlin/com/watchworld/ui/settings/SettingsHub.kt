package com.watchworld.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.watchworld.model.UiMode
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Book
import compose.icons.tablericons.Cloud
import compose.icons.tablericons.Message
import compose.icons.tablericons.Palette
import compose.icons.tablericons.Settings
import compose.icons.tablericons.UserCheck

/**
 * 设置弹窗内的**二级子页面**。
 *
 * ⚠️ **设置里的每一张卡都是独立弹窗，不在这个枚举里**：参考版 `SettingsHubModal.tsx:43-47`
 *    的 `handle()` 是「先关掉设置总入口，再打开目标弹窗」（注释原文「避免多层级弹窗叠压」），
 *    即设置总入口在目标弹窗打开时**并不在场**；关闭后由 `closeWithReturn`
 *    （`App.tsx:280-287`）重新打开设置总入口。
 *    原生对应：`App.kt` 里的 `settingChild` / `settingsReturnDestination` 一对状态。
 *
 * 所以本文件现在只负责**卡片列表本身** —— [SettingsPage] 枚举保留的是
 * 「卡片文案 + 图标」这一份数据（顺序照参考版），点击后由调用方开独立弹窗。
 */
/**
 * 设置总入口的**卡片数据**（顺序照参考版 `SettingsHubModal.tsx:81-177`）。
 *
 * ⚠️ 枚举里**没有**「正则库」：正则库改由侧边栏底部「正则库」格独立打开
 *    （用户 2026-10-10 定案）。参考版的 SettingsHubModal 里有这张卡，属于有意分歧。
 */
enum class SettingsPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: SettingsDestination,
) {
    PREFERENCES("默认与偏好", "默认模型、新窗口默认配置与启动行为", TablerIcons.Settings, SettingsDestination.PREFERENCES),
    DISPLAY("文本设置", "气泡样式、界面字体、模型名、时间、耗时与 Token 显示", TablerIcons.Message, SettingsDestination.DISPLAY),
    THEME("主题", "配色方案与气泡风格", TablerIcons.Palette, SettingsDestination.THEME),
    PROMPT("提示词库", "编辑保存不同提示词，按需取用", TablerIcons.Book, SettingsDestination.PROMPT),
}

/**
 * 设置总入口里每张卡点开后要显示的**独立弹窗**（`agentOpen` 那套的统称）。
 *
 * 为什么是一个枚举而不是一堆 Boolean：需求 §三 要求「关闭子级必须回到发起它的那一层」，
 * 一堆互不相干的布尔一旦同时为真就说不清该回哪儿 —— 与 `WindowFunctionDestination` 同一套理由。
 */
enum class SettingsDestination { AGENT, PREFERENCES, DISPLAY, THEME, PROMPT, BACKUP }

/**
 * 独立弹窗入口卡的文案（参考版 `SettingsHubModal.tsx:89-90` / `:173-174`）。
 * 它们**不在** [SettingsPage] 里：点开的是独立弹窗，不是内联子页。
 */
private const val AGENT_CARD_TITLE = "Agent 管理"
private const val AGENT_CARD_SUBTITLE = "新建/编辑顾问，绑定 MCP 与 Skill，配置记忆"
private const val BACKUP_CARD_TITLE = "备份与导入导出"
private const val BACKUP_CARD_SUBTITLE = "WebDAV 云同步、全量 JSON 导出与恢复"

@Composable
fun SettingsHub(
    state: SettingsUiState,
    onOpen: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── 入口卡片列表 ──
    // 顺序照参考版 `SettingsHubModal.tsx`：Agent 管理（独立弹窗入口）在最前、
    // 备份与导入导出（独立弹窗入口）在最后；中间几项同参考版顺序。
    // ⚠️ 参考版中间还有一张「正则库」卡，本项目**有意删除**（改由侧边栏底格打开）。
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsCard(TablerIcons.UserCheck, AGENT_CARD_TITLE, AGENT_CARD_SUBTITLE) { onOpen(SettingsDestination.AGENT) }
        Spacer(Modifier.height(WwSize.cardListGap))
        SettingsPage.entries.forEach { entry ->
            SettingsCard(entry.icon, entry.title, entry.subtitle) { onOpen(entry.destination) }
            Spacer(Modifier.height(WwSize.cardListGap))
        }
        SettingsCard(TablerIcons.Cloud, BACKUP_CARD_TITLE, BACKUP_CARD_SUBTITLE) { onOpen(SettingsDestination.BACKUP) }
    }
}

/**
 * 设置入口卡片：左图标 + 标题，说明收成标题旁的注释图标（单行，框高 = [WwPad.featureBox] ×2 + 标题行）。
 *
 * ⚠️ 参考版 `SettingsHubModal.tsx` 每张卡右侧有 `ChevronRight`，这里**有意去掉**：
 *    用户 2026-10-10 定案「设置弹窗的右箭头全部删除」。别再按参考版补回来。
 */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        // 显式取主题模块1 的淡底，与顶栏图标徽章（FeatureDialog 的 `accentSoft`）同一个色。
        // ⚠️ 不传 `color` 时 M3 取 `MaterialTheme.colorScheme.surface`＝中性灰白 `#F8FAFC`，
        //    框内就成了「灰白块」、与主题脱节（用户 2026-10-13 报的现象）。
        color = WwSurfaces.accentSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, WwSurfaces.cardBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = WwPad.featureBox),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, title, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = WwType.title)
            Spacer(Modifier.width(4.dp))
            // 说明收成注释图标（用户 2026-10-11：副标题占两行 → 单行 + ⓘ）。
            InfoHint(subtitle)
            Spacer(Modifier.weight(1f))
        }
    }
}

