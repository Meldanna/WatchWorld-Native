package com.watchworld.net

import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderApiTest {

    @Test
    fun usesConfiguredApiKeyForModelListAndConnectionCheck() = runBlocking {
        val authorizationHeaders = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            authorizationHeaders += request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"data":[{"id":"gpt-test"}]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        try {
            val api = ProviderApi(client)
            val provider = ApiProviderConfig(
                id = "test",
                name = "Test",
                type = ApiProviderType.OPENAI,
                baseUrl = "https://provider.test/v1",
                apiKey = "test-key",
                defaultModel = "gpt-test",
            )

            assertEquals(listOf("gpt-test"), api.fetchModels(provider).models)
            assertTrue(api.testConnection(provider).success)
            assertTrue(authorizationHeaders.all { it?.startsWith("Bearer ") == true })
            assertEquals(
                listOf(provider.apiKey, provider.apiKey),
                authorizationHeaders.map { it?.removePrefix("Bearer ") },
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun reportsHtmlModelResponseAsNonJson() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                content = "<!doctype html><html><head></head><body>Not an API route</body></html>",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        })

        try {
            val result = ProviderApi(client).fetchModels(
                ApiProviderConfig(
                    id = "test",
                    name = "Test",
                    type = ApiProviderType.OPENAI,
                    baseUrl = "https://provider.test/v1",
                    apiKey = "test-key",
                ),
            )

            assertTrue(result.error.orEmpty().contains("非 JSON"))
            assertTrue(result.error.orEmpty().contains("text/html"))
        } finally {
            client.close()
        }
    }

    @Test
    fun includesServerErrorBodyWhenModelRequestFails() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                content = """{"error":"model listing is unavailable"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        try {
            val result = ProviderApi(client).fetchModels(
                ApiProviderConfig(
                    id = "test",
                    name = "Test",
                    type = ApiProviderType.CLAUDE,
                    baseUrl = "https://provider.test/v1",
                    apiKey = "test-key",
                ),
            )

            assertTrue(result.error.orEmpty().contains("500"))
            assertTrue(result.error.orEmpty().contains("model listing is unavailable"))
        } finally {
            client.close()
        }
    }
}
