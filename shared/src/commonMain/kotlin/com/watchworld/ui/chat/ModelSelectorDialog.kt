package com.watchworld.ui.chat

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ModelFavorite
import com.watchworld.ui.components.SearchField
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.settings.FeatureDialog
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Star

/**
 * 完整模型选择器（对标参考版 `ModelSelectorModal.tsx`）。
 *
 * 与输入栏内嵌下拉（`ModelSwitchPill`）的分工：
 * - 内嵌下拉：常用切换，按供应商分组折叠、**只列有 apiKey 的供应商**；
 * - 本弹窗：跨**全部**供应商搜索（模型名 / 供应商名）+ 「收藏」子页筛选。
 *
 * ### 与源码逐项对齐（`ModelSelectorModal.tsx:28-242`）
 * - 标题「模型选择器」，宽度 `max-w-2xl` = 672dp、`max-h-[80vh]`；
 * - 工具条：搜索框（占位 `搜索模型名或供应商...`）+ 右侧「收藏」切换钮
 *   （打开时主色实底白字 + 星标实心，关闭时凹槽底 + 次级文字）；
 * - 列表按供应商分段，段标题是小号徽章（`surface-2` 底）；
 * - 模型行右侧是**心形**收藏钮（参考版用 `Heart`，与内嵌下拉的星标区分开）；
 * - 空态两文案：`还没有收藏任何模型` / `未找到匹配的模型`。
 *
 * ⚠️ **不过滤 apiKey**：参考版这个弹窗列的是全部 provider 的 models，
 *    只有输入栏内嵌下拉才过滤（`ChatInput.tsx:143-145`）。照抄，别"顺手统一"。
 */
@Composable
fun ModelSelectorDialog(
    providers: List<ApiProviderConfig>,
    activeProviderId: String,
    activeModel: String,
    favorites: List<ModelFavorite>,
    onSelectModel: (String, String) -> Unit,
    onToggleFavorite: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }

    val favoriteKeys = remember(favorites) {
        favorites.map { favKey(it.providerId, it.modelName) }.toSet()
    }

    // 过滤链照源码顺序：先收藏筛选、再关键词（顺序只影响空态文案的选择）。
    val groups = remember(providers, favoriteKeys, query, favoritesOnly) {
        val needle = query.trim().lowercase()
        providers.mapNotNull { provider ->
            val models = provider.models.filter { model ->
                val fav = !favoritesOnly || favKey(provider.id, model) in favoriteKeys
                val hit = needle.isEmpty() ||
                    model.lowercase().contains(needle) ||
                    provider.name.lowercase().contains(needle)
                fav && hit
            }
            if (models.isEmpty()) null else provider to models
        }
    }

    FeatureDialog(
        title = "模型选择器",
        onDismiss = onDismiss,
        maxWidth = 672.dp,
        heightFraction = 0.8f,
    ) {
        // ── 工具条：搜索框 + 「收藏」子页切换（`ModelSelectorModal.tsx:131-170`）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索模型名或供应商...",
                modifier = Modifier.weight(1f),
            )
            // 统一走 `WwTag`（设置里的分段 / 小按钮同规格），不再自绘 10×8 的胶囊。
            WwTag(
                text = "收藏",
                onClick = { favoritesOnly = !favoritesOnly },
                background = if (favoritesOnly) MaterialTheme.colorScheme.primary else WwSurfaces.inset,
                contentColor = if (favoritesOnly) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIcon = TablerIcons.Star,
            )
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        // ── 列表：按供应商分段 ──
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (favoritesOnly) "还没有收藏任何模型" else "未找到匹配的模型",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            groups.forEach { (provider, models) ->
                WwTag(
                    text = provider.name,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.height(WwSize.cardListGap))
                models.forEach { model ->
                    ModelSelectorRow(
                        modelName = model,
                        isActive = provider.id == activeProviderId && model == activeModel,
                        isFavorite = favKey(provider.id, model) in favoriteKeys,
                        onSelect = { onSelectModel(provider.id, model); onDismiss() },
                        onToggleFavorite = { onToggleFavorite(provider.id, model) },
                    )
                }
                Spacer(Modifier.height(WwSize.cardListGap))
            }
        }
    }
}

/** 收藏命中键：`providerId|modelName`。 */
private fun favKey(providerId: String, modelName: String): String = providerId + '|' + modelName

/** 单个模型行：模型名（当前项补 `(当前)`）+ 右侧心形收藏钮。 */
@Composable
private fun ModelSelectorRow(
    modelName: String,
    isActive: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = modelName,
            fontSize = WwType.body,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onSelect).padding(vertical = WwSize.boxVerticalPadding),
        )
        if (isActive) {
            Text(
                text = "(当前)",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
        }
        // 心形收藏钮（参考版这里是 `Heart`，与内嵌下拉的星标形成区分）。
        Icon(
            imageVector = TablerIcons.Heart,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite)
                .padding(7.dp),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
    }
}
