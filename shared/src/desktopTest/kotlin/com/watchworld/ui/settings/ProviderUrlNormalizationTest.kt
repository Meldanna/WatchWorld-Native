package com.watchworld.ui.settings

import com.watchworld.model.ApiProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderUrlNormalizationTest {

    @Test
    fun openAiCompatibleUrlsNormalizeToApiRoot() {
        assertEquals(
            "https://provider.test/v1",
            normalizeProviderBaseUrl("https://provider.test", ApiProviderType.OPENAI),
        )
        assertEquals(
            "https://provider.test/v1",
            normalizeProviderBaseUrl("https://provider.test/v1/chat/completions", ApiProviderType.OPENAI),
        )
        assertEquals(
            "https://provider.test/v1",
            normalizeProviderBaseUrl("https://provider.test/v1/models", ApiProviderType.OPENAI),
        )
    }

    @Test
    fun claudeUrlsNormalizeToV1ApiRoot() {
        assertEquals(
            "https://provider.test/v1",
            normalizeProviderBaseUrl("https://provider.test", ApiProviderType.CLAUDE),
        )
        assertEquals(
            "https://provider.test/v1",
            normalizeProviderBaseUrl("https://provider.test/v1/messages", ApiProviderType.CLAUDE),
        )
        assertEquals(
            "https://provider.test/v1",
            normalizeProviderBaseUrl("https://provider.test/v1/models", ApiProviderType.CLAUDE),
        )
    }

    @Test
    fun geminiUrlIsNotModifiedAndBlankUrlUsesDefault() {
        assertEquals(
            "https://provider.test/custom",
            normalizeProviderBaseUrl("https://provider.test/custom/", ApiProviderType.GEMINI),
        )
        assertNull(normalizeProviderBaseUrl(" ", ApiProviderType.OPENAI))
    }
}
