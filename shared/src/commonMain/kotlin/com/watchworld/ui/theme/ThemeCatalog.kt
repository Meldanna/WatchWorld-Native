package com.watchworld.ui.theme

/**
 * theme.ts 的静态数据层,值 1:1 抄自 React 仓(BFSU `src/lib/theme.ts`)。
 * 改这里的数值 = 改 visual baseline,foundation/theme-baseline/snapshots.json 会拦。
 *
 * ## 与 React 版的对应
 * - [FAMILY_SCALES] ← `FAMILY_SCALES`(Tailwind 官方精确 oklch,11 档 × 7 色系)
 * - [L_LADDER] / [C_BELL] ← 同名常量(自定义/多巴胺主题的 buildScale 输入)
 * - [builtInThemes] ← `DAY_MODE_PRESETS` + `NIGHT_MODE_PRESETS` + `DOPAMINE_PRESETS`(经 makeTheme 组装)
 * - [COLOR_SWATCHES] ← 同名(DIY 调色盘,32 色,按色相排序)
 *
 * ## 角色层(ThemeConfig.primaryBg 等)为什么存字符串
 * 与 React 版相同:这是**类名串**不是颜色 —— `text-m2-600 dark:text-m2-400` 同时带
 * 明暗两档,意味着角色值是 `(主题变体 × uiMode) → Color` 的两维解析(见 ThemeRoles)。
 * T15 的设置界面还要原样展示/编辑这些串,所以保留字符串原形,T12 只把它们「解引用」成色。
 */

/** 一个色系名(预设主题用它查 [FAMILY_SCALES])。 */
enum class ThemeFamily { EMERALD, BLUE, VIOLET, ROSE, AMBER, CYAN, SLATE }

/** 主题来源:双色 HEX 推导(自定义/多巴胺)、或指定饱和度级别。 */
sealed interface ThemeSource {
    data class Custom(val m1: String, val m2: String) : ThemeSource
    data class SaturationLevel(
        val family: ThemeFamily,
        val m1Level: SatLevel,
        val m2Level: SatLevel
    ) : ThemeSource
}

/** 饱和度级别 */
enum class SatLevel {
    LOW,        // 低饱和 0.03-0.08
    MID,        // 中饱和 0.08-0.15
    MID_HIGH,   // 中高饱和 0.12-0.22
    HIGH        // 高饱和 (原始)
}

/** 色阶档位(与 Tailwind 11 级一致)。 */
val SHADE_KEYS = intArrayOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950)

/**
 * Tailwind 官方调色板的精确 oklch 值(抄自 tailwindcss/theme.css,theme.ts 原样)。
 * 预设主题走这张表,保证 `--m1-600 === emerald-600` 是数值相等而非肉眼近似。
 */
