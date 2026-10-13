package com.watchworld.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 主题基线快照对拍器(**仅测试源集**,不进 UI/不进产物)。
 *
 * fixture:`fixtures/theme-baseline/snapshots.json` —— **由本侧实现导出**
 * （生成器 [ThemeBaselineGenerator]），22 套主题 × 24 变量，含两个 custom 样本。
 *
 * ⚠️ 它**不是**「对齐 React」的证明。2026-10-13 起主题模型已换成
 *    「白天/黑夜 × 饱和度分级」，与 React 的 `family`/`custom` 两分法不同源；
 *    fixture 的定位改为**冻结当前实现**，用来拦住对色板/派生规则的无意改动。
 */
object ThemeSnapshotCompat {

    private val json = Json { ignoreUnknownKeys = true }

    /** 对拍全部主题;返回「主题 id → diff 列表」(空列表 = 完全一致)。 */
    fun compareAll(snapshotsJson: String): Map<String, List<String>> {
        val root = json.parseToJsonElement(snapshotsJson).let { it as JsonObject }
        val result = LinkedHashMap<String, List<String>>()
        for ((themeId, varsElement) in root) {
            val expected = (varsElement as JsonObject).mapValues { it.value.jsonPrimitive.content }
            // 快照里的 custom_test01/02 在 React 侧走 customThemeToConfig(不走 resolveTheme ——
            // 它们不在内置表里,resolveTheme 会回退 emerald 造成假失败),这里按同一对 HEX 还原。
            val config = if (themeId.startsWith("custom_")) {
                customThemeToConfig(
                    id = themeId,
                    name = themeId,
                    m1 = when (themeId) {
                        "custom_test01" -> "#34d399"
                        else -> "#f59e0b"
                    },
                    m2 = when (themeId) {
                        "custom_test01" -> "#818cf8"
                        else -> "#22d3ee"
                    },
                )
            } else {
                resolveTheme(themeId)
            }
            val actual = resolveThemeVars(config).asVarMap()
            result[themeId] = diffVars(expected, actual)
        }
        return result
    }

    /** 键集合 + 值的完整比较(字符串精确等值;oklch 数字格式化已在生成端对齐)。 */
    fun diffVars(expected: Map<String, String>, actual: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val keys = (expected.keys + actual.keys).toSortedSet()
        for (key in keys) {
            val e = expected[key]
            val a = actual[key]
            when {
                e == null -> problems += "$key: 快照没有此键,实现多出($a)"
                a == null -> problems += "$key: 实现缺少此键(期望 $e)"
                e != a -> problems += "$key: 期望=$e 实际=$a"
            }
        }
        return problems
    }

    /** [ThemeVars] 的 Map 视图(键 `--m1-600` 原样,值 = theme.ts 的原始字符串)。 */
    fun ThemeVars.asVarMap(): Map<String, String> {
        val out = LinkedHashMap<String, String>(24)
        for (i in SHADE_KEYS.indices) {
            out["--m1-${SHADE_KEYS[i]}"] = m1[i]
            out["--m2-${SHADE_KEYS[i]}"] = m2[i]
        }
        out["--m1-bubble"] = bubble
        out["--m1-bubble-dark"] = bubbleDark
        return out
    }
}
