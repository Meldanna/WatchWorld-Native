package com.watchworld.ui.theme

/**
 * `resolveThemeVars` 输出的强类型容器:两模块 11 档 + 气泡两档(明暗)。
 * 顺序跟随 [SHADE_KEYS](50…950),与 CSS 变量 `--m1-50` … `--m1-950` 一一对应。
 */
data class ThemeVars(
    val m1: List<String>,
    val m2: List<String>,
    /** 亮色气泡:主色极淡档 m1[2]。 */
    val bubble: String,
    /**
     * 暗色气泡:**主色极浓档 m1[10]**。
     *
     * ⚠️ 2026-10-13 修:此处原为 `m1[3]`(300 档,L≈84.5% 的**浅**色),
     * 夜晚模式下气泡亮成一块浅色、与深底脱节(用户截图实证);
     * [DarkBubbleScreenshotTest] 早就断言暗色气泡应是「深色块」,一直红着。
     */
    val bubbleDark: String,
) {
    init {
        require(m1.size == 11) { "m1 必须是 11 档,实际 ${m1.size}" }
        require(m2.size == 11) { "m2 必须是 11 档,实际 ${m2.size}" }
    }
}

/**
 * React 仓 `src/lib/theme.ts` 的 `resolveThemeVars` 直译。
 * 输入一整个 [ThemeConfig](不是 id),输出**字符串形式**的 24 个变量 ——
 * 值仍是 oklch 直 string/hex,由 [CssColors] 按需转 [androidx.compose.ui.graphics.Color]。
 *
 * 分支语义(与 JS 一致):
 * - custom 主题:m1/m2 走 [buildScale] 推导;气泡随主色极淡/极浓档
 * - 饱和度主题:按 m1/m2 各自的 [SatLevel] 查对应色阶表
 * - buildScale 失败(hex 非法)回退 family 表(JS `?? FAMILY_SCALES.emerald` / `?? m1`)
 */
fun resolveThemeVars(theme: ThemeConfig): ThemeVars {
    val m1: List<String>
    val m2: List<String>
    val bubble: String
    val bubbleDark: String

    when (val source = theme.source) {
        is ThemeSource.Custom -> {
            m1 = buildScale(source.m1) ?: FAMILY_SCALES.getValue(ThemeFamily.EMERALD)
            m2 = buildScale(source.m2) ?: m1
            bubble = m1[2]
            bubbleDark = m1[10]
        }
        is ThemeSource.SaturationLevel -> {
            m1 = when (source.m1Level) {
                SatLevel.LOW -> FAMILY_SCALES_LOW_SAT.getValue(source.family)
                SatLevel.MID_HIGH -> FAMILY_SCALES_MID_HIGH_SAT.getValue(source.family)
                SatLevel.HIGH -> FAMILY_SCALES.getValue(source.family)
                SatLevel.MID -> FAMILY_SCALES.getValue(source.family).map { v ->
                    val inner = v.substring(v.indexOf('(') + 1, v.length - 1).split(Regex("\\s+"))
                    val chroma = inner[1].toDouble() * 0.7
                    "oklch(${inner[0]} ${chroma.toCssFixed4()} ${inner[2]})"
                }
            }
            m2 = when (source.m2Level) {
                SatLevel.LOW -> FAMILY_SCALES_LOW_SAT.getValue(source.family)
                SatLevel.MID_HIGH -> FAMILY_SCALES_MID_HIGH_SAT.getValue(source.family)
                SatLevel.HIGH -> FAMILY_SCALES.getValue(source.family)
                SatLevel.MID -> FAMILY_SCALES.getValue(source.family).map { v ->
                    val inner = v.substring(v.indexOf('(') + 1, v.length - 1).split(Regex("\\s+"))
                    val chroma = inner[1].toDouble() * 0.7
                    "oklch(${inner[0]} ${chroma.toCssFixed4()} ${inner[2]})"
                }
            }
            bubble = m1[2]
            bubbleDark = m1[10]
        }
    }

    return ThemeVars(m1 = m1, m2 = m2, bubble = bubble, bubbleDark = bubbleDark)
}

/** 一致于 JS `(parseFloat×0.4).toFixed(4)`:固定 4 位小数、不丢尾零(快照实证 0.0680)。 */
private fun Double.toCssFixed4(): String = toFixedCss4()
