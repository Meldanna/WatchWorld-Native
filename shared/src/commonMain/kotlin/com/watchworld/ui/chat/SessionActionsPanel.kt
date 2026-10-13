package com.watchworld.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watchworld.domain.buildSessionExportJson
import com.watchworld.domain.buildSessionExportMarkdown
import com.watchworld.model.ChatSession
import com.watchworld.platform.copyTextToClipboard
import com.watchworld.platform.saveTextFile
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Trash

private enum class SessionActionsTab { EXPORT, DELETE }

/**
 * 窗口导出删除面板（对标 Web 版 `SessionTextModal.tsx`）。
 *
 * 两个 Tab：
 * - 导出：MD 纯文本预览 + 「复制全文 / 下载 Markdown / 下载 JSON」。
 *   ⚠️ 下载的 JSON 与侧边栏「导入对话」兼容（`domain/SessionExchange.kt` 的配套格式）。
 * - 删除：显示警告并二次确认。
 *
 * ⚠️ 导出文本与 `SessionExchange` 的 `buildSessionExportMarkdown` 同源：MD 用 `·` 分隔，
 *    `## #楼 · 我|AI|系统 · [tag]` —— 老版本这里误用 `.`，导出的文件侧边栏读不回来。
 */
@Composable
fun SessionActionsPanel(
    session: ChatSession,
    onDeleteSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(SessionActionsTab.EXPORT) }
    var copied by remember { mutableStateOf(false) }

    val exportMarkdown = remember(session.id, session.messages) { buildSessionExportMarkdown(session) }
    val exportJson = remember(session.id, session.messages) { buildSessionExportJson(session) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Tab 切换 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = WwPad.content),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tab == SessionActionsTab.EXPORT,
                onClick = { tab = SessionActionsTab.EXPORT },
                label = { Text("导出") },
            )
            FilterChip(
                selected = tab == SessionActionsTab.DELETE,
                onClick = { tab = SessionActionsTab.DELETE },
                label = { Text("删除本窗口") },
            )
        }

        when (tab) {
            SessionActionsTab.EXPORT -> {
                WwTextField(
                    value = exportMarkdown,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                    minHeight = 300.dp,
                )
                Spacer(Modifier.height(WwSize.cardListGap))
                Text(
                    "MD 供阅读/分享；JSON 可在侧边栏「导入对话」重建本窗口。",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(WwSize.cardListGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WwPrimaryButton(
                        text = if (copied) "已复制" else "复制全文",
                        onClick = {
                            copyTextToClipboard(exportMarkdown)
                            copied = true
                        },
                        enabled = !copied,
                    )
                    WwSecondaryButton(
                        text = "下载 Markdown",
                        onClick = { saveTextFile(exportFileName(session, "md"), exportMarkdown) },
                    )
                    WwSecondaryButton(
                        text = "下载 JSON",
                        onClick = { saveTextFile(exportFileName(session, "json"), exportJson) },
                    )
                }
            }

            SessionActionsTab.DELETE -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = WwPad.card)) {
                        Text(
                            "即将删除窗口",
                            fontSize = WwType.title,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(WwSize.cardListGap))
                        Text(
                            "「${session.title.ifBlank { "未命名对话" }}」",
                            fontSize = WwType.body,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(WwSize.cardListGap))
                        Text(
                            "此操作不可撤销。窗口内的所有消息、总结与设置将被永久删除。",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(WwSize.cardListGap))
                var confirmDelete by remember { mutableStateOf(false) }
                if (!confirmDelete) {
                    WwPrimaryButton(
                        text = "确认删除本窗口",
                        onClick = { confirmDelete = true },
                    )
                } else {
                    Text(
                        "真的要删除吗？",
                        fontSize = WwType.body,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(WwSize.cardListGap))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WwTag(
                            text = "确定删除",
                            onClick = {
                                onDeleteSession()
                                onDismiss()
                            },
                            background = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                        WwTag(
                            text = "取消",
                            onClick = { confirmDelete = false },
                            background = WwSurfaces.inset,
                        )
                    }
                }
            }
        }
    }
}

/** 导出文件名：窗口标题净化后 + 扩展名（参考版 `handleDownload` 的去特殊字符）。 */
private fun exportFileName(session: ChatSession, extension: String): String {
    val safe = (session.title.ifBlank { "窗口" }).replace(Regex("""[\\/:*?"<>|]"""), "_")
    return "$safe.$extension"
}