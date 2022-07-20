package com.projectronin.interop.proxy.server.hl7.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MDMPractitionerFieldsTest {
    @Test
    fun `can get identifiers`() {
        val identifiers = relaxedMockk<com.projectronin.interop.fhir.r4.datatype.Identifier> {
            every { value } returns "testID"
            every { system } returns Uri("testsystem")
        }

        val mdmPractitionerFields = relaxedMockk<MDMPractitionerFields> {
            every { identifier } returns listOf(identifiers)
        }
        assertEquals("testID", mdmPractitionerFields.identifier[0].value)
        assertEquals(Uri("testsystem"), mdmPractitionerFields.identifier[0].system)
    }
    @Test
    fun `can get names`() {
        val names = relaxedMockk<com.projectronin.interop.fhir.r4.datatype.HumanName> {
            every { family } returns "family"
            every { given } returns listOf("given", "given2")
        }
        val mdmPractitionerFields = relaxedMockk<MDMPractitionerFields> {
            every { name } returns listOf(names)
        }

        assertEquals(listOf("given", "given2"), mdmPractitionerFields.name[0].given)
        assertEquals("family", mdmPractitionerFields.name[0].family)
    }
}
