package com.watchworld.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.WebDavConfig
import com.watchworld.platform.pickTextFile
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Cloud
import compose.icons.tablericons.Database
import compose.icons.tablericons.Download
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Upload
import compose.icons.tablericons.X

/** 两个 Tab（参考版 `BackupSyncModal.tsx:36` 的 `activeTab`）。 */
private enum class BackupTab { WEBDAV, BACKUP }

/**
 * 备份与导入导出弹窗（对标参考版 `BackupSyncModal.tsx`，349 行）。
 *
 * ### 结构（行号指向参考版）
 * - 顶栏（109-128）：徽章 + `备份与导入导出` + 副标题 `WebDAV 跨设备云同步，以及全量 JSON 的导出与恢复` + ✕
 * - Tab 行（130-154）：`WebDAV 云同步` / `导入与导出`，选中项主色下划线
 * - WebDAV 页（157-297）：说明卡 → 三个字段 → 自动同步开关 → 危险开关（含密钥）→ 结果条 → 四个按钮
 * - 导入导出页（300-345）：导出卡 + 导入卡，各带 InfoHint 与主按钮
 *
 * ### 与参考版的有意分歧
 * 1. 外壳用 [FeatureDialog]（项目统一弹窗壳），不是自绘 `fixed inset-0`；
 *    Tab 行按参考版**通栏**贴在外壳顶栏之下，所以 `contentPadding = 0.dp`。
 * 2. 参考版用 `window.alert()` 报告导入结果（成功还会顺手关弹窗），原生改为**页内结果条**
 *    （原生没有 alert；关掉就看不清结果）。结果文案照抄参考版（94、97 行）。
 * 3. 参考版「立即导出」是浏览器下载；原生走 `saveTextFile`（平台保存框），
 *    文件名规则见 [com.watchworld.domain.backupFileName]。
 * 4. 参考版没有「粘贴 JSON 导入」——旧原生面板里的那个文本框**本次删除**（不是参考版的东西）。
 */
@Composable
fun BackupSyncDialog(
    config: WebDavConfig?,
    webDavNotice: BackupNotice?,
    busy: Boolean,
    importNotice: BackupNotice?,
    onSaveConfig: (WebDavConfig) -> Unit,
    onTestConnection: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onSaveConfigApplied: () -> Unit,
    /** 参数是「备份里要不要带 API 密钥与 WebDAV 密码」。 */
    onExport: (Boolean) -> Unit,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cfg = config ?: WebDavConfig()
    var tab by remember { mutableStateOf(BackupTab.WEBDAV) }

    FeatureDialog(
        title = "备份与导入导出",
        subtitle = "WebDAV 跨设备云同步，以及全量 JSON 的导出与恢复",
        icon = TablerIcons.Database,
        onDismiss = onDismiss,
        maxWidth = 576.dp,
        heightFraction = 0.9f,
        // ⚠️ 通栏 + 自管滚动：Tab 行按参考版贴在顶栏之下**不随内容滚动**
        //    （参考版的 tabs 在 `overflow-y-auto` 之外，只有下面的正文滚），
        //    所以关掉外壳的滚动，正文区自己滚。
        //
        // ⚠️ 高度**自适应**（用户 2026-10-12：备份弹窗默认高度太大）：
        //    原来是 `fixedHeight = true`（钉死 90% 高），WebDAV 这种短表单也占满一屏。
        //    现在交给 `heightIn(max = 0.9×可视区)`：内容短 ⇒ 弹窗随内容，长了才封顶并内部滚。
        contentPadding = 0.dp,
        scrollable = false,
        fixedHeight = false,
    ) {
        // ⚠️ `FeatureDialog` 的 content 不是 `ColumnScope`，正文里用不了 `weight` ——
        //    自己套一层撑满高度的 Column，让 Tab 行定高、正文吃掉剩余高度。
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Tab 行（参考版 130-154）：贴在顶栏之下、正文之上，不参与滚动 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BackupTab.entries.forEach { entry ->
                    BackupTabButton(
                        text = when (entry) {
                            BackupTab.WEBDAV -> "WebDAV 云同步"
                            BackupTab.BACKUP -> "导入与导出"
                        },
                        icon = when (entry) {
                            BackupTab.WEBDAV -> TablerIcons.Cloud
                            BackupTab.BACKUP -> TablerIcons.Download
                        },
                        selected = tab == entry,
                        onClick = { tab = entry },
                    )
                }
            }
            HorizontalDivider(color = WwSurfaces.cardBorderSubtle)

            // ── 正文：只有这一块滚（参考版 `flex-1 overflow-y-auto p-4`）──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = WwPad.content),
            ) {
                when (tab) {
                    BackupTab.WEBDAV -> WebDavTab(
                        cfg = cfg,
                        notice = webDavNotice,
                        busy = busy,
                        onSaveConfig = onSaveConfig,
                        onTestConnection = onTestConnection,
                        onPush = onPush,
                        onPull = onPull,
                        onSaveConfigApplied = onSaveConfigApplied,
                    )

                    BackupTab.BACKUP -> LocalBackupTab(
                        notice = importNotice,
                        onExport = onExport,
                        onImport = onImport,
                    )
                }
            }
        }
    }
}


