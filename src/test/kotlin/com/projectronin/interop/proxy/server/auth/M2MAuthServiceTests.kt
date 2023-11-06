package com.projectronin.interop.proxy.server.auth

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder

class M2MAuthServiceTests {
    @Test
    fun `valid token can be validated`() {
        val token = "validToken"
        val authProperties = Auth0MachineToMachineProperties()
        authProperties.issuer = "https://dev-euweyz5a.us.auth0.com/"
        authProperties.audience = "proxy"
        val jwtDecoder = mockk<JwtDecoder>()
        every { jwtDecoder.decode(token) } returns mockk<Jwt>()

        assertTrue(M2MAuthService(authProperties, jwtDecoder).validateToken(token).success)
    }

    @Test
    fun `invalid token fails validation`() {
        val token = "invalidToken"
        val authProperties = Auth0MachineToMachineProperties()
        val jwtDecoder = mockk<JwtDecoder>()
        every { jwtDecoder.decode(token) }.throws(BadJwtException("Invalid"))

        assertFalse(M2MAuthService(authProperties, jwtDecoder).validateToken(token).success)
    }

    @Test
    fun `m2m token can be detected correctly`() {
        val token =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ii1uRW9uWlZYMGsxbFZZN0VSYjV1diJ9.eyJpc3MiOiJodHRwczovL2Rldi1ldXdleXo1YS51cy5hdXRoMC5jb20vIiwic3ViIjoicldocGhFanhRd0VTOHhjWmVmR29Sdk9SYklDbXgydDZAY2xpZW50cyIsImF1ZCI6Imh0dHBzOi8vZGV2LnByb2plY3Ryb25pbi5jb20vYmx1ZXByaW50IiwiaWF0IjoxNjQ1NjMxMTA0LCJleHAiOjE2NDU3MTc1MDQsImF6cCI6InJXaHBoRWp4UXdFUzh4Y1plZkdvUnZPUmJJQ214MnQ2IiwiZ3R5IjoiY2xpZW50LWNyZWRlbnRpYWxzIn0.NOSIGNEEDED"
        val authProperties = Auth0MachineToMachineProperties()
        authProperties.issuer = "https://dev-euweyz5a.us.auth0.com/"
        val jwtDecoder = mockk<JwtDecoder>()

        assertTrue(M2MAuthService(authProperties, jwtDecoder).isM2MToken(token))
    }

    @Test
    fun `non-m2m token can be detected correctly`() {
        val token =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ii1uRW9uWlZYMGsxbFZZN0VSYjV1diJ9.eyJpc3MiOiJodHRwczovL2Rldi1ldXdleXo1YS51cy5hdXRoMC5jb20vIiwic3ViIjoicldocGhFanhRd0VTOHhjWmVmR29Sdk9SYklDbXgydDZAY2xpZW50cyIsImF1ZCI6Imh0dHBzOi8vZGV2LnByb2plY3Ryb25pbi5jb20vYmx1ZXByaW50IiwiaWF0IjoxNjQ1NjMxMTA0LCJleHAiOjE2NDU3MTc1MDQsImF6cCI6InJXaHBoRWp4UXdFUzh4Y1plZkdvUnZPUmJJQ214MnQ2IiwiZ3R5IjoiY2xpZW50LWNyZWRlbnRpYWxzIn0.NOSIGNEEDED"
        val authProperties = Auth0MachineToMachineProperties()
        authProperties.issuer = "https://localhost/"
        val jwtDecoder = mockk<JwtDecoder>()

        assertFalse(M2MAuthService(authProperties, jwtDecoder).isM2MToken(token))
    }
}
