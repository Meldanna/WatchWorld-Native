package com.watchworld.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watchworld.ui.components.HintBounds
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwModalScrim
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwTheme
import com.watchworld.ui.theme.WwType
import com.watchworld.ui.theme.mixedTitle
import compose.icons.TablerIcons
import compose.icons.tablericons.X

/**
 * 功能弹窗的统一外壳（对标 Web 版各 Modal 的 `fixed inset-0 … max-w-* max-h-[9xvh] rounded-2xl`）。
 *
 * ### 尺寸（⚠️ 之前这里是错的）
 * Web 版是 `w-full max-w-lg` + `max-h-[90vh] flex flex-col`：**小内容按内容高**，
 * 只有撑到 `90vh` 才封顶、之后由内部滚动。旧实现用 `fillMaxHeight(0.85f)` 把高度
 * **钉死**成屏幕的 85%，所以每个弹窗都一样高 —— 这次改成 `heightIn(max = …)`，
 * 高度由内容决定、到上限才截断。
 *
 * 宽度按源码逐弹窗传入（Tailwind 的 `max-w-*` ≈ dp）：
 * `max-w-lg` = 512dp · `max-w-xl` = 576dp · `max-w-2xl` = 672dp。
 *
 * ### 形状与描边
 * `rounded-2xl` = 16dp（不是 M3 的 `shapes.extraLarge` = 28dp），另加 1dp `border-default` 描边。
 *
 * ### 顶栏
 * 左侧：可选 32dp 主色图标徽章 + 标题（说明默认收成标题旁的注释图标 ⓘ）+ 关闭 ✕。
 *
 * ### 高度：内容自适应 vs 固定高
 * 默认 `heightIn(max = …)`：内容小则随内容。以下两种情况要传 `fixedHeight = true`
 * 或给 `footer`，此时弹窗**钉死**成 `heightFraction` 的高度（Web 的 `h-[9xvh]`）：
 * - Web 里写死高度、靠内部双栏各自滚动的弹窗（世界观 `h-[90vh]`、知识库 `h-[92vh]`）；
 * - 需要「中间滚动 + 底部固定操作区」三段式结构的编辑弹窗（见
 *   `memory/ui-replication-requirements.md` §七）。
 *
 * @param maxWidth 弹窗最大宽度，按各弹窗在 Web 里的 `max-w-*` 传
 *   （md=448 · lg=512 · xl=576 · 2xl=672 · 3xl=768 · 4xl=896 dp）。
 * @param heightFraction 高度上限占可视区的比例，对应 Web 的 `max-h-[8xvh]` / `h-[9xvh]`
 *   （搜索类 0.85、常规 0.9、宽面板 0.92）。
 * @param contentPadding 内容区的**水平**内边距。默认 16dp（Web 的 `p-4`）；内容自带
 *   「通栏工具条」的弹窗（如双框提示词）传 `0.dp`，否则工具条会被缩进到边框内。
 * @param contentPaddingVertical 内容区的**垂直**内边距（面板底板 ↔ 内部最近框）。
 *   默认 `WwPad.dialogInset`（3sp）；通栏布局（工具条 / 底栏贴着面板边）传 0。
 * @param icon 顶栏左侧图标（Web 的 `w-8 h-8 rounded-xl` 徽章）。
 * @param subtitle 标题的说明文字（Web 的 `p.text-[11px]`，本项目统一 12sp）。
 * @param subtitleAsHint 是否把 [subtitle] 收成标题旁的 ⓘ 浮层（用户 2026-10-11 定：弹窗只留一行标题）。
 *   数据型副标题（条数、当前值这类要一眼看到的）传 `false`，仍按副标题那一行渲染。
 * @param fixedHeight 钉死高度（Web 的 `h-[9xvh]`）。内容自带滚动容器时必须为 `true`，
 *   否则内容区拿不到有界高度、内部 `weight`/`verticalScroll` 不生效。
 * @param footer 固定底部操作区（Web 各表单的 `flex justify-end gap-2 pt-4 border-t`）。
 *   传入后弹窗自动按固定高布局：中间内容滚动、底部常驻。
 */
