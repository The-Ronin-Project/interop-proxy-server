package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Address as R4Address
import com.projectronin.interop.fhir.r4.datatype.ContactPoint as R4ContactPoint
import com.projectronin.interop.fhir.r4.datatype.HumanName as R4HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier
import com.projectronin.interop.fhir.r4.resource.Patient as R4Patient

internal class PatientTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "ten"
    }

    @Test
    fun `can get id`() {
        val ehrPatient = relaxedMockk<R4Patient> {
            every { id } returns Id("13579")
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals("ten-13579", patient.id)
    }

    @Test
    fun `can get identifier`() {
        val ehrIdentifier1 = relaxedMockk<R4Identifier>()
        val ehrIdentifier2 = relaxedMockk<R4Identifier>()
        val ehrPatient = relaxedMockk<R4Patient> {
            every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals(2, patient.identifier.size)
    }

    @Test
    fun `can get identifier including ronin identifiers`() {
        val ehrIdentifier1 = relaxedMockk<R4Identifier>()
        val ehrIdentifier2 = relaxedMockk<R4Identifier>()
        val ehrPatient = relaxedMockk<R4Patient> {
            every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
        }
        val roninIdentifiers = listOf(
            R4Identifier(system = Uri("system1"), value = "value"),
            R4Identifier(system = Uri("system2"), value = "value")
        )

        val patient = Patient(ehrPatient, mockTenant, roninIdentifiers)
        assertEquals(4, patient.identifier.size)
    }

    @Test
    fun `can get name`() {
        val ehrHumanName1 = relaxedMockk<R4HumanName>()
        val ehrHumanName2 = relaxedMockk<R4HumanName>()
        val ehrPatient = relaxedMockk<R4Patient> {
            every { name } returns listOf(ehrHumanName1, ehrHumanName2)
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals(2, patient.name.size)
    }

    @Test
    fun `can get birthdate`() {
        val ehrPatient = relaxedMockk<R4Patient> {
            every { birthDate } returns Date("1976-07-04")
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals("1976-07-04", patient.birthDate)
    }

    @Test
    fun `null birthdate`() {
        val ehrPatient = relaxedMockk<R4Patient> {
            every { birthDate } returns null
        }
        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertNull(patient.birthDate)
    }

    @Test
    fun `can get gender`() {
        val ehrPatient = relaxedMockk<R4Patient> {
            every { gender } returns AdministrativeGender.MALE.asCode()
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals("male", patient.gender)
    }

    @Test
    fun `can get null gender`() {
        val ehrPatient = relaxedMockk<R4Patient> {
            every { gender } returns null
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertNull(patient.gender)
    }

    @Test
    fun `can get telecom`() {
        val ehrContactPoint1 = relaxedMockk<R4ContactPoint>()
        val ehrContactPoint2 = relaxedMockk<R4ContactPoint>()
        val ehrPatient = relaxedMockk<R4Patient> {
            every { telecom } returns listOf(ehrContactPoint1, ehrContactPoint2)
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals(2, patient.telecom.size)
    }

    @Test
    fun `can get address`() {
        val ehrAddress1 = relaxedMockk<R4Address>()
        val ehrAddress2 = relaxedMockk<R4Address>()
        val ehrPatient = relaxedMockk<R4Patient> {
            every { address } returns listOf(ehrAddress1, ehrAddress2)
        }

        val patient = Patient(ehrPatient, mockTenant, emptyList())
        assertEquals(2, patient.address.size)
    }
}
