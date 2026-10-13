package com.watchworld.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenEstimatorTest {

    @Test
    fun emptyTextReturnsZero() {
        assertEquals(0, estimateTokens(null))
        assertEquals(0, estimateTokens(""))
    }

    @Test
    fun cjkCharactersEstimatedAt11PerChar() {
        // 10 个中文字符 → ceil(10 * 1.1) = 11
        // length/4 = ceil(10/4) = 3
        // max(11, 3) = 11
        assertEquals(11, estimateTokens("一二三四五六七八九十"))
    }

    @Test
    fun englishWordsEstimatedAt13PerWord() {
        // 5 个英文单词 → ceil(5 * 1.3) = 7
        // length/4 = ceil(19/4) = 5
        // max(7, 5) = 7
        val text = "hello world foo bar baz"
        assertEquals(7, estimateTokens(text))
    }

    @Test
    fun mixedCjkAndEnglish() {
        // "Hello世界" → 1 个英文单词（Hello）+ 2 个中文字符（世界）
        // cjk:  ceil(2 * 1.1) = 3
        // words: ceil(1 * 1.3) = 2
        // tokens = 5, length = 7, ceil(7/4) = 2
        // max(5, 2) = 5
        //
        // ⚠️ 2026-10-13 修：原期望写 6，注释还写「2 个英文单词」——"Hello世界" 里只有
        //    `Hello` 一个单词（`世界` 被 CJK 正则替换成空格后不产生词）。
        //    用 node 跑 TS 原实现（`src/lib/tokenEstimator.ts`）实测返回 5，
        //    与原生实现一致；是这条用例的期望值算错了。
        assertEquals(5, estimateTokens("Hello世界"))
    }

    @Test
    fun lengthFallbackForShortText() {
        // 单个英文字母 "a" → 1 word → ceil(1 * 1.3) = 2
        // length/4 = ceil(1/4) = 1
        // max(2, 1) = 2
        assertEquals(2, estimateTokens("a"))
    }
}