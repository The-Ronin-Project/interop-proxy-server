package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.proxy.server.input.NoteInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteHandlerTest {
    private lateinit var noteHandler: NoteHandler

    @Test
    fun `accepts note`() {
        noteHandler = NoteHandler()
        val noteInput = NoteInput("PatientTestId", "PractitionerTestId", "Example Note Text")
        val response = noteHandler.sendNote("TestTenant", noteInput)
        assertEquals(noteInput.patientFhirId, response)
    }
}
