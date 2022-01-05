package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HumanNameTest {
    @Test
    fun `check defaults`() {
        val humanName = HumanName()
        assertEquals(null, humanName.use)
        assertEquals(null, humanName.family)
        assertEquals(listOf<String>(), humanName.given)
    }

    @Test
    fun `check getters`() {
        val humanName = HumanName("official", "Last", listOf("First"))
        assertEquals("official", humanName.use)
        assertEquals("Last", humanName.family)
        assertEquals(listOf("First"), humanName.given)
    }
}
