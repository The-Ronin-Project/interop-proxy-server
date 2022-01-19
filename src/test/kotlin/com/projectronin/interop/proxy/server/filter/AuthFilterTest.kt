package com.projectronin.interop.proxy.server.filter

import com.projectronin.interop.proxy.server.auth.AuthResponse
import com.projectronin.interop.proxy.server.auth.AuthService
import com.projectronin.interop.proxy.server.auth.User
import com.projectronin.interop.proxy.server.auth.UserSession
import io.ktor.client.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthFilterTest {

    @Test
    fun `bad auth returns forbidden error`() {
        val http = mockk<HttpClient>()
        val chain = mockk<WebFilterChain>()
        val authService = AuthService(http, "/auth")
        val authFilter = AuthFilter(authService)
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "")
            )
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `good auth adds tenant ID`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val authService = mockk<AuthService>()
        val slot = slot<ServerWebExchange>() // capture slot for validating argument
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authResponse = AuthResponse(User("tenantId"), UserSession("12345"))
        val authFilter = AuthFilter(authService)
        every { authService.validateToken("12345") } returns authResponse
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertEquals(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER), authResponse.user.tenantId)
    }
}
