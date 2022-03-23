package com.projectronin.interop.proxy.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ServerResponseException
import io.ktor.client.features.json.JsonFeature
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junitpioneer.jupiter.ClearEnvironmentVariable
import org.junitpioneer.jupiter.SetEnvironmentVariable

class UserAuthServiceTest {
    private val mockWebServer = MockWebServer()

    private val validAuthServiceResponse = this::class.java.getResource("/ExampleAuthResponse.json")!!.readText()

    @Test
    @SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc")
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
            MockResponse().setBody(validAuthServiceResponse).setHeader("Content-Type", "application/json").setResponseCode(200)
        )
        mockWebServer.start()
        val userAuthService = UserAuthService(getClient(), mockWebServer.url("/auth").toString())
        val responseBody = userAuthService.validateToken("fake token")

        assertEquals(expectedResponse, responseBody)
    }

    @Test
    @SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc")
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
    @SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc")
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

    @Test
    @ClearEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET")
    fun `ensure get validation throws an exception when secret environment var is not set`() {
        mockWebServer.enqueue(
            MockResponse().setBody(validAuthServiceResponse).setHeader("Content-Type", "application/json").setResponseCode(200)
        )
        mockWebServer.start()
        val userAuthService = UserAuthService(getClient(), mockWebServer.url("/auth").toString())

        assertThrows<IllegalStateException> {
            userAuthService.validateToken("fake token")
        }
    }

    private fun getClient(): HttpClient {
        return HttpClient(CIO) {
            install(JsonFeature)
        }
    }
}
