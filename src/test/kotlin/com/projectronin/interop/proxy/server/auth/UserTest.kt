package com.projectronin.interop.proxy.server.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserTest {
    @Test
    fun `test getters`() {
        val user = User(tenantId = "tenant")
        assertEquals("tenant", user.tenantId)
    }
}
