package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.CodeableConcept as EHRCodeableConcept
import com.projectronin.interop.ehr.model.Coding as EHRCoding

class CodeableConceptTest {
    @Test
    fun `can get coding`() {
        val ehrCoding1 = relaxedMockk<EHRCoding>()
        val ehrCoding2 = relaxedMockk<EHRCoding>()

        val ehrCodeableConcept = relaxedMockk<EHRCodeableConcept> {
            every { coding } returns listOf(ehrCoding1, ehrCoding2)
        }
        val codeableConcept = CodeableConcept(ehrCodeableConcept)
        assertEquals(2, codeableConcept.coding.size)
    }

    @Test
    fun `can get text`() {
        val ehrCodeableConcept = relaxedMockk<EHRCodeableConcept> {
            every { text } returns "text"
        }
        val codeableConcept = CodeableConcept(ehrCodeableConcept)
        assertEquals("text", codeableConcept.text)
    }
}
