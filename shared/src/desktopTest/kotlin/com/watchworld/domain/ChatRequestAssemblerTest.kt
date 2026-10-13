package com.watchworld.domain

import com.watchworld.data.DEFAULT_APP_PREFERENCES
import com.watchworld.model.Agent
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.ChatSession
import com.watchworld.model.DefaultSessionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChatRequestAssemblerTest {
    private val provider = ApiProviderConfig(
        id = "p",
        name = "Provider",
        type = ApiProviderType.OPENAI,
        defaultModel = "provider-model",
    )
    private val agent = Agent(id = "a", temperature = 0.6, topP = 0.8)
    private val context = AssembledContext("system", emptyList(), emptyList(), emptyList(), "")

    @Test
    fun samplingFallbackOrderMatchesApiTsNativePath() {
        val preferences = DEFAULT_APP_PREFERENCES.copy(
            defaultSession = DefaultSessionConfig(
                temperature = 0.5,
                topP = 0.7,
                topK = 12,
                frequencyPenalty = 0.2,
                presencePenalty = 0.3,
                maxTokens = 900,
                toolCallStepLimit = 4,
            ),
        )
        val params = ChatRequestAssembler.assemble(
            ChatSession(id = "s", agentId = "a", temperature = 0.4, topP = 0.65, toolCallStepLimit = 2),
            agent,
            provider,
            preferences,
            context,
        )
        assertEquals(0.4, params.temperature)
        assertEquals(0.65, params.topP)
        assertEquals(12, params.topK)
        assertEquals(0.2, params.frequencyPenalty)
        assertEquals(0.3, params.presencePenalty)
        assertEquals(900, params.maxTokens)
        assertEquals(2, params.toolCallStepLimit)
    }

    @Test
    fun nullSamplingKeysRemainNullWhenEveryFallbackIsNull() {
        val params = ChatRequestAssembler.assemble(
            ChatSession(id = "s", agentId = "a"),
            agent.copy(topP = null),
            provider,
            DEFAULT_APP_PREFERENCES.copy(defaultSession = DefaultSessionConfig(toolCallStepLimit = 0)),
            context,
        )
        assertEquals(agent.temperature, params.temperature)
        assertEquals(0.95, params.topP)
        assertNull(params.topK)
        assertNull(params.frequencyPenalty)
        assertNull(params.presencePenalty)
        assertNull(params.maxTokens)
        assertEquals(0, params.toolCallStepLimit)
    }

    @Test
    fun explicitFalseStreamingAndExplicitZeroToolLimitArePreserved() {
        val params = ChatRequestAssembler.assemble(
            ChatSession(id = "s", agentId = "a", streamingEnabled = false, toolCallStepLimit = 0),
            agent,
            provider,
            DEFAULT_APP_PREFERENCES,
            context,
        )
        assertFalse(params.stream)
        assertEquals(0, params.toolCallStepLimit)
        assertEquals("provider-model", params.model)
    }
}
