package com.watchworld.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Book
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.Clipboard
import compose.icons.tablericons.Code
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Search
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Stars
import compose.icons.tablericons.User
import com.watchworld.ui.components.WwModalScrim
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType

/**
 * 当前窗口功能底部弹层。
 *
 * 对标参考项目 WindowFunctionPanel：全屏遮罩、贴底圆角面板、拖拽短横和三列功能卡片。
 * 子功能的导航状态由 App 持有，因此子弹窗关闭后可以恢复本弹层。
 *
 * ⚠️ 参考版面板里有「双框提示词」一项，这里**有意去掉**（用户 2026-10-10 定案）。
 *    别再按参考版补回来：双框提示词的入口保留在输入栏的「提示词」按钮
 *    （`App.kt` 里 `onOpenPrompts` → `WindowFunctionDestination.DualBoxPrompt`）。
 *
 * ⚠️ 面板项以定案为准：**知识库是新加的一格**（用户 2026-10-10）。参考版没有这一格，
 *    它占的是第一行剩下的空位（前文总结 / 全文搜索 之后），开到「本窗口知识库」（窗口形态）。
 */
@Composable
fun WindowFunctionSheet(
    onDismiss: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenWindowWorld: () -> Unit,
    onAnalyzeRole: () -> Unit,
    onOpenWindowApi: () -> Unit,
    onOpenSessionActions: () -> Unit,
    onOpenWindowRegex: () -> Unit,
    onOpenAvatarSettings: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        WindowFunctionSheetContent(
            onDismiss = onDismiss,
            onOpenSummary = onOpenSummary,
            onOpenSearch = onOpenSearch,
            onOpenKnowledge = onOpenKnowledge,
            onOpenWindowWorld = onOpenWindowWorld,
            onAnalyzeRole = onAnalyzeRole,
            onOpenWindowApi = onOpenWindowApi,
            onOpenSessionActions = onOpenSessionActions,
            onOpenWindowRegex = onOpenWindowRegex,
            onOpenAvatarSettings = onOpenAvatarSettings,
        )
    }
}

@Composable
internal fun WindowFunctionSheetContent(
    onDismiss: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenWindowWorld: () -> Unit,
    onAnalyzeRole: () -> Unit,
    onOpenWindowApi: () -> Unit,
    onOpenSessionActions: () -> Unit,
    onOpenWindowRegex: () -> Unit,
    onOpenAvatarSettings: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：全应用统一款式（毛玻璃雾面，不压黑），并登记自己让主内容去模糊。
        WwModalScrim(onDismiss = onDismiss)

        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = WwSurfaces.sheetBase,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                // 内容区垂直内边距（嵌套链 段行 10 → 卡片 12 → 内容区 14 → 外壳 16）。
                // 底部留白 ×2（用户 2026-10-13：面板底部边缘离最后一排卡片太近）。
                modifier = Modifier.fillMaxWidth().padding(
                    start = 16.dp, end = 16.dp, top = WwPad.content, bottom = WwPad.content * 2,
                ),
            ) {
                Surface(
                    modifier = Modifier.size(width = 40.dp, height = 4.dp).align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ) {}
                Spacer(Modifier.height(WwSize.cardListGap))

                WindowFunctionRow(
                    items = listOf(
                        WindowFunctionItem("前文总结", TablerIcons.ChartBar, onOpenSummary),
                        WindowFunctionItem("本地知识库", TablerIcons.Book, onOpenKnowledge),
                        WindowFunctionItem("全文搜索", TablerIcons.Search, onOpenSearch),
                    ),
                )
                Spacer(Modifier.height(WwSize.cardListGap))
                WindowFunctionRow(
                    items = listOf(
                        WindowFunctionItem("本窗口世界观", TablerIcons.FileText, onOpenWindowWorld),
                        WindowFunctionItem("角色心理分析", TablerIcons.Stars, onAnalyzeRole),
                        WindowFunctionItem("窗口 API 参数", TablerIcons.Settings, onOpenWindowApi),
                    ),
                )
                Spacer(Modifier.height(WwSize.cardListGap))
                WindowFunctionRow(
                    items = listOf(
                        WindowFunctionItem("窗口导出删除", TablerIcons.Clipboard, onOpenSessionActions),
                        WindowFunctionItem("本窗口正则", TablerIcons.Code, onOpenWindowRegex),
                        WindowFunctionItem("头像设置", TablerIcons.User, onOpenAvatarSettings),
                    ),
                )
            }
        }
    }
}

private data class WindowFunctionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun WindowFunctionRow(items: List<WindowFunctionItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            WindowFunctionCard(
                item = item,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - items.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WindowFunctionCard(
    item: WindowFunctionItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = item.onClick),
        shape = RoundedCornerShape(12.dp),
        color = WwSurfaces.card,
        tonalElevation = 0.dp,
        // 非胶囊的框补一根模块2 框线（用户 2026-10-13）。
        border = androidx.compose.foundation.BorderStroke(1.dp, WwSurfaces.cardBorder),
    ) {
        Column(
            // 卡片垂直内边距（比内容区少 2，比字段行多 2）。
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = WwPad.card),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(WwSize.cardListGap))
            Text(
                text = item.label,
                fontSize = WwType.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
