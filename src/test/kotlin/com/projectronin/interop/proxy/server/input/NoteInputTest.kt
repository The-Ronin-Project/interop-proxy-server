package com.projectronin.interop.proxy.server.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteInputTest {
    @Test
    fun `NoteInput can be created and accessed`() {
        val input = NoteInput("PatientTestId", PatientIdType.FHIR, "PractitionerTestId", "Example Note Text", "202206201245")
        assertEquals("PatientTestId", input.patientId)
        assertEquals(PatientIdType.FHIR, input.patientIdType)
        assertEquals("PractitionerTestId", input.practitionerFhirId)
        assertEquals("Example Note Text", input.noteText)
        assertEquals("202206201245", input.datetime)
    }
}