/**
 * Tab 按钮：选中 = 主色文字 + 主色下划线（参考版 `border-b-2 border-indigo-600`）。
 *
 * ⚠️ 下划线必须用 `width(IntrinsicSize.Max)` 把整列的宽**钉到内容宽**。
 *    直接写 `fillMaxWidth()` 的话，在父 `Row` 里它会吃掉整行剩余宽度 ——
 *    第二个 tab 于是被挤成「一列单字竖排」（预览截图里那个症状）。
 */
@Composable
private fun BackupTabButton(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(13.dp), tint = color)
            Text(text, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = color, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/** WebDAV 页（参考版 157-297）。 */
@Composable
private fun WebDavTab(
    cfg: WebDavConfig,
    notice: BackupNotice?,
    busy: Boolean,
    onSaveConfig: (WebDavConfig) -> Unit,
    onTestConnection: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onSaveConfigApplied: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        // ── 说明卡（参考版 159-165，主色浅底）──
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = WwSurfaces.accentSoft,
            border = BorderStroke(1.dp, WwSurfaces.cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TablerIcons.Cloud, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("WebDAV 同步支持", fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                InfoHint(
                    "这是观界客户端的数据同步功能，不是聊天模型可调用的工具。默认纯本地存储；" +
                        "可配置坚果云、Nextcloud、Alist、自建 NAS 等 WebDAV 连接。" +
                        "数据变动时自动防抖同步，启动时自动拉取最新数据，冲突以时间戳较新为准。",
                )
            }
        }

        // ── 三个字段（参考版 167-206）──
        WwTextField(
            value = cfg.url,
            onValueChange = { onSaveConfig(cfg.copy(url = it.trim())) },
            label = "WebDAV 服务器地址 *",
            placeholder = "https://dav.jianguoyun.com/dav/ 或 https://your-nas/remote.php/dav/files/user/",
            fontSize = WwType.hint,
            monospace = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            WwTextField(
                value = cfg.username,
                onValueChange = { onSaveConfig(cfg.copy(username = it.trim())) },
                label = "用户名 / 账号 *",
                placeholder = "如：your_account@gmail.com",
                modifier = Modifier.weight(1f),
            )
            WwTextField(
                value = cfg.password.orEmpty(),
                onValueChange = { onSaveConfig(cfg.copy(password = it.trim())) },
                label = "应用密码 / Token *",
                placeholder = "坚果云应用专用密码或Token",
                fontSize = WwType.hint,
                monospace = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
        }

        // ── 自动同步开关（参考版 208-221）──
        SettingToggleRow(
            title = "数据变动时自动同步",
            hint = "发消息或新建时间线分支后自动防抖同步到云盘",
            checked = cfg.autoSync,
            onChange = { onSaveConfig(cfg.copy(autoSync = it)) },
        )

        // ── 危险开关：备份内含密钥（参考版 223-236，rose 色系）──
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "在手动备份时包含 API 配置",
                        fontSize = WwType.body,
                        lineHeight = WwSize.compactTextLineHeight,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    InfoHint(
                        "打开后，「立即同步到云端」会把你的 API 密钥与 WebDAV 密码一并上传到远端。" +
                            "建议只在信任的个人云盘使用，或者保持关闭（恢复后需重新填入密钥）。",
                    )
                }
                Checkbox(
                    checked = cfg.includeApiConfig == true,
                    onCheckedChange = { onSaveConfig(cfg.copy(includeApiConfig = it)) },
                )
            }
        }

        // ── 结果条（参考版 238-253）：只有操作过才出现 ──
        notice?.let { BackupNoticeBar(it, spinning = it.level == BackupNoticeLevel.BUSY || busy) }

        // ── 四个按钮（参考版 255-294）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SoftActionButton("测试连接", null, MaterialTheme.colorScheme.onSurface, !busy, onTestConnection)
            SoftActionButton(
                "立即同步到云端",
                TablerIcons.Upload,
                MaterialTheme.colorScheme.primary,
                !busy,
                onPush,
            )
            SoftActionButton(
                "从云端拉取",
                TablerIcons.Download,
                MaterialTheme.colorScheme.primary,
                !busy,
                onPull,
            )
            Spacer(Modifier.weight(1f))
            WwPrimaryButton("保存配置", onClick = onSaveConfigApplied, enabled = !busy)
        }
    }
}

