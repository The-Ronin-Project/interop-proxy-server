package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdentifierTest {
    @Test
    fun `Ensure an identifier can be created`() {
        val testIdentifier = Identifier("system", "123")
        assertEquals("system", testIdentifier.system)
        assertEquals("123", testIdentifier.value)
    }
}