val FAMILY_SCALES: Map<ThemeFamily, List<String>> = mapOf(
    ThemeFamily.EMERALD to listOf(
        "oklch(97.9% 0.021 166.113)",
        "oklch(95% 0.052 163.051)",
        "oklch(90.5% 0.093 164.15)",
        "oklch(84.5% 0.143 164.978)",
        "oklch(76.5% 0.177 163.223)",
        "oklch(69.6% 0.17 162.48)",
        "oklch(59.6% 0.145 163.225)",
        "oklch(50.8% 0.118 165.612)",
        "oklch(43.2% 0.095 166.913)",
        "oklch(37.8% 0.077 168.94)",
        "oklch(26.2% 0.051 172.552)",
    ),
    ThemeFamily.BLUE to listOf(
        "oklch(97% 0.014 254.604)",
        "oklch(93.2% 0.032 255.585)",
        "oklch(88.2% 0.059 254.128)",
        "oklch(80.9% 0.105 251.813)",
        "oklch(70.7% 0.165 254.624)",
        "oklch(62.3% 0.214 259.815)",
        "oklch(54.6% 0.245 262.881)",
        "oklch(48.8% 0.243 264.376)",
        "oklch(42.4% 0.199 265.638)",
        "oklch(37.9% 0.146 265.522)",
        "oklch(28.2% 0.091 267.935)",
    ),
    ThemeFamily.VIOLET to listOf(
        "oklch(96.9% 0.016 293.756)",
        "oklch(94.3% 0.029 294.588)",
        "oklch(89.4% 0.057 293.283)",
        "oklch(81.1% 0.111 293.571)",
        "oklch(70.2% 0.183 293.541)",
        "oklch(60.6% 0.25 292.717)",
        "oklch(54.1% 0.281 293.009)",
        "oklch(49.1% 0.27 292.581)",
        "oklch(43.2% 0.232 292.759)",
        "oklch(38% 0.189 293.745)",
        "oklch(28.3% 0.141 291.089)",
    ),
    ThemeFamily.ROSE to listOf(
        "oklch(96.9% 0.015 12.422)",
        "oklch(94.1% 0.03 12.58)",
        "oklch(89.2% 0.058 10.001)",
        "oklch(81% 0.117 11.638)",
        "oklch(71.2% 0.194 13.428)",
        "oklch(64.5% 0.246 16.439)",
        "oklch(58.6% 0.253 17.585)",
        "oklch(51.4% 0.222 16.935)",
        "oklch(45.5% 0.188 13.697)",
        "oklch(41% 0.159 10.272)",
        "oklch(27.1% 0.105 12.094)",
    ),
    ThemeFamily.AMBER to listOf(
        "oklch(98.7% 0.022 95.277)",
        "oklch(96.2% 0.059 95.617)",
        "oklch(92.4% 0.12 95.746)",
        "oklch(87.9% 0.169 91.605)",
        "oklch(82.8% 0.189 84.429)",
        "oklch(76.9% 0.188 70.08)",
        "oklch(66.6% 0.179 58.318)",
        "oklch(55.5% 0.163 48.998)",
        "oklch(47.3% 0.137 46.201)",
        "oklch(41.4% 0.112 45.904)",
        "oklch(27.9% 0.077 45.635)",
    ),
    ThemeFamily.CYAN to listOf(
        "oklch(98.4% 0.019 200.873)",
        "oklch(95.6% 0.045 203.388)",
        "oklch(91.7% 0.08 205.041)",
        "oklch(86.5% 0.127 207.078)",
        "oklch(78.9% 0.154 211.53)",
        "oklch(71.5% 0.143 215.221)",
        "oklch(60.9% 0.126 221.723)",
        "oklch(52% 0.105 223.128)",
        "oklch(45% 0.085 224.283)",
        "oklch(39.8% 0.07 227.392)",
        "oklch(30.2% 0.056 229.695)",
    ),
    ThemeFamily.SLATE to listOf(
        "oklch(98.4% 0.003 247.858)",
        "oklch(96.8% 0.007 247.896)",
        "oklch(92.9% 0.013 255.508)",
        "oklch(86.9% 0.022 252.894)",
        "oklch(70.4% 0.04 256.788)",
        "oklch(55.4% 0.046 257.417)",
        "oklch(44.6% 0.043 257.281)",
        "oklch(37.2% 0.044 257.287)",
        "oklch(27.9% 0.041 260.031)",
        "oklch(20.8% 0.042 265.755)",
        "oklch(12.9% 0.042 264.695)",
    ),
)

/**
 * 低饱和颜色系列 - 适合白天模式使用
 * 彩度控制在 0.03-0.08 之间，确保在白底上柔和舒适
 */
