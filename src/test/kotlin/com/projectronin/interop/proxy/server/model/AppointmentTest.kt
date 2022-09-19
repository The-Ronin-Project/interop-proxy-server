package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept as R4CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier
import com.projectronin.interop.fhir.r4.datatype.Participant as R4Participant
import com.projectronin.interop.fhir.r4.resource.Appointment as R4Appointment

internal class AppointmentTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "ten"
    }

    @Test
    fun `can get id`() {
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { id } returns Id("1234")
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals("ten-1234", appointment.id)
    }

    @Test
    fun `can get identifier`() {
        val ehrIdentifier1 = relaxedMockk<R4Identifier>()
        val ehrIdentifier2 = relaxedMockk<R4Identifier>()
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals(2, appointment.identifier.size)
    }

    @Test
    fun `can get start`() {
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { start?.value } returns "2021-03-07"
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals("2021-03-07", appointment.start)
    }

    @Test
    fun `null start`() {
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { start } returns null
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNull(appointment.start)
    }

    @Test
    fun `can get status`() {
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { status } returns AppointmentStatus.PROPOSED.asCode()
        }
        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals("proposed", appointment.status)
    }

    @Test
    fun `can get service type`() {
        val ehrCodeableConcept1 = relaxedMockk<R4CodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<R4CodeableConcept>()
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { serviceType } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertEquals(2, appointment.serviceType.size)
    }

    @Test
    fun `can get appointment type`() {
        val ehrCodeableConcept1 = relaxedMockk<R4CodeableConcept>()
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { appointmentType } returns ehrCodeableConcept1
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNotNull(appointment.appointmentType)
    }

    @Test
    fun `can get null appointment type`() {
        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { appointmentType } returns null
        }

        val appointment = Appointment(ehrAppointment, mockTenant)
        assertNull(appointment.appointmentType)
    }
    @Test
    fun `get participants when practitioners null`() {
        val mockEHRParticipant = relaxedMockk<R4Participant> {
            every { actor } returns null
        }
        val mockEHRParticipant2 = relaxedMockk<R4Participant> {
            every { actor?.reference } returns null
        }

        val ehrAppointment = relaxedMockk<R4Appointment> {
            every { participant } returns listOf(mockEHRParticipant, mockEHRParticipant2)
        }
        val appointment = Appointment(ehrAppointment, mockTenant)

        assertEquals(1, appointment.participants.size)
    }
}
