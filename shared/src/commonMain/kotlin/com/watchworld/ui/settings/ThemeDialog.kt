package com.watchworld.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.CustomTheme
import com.watchworld.model.ThemePalette
import com.watchworld.platform.currentTimeMillis
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwType
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.components.dashedRoundedBorder
import com.watchworld.ui.theme.COLOR_SWATCHES
import com.watchworld.ui.theme.ThemeFamily
import com.watchworld.ui.theme.ThemeSource
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.builtInThemes
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronLeft
import compose.icons.tablericons.Palette
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash

/**
 * 主题弹窗（对标参考版 `ThemeModal.tsx`，317 行）——设置总入口的**独立弹窗**。
 *
 * ### 两层
 * - **主题列表**：13 套内置（圆点 = 主色，右侧两个小圆点 = 模块 1 / 模块 2 预览）+
 *   自定义主题（可删）+ 底部虚线「DIY 新建主题」。
 * - **DIY 表单**：主题名 + 两个调色盘（模块 1 主色 / 模块 2 辅助色）+ 取消 / 保存。
 *   顶栏的徽章位变成「← 返回」，与参考版一致（`ThemeModal.tsx:186-196`）。
 *
 * ⚠️ 参考版**没有**「明暗模式」这一节 —— 明暗由顶栏 / 侧边栏的日/月按钮切换，
 *    主题弹窗只管配色方案。原生旧 `ThemePanel` 里那组「浅色 / 深色」按钮是自造，
 *    本次删除（明暗入口仍在侧边栏与顶栏）。
 */
@Composable
fun ThemeDialog(
    palette: ThemePalette,
    customThemes: List<CustomTheme>,
    onSelectTheme: (ThemePalette) -> Unit,
    onSaveCustomTheme: (CustomTheme) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // null = 主题列表；非 null = DIY 表单草稿（参考版 `ThemeModal.tsx:135`）。
    var draft by remember { mutableStateOf<DiyDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = draft

    FeatureDialog(
        title = if (current == null) "UI 主题色彩切换" else "DIY 主题",
        subtitle = if (current == null) {
            "个性化定制气泡配色、高亮主题与交互风格"
        } else {
            "模块 1 管气泡与按钮，模块 2 管焦点与选中态"
        },
        icon = if (current == null) TablerIcons.Palette else null,
        onDismiss = onDismiss,
        maxWidth = 448.dp,
        heightFraction = 0.88f,
        // 列表层是「← 返回」+ 列表；参考版这里是标题栏的徽章位换成返回按钮，
        // 原生统一用 `titleContent` 表达（外壳的顶栏结构不变）。
        titleContent = if (current == null) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp).clickable { draft = null; error = null },
                        shape = RoundedCornerShape(10.dp),
                        color = WwSurfaces.inset,
                        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                TablerIcons.ChevronLeft,
                                contentDescription = "返回主题列表",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("DIY 主题", fontSize = WwType.title, fontWeight = WwType.titleWeight)
                        Text(
                            "模块 1 管气泡与按钮，模块 2 管焦点与选中态",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        footer = if (current == null) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                    error?.let {
                        Text(it, fontSize = WwType.hint, color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(Modifier.weight(1f))
                        WwSecondaryButton("取消", onClick = { draft = null; error = null })
                        WwPrimaryButton(
                            text = "保存",
                            enabled = current.name.isNotBlank(),
                            onClick = {
                                val name = current.name.trim()
                                if (name.isEmpty()) {
                                    error = "请先给主题起个名字"
                                    return@WwPrimaryButton
                                }
                                val saved = CustomTheme(
                                    id = "custom_${currentTimeMillis().toString(36)}",
                                    name = name,
                                    m1 = current.m1,
                                    m2 = current.m2,
                                    createdAt = currentTimeMillis(),
                                )
                                onSaveCustomTheme(saved)
                                onSelectTheme(saved.id)
                                draft = null
                                error = null
                            },
                        )
                    }
                }
            }
        },
    ) {
        if (current == null) {
            ThemeList(
                palette = palette,
                customThemes = customThemes,
                onSelectTheme = onSelectTheme,
                onDeleteCustomTheme = onDeleteCustomTheme,
                onStartDiy = {
                    error = null
                    draft = DiyDraft(name = "", m1 = COLOR_SWATCHES.first(), m2 = COLOR_SWATCHES.first())
                },
            )
        } else {
            DiyForm(
                draft = current,
                onChange = { draft = it; error = null },
            )
        }
    }
}

/** DIY 表单草稿（参考版 `ThemeModal.tsx:135` 的 `draft` 状态）。 */
private data class DiyDraft(val name: String, val m1: String, val m2: String)

