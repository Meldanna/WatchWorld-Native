package com.watchworld.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watchworld.model.UiMode
import com.watchworld.ui.theme.LocalWwColors

/**
 * 面板内的**分层底色**（T24 新增；T27 新增底板统一管理）。
 *
 * ## 为什么需要它
 * M3 的 `colorScheme.surfaceVariant` 在本主题体系里落到 index.css 的 `--surface-3`
 * （亮色 `#E2E8F0`）—— 一个**完全中性**的灰。把它铺在弹窗底板上的卡片上，卡片就成了
 * 「灰白块」，与整套主题色脱节（暗色同理）。
 *
 * Web 参考版在同样的位置写的是 `bg-slate-100 dark:bg-slate-800`，但观感之所以不灰，
 * 靠的是**卡片自带主题色描边与浅色渐变底**（`from-indigo-50/50 to-purple-50/50`
 * + `border-indigo-200/60`）。这里给原生等价物：把主色按极低比例混进表面色，
 * 得到「同色系的浅底」，再配一根主色描边。
 *
 * ## 用法纪律
 * ### 底板类（新增 T27）
 * | 位置 | 取值 |
 * |---|---|
 * | Dialog 弹窗底板 | [dialogBase] |
 * | Sheet 侧边栏/底部面板底板 | [sheetBase] |
 * | Panel 功能面板底板 | [panelBase] |
 * | Window 主窗口背景 | [windowBase] |
 *
 * ### 分层类
 * | 位置 | 取值 |
 * |---|---|
 * | 底板上的卡片 / 分组块 | [card]（弱）· [cardStrong]（强） |
 * | 卡片内部的次级凹槽（数值框、标签框） | [inset] |
 * | 选中 / 聚焦态 | [cardSelected] |
 * | 整块高亮（如「当前激活」） | [accentSoft] |
 * | 卡片描边 | [cardBorder] |
 *
 * ⚠️ 新代码**不要**再用 `MaterialTheme.colorScheme.surfaceVariant` 做卡片底。
 *    它仍保留，只为兼容既有面板（设置类弹窗等），新面板一律走本对象。
 * ⚠️ 全部取值都是 `@Composable`：依赖当前 `WwTheme.colors`，**不能**缓存成顶层常量。
 */
object WwSurfaces {

    /** 亮色模式判定（亮色下底板要纯白、描边要走「淡色勾线」）。 */
    /** 当前是否处于亮色模式（从 LocalWwColors 读取）。 */
    @Composable
    private fun isLight(): Boolean = LocalWwColors.current.uiMode == UiMode.LIGHT