/**
 * 三色操作按钮（参考版 `px-3.5 py-2 rounded-xl` 的软底按钮）：
 * 底 = 语义色 10% 叠加，文字与图标 = 语义色。主操作走 [WwPrimaryButton]。
 */
@Composable
private fun SoftActionButton(
    text: String,
    icon: ImageVector?,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = if (enabled) 0.10f else 0.05f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = WwSize.buttonVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = tint.copy(alpha = if (enabled) 1f else 0.4f))
        }
        Text(text, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = tint.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** 带说明角标的一行开关（参考版 `p-3 rounded-xl` 的灰底卡片）。 */
@Composable
private fun SettingToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WwSurfaces.inset,
        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                InfoHint(hint)
            }
            Checkbox(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** 操作结果条（参考版 238-253）：成功绿 / 进行中蓝 / 失败红，左侧一枚状态图标。 */
@Composable
private fun BackupNoticeBar(notice: BackupNotice, spinning: Boolean) {
    val tint = when (notice.level) {
        BackupNoticeLevel.SUCCESS -> MaterialTheme.colorScheme.primary
        BackupNoticeLevel.BUSY -> MaterialTheme.colorScheme.primary
        BackupNoticeLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    val angle = if (notice.level == BackupNoticeLevel.BUSY && spinning) rememberSpinAngle() else 0f
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (notice.level) {
                    BackupNoticeLevel.SUCCESS -> TablerIcons.Check
                    BackupNoticeLevel.BUSY -> TablerIcons.Refresh
                    BackupNoticeLevel.ERROR -> TablerIcons.X
                },
                contentDescription = null,
                modifier = Modifier.size(13.dp).rotate(angle),
                tint = tint,
            )
            Spacer(Modifier.width(8.dp))
            Text(notice.message, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = tint)
        }
    }
}

/** 只在「进行中」存在的无限旋转角（参考版 `animate-spin`）。 */
@Composable
private fun rememberSpinAngle(): Float {
    val transition = rememberInfiniteTransition(label = "backup-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "backup-spin-angle",
    )
    return angle
}

/**
 * 导入与导出页（参考版 300-345 的两张卡，原生补成**真备份**）。
 *
 * - 导出卡：说明 + **「备份中包含 API 密钥与密码」开关**（默认关）+ 导出按钮。
 *   开关关时 Provider 的 `apiKey` 与 WebDAV 密码被擦成空串，与参考版
 *   `exportAllData({ redactSecrets })` 同一语义（`storage.ts:439-448`）；
 *   打开则原样带走，换设备导入即可直接用。
 * - 导入卡：选文件 → **二次确认** → 整库恢复；结果显示「恢复了什么」的收据。
 *
 * ⚠️ 参考版把密钥开关放在 **WebDAV 页**（只服务「立即同步到云端」）；
 *    原生在导出卡上也给一个 —— **本地导出同样需要这个选择**。
 *    两处写的是同一个 `webdavConfig.includeApiConfig` 字段，语义一致：
 *    要不要把密钥带出本机。
 */
