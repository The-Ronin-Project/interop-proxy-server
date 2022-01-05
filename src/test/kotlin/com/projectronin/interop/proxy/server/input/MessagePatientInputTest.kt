package com.projectronin.interop.proxy.server.input

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class MessagePatientInputTest {
    @Test
    fun `Ensure MessagePatientInput can be created and accessed`() {
        val input = MessagePatientInput("12345")
        assertThat(input.mrn, `is`("12345"))
    }
}
