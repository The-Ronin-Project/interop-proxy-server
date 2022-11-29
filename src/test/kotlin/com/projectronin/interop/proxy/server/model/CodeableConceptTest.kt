package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept as R4CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding as R4Coding

class CodeableConceptTest {
    @Test
    fun `can get coding`() {
        val ehrCoding1 = relaxedMockk<R4Coding>()
        val ehrCoding2 = relaxedMockk<R4Coding>()

        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept> {
            every { coding } returns listOf(ehrCoding1, ehrCoding2)
        }
        val codeableConcept = CodeableConcept(ehrCodeableConcept)
        assertEquals(2, codeableConcept.coding.size)
    }

    @Test
    fun `can get text`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept> {
            every { text } returns "text".asFHIR()
        }
        val codeableConcept = CodeableConcept(ehrCodeableConcept)
        assertEquals("text", codeableConcept.text)
    }

    @Test
    fun `can get null text`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept> {
            every { text } returns null
        }
        val codeableConcept = CodeableConcept(ehrCodeableConcept)
        assertNull(codeableConcept.text)
    }

    @Test
    fun `can get text with null value`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept> {
            every { text } returns FHIRString(null)
        }
        val codeableConcept = CodeableConcept(ehrCodeableConcept)
        assertNull(codeableConcept.text)
    }
}