@Composable
fun FeatureDialog(
    title: String,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    maxWidth: Dp = 600.dp,
    heightFraction: Float = 0.9f,
    contentPadding: Dp = 16.dp,
    contentPaddingVertical: Dp = WwPad.dialogInset,
    icon: ImageVector? = null,
    subtitle: String? = null,
    subtitleAsHint: Boolean = true,
    titleContent: (@Composable () -> Unit)? = null,
    fixedHeight: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：全应用统一款式（毛玻璃雾面，不压黑），并登记自己让主内容去模糊。
            WwModalScrim(onDismiss = onDismiss)

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = WwPad.shell),
                contentAlignment = Alignment.Center,
            ) {
                // 高度有三档：
                //  - fixedHeight：钉死（世界观双栏 / 知识库那种自己要滚的板式，等价 `h-[9xvh]`）
                //  - 只有 footer：内容自适应、到 `max-h` 才封顶（短表单不拉满，长表单中段滚动）
                //  - 都没有：纯内容高（等价 `max-h-[9xvh]`）
                val maxDialogHeight = maxHeight * heightFraction
                // 面板范围 → 面板里所有 InfoHint 浮层都夹在这一块之内，不许压到弹窗外面。
                HintBounds {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .fillMaxWidth()
                            .then(
                                if (fixedHeight) Modifier.fillMaxHeight(heightFraction)
                                else Modifier.heightIn(max = maxDialogHeight),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = WwSurfaces.dialogBase,
                        tonalElevation = 0.dp,
                        // 亮色＝淡色勾线（主题 200 档）、暗色＝掺主色的中性描边，见 WwSurfaces。
                        border = BorderStroke(1.dp, WwSurfaces.panelBorder),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .then(if (fixedHeight) Modifier.fillMaxHeight() else Modifier),
                        ) {
                            // ── 顶栏：图标徽章 + 标题(+ⓘ) + 关闭 ──
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = WwPad.dialogInset, bottom = WwSize.boxVerticalPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (icon != null) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = WwSurfaces.accentSoft,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                }
                                if (titleContent != null) {
                                    Box(modifier = Modifier.weight(1f)) { titleContent() }
                                } else if (subtitle != null && subtitleAsHint) {
                                    // 单行标题 + 注释图标（说明收进浮层，标题分区因此变矮）。
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            mixedTitle(title),
                                            fontSize = WwType.title,
                                            fontWeight = WwType.titleWeight,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        InfoHint(subtitle)
                                        Spacer(Modifier.weight(1f))
                                    }
                                } else {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            mixedTitle(title),
                                            fontSize = WwType.title,
                                            fontWeight = WwType.titleWeight,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (subtitle != null) {
                                            Text(
                                                subtitle,
                                                fontSize = WwType.hint,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(TablerIcons.X, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider()
                            // 有底栏时让内容区吃掉「剩余」高度（`fill = false`：内容短就只占自己那么高，
                            // 弹窗跟着变矮；内容长才被封顶并内部滚动）。没有底栏就不加 weight。
                            val bodyModifier = Modifier.fillMaxWidth()
                                .then(if (footer != null) Modifier.weight(1f, fill = false) else Modifier)
                            // ── 分隔线两侧的留白（用户 2026-10-12）──
                            // ⚠️ 留白会**叠加**：顶栏自己已经有 `bottom = boxVerticalPadding`(2sp)，
                            //    内容区再各给 `WwPad.content`(4dp) 就成了线上下 2.7 / 4 的**不对称** seam。
                            //    所以线两侧只算一次盒内口径：标题行 2sp + 内容区顶 2sp。
                            //    通栏（`contentPaddingVertical = 0`，如双框提示词 / 备份）保持 0。
                            val seamPadding =
                                if (contentPaddingVertical == 0.dp) 0.dp else WwSize.boxVerticalPadding
                            if (scrollable) {
                                Column(
                                    modifier = bodyModifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(
                                            start = contentPadding,
                                            top = seamPadding,
                                            end = contentPadding,
                                            bottom = contentPaddingVertical,
                                        ),
                                ) { content() }
                            } else {
                                Column(
                                    modifier = bodyModifier.padding(
                                        start = contentPadding,
                                        top = seamPadding,
                                        end = contentPadding,
                                        bottom = contentPaddingVertical,
                                    ),
                                ) { content() }
                            }
                            // ── 固定底部操作区 ──
                            if (footer != null) {
                                HorizontalDivider()
                                // 底栏与内容区之间同理：线两侧各 2sp（通栏保持 0）。
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(
                                        start = contentPadding,
                                        top = seamPadding,
                                        end = contentPadding,
                                        bottom = contentPaddingVertical,
                                    ),
                                ) { footer() }
                            }
                        }
                    }
                }
            }
        }
    }
}
