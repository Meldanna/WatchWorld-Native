package com.watchworld.ui.theme

import java.io.File
import kotlin.test.Test

/**
 * `fixtures/theme-baseline/snapshots.json` 的**生成器**（唯一权威出口）。
 *
 * ## 为什么要它（2026-10-13 变更）
 *
 * 原生成器是 React 侧的 `fixtures/tools/gen-theme-baseline.ts`，导出 13 套内置
 * （`PRESETS` 7 + `DOPAMINE` 6）+ 2 自定义样本。但原生侧后来把主题模型换成了
 * **白天/黑夜 × 饱和度分级**（20 套内置，id 形如 `emerald-day` / `emerald-night`），
 * 旧 fixture 的键（`emerald` / `midnight` …）在新模型里**根本不存在**，
 * 对拍必然失败 —— 而 fixture 从 `c836984` 起就没再更新过（用户确认「当时忘了」）。
 *
 * 现在改为**由本侧实现导出**：fixture 的语义从「React 基准」变成
 * 「当前主题模型的冻结快照」—— 它的作用不再是证明「和 React 一样」，
 * 而是**拦住任何对色板/派生规则的无意改动**（改了就必须显式重新生成一次，
 * 从而在 code review 里被看见）。
 *
 * ## 用法
 *
 * 改了 `ThemeCatalog` / `ThemeVars` / `ThemeScale` 里任何**有意的**取值后：
 * ```
 * ./gradlew :shared:desktopTest --tests "com.watchworld.ui.theme.ThemeBaselineGenerator"
 * ```
 * 然后 `git diff fixtures/theme-baseline/snapshots.json` 逐条确认差异都是你想要的。
 *
 * ⚠️ 它**同时**是快照测试的逆向操作：跑完生成器，`ThemeSnapshotTest` 必绿
 * （因为基准就是实现自己产出的）。**因此绝不能把它塞进 CI 当作验证** ——
 * 它只负责「冻结」，验证的责任在人工 review 那份 diff。
 */
class ThemeBaselineGenerator {

    private fun repoRoot(): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "fixtures").isDirectory }
            ?: error("找不到仓库根（从 ${start.absolutePath} 向上找 fixtures/）")
    }

    @Test
    fun writeSnapshots() {
        val out = LinkedHashMap<String, Map<String, String>>()

        // 20 套内置（白天 7 + 黑夜 7 + 多巴胺 6）
        for ((id, cfg) in builtInThemes()) {
            out[id] = ThemeSnapshotCompat.run { resolveThemeVars(cfg).asVarMap() }
        }

        // 2 个自定义样本：验证 buildScale 的 L_LADDER / C_BELL / toFixed 精度。
        // ⚠️ 与 ThemeSnapshotCompat.compareAll 里硬编码的一对 HEX 必须一致，
        //    否则对拍时重建出的 config 与 fixture 不符。
        val customs = mapOf(
            "custom_test01" to ("#34d399" to "#818cf8"),
            "custom_test02" to ("#f59e0b" to "#22d3ee"),
        )
        for ((id, hex) in customs) {
            val cfg = customThemeToConfig(id, id, hex.first, hex.second)
            out[id] = ThemeSnapshotCompat.run { resolveThemeVars(cfg).asVarMap() }
        }

        val target = File(repoRoot(), "fixtures/theme-baseline/snapshots.json")
        target.writeText(render(out))
        println("已写入 ${target.absolutePath}：${out.size} 套主题")
    }

    /** 稳定输出：键按插入序、变量按 `SHADE_KEYS` 序，缩进 2 空格，与本仓其余 JSON 风格一致。 */
    private fun render(all: Map<String, Map<String, String>>): String {
        val sb = StringBuilder("{\n")
        all.entries.forEachIndexed { ti, (id, vars) ->
            sb.append("  \"").append(id).append("\": {\n")
            vars.entries.forEachIndexed { vi, (k, v) ->
                sb.append("    \"").append(k).append("\": \"").append(v).append('"')
                if (vi != vars.size - 1) sb.append(',')
                sb.append('\n')
            }
            sb.append("  }")
            if (ti != all.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append("}\n")
        return sb.toString()
    }
}
