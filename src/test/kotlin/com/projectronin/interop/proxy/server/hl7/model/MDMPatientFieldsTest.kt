package com.projectronin.interop.proxy.server.hl7.model

import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MDMPatientFieldsTest {
    @Test
    fun `can get identifiers`() {
        val identifiers = relaxedMockk<Identifier> {
            every { value } returns "testID"
            every { system } returns Uri("testsystem")
        }
        val mdmPatientFields = relaxedMockk<MDMPatientFields> {
            every { identifier } returns listOf(identifiers)
        }
        assertEquals("testID", mdmPatientFields.identifier[0].value)
        assertEquals(Uri("testsystem"), mdmPatientFields.identifier[0].system)
    }

    @Test
    fun `can get names`() {
        val names = relaxedMockk<com.projectronin.interop.fhir.r4.datatype.HumanName> {
            every { family } returns "family"
            every { given } returns listOf("given", "given2")
        }
        val mdmPatientFields = relaxedMockk<MDMPatientFields> {
            every { name } returns listOf(names)
        }
        assertEquals("family", mdmPatientFields.name[0].family)
        assertEquals(listOf("given", "given2"), mdmPatientFields.name[0].given)
    }

    @Test
    fun `can get DOB`() {
        val mdmPatientFields = relaxedMockk<MDMPatientFields> {
            every { dob } returns Date("2022-06-01")
        }
        assertEquals(Date("2022-06-01"), mdmPatientFields.dob)
    }

    @Test
    fun `can get gender`() {
        val mdmPatientFields = relaxedMockk<MDMPatientFields> {
            every { gender } returns AdministrativeGender.FEMALE
        }
        assertEquals(AdministrativeGender.FEMALE, mdmPatientFields.gender)
    }

    @Test
    fun `can get addresses`() {
        val addresses = relaxedMockk<com.projectronin.interop.fhir.r4.datatype.Address> {
            every { line } returns listOf("123 ABC Street", "Unit 1")
            every { city } returns "Anytown"
            every { state } returns "CA"
            every { postalCode } returns "12345"
            every { country } returns "USA"
        }
        val mdmPatientFields = relaxedMockk<MDMPatientFields> {
            every { address } returns listOf(addresses)
        }
        assertEquals(listOf(addresses), mdmPatientFields.address)
    }

    @Test
    fun `can get phones`() {
        val phone1 = relaxedMockk<ContactPoint> {
            every { value } returns "1234567890"
            every { use } returns ContactPointUse.HOME.asCode()
        }
        val mdmPatientFields = relaxedMockk<MDMPatientFields> {
            every { phone } returns listOf(phone1)
        }
        assertEquals(listOf(phone1), mdmPatientFields.phone)
    }
}
