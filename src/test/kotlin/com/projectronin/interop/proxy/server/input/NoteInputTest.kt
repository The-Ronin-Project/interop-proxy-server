package com.projectronin.interop.proxy.server.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteInputTest {
    @Test
    fun `NoteInput can be created and accessed`() {
        val input = NoteInput("PatientTestId", "PractitionerTestId", "Example Note Text")
        assertEquals("PatientTestId", input.patientFhirId)
        assertEquals("PractitionerTestId", input.practitionerFhirId)
        assertEquals("Example Note Text", input.noteText)
    }
}
