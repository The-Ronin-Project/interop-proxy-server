package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.ehr.model.ReferenceTypes
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.proxy.server.dataloaders.ParticipantDataLoader
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import org.dataloader.DataLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Appointment as EHRAppointment
import com.projectronin.interop.ehr.model.CodeableConcept as EHRCodeableConcept
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier
import com.projectronin.interop.ehr.model.Participant as EHRParticipant

internal class AppointmentTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "ten"
    }

    @Test
    fun `can get id`() {
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { id } returns "1234"
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals("ten-1234", appointment.id)
    }

    @Test
    fun `can get identifier`() {
        val ehrIdentifier1 = relaxedMockk<EHRIdentifier>()
        val ehrIdentifier2 = relaxedMockk<EHRIdentifier>()
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals(2, appointment.identifier.size)
    }

    @Test
    fun `can get start`() {
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { start } returns "2021-03-07"
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals("2021-03-07", appointment.start)
    }

    @Test
    fun `can get status`() {
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { status } returns AppointmentStatus.PROPOSED
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals("proposed", appointment.status)
    }

    @Test
    fun `can get null status`() {
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { status } returns null
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNull(appointment.status)
    }

    @Test
    fun `can get service type`() {
        val ehrCodeableConcept1 = relaxedMockk<EHRCodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<EHRCodeableConcept>()
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { serviceType } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals(2, appointment.serviceType.size)
    }

    @Test
    fun `can get appointment type`() {
        val ehrCodeableConcept1 = relaxedMockk<EHRCodeableConcept>()
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { appointmentType } returns ehrCodeableConcept1
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNotNull(appointment.appointmentType)
    }

    @Test
    fun `can get null appointment type`() {
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { appointmentType } returns null
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNull(appointment.appointmentType)
    }

    @Test
    fun `get participants when none exist`() {
        val mockDataLoader = mockk<DataLoader<TenantParticipantTest, Participant>> {
            every { loadMany(listOf()) } returns mockk()
        }
        val mockEnv = mockk<DataFetchingEnvironment> {
            every {
                getDataLoader<TenantParticipantTest, Participant>(ParticipantDataLoader.name)
            } returns mockDataLoader
        }
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { participants } returns listOf()
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNotNull(appointment.participants(mockEnv))
    }

    @Test
    fun `get participants when no practitioners`() {
        val mockEHRParticipant = mockk<EHRParticipant> {
            every { actor.type } returns "different type"
        }
        val mockDataLoader = mockk<DataLoader<TenantParticipantTest, Participant>> {
            every { loadMany(listOf()) } returns mockk()
        }
        val mockEnv = mockk<DataFetchingEnvironment> {
            every {
                getDataLoader<TenantParticipantTest, Participant>(ParticipantDataLoader.name)
            } returns mockDataLoader
        }
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { participants } returns listOf(mockEHRParticipant)
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNotNull(appointment.participants(mockEnv))
    }

    @Test
    fun `get participants when practitioners found and not found`() {
        val mockEHRParticipant = mockk<EHRParticipant> {
            every { actor.type } returns ReferenceTypes.PRACTITIONER
        }
        val mockEHRParticipant2 = mockk<EHRParticipant> {
            every { actor.type } returns "different type"
        }

        val mockTenantParticipant = mockk<TenantParticipant>() {
            every { participant } returns mockEHRParticipant
            every { tenant } returns mockTenant
        }
        val mockTenantParticipants = listOf(mockTenantParticipant)
        val mockDataLoader = mockk<DataLoader<TenantParticipant, Participant>> {
            every { loadMany(mockTenantParticipants) } returns mockk()
        }
        val mockEnv = mockk<DataFetchingEnvironment> {
            every {
                getDataLoader<TenantParticipant, Participant>(ParticipantDataLoader.name)
            } returns mockDataLoader
        }
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { participants } returns listOf(mockEHRParticipant, mockEHRParticipant2)
        }
        val appointment = Appointment(ehrAppointment, mockTenant)

        assertNotNull(appointment.participants(mockEnv))
    }

    @Test
    fun `get participants when practitioners found`() {
        val mockEHRParticipant = mockk<EHRParticipant> {
            every { actor.type } returns ReferenceTypes.PRACTITIONER
        }

        val mockTenantParticipant = mockk<TenantParticipant>() {
            every { participant } returns mockEHRParticipant
            every { tenant } returns mockTenant
        }
        val mockTenantParticipants = listOf(mockTenantParticipant)
        val mockDataLoader = mockk<DataLoader<TenantParticipant, Participant>> {
            every { loadMany(mockTenantParticipants) } returns mockk()
        }
        val mockEnv = mockk<DataFetchingEnvironment> {
            every {
                getDataLoader<TenantParticipant, Participant>(ParticipantDataLoader.name)
            } returns mockDataLoader
        }
        val ehrAppointment = relaxedMockk<EHRAppointment> {
            every { participants } returns listOf(mockEHRParticipant)
        }
        val appointment = Appointment(ehrAppointment, mockTenant)

        assertNotNull(appointment.participants(mockEnv))
    }
}
