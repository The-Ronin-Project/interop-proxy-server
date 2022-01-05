package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PatientTest {
    @Test
    fun `Ensure patient can be created`() {
        val identifier = Identifier("MRN", "555")
        val name = HumanName("official", "Last", listOf("First"))
        val contactPoint = ContactPoint("phone", "home", "555-555-5555")
        val address = Address("home", listOf("1234 Main St"), "Anywhere", "FL", "37890")
        val patient = Patient(
            "id1",
            listOf(identifier),
            listOf(name),
            "1981-01-01",
            "Male",
            listOf(contactPoint),
            listOf(address)
        )
        assertEquals("id1", patient.id)
        assertEquals(listOf(identifier), patient.identifier)
        assertEquals(listOf(name), patient.name)
        assertEquals("1981-01-01", patient.birthDate)
        assertEquals("Male", patient.gender)
        assertEquals(listOf(contactPoint), patient.telecom)
        assertEquals(listOf(address), patient.address)
    }

    @Test
    fun `patient can be created with default values`() {
        val patient = Patient("id1")
        assertEquals("id1", patient.id)
        assertEquals(listOf<Identifier>(), patient.identifier)
        assertEquals(listOf<String>(), patient.name)
        assertNull(patient.birthDate)
        assertNull(patient.gender)
        assertEquals(listOf<ContactPoint>(), patient.telecom)
        assertEquals(listOf<Address>(), patient.address)
    }
}
