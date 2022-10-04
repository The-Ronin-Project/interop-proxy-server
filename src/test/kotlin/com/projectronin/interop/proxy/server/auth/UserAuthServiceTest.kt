package com.projectronin.interop.proxy.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserAuthServiceTest {
    private val mockWebServer = MockWebServer()

    private val validAuthServiceResponse = this::class.java.getResource("/ExampleAuthResponse.json")!!.readText()

    @Test
    fun `ensure validate token returns correctly with real data`() {
        val expectedResponse = AuthResponse(
            user = User(
                tenantId = "peeng"
            ),
            userSession = UserSession(
                expiresAt = "2022-03-05T00:38:09"
            )
        )

        mockWebServer.enqueue(
            MockResponse().setBody(validAuthServiceResponse).setHeader("Content-Type", "application/json")
                .setResponseCode(200)
        )
        mockWebServer.start()
        val userAuthService = UserAuthService(getClient(), mockWebServer.url("/auth").toString())
        val responseBody = userAuthService.validateToken("fake token")

        assertEquals(expectedResponse, responseBody)
    }

    @Test
    fun `ensure get validation handles unauthorized token`() {
        mockWebServer.enqueue(
            MockResponse().setBody("Unauthorized").setHeader("Content-Type", "application/json").setResponseCode(401)
        )
        mockWebServer.start()
        val userAuthService = UserAuthService(getClient(), mockWebServer.url("/auth").toString())
        val responseBody = userAuthService.validateToken("fake token")

        assertNull(responseBody)
    }

    @Test
    fun `ensure get validation throws an exception on server error`() {
        mockWebServer.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500)
        )
        mockWebServer.start()
        val userAuthService = UserAuthService(getClient(), mockWebServer.url("/auth").toString())

        assertThrows<ServerResponseException> {
            userAuthService.validateToken("fake token")
        }
    }

    private fun getClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                jackson()
            }
            expectSuccess = true
        }
    }
}
