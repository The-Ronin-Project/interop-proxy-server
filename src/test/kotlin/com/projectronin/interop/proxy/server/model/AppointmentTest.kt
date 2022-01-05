package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class AppointmentTest {
    @Test
    fun `check defaults`() {
        val appointment = Appointment(id = "id1", start = "2021-01-01T10:10:00Z", status = "status")
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
        val appointment = Appointment(
            "id1",
            listOf(identifier),
            "2021-01-01T10:10:00Z",
            "status",
            listOf(serviceType),
            appointmentType
        )
        assertEquals("id1", appointment.id)
        assertEquals(listOf(identifier), appointment.identifier)
        assertEquals("2021-01-01T10:10:00Z", appointment.start)
        assertEquals("status", appointment.status)
        assertEquals(listOf(serviceType), appointment.serviceType)
        assertEquals(appointmentType, appointment.appointmentType)
    }
}
