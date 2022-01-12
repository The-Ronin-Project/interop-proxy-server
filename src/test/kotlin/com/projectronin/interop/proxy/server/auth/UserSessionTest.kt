package com.projectronin.interop.proxy.server.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserSessionTest {
    @Test
    fun `test getters`() {
        val userSession = UserSession(expiresAt = "2022-03-05T00:38:09")
        assertEquals("2022-03-05T00:38:09", userSession.expiresAt)
    }
}
