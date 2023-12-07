package com.projectronin.interop.proxy.server.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthResponseTest {
    @Test
    fun `test getters`() {
        val authResponse =
            AuthResponse(
                user =
                    User(
                        tenantId = "tenant",
                    ),
                userSession =
                    UserSession(
                        expiresAt = "2022-03-05T00:38:09",
                    ),
            )

        assertEquals("tenant", authResponse.user.tenantId)
        assertEquals("2022-03-05T00:38:09", authResponse.userSession.expiresAt)
    }
}
