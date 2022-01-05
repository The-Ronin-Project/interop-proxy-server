package com.projectronin.interop.proxy.server.input

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class MessageInputTest {
    @Test
    fun `Ensure MessageInput can be created and accessed`() {
        val patient = MessagePatientInput("12345")
        val recipient1 = MessageRecipientInput("1234", true)
        val recipient2 = MessageRecipientInput("5678", false)
        val input = MessageInput("Message text", patient, listOf(recipient1, recipient2))

        assertThat(input.text, `is`("Message text"))
        assertThat(input.patient, `is`(patient))
        assertThat(input.recipients, `is`(listOf(recipient1, recipient2)))
    }
}