/** 主题列表层（参考版 `ThemeModal.tsx:281-320`）。 */
@Composable
private fun ThemeList(
    palette: ThemePalette,
    customThemes: List<CustomTheme>,
    onSelectTheme: (ThemePalette) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onStartDiy: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        builtInThemes().forEach { (id, theme) ->
            ThemeRow(
                name = theme.name,
                m1 = theme.primaryHex,
                m2 = m2Preview(theme.source, theme.primaryHex),
                selected = palette == id,
                onSelect = { onSelectTheme(id) },
                onDelete = null,
            )
        }

        if (customThemes.isNotEmpty()) {
            Text(
                "自定义主题",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        customThemes.forEach { custom ->
            ThemeRow(
                name = custom.name,
                m1 = custom.m1,
                m2 = custom.m2,
                selected = palette == custom.id,
                onSelect = { onSelectTheme(custom.id) },
                onDelete = { onDeleteCustomTheme(custom.id) },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .dashedRoundedBorder(WwSurfaces.cardBorderSubtle)
                .clickable(onClick = onStartDiy)
                .padding(vertical = WwPad.card),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.Plus, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("DIY 新建主题", fontSize = WwType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 一套主题一行：主色圆点（选中时内含 ✓）+ 名称 + 主色 HEX + 右侧两个小圆点 + 可选删除。
 * 参考版 `ThemeModal.tsx:87-137`。
 */
@Composable
private fun ThemeRow(
    name: String,
    m1: String,
    m2: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) WwSurfaces.cardSelected else WwSurfaces.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) WwSurfaces.cardBorder else WwSurfaces.cardBorderSubtle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = parseHexOrFallback(m1),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            TablerIcons.Check,
                            contentDescription = "当前主题",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontSize = WwType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    m1,
                    fontSize = WwType.hint,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 两个 12dp 预览圆点：模块 1 / 模块 2（参考版 `ThemeDots`）。
            Box(Modifier.size(12.dp).background(parseHexOrFallback(m1), CircleShape))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(12.dp).background(parseHexOrFallback(m2), CircleShape))
            if (onDelete != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    TablerIcons.Trash,
                    contentDescription = "删除主题",
                    modifier = Modifier.size(13.dp).clickable(onClick = onDelete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** DIY 表单层（参考版 `ThemeModal.tsx:239-279`）。 */
@Composable
private fun DiyForm(draft: DiyDraft, onChange: (DiyDraft) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        WwTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = "主题名称",
            placeholder = "给这套配色起个名字",
        )
        SwatchSection(
            label = "模块 1 · 主色",
            hint = "气泡、回底/回顶键、主按钮、开关、滑块、发送键",
            selected = draft.m1,
            onSelect = { onChange(draft.copy(m1 = it)) },
        )
        SwatchSection(
            label = "模块 2 · 辅助色",
            hint = "输入框焦点边框与焦点环、各种选中态浅底",
            selected = draft.m2,
            onSelect = { onChange(draft.copy(m2 = it)) },
        )
    }
}

/** 调色托盘：一行 12dp 圆点，选中的放大加环（参考版 `SwatchGrid`）。 */
@Composable
private fun SwatchSection(
    label: String,
    hint: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            InfoHint(hint)
            Spacer(Modifier.weight(1f))
            Text(
                selected,
                fontSize = WwType.hint,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // ⚠️ 32 个色点用 `chunked` 手动换行：`FlowRow` 在 Compose MP 1.12 仍是实验 API，
        //    这里只要「自动换行」这一件事，手动分行更省事也更可控。
        COLOR_SWATCHES.chunked(8).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { hex ->
                    val active = selected.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(if (active) 16.dp else 12.dp)
                            .background(parseHexOrFallback(hex), CircleShape)
                            .clickable { onSelect(hex) },
                    )
                }
            }
        }
    }
}

/**
 * 预设主题的模块 2 预览色（参考版 `m2Preview`，`ThemeModal.tsx:64-76`）：
 * 与 `resolveThemeVars` 里「同色系降饱和」的印象一致；DIY 主题直接用它的 `m2`。
 */
private fun m2Preview(source: ThemeSource, primaryHex: String): String = when (source) {
    is ThemeSource.Custom -> source.m2
    is ThemeSource.SaturationLevel -> when (source.family) {
        ThemeFamily.EMERALD -> "#A3BFB2"
        ThemeFamily.BLUE -> "#9DAEC4"
        ThemeFamily.VIOLET -> "#AFA3C4"
        ThemeFamily.ROSE -> "#C9A0A8"
        ThemeFamily.AMBER -> "#C7AE8E"
        ThemeFamily.CYAN -> "#9DBDC4"
        ThemeFamily.SLATE -> "#9AA3B2"
    }
}

/** `#RRGGBB` → `Color`；解析不了就退回灰色（色点只是预览，不值得为此抛错）。 */
private fun parseHexOrFallback(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return Color.Gray
    val value = cleaned.toLongOrNull(16) ?: return Color.Gray
    return Color(0xFF000000L or value)
}