@Composable
private fun LocalBackupTab(
    notice: BackupNotice?,
    onExport: (Boolean) -> Unit,
    onImport: (String) -> Unit,
) {
    var includeApiKeys by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        // ── 导出卡 ──
        BackupActionCard(
            title = "一键本地数据全量导出 (JSON)",
            titleIcon = TablerIcons.Download,
            hint = "包含所有会话（连同每条消息、时间线分支与前文总结）、世界观文档与分组、" +
                "知识库、正则、MCP、Skill、提示词、顾问、Provider、分组、主题配色、明暗、" +
                "显示设置、应用偏好与模型收藏。单文件备份，完全自主掌控。",
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (includeApiKeys) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                } else {
                    WwSurfaces.inset
                },
                border = BorderStroke(
                    1.dp,
                    if (includeApiKeys) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    } else {
                        WwSurfaces.cardBorderSubtle
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "备份中包含 API 密钥与 WebDAV 密码",
                            fontSize = WwType.body,
                            color = if (includeApiKeys) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        InfoHint(
                            "打开后导出的 JSON 里带 Provider 的 API Key 与 WebDAV 密码，换设备导入即可直接用；" +
                                "关闭则擦成空串，恢复后需要重新填。" +
                                "备份文件若要放到网盘或发给别人，建议保持关闭。",
                        )
                    }
                    Checkbox(checked = includeApiKeys, onCheckedChange = { includeApiKeys = it })
                }
            }
            Spacer(Modifier.height(WwSize.cardListGap))
            WwPrimaryButton("立即导出全量 JSON 文件", onClick = { onExport(includeApiKeys) })
        }

        // ── 导入卡 ──
        BackupActionCard(
            title = "导入 JSON 恢复全量数据",
            titleIcon = TablerIcons.Upload,
            hint = "选择此前「导出」出来的备份文件，把**整库**恢复成备份里的内容：" +
                "会话、时间线、世界观、知识库、正则、MCP、Skill、提示词、顾问、Provider、" +
                "主题与全部设置。备份里没有的键保持不动。",
        ) {
            Text(
                "⚠️ 恢复会覆盖当前全部数据，且不可撤销。",
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(WwSize.cardListGap))
            WwSecondaryAction("选择文件并恢复") {
                // 系统文件框是模态的，必须在 UI 线程（点击回调）里挑完再交给上层落库。
                val picked = pickTextFile(listOf("json")) ?: return@WwSecondaryAction
                pendingRestore = picked.content
            }
        }

        notice?.let { BackupNoticeBar(it, spinning = it.level == BackupNoticeLevel.BUSY) }
    }

    // ── 覆盖确认（参考版直接覆盖，原生更保守）──
    pendingRestore?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("确认恢复全量数据") },
            text = {
                Text(
                    "当前所有会话、世界观、知识库、正则与设置都会被备份文件里的内容完全替换，" +
                        "且无法撤销。建议先导出一份当前数据再做恢复。",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
                        confirmButton = {
                WwTag(
                    text = "覆盖并恢复",
                    onClick = { onImport(content); pendingRestore = null },
                    contentColor = MaterialTheme.colorScheme.error,
                )
            },
            dismissButton = {
                WwTag(text = "取消", onClick = { pendingRestore = null })
            },
        )
    }
}

/** 导入导出页的一张卡：标题行（图标 + 标题 + InfoHint）+ 正文（按钮）。 */
@Composable
private fun BackupActionCard(
    title: String,
    titleIcon: ImageVector,
    hint: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WwSurfaces.card,
        border = BorderStroke(1.dp, WwSurfaces.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = WwPad.card),
            verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(titleIcon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                InfoHint(hint)
            }
            content()
        }
    }
}

/**
 * 导入卡里的次按钮（参考版是 `bg-slate-200` 的实底灰按钮，
 * 比全局 `WwSecondaryButton` 的描边样式更「重」一档）。
 */
@Composable
private fun WwSecondaryAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .background(WwSurfaces.inset, shape)
            // 非胶囊的框补一根模块2 框线（用户 2026-10-13）。
            .border(1.dp, WwSurfaces.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = WwSize.buttonVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(TablerIcons.Upload, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(text, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurface)
    }
}
