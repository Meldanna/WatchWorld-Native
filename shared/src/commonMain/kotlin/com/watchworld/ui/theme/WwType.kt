package com.watchworld.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全应用统一的**文字基准**（用户 2026-10-11 定案；2026-10-13 改为「设置值即字号」）。
 *
 * 三档（默认基准 15 时）：
 * - [body] `15sp` —— 默认正文（标签、值、按钮、列表项、输入框内文字……一切「正常文字」）；
 * - [title] `17sp` —— 弹窗标题 / 分区标题（**加粗只给弹窗顶栏标题**，见 [titleWeight]）；
 * - [hint] `13sp` —— 框外提示、注释浮层文字、次级说明。
 *
 * ## 跟随「界面字体大小」
 * 三档不再做 Web 版 `chat-text-*` 的倍率换算，而是**直接用 sp**：
 * 默认正文 = 设置 →「文本设置 → 界面字体大小」的 `AppPreferences.chatFontSize`（12–22px，默认 15）
 * 本身，标题 = 正文 + 2sp，提示 = 正文 − 2sp。base 经 [WwTheme] 的 `chatFontSize` 参数
 * 注入 [WwTypographyTokens]。因此用户在设置里拖大字体时，全应用三档一起按 sp 平移。
 *
 * ⚠️ 这是 `@Composable` 取值：只能在组合作用域里读（`Text(fontSize = WwType.body)`），
 *    不要存进 `remember` 之外的顶层常量或非组合上下文。
 *
 * ## 输入框里的文字
 * 能输入内容的框（[com.watchworld.ui.components.WwTextField] / `SearchField` / 聊天输入框）里的
 * **文字与占位都用 [body]（默认正文 14sp）** —— 用户 2026-10-12 定的口径：早前用 [hint]，
 * 结果「窗口 API 参数」这类面板里**标签 14sp 反而比框里的值 12sp 还大**。
 * 字号同样跟着「界面字体大小」缩放；**框本身的高度**不受三档规则影响，由单行文字行高决定。
 *
 * 取代此前散落各处的 `10/11/12/13/22sp` 硬编码；新增代码请引用本对象，
 * 不要再写字面量（`fontSize = 11.sp` 这类写法）。
 */
object WwType {
    /** 默认正文 = 设置里的「界面字体大小」本身（不再乘 0.9333）。 */
    val body: TextUnit
        @Composable get() = WwTheme.colors.typography.smFontSize.sp

    /** 弹窗 / 分区标题 = 正文 + 2sp。 */
    val title: TextUnit
        @Composable get() = WwTheme.colors.typography.baseFontSizeScaled.sp

    /**
     * **弹窗顶栏标题**的字重（不随字号变）。
     *
     * 用户 2026-10-13：除弹窗顶栏标题外，全应用一律不加粗 ——
     * 分区标题、卡片标题、列表项、按钮都只靠字号（[title] vs [body]）拉开层级。
     */
    val titleWeight = FontWeight.Bold

    /** 正文档配套行高（= 正文字号 + 5sp；默认 15 时 20sp）。 */
    val bodyLineHeight: TextUnit
        @Composable get() = WwTheme.colors.typography.smLineHeight.sp

    /** 标题档配套行高（= 标题字号 + 7sp；默认 15 时 24sp）。 */
    val titleLineHeight: TextUnit
        @Composable get() = WwTheme.colors.typography.baseLineHeight.sp

    /** 框外提示 / 注释浮层 / 次级说明 = 正文 − 2sp。 */
    val hint: TextUnit
        @Composable get() = WwTheme.colors.typography.xsFontSize.sp
}

/**
 * 界面字体族（设置 →「文本设置 → 界面字体」）。
 *
 * - [SANS]：平台默认无衬线体（Windows 上是 Segoe UI ＋ 中文回退微软雅黑，即俗称的「黑体」），
 *   默认项，与改动前的观感完全一致；
 * - [SERIF]：平台的衬线体（`FontFamily.Serif`）。Windows 的拉丁文回退 Times New Roman、
 *   中文回退宋体（SimSun），即用户说的「宋体」。
 *
 * 只改**正文字体**：等宽位（模型名、Tag、代码块）永远是 [FontFamily.Monospace]，不随本项变。
 *
 * ⚠️ [storageValue] 是写在 `kv` 表里的原生设置（`StorageKeys.UI_FONT_FAMILY`），
 *    **不进 v5 存档**（Web 版没有这个设置，见 `docs/MIGRATION_CONSTRAINTS.md` §1）。
 *    改这两个字面量等于让老用户的字体选择失效（会静默回落到默认）。
 */
enum class UiFontFamily(val label: String, val storageValue: String) {
    SANS("黑体（默认）", "sans"),
    SERIF("宋体", "serif"),
    ;

