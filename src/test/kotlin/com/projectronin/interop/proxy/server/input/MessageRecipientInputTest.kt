package com.projectronin.interop.proxy.server.input

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class MessageRecipientInputTest {
    @Test
    fun `Ensure MessageRecipientInput can be created and accessed`() {
        val input = MessageRecipientInput("1234")
        assertThat(input.fhirId, `is`("1234"))
    }
}
