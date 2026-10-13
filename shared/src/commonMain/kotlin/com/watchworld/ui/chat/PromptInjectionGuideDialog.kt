package com.watchworld.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Stack
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType

/**
 * Prompt injection order guide dialog.
 *
 * Shows the 10-step system instruction assembly order, matching
 * the reference project's PromptInjectionGuideModal.
 *
 * Data source: ContextBuilder.build() in domain/ContextBuilder.kt
 * and buildSystemInstruction() -- the order is hardcoded here to
 * match the reference project exactly.
 */
@Composable
fun PromptInjectionGuideDialog(
    onDismiss: () -> Unit,
) {
    FeatureDialog(
        title = "提示词注入讲解",
        onDismiss = onDismiss,
        // Web `PromptInjectionGuideModal`: max-w-3xl (768dp) + max-h-[92vh]
        maxWidth = 768.dp,
        heightFraction = 0.92f,
    ) {
        Text(
            text = "查看每轮请求的 system 拼装顺序、消息筛选和缓存去重规则",
            fontSize = WwType.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(WwSize.cardListGap))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                TablerIcons.Stack, null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "最终 system 注入顺序",
                fontSize = WwType.title,
            )
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        val steps = listOf(
            "Agent 角色人格设定" to "当前 Agent 的身份、语气和基础行为规则。",
            "当前窗口指令" to "双框中的“本身的提示词”，不是用户最新消息；与人格冲突时以窗口指令为准。",
            "Agent 长期记忆" to "当前 Agent 持续生效的长期记忆。",
            "世界观正典设定" to "当前窗口启用的正式世界观文档。",
            "已挂载 MCP 服务能力" to "仅注入全局启用且当前 Agent 已绑定的服务与工具。",
            "激活的专业 Skill" to "当前窗口启用的专业工作方法和行为要求。",
            "外部参考资料·知识库" to "全局启用且当前窗口已勾选的参考资料。",
            "通用世界观基石缓存" to "点击“推入通用”后形成；已缓存来源消息不会再作为普通历史重复发送。",
            "前文总结" to "按时间顺序注入；已总结楼层的原文会从发送上下文中移除。",
            "时间线树上下文" to "当前活动分支的树状提示词，包含分支关系、剧情脉络和当前进展。",
        )

        Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
            steps.forEachIndexed { index, (title, desc) ->
                InjectionStepCard(
                    number = index + 1,
                    title = title,
                    description = desc,
                )
            }
        }

        Spacer(Modifier.height(WwSize.cardListGap))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("我知道了")
            }
        }
    }
}

@Composable
private fun InjectionStepCard(
    number: Int,
    title: String,
    description: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WwSurfaces.card,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = WwPad.card),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = number.toString(),
                        fontSize = WwType.body,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    fontSize = WwType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
