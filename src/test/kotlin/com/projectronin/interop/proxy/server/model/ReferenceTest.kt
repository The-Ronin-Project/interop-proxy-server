package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier
import com.projectronin.interop.ehr.model.Reference as EHRReference

class ReferenceTest {
    @Test
    fun `ensure reference can be created`() {
        val testIdentifier = mockk<Identifier>()
        val testReference = Reference(
            id = "123",
            reference = "Reference",
            identifier = testIdentifier,
            type = "type",
            display = "display"
        )
        assertEquals("123", testReference.id)
        assertEquals("Reference", testReference.reference)
        assertEquals(testIdentifier, testReference.identifier)
        assertEquals("type", testReference.type)
        assertEquals("display", testReference.display)
    }

    @Test
    fun `can build from fully populated EHR reference`() {
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        val ehrIdentifier = relaxedMockk<EHRIdentifier>()
        val ehrReference = mockk<EHRReference> {
            every { reference } returns "Patient/1234"
            every { type } returns "Patient"
            every { display } returns "Patient 1234"
            every { identifier } returns ehrIdentifier
            every { id } returns "1234"
        }
        val reference = Reference.from(ehrReference, tenant)
        assertEquals("tenant-1234", reference.id)
        assertEquals("Patient/tenant-1234", reference.reference)
        assertEquals(Identifier(ehrIdentifier), reference.identifier)
        assertEquals("Patient", reference.type)
        assertEquals("Patient 1234", reference.display)
    }

    @Test
    fun `can build from partially populated EHR reference`() {
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        val ehrReference = mockk<EHRReference> {
            every { reference } returns null
            every { type } returns null
            every { display } returns "Patient 1234"
            every { identifier } returns null
            every { id } returns null
        }
        val reference = Reference.from(ehrReference, tenant)
        assertNull(reference.id)
        assertNull(reference.reference)
        assertNull(reference.identifier)
        assertNull(reference.type)
        assertEquals("Patient 1234", reference.display)
    }
}