    companion object {
        /** `kv` 里的原始串 → 枚举；认不出的（含从没存过）一律当 [SANS]（与读接口的容错一致）。 */
        fun fromStorage(raw: String?): UiFontFamily =
            entries.firstOrNull { it.storageValue == raw } ?: SANS
    }
}

/** [UiFontFamily] → Compose 字体族。 */
fun UiFontFamily.toFontFamily(): FontFamily = when (this) {
    UiFontFamily.SANS -> FontFamily.Default
    UiFontFamily.SERIF -> FontFamily.Serif
}

/**
 * 当前界面字体族（由 [WwTheme] 注入）。
 *
 * ⚠️ 只在**组合作用域**里读：[WwFont.family]。想给整棵 UI 换字体，改 `WwTheme` 的参数，
 *    不要在组件里各自读设置 —— 那会让主题之外的字漏掉。
 */
val LocalWwFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** 惯用入口：`fontFamily = WwFont.family`（等宽位请直接用 [FontFamily.Monospace]）。 */
object WwFont {
    val family: FontFamily
        @Composable get() = LocalWwFontFamily.current
}

/** 紧凑控件与嵌套层级共用的垂直尺寸 token。 */
object WwSize {
    /**
     * 嵌套层级每级每侧的递增步长 = 2sp（用户 2026-10-13：**不再按字号缩放**，直接用 sp）。
     * 相邻层级的总高度差因此为 4sp（上下各 2sp）。
     */
    val verticalLevelStep: Dp
        get() = 2.dp

    /** 文字框只比内容行高一点，统一保留固定的上下间距。 */
    val compactFrameVerticalPadding: Dp
        get() = 1.dp

    /** 移除固定最小高度，让紧凑框由文字行高和固定间距决定。 */
    val compactTagMinHeight: Dp
        get() = 0.dp

    /** 紧凑标签共享固定内边距。 */
    val compactTagVerticalPadding: Dp
        get() = compactFrameVerticalPadding

    /** 单行表单框不设固定最小高度，文字变大时由内容自然撑高。 */
    val formFieldMinHeight: Dp
        get() = 0.dp

    /** 表单、搜索及选择框共享紧凑固定内边距。 */
    val formFieldVerticalPadding: Dp
        get() = compactFrameVerticalPadding

    val searchFieldVerticalPadding: Dp
        get() = compactFrameVerticalPadding

    /** 按钮留白与其他文字框一致。 */
    val buttonVerticalPadding: Dp
        get() = compactFrameVerticalPadding

    /**
     * 框内文字显式设置行高，避免继承 Material 默认行高撑大外框。
     * = 提示字号 + 3sp（默认 15 时 16sp；用户 2026-10-13：不再乘 1.0667）。
     */
    val compactTextLineHeight: TextUnit
        @Composable get() = WwTheme.colors.typography.xsLineHeight.sp

    /** 次操作按钮使用统一按钮留白。 */
    val secondaryButtonVerticalPadding: Dp
        @Composable get() = buttonVerticalPadding

    /**
     * 带文字的**大框**（设置功能框、输入栏模型胶囊）的**每侧**留白 = 2sp。
     *
     * 口径（用户 2026-10-12 修正）：「框 = 基准字号 + 4」里的 **4 是上下合计**，
     * 所以每侧是 2 —— 早前误把它当成每侧值（功能框 20dp/侧、胶囊 8dp/侧），框就撑得老高。
     * 2026-10-13 起**直接用 sp 字面值**（不再按提示字号缩放）。
     */
    val boxVerticalPadding: Dp
        get() = 2.dp

    /** 列表 / 网格「空态」里居中提示文字的垂直留白。 */
    val emptyStateVerticalPadding: Dp
        get() = 32.dp

    /** 侧边栏功能导航格（图标 + 标签）的垂直留白。 */
    val navCellVerticalPadding: Dp
        get() = 6.dp

    /**
     * 侧边栏功能格里的**图标边长**（用户 2026-10-13：「功能区的内部图标大小改为与文字 1:1 变化」）。
     *
     * 取值 = 该格标签的字号本身（[WwType.hint] 的 sp 值直接当 dp 用）——
     * 所以图标与文字是 1:1 的，用户在设置里拖大字体时两者同步变大，
     * 不再像改前那样图标死钉在 14dp、字变大后图标显小。
     */
    val navIconSize: Dp
        @Composable get() = WwType.hint.value.dp

    /** 卡片列表里相邻卡片的垂直间距。 */
    val cardListGap: Dp
        get() = 4.dp

    /**
     * **带底色卡片**列表里相邻卡片的目标间隙（用户 2026-10-13：6dp）。
     * 由容器 `Arrangement.spacedBy(WwSize.cardGap)` 负责，卡片自身**不再加垂直外边距** ——
     * 避免「子项双侧 padding」把相邻间隙翻倍。
     */
    val cardGap: Dp
        get() = 6.dp

    /** 列表 / 树里可点击行（分组表头、树节点、列表行）的行内垂直留白。 */
    val listRowVerticalPadding: Dp
        get() = 4.dp

