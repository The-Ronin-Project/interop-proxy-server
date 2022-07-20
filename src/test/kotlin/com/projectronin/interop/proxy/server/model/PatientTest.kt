package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.ehr.model.Address
import com.projectronin.interop.ehr.model.ContactPoint
import com.projectronin.interop.ehr.model.HumanName
import com.projectronin.interop.ehr.model.Identifier
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Patient as EHRPatient

internal class PatientTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "ten"
    }

    @Test
    fun `can get id`() {
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { id } returns "13579"
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals("ten-13579", patient.id)
    }

    @Test
    fun `can get identifier`() {
        val ehrIdentifier1 = relaxedMockk<Identifier>()
        val ehrIdentifier2 = relaxedMockk<Identifier>()
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals(2, patient.identifier.size)
    }

    @Test
    fun `can get name`() {
        val ehrHumanName1 = relaxedMockk<HumanName>()
        val ehrHumanName2 = relaxedMockk<HumanName>()
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { name } returns listOf(ehrHumanName1, ehrHumanName2)
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals(2, patient.name.size)
    }

    @Test
    fun `can get birthdate`() {
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { birthDate } returns "1976-07-04"
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals("1976-07-04", patient.birthDate)
    }

    @Test
    fun `can get gender`() {
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { gender } returns AdministrativeGender.MALE
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals("male", patient.gender)
    }

    @Test
    fun `can get null gender`() {
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { gender } returns null
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertNull(patient.gender)
    }

    @Test
    fun `can get telecom`() {
        val ehrContactPoint1 = relaxedMockk<ContactPoint>()
        val ehrContactPoint2 = relaxedMockk<ContactPoint>()
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { telecom } returns listOf(ehrContactPoint1, ehrContactPoint2)
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals(2, patient.telecom.size)
    }

    @Test
    fun `can get address`() {
        val ehrAddress1 = relaxedMockk<Address>()
        val ehrAddress2 = relaxedMockk<Address>()
        val ehrPatient = relaxedMockk<EHRPatient> {
            every { address } returns listOf(ehrAddress1, ehrAddress2)
        }

        val patient = Patient(ehrPatient, mockTenant)
        assertEquals(2, patient.address.size)
    }
}
