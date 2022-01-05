package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContactPointTest {
    @Test
    fun `check defaults`() {
        val contactDetail = ContactPoint()
        assertEquals(null, contactDetail.system)
        assertEquals(null, contactDetail.use)
        assertEquals(null, contactDetail.value)
    }

    @Test
    fun `check getters`() {
        val contactDetail = ContactPoint("phone", "home", "123-456-7890")
        assertEquals("phone", contactDetail.system)
        assertEquals("home", contactDetail.use)
        assertEquals("123-456-7890", contactDetail.value)
    }
}
