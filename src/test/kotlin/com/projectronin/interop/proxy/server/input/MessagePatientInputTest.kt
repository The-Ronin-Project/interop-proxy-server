package com.projectronin.interop.proxy.server.input

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class MessagePatientInputTest {
    @Test
    fun `Ensure MessagePatientInput can be created and accessed`() {
        val input = MessagePatientInput("12345", null)
        assertThat(input.mrn, `is`("12345"))
        assertThat("Ronin Patient ID is not null", input.patientFhirId == null)
    }

    @Test
    fun `ensure MessagePatientInput can be have null mrns`() {
        val input = MessagePatientInput(null, "123")
        assertThat("Patient mrn is not null", input.mrn == null)
        assertThat(input.patientFhirId, `is`("123"))
    }
}