val FAMILY_SCALES_LOW_SAT: Map<ThemeFamily, List<String>> = mapOf(
    ThemeFamily.EMERALD to listOf(
        "oklch(97.9% 0.015 166.113)",
        "oklch(95% 0.035 163.051)",
        "oklch(90.5% 0.055 164.15)",
        "oklch(84.5% 0.075 164.978)",
        "oklch(76.5% 0.090 163.223)",
        "oklch(69.6% 0.085 162.48)",
        "oklch(59.6% 0.075 163.225)",
        "oklch(50.8% 0.065 165.612)",
        "oklch(43.2% 0.055 166.913)",
        "oklch(37.8% 0.045 168.94)",
        "oklch(26.2% 0.035 172.552)",
    ),
    ThemeFamily.BLUE to listOf(
        "oklch(97% 0.010 254.604)",
        "oklch(93.2% 0.022 255.585)",
        "oklch(88.2% 0.040 254.128)",
        "oklch(80.9% 0.060 251.813)",
        "oklch(70.7% 0.080 254.624)",
        "oklch(62.3% 0.090 259.815)",
        "oklch(54.6% 0.095 262.881)",
        "oklch(48.8% 0.100 264.376)",
        "oklch(42.4% 0.090 265.638)",
        "oklch(37.9% 0.075 265.522)",
        "oklch(28.2% 0.055 267.935)",
    ),
    ThemeFamily.VIOLET to listOf(
        "oklch(96.9% 0.012 293.756)",
        "oklch(94.3% 0.020 294.588)",
        "oklch(89.4% 0.038 293.283)",
        "oklch(81.1% 0.065 293.571)",
        "oklch(70.2% 0.090 293.541)",
        "oklch(60.6% 0.105 292.717)",
        "oklch(54.1% 0.110 293.009)",
        "oklch(49.1% 0.105 292.581)",
        "oklch(43.2% 0.095 292.759)",
        "oklch(38% 0.080 293.745)",
        "oklch(28.3% 0.060 291.089)",
    ),
    ThemeFamily.ROSE to listOf(
        "oklch(96.9% 0.012 12.422)",
        "oklch(94.1% 0.022 12.58)",
        "oklch(89.2% 0.040 10.001)",
        "oklch(81% 0.065 11.638)",
        "oklch(71.2% 0.090 13.428)",
        "oklch(64.5% 0.105 16.439)",
        "oklch(58.6% 0.110 17.585)",
        "oklch(51.4% 0.100 16.935)",
        "oklch(45.5% 0.085 13.697)",
        "oklch(41% 0.070 10.272)",
        "oklch(27.1% 0.050 12.094)",
    ),
    ThemeFamily.AMBER to listOf(
        "oklch(98.7% 0.018 95.277)",
        "oklch(96.2% 0.040 95.617)",
        "oklch(92.4% 0.070 95.746)",
        "oklch(87.9% 0.090 91.605)",
        "oklch(82.8% 0.100 84.429)",
        "oklch(76.9% 0.095 70.08)",
        "oklch(66.6% 0.090 58.318)",
        "oklch(55.5% 0.085 48.998)",
        "oklch(47.3% 0.075 46.201)",
        "oklch(41.4% 0.065 45.904)",
        "oklch(27.9% 0.050 45.635)",
    ),
    ThemeFamily.CYAN to listOf(
        "oklch(98.4% 0.015 200.873)",
        "oklch(95.6% 0.032 203.388)",
        "oklch(91.7% 0.055 205.041)",
        "oklch(86.5% 0.075 207.078)",
        "oklch(78.9% 0.085 211.53)",
        "oklch(71.5% 0.080 215.221)",
        "oklch(60.9% 0.070 221.723)",
        "oklch(52% 0.060 223.128)",
        "oklch(45% 0.050 224.283)",
        "oklch(39.8% 0.042 227.392)",
        "oklch(30.2% 0.035 229.695)",
    ),
    ThemeFamily.SLATE to listOf(
        "oklch(98.4% 0.003 247.858)",
        "oklch(96.8% 0.007 247.896)",
        "oklch(92.9% 0.013 255.508)",
        "oklch(86.9% 0.022 252.894)",
        "oklch(70.4% 0.040 256.788)",
        "oklch(55.4% 0.046 257.417)",
        "oklch(44.6% 0.043 257.281)",
        "oklch(37.2% 0.044 257.287)",
        "oklch(27.9% 0.041 260.031)",
        "oklch(20.8% 0.042 265.755)",
        "oklch(12.9% 0.042 264.695)",
    ),
)

