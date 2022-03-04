package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReferenceTest {
    @Test
    fun `ensure reference can be created`() {
        val testIdentifier = Identifier(
            value = "value",
            system = "system"
        )
        val testReference = Reference(
            id = "123",
            reference = "Reference",
            identifier = testIdentifier,
            type = "type",
            display = "display"
        )
        assertEquals("123", testReference.id)
        assertEquals("Reference", testReference.reference)
        assertEquals(testIdentifier, testReference.identifier)
        assertEquals("type", testReference.type)
        assertEquals("display", testReference.display)
    }
}
