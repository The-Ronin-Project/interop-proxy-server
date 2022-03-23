package com.projectronin.interop.proxy.server.filter

import com.projectronin.interop.proxy.server.auth.AuthResponse
import com.projectronin.interop.proxy.server.auth.M2MAuthService
import com.projectronin.interop.proxy.server.auth.User
import com.projectronin.interop.proxy.server.auth.UserAuthService
import com.projectronin.interop.proxy.server.auth.UserSession
import io.ktor.client.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthFilterTest {

    @Test
    fun `bad user auth returns forbidden error`() {
        val http = mockk<HttpClient>()
        val chain = mockk<WebFilterChain>()
        val userAuthService = UserAuthService(http, "/auth")
        val m2MAuthService = mockk<M2MAuthService>()
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "")
            )
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `good user auth adds tenant ID`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val slot = slot<ServerWebExchange>() // capture slot for validating argument
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authResponse = AuthResponse(User("tenantId"), UserSession("12345"))
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns false
        every { userAuthService.validateToken("12345") } returns authResponse
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertEquals(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER), authResponse.user.tenantId)
    }

    @Test
    fun `good m2m auth does not add tenant ID`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val slot = slot<ServerWebExchange>() // capture slot for validating argument
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns true
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertNull(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER))
    }

    @Test
    fun `bad m2m auth returns forbidden error`() {
        val chain = mockk<WebFilterChain>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns false
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }
}
