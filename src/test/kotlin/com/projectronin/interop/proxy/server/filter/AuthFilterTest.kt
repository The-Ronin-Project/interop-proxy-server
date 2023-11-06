package com.projectronin.interop.proxy.server.filter

import com.projectronin.interop.proxy.server.auth.AuthResponse
import com.projectronin.interop.proxy.server.auth.M2MAuthService
import com.projectronin.interop.proxy.server.auth.ParsedM2MToken
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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthFilterTest {
    @Test
    fun `actuator requests skip auth`() {
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/actuator/health")
                    .header("Authorization", "")
            )

        val mono = mockk<Mono<Void>>()
        val chain = mockk<WebFilterChain> {
            every { filter(exchange) } returns mono
        }

        val authFilter = AuthFilter(mockk(), mockk())
        val response = authFilter.filter(exchange, chain)
        assertEquals(response, mono)
    }

    @Test
    fun `null bearer token returns forbidden error`() {
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
    fun `no bearer token returns forbidden error`() {
        val http = mockk<HttpClient>()
        val chain = mockk<WebFilterChain>()
        val userAuthService = UserAuthService(http, "/auth")
        val m2MAuthService = mockk<M2MAuthService>()
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
            )
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `null user auth returns forbidden`() {
        val chain = mockk<WebFilterChain>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns false
        every { userAuthService.validateToken("12345") } returns null
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `error user auth returns forbidden`() {
        val chain = mockk<WebFilterChain>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns false
        every { userAuthService.validateToken("12345") } throws Exception()
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
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(mockk(relaxed = true), true)
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertNull(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER))
    }

    @Test
    fun `good m2m auth does not add tenant ID with partial claims`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val jwt = mockk<Jwt>()
        val slot = slot<ServerWebExchange>() // capture slot for validating argument
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(jwt, true)
        every { chain.filter(capture(slot)) } answers { mono }
        every { jwt.claims } returns mapOf(
            "urn:projectronin:authorization:claims:version:1" to mapOf<String, Any>(
                "user" to emptyMap<String, Any>()
            )
        )
        authFilter.filter(exchange, chain)
        assertNull(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER))
    }

    @Test
    fun `good m2m auth does add tenant ID with full claims`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val jwt = mockk<Jwt>()
        val slot = slot<ServerWebExchange>() // capture slot for validating argument
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(jwt, true)
        every { chain.filter(capture(slot)) } answers { mono }
        every { jwt.claims } returns mapOf(
            "urn:projectronin:authorization:claims:version:1" to mapOf<String, Any>(
                "user" to mapOf<String, Any>(
                    "loginProfile" to mapOf<String, Any>(
                        "accessingTenantId" to "apposnd"
                    )
                )
            )
        )
        authFilter.filter(exchange, chain)
        assertEquals("apposnd", slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER))
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
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(mockk(relaxed = true), false)
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `bad m2m auth falls back to good user auth, adds tenant ID`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val slot = slot<ServerWebExchange>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authResponse = AuthResponse(User("tenantId"), UserSession("12345"))
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(mockk(relaxed = true), false)
        every { userAuthService.validateToken("12345") } returns authResponse
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertEquals(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER), authResponse.user.tenantId)
    }

    @Test
    fun `bad m2m auth falls back to bad user auth, returns forbidden error`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val slot = slot<ServerWebExchange>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/graphql")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(mockk(relaxed = true), false)
        every { userAuthService.validateToken("12345") } returns null
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `general tenant health requests skip auth`() {
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/tenants/health")
                    .header("Authorization", "")
            )

        val mono = mockk<Mono<Void>>()
        val chain = mockk<WebFilterChain> {
            every { filter(exchange) } returns mono
        }

        val authFilter = AuthFilter(mockk(), mockk())
        val response = authFilter.filter(exchange, chain)
        assertEquals(response, mono)
    }

    @Test
    fun `specific tenant health requests skip auth`() {
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/tenants/test/health")
                    .header("Authorization", "")
            )

        val mono = mockk<Mono<Void>>()
        val chain = mockk<WebFilterChain> {
            every { filter(exchange) } returns mono
        }

        val authFilter = AuthFilter(mockk(), mockk())
        val response = authFilter.filter(exchange, chain)
        assertEquals(response, mono)
    }

    @Test
    fun `other tenant endpoints require auth`() {
        val chain = mockk<WebFilterChain>()
        val mono = mockk<Mono<Void>>()
        val userAuthService = mockk<UserAuthService>()
        val m2MAuthService = mockk<M2MAuthService>()
        val slot = slot<ServerWebExchange>() // capture slot for validating argument
        val exchange = MockServerWebExchange
            .from(
                MockServerHttpRequest.get("/tenants/test/not-health")
                    .header("Authorization", "Bearer 12345")
            )
        val authFilter = AuthFilter(userAuthService, m2MAuthService)
        every { m2MAuthService.isM2MToken("12345") } returns true
        every { m2MAuthService.validateToken("12345") } returns ParsedM2MToken(mockk(relaxed = true), true)
        every { chain.filter(capture(slot)) } answers { mono }
        authFilter.filter(exchange, chain)
        assertNull(slot.captured.request.headers.getFirst(AUTHZ_TENANT_HEADER))
    }
}
