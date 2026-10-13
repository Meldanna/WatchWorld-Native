package com.watchworld.net

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class DeepSeekLiveSmokeTest {

    @Test
    fun deepSeekOpenAiCompatibleStreamPassesThroughParserAndAdapter() = runBlocking {
        assumeTrue(
            "Set WW_T04_LIVE_DEEPSEEK=1 to run the opt-in live API smoke test",
            System.getenv("WW_T04_LIVE_DEEPSEEK") == "1",
        )

        val client = ChatStreamClient(HttpClientFactory.create())
        try {
            val events = client.streamAssistantEvents("请只回复：DEEPSEEK_STREAM_OK").toList()
            val text = events.filterIsInstance<ProviderStreamEvent.TextDelta>().joinToString("") { it.text }
            assertTrue(text.isNotBlank(), "DeepSeek stream should produce normalized text events")
        } finally {
            client.close()
        }
    }
}
