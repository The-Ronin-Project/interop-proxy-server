package com.projectronin.interop.proxy.server.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoteInputTest {
    @Test
    fun `NoteInput can be created and accessed`() {
        val input =
            NoteInput(
                "PatientTestId",
                PatientIdType.FHIR,
                "PractitionerTestId",
                "Example Note Text",
                "202206201245",
                NoteSender.PRACTITIONER,
                true,
            )
        assertEquals("PatientTestId", input.patientId)
        assertEquals(PatientIdType.FHIR, input.patientIdType)
        assertEquals("PractitionerTestId", input.practitionerFhirId)
        assertEquals("Example Note Text", input.noteText)
        assertEquals("202206201245", input.datetime)
        assertEquals(NoteSender.PRACTITIONER, input.noteSender)
        assertTrue(input.isAlert)
    }

    @Test
    fun `validates successfully with datetime with seconds`() {
        val input =
            NoteInput(
                "PatientTestId",
                PatientIdType.FHIR,
                "PractitionerTestId",
                "Example Note Text",
                "20220620124533",
                NoteSender.PRACTITIONER,
                true,
            )
        input.validate()
    }

    @Test
    fun `validates successfully with datetime without seconds`() {
        val input =
            NoteInput(
                "PatientTestId",
                PatientIdType.FHIR,
                "PractitionerTestId",
                "Example Note Text",
                "202206201245",
                NoteSender.PRACTITIONER,
                true,
            )
        input.validate()
    }

    @Test
    fun `validate fails for incorrect datetime format`() {
        val input =
            NoteInput(
                "PatientTestId",
                PatientIdType.FHIR,
                "PractitionerTestId",
                "Example Note Text",
                "20230515 30042",
                NoteSender.PRACTITIONER,
                true,
            )
        val exception = assertThrows<IllegalArgumentException> { input.validate() }
        assertEquals("datetime must be of form \"yyyyMMddHHmm[ss]\" but was \"20230515 30042\"", exception.message)
    }
}
