package com.watchworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import compose.icons.tablericons.X

/**
 * 各功能面板通用的一行搜索框。
 *
 * 参考版这些工具条里的 `<input>` 是 `px-2.5 py-1.5 rounded-xl`（约 30dp 高）。
 * M3 的 `OutlinedTextField` 强制 `defaultMinSize(minHeight = 56.dp)`，压不到这个高度，
 * 铺出来会比参考版高近一倍 —— 所以这里自绘 `Surface + BasicTextField`。
 *
 * ⚠️ 需要带 label 的大输入框（表单里的那种）仍用 `OutlinedTextField`；这个只给「工具条内联搜索」。
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(WwSurfaces.inset, shape)
            .border(1.dp, WwSurfaces.cardBorderSubtle, shape)
            .padding(horizontal = 10.dp, vertical = WwSize.searchFieldVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = WwType.body,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontSize = WwType.body,
                                lineHeight = WwSize.compactTextLineHeight,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = "清除搜索",
                modifier = Modifier.size(13.dp).clickable { onValueChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