    /**
     * 滑杆（`WwRangeSlider`）的**盒子高**：贴紧内容（拇指直径 12dp + 2dp 余量）。
     *
     * 原来 24dp——可见轨道只有 4dp、居中画在盒子里，于是滑杆上下各空出 10dp，
     * 看起来就成了「组与组之间的距离」（用户 2026-10-12：偏好弹窗每组之间距离太大）。
     */
    val sliderHeight: Dp
        get() = 16.dp
}

/**
 * 垂直留白只有**两个口径**（用户 2026-10-12 定案，旧「嵌套链」已废除）：
 *
 * 1. **框内留白**（带子 / 卡片 / 行里的内容到框边）= [WwSize.boxVerticalPadding]（每侧 2sp）
 *    —— 即「框总高 = 文字行 + 4」，4 是上下合计。
 * 2. **框与框之间**、分隔线上下、弹窗内容区上下 = [WwSize.cardListGap]（4dp）。
 *
 * 嵌套层级每级每侧 +2sp（用户 2026-10-13：card=2 / content=4 / shell=6，总高差 4sp），
 * 且**不再按界面字号缩放**（直接用 sp）。水平内边距不受此约束。
 */
object WwPad {
    /** 字段行之间的**间隙**（表单里两个字段之间）。 */
    val fieldGap: Dp
        get() = WwSize.cardListGap

    /**
     * 设置弹窗的**功能框**（SettingsHub 的功能入口卡等）：每侧 [WwSize.boxVerticalPadding]，
     * 即**框总高 = 文字行 + 4**（4 是上下合计、每侧 2），直接用 sp（不随界面字号缩放）。
     */
    val featureBox: Dp
        @Composable get() = WwSize.boxVerticalPadding

    /** 功能框外面那圈**框**（设置弹窗内容区）：下级每侧 + 2sp（外框总高 + 4）。 */
    val featureFrame: Dp
        @Composable get() = featureBox + WwSize.boxVerticalPadding

    /**
     * 卡片 / 行 / 带子的**框内每侧留白**（历史名字，值 = [WwSize.boxVerticalPadding]）。
     *
     * 旧值 11dp 是「嵌套链」的产物，被当成了框内留白 → 行里的文字离框边快 1 个字高。
     */
    val card: Dp
        @Composable get() = WwSize.boxVerticalPadding

    /** 弹窗内容区上下 / 分隔线上下（历史名字，值 = [WwSize.cardListGap]）。 */
    val content: Dp
        get() = WwSize.cardListGap

    /**
     * 弹窗外壳（FeatureDialog 最外层与可视区之间的留白）= 内容区（4）+ 每级步长（2）= 6sp。
     * 2026-10-13 前是「内容区 + 固定 3」，现改为按每级 +2sp 的嵌套口径。
     */
    val shell: Dp
        @Composable get() = content + WwSize.boxVerticalPadding

    /**
     * **弹窗面板（底板）到内部最近内容框**的距离 = 3sp（用户 2026-10-13）。
     * 用于 `FeatureDialog` 顶栏的上边距，以及内容区 / 底栏的下边距。
     */
    val dialogInset: Dp
        get() = 3.dp
}

/**
 * 标题里西文的**字号补偿倍率**（用户 2026-10-12 报「Skill 弹窗标题里 SKILL 比旁边汉字小」）。
 *
 * 同一个 sp 下，汉字占满**字身**（约 1.0em），而拉丁字母的可见高度只有大写高度（约 0.72em）、
 * 小写更矮 —— 所以「Skill 技能库」里那截西文看着比旁边的汉字小一圈。
 * 把标题里的**西文段**放大这一档即可视觉拉平。
 */
const val TITLE_LATIN_SCALE = 1.15f

/** 连续可打印 ASCII（西文 / 数字 / 半角标点）。 */
private val LATIN_RUN = Regex("[\\x20-\\x7E]+")

/** 汉字 / 全角标点（CJK 标点 + 汉字 + 全角形式）。 */
private val CJK_RUN = Regex("[\\u3000-\\u303F\\u4E00-\\u9FFF\\uFF00-\\uFFEF]")

/**
 * 弹窗 / 分区标题的**中西文混排**取值：汉字用 [fontSize]，西文段 × [TITLE_LATIN_SCALE]。
 *
 * 用法：`Text(mixedTitle(title), fontSize = WwType.title, fontWeight = WwType.titleWeight)`
 * —— 字号仍要显式传一次，它是非西文段的基准。
 *
 * 纯汉字、纯西文（没有并排的汉字可比）时原样返回，不做补偿。
 */
@Composable
fun mixedTitle(text: String, fontSize: TextUnit = WwType.title): AnnotatedString {
    if (!CJK_RUN.containsMatchIn(text)) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (match in LATIN_RUN.findAll(text)) {
            if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontSize = fontSize * TITLE_LATIN_SCALE)) { append(match.value) }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
