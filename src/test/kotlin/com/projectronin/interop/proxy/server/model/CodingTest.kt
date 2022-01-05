package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodingTest {
    @Test
    fun `check defaults`() {
        val coding = Coding()
        assertNull(coding.system)
        assertNull(coding.version)
        assertNull(coding.code)
        assertNull(coding.display)
        assertNull(coding.userSelected)
    }

    @Test
    fun `check getters`() {
        val coding = Coding("system", "version", "code", "display", true)
        assertEquals("system", coding.system)
        assertEquals("version", coding.version)
        assertEquals("code", coding.code)
        assertEquals("display", coding.display)
        assertTrue(coding.userSelected!!)
    }
}
