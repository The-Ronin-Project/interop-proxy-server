package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
class ParticipantTest {
    @Test
    fun `ensure practitioner can be created`() {
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
        val testPractitioner = Participant(actor = testReference)
        assertEquals(testReference, testPractitioner.actor)
    }
}