    // ══════════════════════════════════════════════════════════════════════════
    // 底板类（T27 新增）- 统一管理各类弹窗/窗口的底板样式
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Dialog 弹窗底板（设置、编辑器、选择器等对话框的最外层 Surface）。
     * 
     * 亮色：纯白 (#FFFFFF)
     * 暗色：深色底板 (surfaceElevated)
     */
    val dialogBase: Color
        @Composable get() = if (isLight()) Color.White else WwTheme.colors.surfaceElevated

    /**
     * Sheet 侧边栏/底部面板底板（SidebarSheet、WindowFunctionSheet 等）。
     * 
     * 亮色：纯白 (#FFFFFF)
     * 暗色：深色底板 (surfaceElevated)
     */
    val sheetBase: Color
        @Composable get() = if (isLight()) Color.White else WwTheme.colors.surfaceElevated

    /**
     * Panel 功能面板底板（WorldPanel、TimelinePanel 等嵌入式面板）。
     * 
     * 亮色：纯白 (#FFFFFF)
     * 暗色：深色底板 (surfaceElevated)
     */
    val panelBase: Color
        @Composable get() = if (isLight()) Color.White else WwTheme.colors.surfaceElevated

    /**
     * Window 主窗口背景（ChatScreen 等主界面的底层背景）。
     * 
     * 亮色：纯白 (#FFFFFF)
     * 暗色：深色背景 (surface0)
     */
    val windowBase: Color
        @Composable get() = if (isLight()) Color.White else WwTheme.colors.surface0

    // ══════════════════════════════════════════════════════════════════════════
    // 模态遮罩（毛玻璃）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * **全应用唯一的模态遮罩色**（用户 2026-10-13：「检查遮罩，所有的遮罩，把它们统一款式，
     * 毛玻璃效果，不用压黑」）。
     *
     * 改前每个弹窗各写各的：`Color.Black.copy(alpha = 0.5f)`（侧边栏抽屉 / 窗口功能区）、
     * `colorScheme.scrim.copy(alpha = 0.5f / 0.6f / 0.75f / 0.8f)`（时间线三兄弟 / 图片预览 /
     * 功能弹窗）—— 深浅不一、且都是压黑。现在一律走这一个取值，
     * 由 [com.watchworld.ui.components.WwModalScrim] 统一铺（连「点外面关闭」也一起收进去）。
     *
     * ## 取值口径
     * **半透明的主题表面色**，不掺黑：
     * - 亮色：白 35%（雾面提亮，底板仍然靠纯白 + 描边 + 阴影分层）；
     * - 暗色：`surfaceElevated` 35%（比 `surface0` 略亮的一层灰）。
     *
     * ## 「毛玻璃」靠什么成立
     * 玻璃的模糊**不是遮罩自己做的**（它只是一层平色，糊它没有意义）：遮罩在**独立窗口**里画，
     * 拿不到下层像素。真正的模糊由**主内容自己**做 —— 遮罩在组合里登记，
     * [`com.watchworld.ui.App`] 读到「有遮罩」就把主内容整体 [androidx.compose.ui.draw.blur] 掉，
     * 于是透过薄雾看到的是糊掉的界面。
     * ⚠️ 依赖它就必须经 [com.watchworld.ui.components.WwModalScrim] 铺遮罩，
     *    自己写 `background(Black.copy(...))` 的弹窗不会参与登记（也就不会模糊）。
     */
    val scrim: Color
        @Composable get() = if (isLight()) Color.White.copy(alpha = 0.35f)
        else WwTheme.colors.surfaceElevated.copy(alpha = 0.35f)

    /**
     * 毛玻璃遮罩配套的**模糊半径**：主内容在遮罩下糊多少。
     *
     * 取值和 [scrim] 是一对：雾面越薄（35%），模糊越要够（8dp）才看得出是「玻璃」而不是
     * 「没画背景」。半径大了会把整窗口的文字糊成一团灰，正好卡在这档。
     */
    val scrimBlurRadius: Dp = 8.dp

    // ══════════════════════════════════════════════════════════════════════════
    // 分层类 - 底板之上的卡片与控件
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 卡片底：统一使用模块1，底板之上「框」的默认取值。
     * 模块1使用淡色档位，适合作为底板上的第一层。
     * 
     * 亮色：m1-100（极淡主色）
     * 暗色：m1-900（浓主色）
     */
    val card: Color
        @Composable get() = m1Shade(if (isLight()) 1 else 9)

    /** 
     * 强卡片底：统一使用模块1。用于需要明显分层的主卡片（如时间线分支卡片）。
     * 
     * 亮色:m1-100 with 98% alpha(依靠透明度形成层次区分,同时满足 alpha ≥ 250 要求)
     * 暗色：m1-900（与 card 相同）
     */
    val cardStrong: Color
        @Composable get() = m1Shade(if (isLight()) 1 else 9).copy(alpha = if (isLight()) 0.98f else 1f)

    /** 
     * 凹槽底：统一使用模块2，卡片内部的次级容器。
     * 模块2使用浓色档位，与模块1形成对比。
     * 
     * 亮色：m2-100（极淡辅助色）
     * 暗色：m2-800（比模块1更浓）
     */
    val inset: Color
        @Composable get() = m2Shade(if (isLight()) 1 else 8)

        /**
     * 选中 / 聚焦卡片底：统一使用模块1。
     * 与 card 相同，保持简洁。选中状态的视觉区分靠 border + icon tint，而非背景色深浅。
     */
    val cardSelected: Color
        @Composable get() = card

    /** 整块高亮底：统一使用模块1。 */
    val accentSoft: Color
        @Composable get() = card

    /**
     * 弹窗 / 卡片的描边。
     *
     * ⚠️ **框线连接主题「模块2」**（用户 2026-10-13；原来取模块1 的 200 档）——
     * 与 [inset]（同为模块2）同族，框线与框内底色不脱节，换主题辅助色时框线一起变。
     * 亮色取 200 档（淡色勾线：底板纯白 + 淡辅助色边框），暗色在 500 档里掺中性描边，
     * 避免暗底上出现刺眼的亮描边。
     */
    val cardBorder: Color
        @Composable get() = panelBorder

    /** 面板 / 弹窗底板描边（与 [cardBorder] 同源，供 Dialog 直接用）。 */
    val panelBorder: Color
        @Composable get() = if (isLight()) m2Shade(2)
        else mix(WwTheme.colors.borderDefault, m2Shade(5), 0.45f)

    /** 卡片描边的弱化版（次级凹槽用）。同样连接模块2，比 [cardBorder] 更淡。 */
    val cardBorderSubtle: Color
        @Composable get() = if (isLight()) m2Shade(1)
        else mix(WwTheme.colors.borderSubtle, m2Shade(5), 0.35f)

    @Composable
    private fun m1Shade(index: Int): Color {
        val cssColor = WwTheme.colors.vars.m1.getOrNull(index) ?: return WwTheme.colors.primaryBg
        return CssColors.parse(cssColor) ?: WwTheme.colors.primaryBg
    }

    @Composable
    private fun m2Shade(index: Int): Color {
        val cssColor = WwTheme.colors.vars.m2.getOrNull(index) ?: return WwTheme.colors.primaryBg
        return CssColors.parse(cssColor) ?: WwTheme.colors.primaryBg
    }

    /** 把 [overlay] 按 [ratio] 混到 [base] 上；ratio = 0 取 base，1 取 overlay。 */
    private fun mix(base: Color, overlay: Color, ratio: Float): Color =
        lerp(base, overlay, ratio.coerceIn(0f, 1f))
}
