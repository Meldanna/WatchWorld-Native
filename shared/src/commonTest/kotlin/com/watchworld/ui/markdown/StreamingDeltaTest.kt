package com.watchworld.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T13 追加式增量逻辑（[streamingDelta]）。
 *
 * 库的 `StreamingMarkdownState.append` 只重解析不稳定尾部 —— 它是否正确，取决于我们
 * 每次只喂**新增后缀**。这个纯函数就是那层判断：前缀追加返回后缀，否则返回 null（调用方重建状态）。
 */
class StreamingDeltaTest {

    @Test
    fun firstChunkReturnsWholeContent() {
        assertEquals("abc", streamingDelta("", "abc"))
    }

    @Test
    fun growthReturnsOnlyNewSuffix() {
        assertEquals("def", streamingDelta("abc", "abcdef"))
        assertEquals("!", streamingDelta("abcde", "abcde!"))
    }

    @Test
    fun noGrowthReturnsEmptySuffix() {
        assertEquals("", streamingDelta("abc", "abc"))
        assertEquals("", streamingDelta("", ""))
    }

    @Test
    fun nonAppendReturnsNull() {
        // 整体被替换（前缀不同）→ null，调用方重建状态。
        assertNull(streamingDelta("abc", "xyz")) // 同长度替换也能识别
        assertNull(streamingDelta("abcdef", "abc")) // 变短
        assertNull(streamingDelta("abc", "")) // 清空
    }
}
