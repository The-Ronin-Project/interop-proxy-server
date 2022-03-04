package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.dataloaders.ParticipantDataLoader
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import org.dataloader.DataLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Participant as EHRParticipant

internal class AppointmentTest {
    private val mockTenant = mockk<Tenant>()
    @Test
    fun `check defaults`() {
        val appointment = Appointment(id = "id1", start = "2021-01-01T10:10:00Z", status = "status", tenant = mockTenant)
        assertEquals("id1", appointment.id)
        assertEquals(listOf<Identifier>(), appointment.identifier)
        assertEquals("2021-01-01T10:10:00Z", appointment.start)
        assertEquals("status", appointment.status)
        assertEquals(listOf<CodeableConcept>(), appointment.serviceType)
        assertNull(appointment.appointmentType)
    }

    @Test
    fun `check getters`() {
        val identifier = Identifier("system", "value")
        val serviceType = CodeableConcept(text = "visit")
        val appointmentType = CodeableConcept(text = "followup")
        val appointmentProvider = mockk<EHRParticipant>()
        val appointment = Appointment(
            "id1",
            listOf(identifier),
            mockTenant,
            "2021-01-01T10:10:00Z",
            "status",
            listOf(serviceType),
            appointmentType,
            listOf(appointmentProvider)
        )
        assertEquals("id1", appointment.id)
        assertEquals(listOf(identifier), appointment.identifier)
        assertEquals("2021-01-01T10:10:00Z", appointment.start)
        assertEquals("status", appointment.status)
        assertEquals(listOf(serviceType), appointment.serviceType)
        assertEquals(appointmentType, appointment.appointmentType)
        assertEquals(listOf(appointmentProvider), appointment.providers)
        assertEquals(mockTenant, appointment.tenant)
    }

    @Test
    fun `check participants init`() {
        val mockDataLoader = mockk<DataLoader<TenantParticipantTest, Participant>> {
            every { loadMany(listOf()) } returns mockk()
        }
        val mockEnv = mockk<DataFetchingEnvironment> {
            every {
                getDataLoader<TenantParticipantTest, Participant>(ParticipantDataLoader.name)
            } returns mockDataLoader
        }
        val appointment = Appointment(id = "id1", start = "2021-01-01T10:10:00Z", status = "status", providers = listOf(), tenant = mockTenant)
        assertNotNull(appointment.participants(mockEnv))
    }
    @Test
    fun `check participants`() {
        val mockEHRParticipant = mockk<EHRParticipant>()

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
        val appointment = Appointment(
            id = "id1",
            start = "2021-01-01T10:10:00Z",
            status = "status",
            providers = listOf(mockEHRParticipant),
            tenant = mockTenant
        )

        assertNotNull(appointment.participants(mockEnv))
    }
}