/**
 * 中高饱和颜色系列 - 适合夜晚模式使用
 * 彩度控制在 0.12-0.22 之间，在黑底上清晰可见
 */
val FAMILY_SCALES_MID_HIGH_SAT: Map<ThemeFamily, List<String>> = mapOf(
    ThemeFamily.EMERALD to listOf(
        "oklch(97.9% 0.030 166.113)",
        "oklch(95% 0.075 163.051)",
        "oklch(90.5% 0.130 164.15)",
        "oklch(84.5% 0.185 164.978)",
        "oklch(76.5% 0.215 163.223)",
        "oklch(69.6% 0.205 162.48)",
        "oklch(59.6% 0.175 163.225)",
        "oklch(50.8% 0.145 165.612)",
        "oklch(43.2% 0.120 166.913)",
        "oklch(37.8% 0.095 168.94)",
        "oklch(26.2% 0.065 172.552)",
    ),
    ThemeFamily.BLUE to listOf(
        "oklch(97% 0.020 254.604)",
        "oklch(93.2% 0.045 255.585)",
        "oklch(88.2% 0.080 254.128)",
        "oklch(80.9% 0.135 251.813)",
        "oklch(70.7% 0.200 254.624)",
        "oklch(62.3% 0.245 259.815)",
        "oklch(54.6% 0.270 262.881)",
        "oklch(48.8% 0.265 264.376)",
        "oklch(42.4% 0.220 265.638)",
        "oklch(37.9% 0.170 265.522)",
        "oklch(28.2% 0.110 267.935)",
    ),
    ThemeFamily.VIOLET to listOf(
        "oklch(96.9% 0.022 293.756)",
        "oklch(94.3% 0.040 294.588)",
        "oklch(89.4% 0.075 293.283)",
        "oklch(81.1% 0.135 293.571)",
        "oklch(70.2% 0.210 293.541)",
        "oklch(60.6% 0.275 292.717)",
        "oklch(54.1% 0.305 293.009)",
        "oklch(49.1% 0.295 292.581)",
        "oklch(43.2% 0.255 292.759)",
        "oklch(38% 0.210 293.745)",
        "oklch(28.3% 0.160 291.089)",
    ),
    ThemeFamily.ROSE to listOf(
        "oklch(96.9% 0.020 12.422)",
        "oklch(94.1% 0.042 12.58)",
        "oklch(89.2% 0.075 10.001)",
        "oklch(81% 0.140 11.638)",
        "oklch(71.2% 0.220 13.428)",
        "oklch(64.5% 0.270 16.439)",
        "oklch(58.6% 0.280 17.585)",
        "oklch(51.4% 0.245 16.935)",
        "oklch(45.5% 0.210 13.697)",
        "oklch(41% 0.180 10.272)",
        "oklch(27.1% 0.125 12.094)",
    ),
    ThemeFamily.AMBER to listOf(
        "oklch(98.7% 0.030 95.277)",
        "oklch(96.2% 0.080 95.617)",
        "oklch(92.4% 0.145 95.746)",
        "oklch(87.9% 0.200 91.605)",
        "oklch(82.8% 0.220 84.429)",
        "oklch(76.9% 0.215 70.08)",
        "oklch(66.6% 0.205 58.318)",
        "oklch(55.5% 0.190 48.998)",
        "oklch(47.3% 0.165 46.201)",
        "oklch(41.4% 0.140 45.904)",
        "oklch(27.9% 0.100 45.635)",
    ),
    ThemeFamily.CYAN to listOf(
        "oklch(98.4% 0.025 200.873)",
        "oklch(95.6% 0.060 203.388)",
        "oklch(91.7% 0.105 205.041)",
        "oklch(86.5% 0.155 207.078)",
        "oklch(78.9% 0.185 211.53)",
        "oklch(71.5% 0.170 215.221)",
        "oklch(60.9% 0.150 221.723)",
        "oklch(52% 0.130 223.128)",
        "oklch(45% 0.110 224.283)",
        "oklch(39.8% 0.090 227.392)",
        "oklch(30.2% 0.070 229.695)",
    ),
    ThemeFamily.SLATE to listOf(
        "oklch(98.4% 0.003 247.858)",
        "oklch(96.8% 0.007 247.896)",
        "oklch(92.9% 0.013 255.508)",
        "oklch(86.9% 0.022 252.894)",
        "oklch(70.4% 0.040 256.788)",
        "oklch(55.4% 0.046 257.417)",
        "oklch(44.6% 0.043 257.281)",
        "oklch(37.2% 0.044 257.287)",
        "oklch(27.9% 0.041 260.031)",
        "oklch(20.8% 0.042 265.755)",
        "oklch(12.9% 0.042 264.695)",
    ),
)

