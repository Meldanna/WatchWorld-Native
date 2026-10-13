package com.watchworld.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import com.watchworld.ui.components.WwModalScrim
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType

/**
 * AI 时间线操作说明弹窗（对标 `TimelineModal.tsx` 的 `isAiGuideOpen` 弹层，:897-935）。
 *
 * 内容就是**当前激活分支实际会收到的那段规则文本** —— 直接喂
 * [com.watchworld.domain.buildTimelineTreePromptContext] 的真实输出，不做二次加工，
 * 这样「模型看到的」与「界面上写的」永远同源。
 *
 * ⚠️ 本弹窗只叠在**时间线管理器**之上（需求 §七）：关闭后回到管理器，不关闭管理器。
 *    因此它是一个独立的 Dialog 窗口，不嵌在管理器内部。
 */
@Composable
fun TimelineGuideDialog(
    guide: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：全应用统一款式（毛玻璃雾面，不压黑），并登记自己让主内容去模糊。
            WwModalScrim(onDismiss = onDismiss)

            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = WwSurfaces.dialogBase,
                    border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                ) {
                    Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 时间线操作说明", fontSize = WwType.title, fontWeight = WwType.titleWeight)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "这是当前激活分支实际发送给窗口模型的时间线规则。",
                                fontSize = WwType.hint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(TablerIcons.X, contentDescription = "关闭")
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (guide.isBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    "时间线功能未开启，或当前没有可用分支，因此不会向模型发送时间线操作说明。",
                                    fontSize = WwType.hint,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        } else {
                            Surface(
                                color = WwSurfaces.inset,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                            ) {
                                Text(
                                    text = guide.trim(),
                                    fontSize = WwType.hint,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp),
                                )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
