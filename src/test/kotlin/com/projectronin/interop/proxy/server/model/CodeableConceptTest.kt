package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeableConceptTest {
    @Test
    fun `check defaults`() {
        val codeableConcept = CodeableConcept()
        assertEquals(listOf<Coding>(), codeableConcept.coding)
        assertNull(codeableConcept.text)
    }

    @Test
    fun `check getters`() {
        val coding = Coding(code = "code")
        val codeableConcept = CodeableConcept(listOf(coding), "text")
        assertEquals(listOf(coding), codeableConcept.coding)
        assertEquals("text", codeableConcept.text)
    }
}