/** Tailwind 各档的亮度阶梯(buildScale 输入,theme.ts 原样)。 */
val L_LADDER = doubleArrayOf(
    97.9, 95.0, 90.5, 84.5, 76.5, 69.6, 59.6, 50.8, 43.2, 37.8, 26.2,
)

/** 彩度钟形分布(相对 500 档),buildScale 输入,theme.ts 原样。 */
val C_BELL = doubleArrayOf(
    0.124, 0.306, 0.547, 0.841, 1.041, 1.0, 0.853, 0.694, 0.559, 0.453, 0.3,
)

/**
 * 单套主题的角色配置。字段与 theme.ts `ThemeConfig` 一一对应,
 * 值是 Tailwind **类名串**(不是颜色)—— 见文件头注释。
 */
data class ThemeConfig(
    val id: String,
    val name: String,
    /** 展示用的主色(主题列表圆点、侧边栏 logo 方块)。 */
    val primaryHex: String,
    val source: ThemeSource,
    val primaryBg: String,
    val primaryHover: String,
    val primaryText: String,
    val userBubble: String,
    val accentBadge: String,
    val badgeBg: String,
    val badgeText: String,
)

/**
 * 组装一份主题:角色串来自 [StandardRoles](ThemeRoles,唯一权威),这里只做拼接。
 * 对齐 theme.ts makeTheme(id, name, primaryHex, source, classes)。
 */
internal fun makeTheme(
    id: String,
    name: String,
    primaryHex: String,
    source: ThemeSource,
    roles: StandardRoles = ThemeRoles.STANDARD,
): ThemeConfig = ThemeConfig(
    id = id,
    name = name,
    primaryHex = primaryHex,
    source = source,
    primaryBg = roles.primaryBg,
    primaryHover = roles.primaryHover,
    primaryText = roles.primaryText,
    userBubble = roles.userBubble,
    accentBadge = roles.accentBadge,
    badgeBg = roles.badgeBg,
    badgeText = roles.badgeText,
)

/** 白天模式主题：低饱和1 + 中饱和2，适合白色底板 */
private val DAY_MODE_PRESETS = listOf(
    Triple("emerald-day", "翡翠玉绿・白天", "#059669") to ThemeFamily.EMERALD,
    Triple("blue-day", "深邃科技蓝・白天", "#2563eb") to ThemeFamily.BLUE,
    Triple("violet-day", "极光幻夜紫・白天", "#7c3aed") to ThemeFamily.VIOLET,
    Triple("rose-day", "热烈绯红玫瑰・白天", "#e11d48") to ThemeFamily.ROSE,
    Triple("amber-day", "明亮落日琥珀・白天", "#d97706") to ThemeFamily.AMBER,
    Triple("cyan-day", "霓虹赛博青・白天", "#0891b2") to ThemeFamily.CYAN,
    Triple("slate-day", "极简黑曜石・白天", "#334155") to ThemeFamily.SLATE,
)

