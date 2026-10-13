package com.watchworld.domain

import com.watchworld.model.RegexRule
import com.watchworld.model.RegexScope

/*
 * `src/lib/regexProcessor.ts`（`applyRegexRules`:3）。
 *
 * ⚠️ 与 JS 的三处语义差异必须手工补齐，否则同一条规则在两版输出不同：
 *  1. 替换串语法：JS 的 `$&` / `` $` `` / `$'` / `$$` / `$<name>` / `$n`、`$nn`，Kotlin `Regex.replace`
 *     只认 `$n` / `${name}`，且反斜杠是转义符 → 这里自己展开替换串，不交给 `Regex.replace`。
 *  2. 无 `g` 标志时 JS 只替换**第一处**；`flags` 为空串时按 TS 的 `rule.flags || 'g'` 当作 `g`。
 *  3. 非法 pattern / 非法 flags 在 JS 里 `new RegExp` 抛错 → 跳过该条规则，不中断后续规则。
 */

/** 正则作用方向，对应 TS 的 `scope: 'input' | 'output'` 参数。 */
enum class RegexApplyScope { INPUT, OUTPUT }

/**
 * `testRegexRule` 的结果（`regexProcessor.ts:26-50` 的返回对象）。
 *
 * @param matchCount 命中处数。⚠️ 参考版非全局分支走的是 `text.match(reg).length`，
 *   那在 JS 里数的是「首个匹配 + 捕获组个数」，不是命中处数；这里一律按**真实命中处数**算，
 *   因为界面要显示的是「匹配命中: N 处」。
 */
data class RegexTestResult(
    val success: Boolean,
    val result: String,
    val matchCount: Int,
    val error: String? = null,
)

/**
 * `regexProcessor.ts:26 testRegexRule`：拿样例文本跑一次替换（正则库的「实时沙箱测试」）。
 *
 * 与 [applyRegexRules] 的区别：这里**校验并暴露错误**（非法 pattern / 非法 flags 返回
 * `success = false`），而清洗链路是静默跳过该条规则。
 */
fun testRegexRule(
    text: String,
    pattern: String,
    flags: String = "",
    replacement: String = "",
): RegexTestResult {
    val compiled = compileJsRegex(pattern, flags.ifEmpty { "g" })
        ?: return RegexTestResult(
            success = false,
            result = text,
            matchCount = 0,
            error = "正则表达式语法错误，或使用了不支持的标志。",
        )
    return RegexTestResult(
        success = true,
        result = replaceJs(text, compiled.regex, compiled.global, replacement),
        matchCount = compiled.regex.findAll(text).count(),
    )
}

/** `regexProcessor.ts:3 applyRegexRules`。 */
fun applyRegexRules(text: String, rules: List<RegexRule>, scope: RegexApplyScope): String {
    if (text.isEmpty()) return ""
    var result = text
    for (rule in rules) {
        if (!rule.enabled) continue
        if (!rule.scope.appliesTo(scope)) continue
        val compiled = compileJsRegex(rule.pattern, rule.flags.ifEmpty { "g" }) ?: continue
        result = replaceJs(result, compiled.regex, compiled.global, rule.replacement)
    }
    return result
}

private fun RegexScope.appliesTo(scope: RegexApplyScope): Boolean = when (this) {
    RegexScope.INPUT -> scope == RegexApplyScope.INPUT
    RegexScope.OUTPUT -> scope == RegexApplyScope.OUTPUT
    RegexScope.BOTH -> true
}

internal class JsRegex(val regex: Regex, val global: Boolean)

/**
 * 按 JS 的 flags 编译。`u` / `d` 无对应选项、行为上可忽略；`y`（sticky）不支持 → 视为非法跳过。
 * 重复或未知的 flag 在 JS 里抛 SyntaxError，这里同样返回 `null`。
 */
internal fun compileJsRegex(pattern: String, flags: String): JsRegex? {
    if (flags.toSet().size != flags.length) return null
    val options = mutableSetOf<RegexOption>()
    var global = false
    for (flag in flags) {
        when (flag) {
            'g' -> global = true
            'i' -> options += RegexOption.IGNORE_CASE
            'm' -> options += RegexOption.MULTILINE
            's' -> options += RegexOption.DOT_MATCHES_ALL
            'u', 'd' -> Unit
            else -> return null
        }
    }
    return try {
        JsRegex(Regex(pattern, options), global)
    } catch (_: Exception) {
        null
    }
}

/** `String.prototype.replace(regex, string)` 的语义：global 替换全部，否则只替换第一处。 */
internal fun replaceJs(input: String, regex: Regex, global: Boolean, replacement: String): String {
    val matches = if (global) regex.findAll(input).toList() else listOfNotNull(regex.find(input))
    if (matches.isEmpty()) return input
    val out = StringBuilder()
    var last = 0
    for (match in matches) {
        out.append(input, last, match.range.first)
        out.append(expandJsReplacement(replacement, match, input))
        last = match.range.last + 1
    }
    out.append(input, last, input.length)
    return out.toString()
}

/** 展开 JS 替换模式（ECMAScript GetSubstitution）。无法识别的 `$x` 原样保留，与 JS 一致。 */
private fun expandJsReplacement(replacement: String, match: MatchResult, input: String): String {
    if ('$' !in replacement) return replacement
    val groupCount = match.groupValues.size - 1
    val out = StringBuilder()
    var i = 0
    while (i < replacement.length) {
        val c = replacement[i]
        if (c != '$' || i + 1 >= replacement.length) {
            out.append(c)
            i++
            continue
        }
        val next = replacement[i + 1]
        when {
            next == '$' -> { out.append('$'); i += 2 }
            next == '&' -> { out.append(match.value); i += 2 }
            next == '`' -> { out.append(input, 0, match.range.first); i += 2 }
            next == '\'' -> { out.append(input, match.range.last + 1, input.length); i += 2 }
            next.isDigit() -> {
                // 先试两位组号（$10），不存在再退回一位（$1 + "0"）。
                val two = if (i + 2 < replacement.length && replacement[i + 2].isDigit()) {
                    replacement.substring(i + 1, i + 3).toInt()
                } else -1
                val one = next.digitToInt()
                when {
                    two in 1..groupCount -> { out.append(match.groupValues[two]); i += 3 }
                    one in 1..groupCount -> { out.append(match.groupValues[one]); i += 2 }
                    else -> { out.append(c); i++ }
                }
            }
            next == '<' -> {
                val close = replacement.indexOf('>', i + 2)
                if (close < 0) {
                    out.append(c)
                    i++
                } else {
                    val name = replacement.substring(i + 2, close)
                    val value = try { match.groups[name]?.value } catch (_: Exception) { null }
                    out.append(value ?: "")
                    i = close + 1
                }
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}