/** 夜晚模式主题：中高饱和1 + 高饱和2，适合黑色底板 */
private val NIGHT_MODE_PRESETS = listOf(
    Triple("emerald-night", "翡翠玉绿・夜晚", "#059669") to ThemeFamily.EMERALD,
    Triple("blue-night", "深邃科技蓝・夜晚", "#2563eb") to ThemeFamily.BLUE,
    Triple("violet-night", "极光幻夜紫・夜晚", "#7c3aed") to ThemeFamily.VIOLET,
    Triple("rose-night", "热烈绯红玫瑰・夜晚", "#e11d48") to ThemeFamily.ROSE,
    Triple("amber-night", "明亮落日琥珀・夜晚", "#d97706") to ThemeFamily.AMBER,
    Triple("cyan-night", "霓虹赛博青・夜晚", "#0891b2") to ThemeFamily.CYAN,
    Triple("slate-night", "极简黑曜石・夜晚", "#334155") to ThemeFamily.SLATE,
)

/** 6 套多巴胺预设:高饱和明亮,模块 2 配同调性撞色(theme.ts DOPAMINE_PRESETS 原样)。 */
private val DOPAMINE_PRESETS = listOf(
    listOf("coral", "珊瑚汽水", "#FF6B6B", "#FFA94D"),
    listOf("honey", "蜂蜜柠檬", "#FFB020", "#FFD43B"),
    listOf("mint", "薄荷苏打", "#38D9A9", "#69DB7C"),
    listOf("soda", "苏打气泡", "#4DABF7", "#22B8CF"),
    listOf("grape", "葡萄糖果", "#9775FA", "#DA77F2"),
    listOf("bubble", "泡泡草莓", "#F783AC", "#FFA8A8"),
)

/** 内置的 20 套主题 (7 套白天模式 + 7 套夜晚模式 + 6 套多巴胺)。自定义主题不在其中。 */
fun builtInThemes(): Map<String, ThemeConfig> {
    val out = LinkedHashMap<String, ThemeConfig>()
    
    // 白天模式主题：低饱和1 + 中饱和2
    for ((triple, family) in DAY_MODE_PRESETS) {
        val (id, name, hex) = triple
        out[id] = makeTheme(
            id = id,
            name = name,
            primaryHex = hex,
            source = ThemeSource.SaturationLevel(family, SatLevel.LOW, SatLevel.MID),
            roles = if (family == ThemeFamily.SLATE) ThemeRoles.MIDNIGHT else ThemeRoles.STANDARD,
        )
    }
    
    // 夜晚模式主题：中高饱和1 + 高饱和2
    for ((triple, family) in NIGHT_MODE_PRESETS) {
        val (id, name, hex) = triple
        out[id] = makeTheme(
            id = id,
            name = name,
            primaryHex = hex,
            source = ThemeSource.SaturationLevel(family, SatLevel.MID_HIGH, SatLevel.HIGH),
            roles = if (family == ThemeFamily.SLATE) ThemeRoles.MIDNIGHT else ThemeRoles.STANDARD,
        )
    }
    
    // 多巴胺主题
    for ((id, name, m1, m2) in DOPAMINE_PRESETS) {
        out[id] = makeTheme(id, name, m1, ThemeSource.Custom(m1, m2))
    }
    
    return out
}

/** DIY 调色盘(theme.ts COLOR_SWATCHES 原样,顺序按色相)。 */
val COLOR_SWATCHES = listOf(
    // 暖:红 → 黄
    "#EF4444", "#F43F5E", "#FF6B6B", "#F783AC", "#EC4899", "#D946EF",
    "#F97316", "#FFB020", "#F59E0B", "#FFD43B", "#EAB308", "#B39B7D",
    // 绿:黄绿 → 青
    "#84CC16", "#22C55E", "#10B981", "#38D9A9", "#14B8A6", "#06B6D4",
    "#7D9082", "#6E8889",
    // 冷:蓝 → 紫
    "#0EA5E9", "#4DABF7", "#3B82F6", "#6366F1", "#8B5CF6", "#9775FA",
    "#A855F7", "#8A7C99", "#6B7F99", "#A67C6D", "#64748B", "#334155",
)

/** 由一条用户 DIY 主题记录转成可用的 ThemeConfig(theme.ts customThemeToConfig 原样)。 */
fun customThemeToConfig(id: String, name: String, m1: String, m2: String): ThemeConfig =
    makeTheme(id, name, m1, ThemeSource.Custom(m1, m2))
