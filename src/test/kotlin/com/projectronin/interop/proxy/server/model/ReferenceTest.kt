package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

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
        val ehrIdentifier = relaxedMockk<R4Identifier>()
        val ehrReference = mockk<R4Reference> {
            every { reference } returns "Patient/1234"
            every { type } returns Uri("Patient")
            every { display } returns "Patient 1234"
            every { identifier } returns ehrIdentifier
            every { id } returns "1234"
            every { decomposedType() } returns "Patient"
            every { decomposedId() } returns "1234"
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
        val ehrReference = mockk<R4Reference> {
            every { reference } returns null
            every { type } returns null
            every { display } returns "Patient 1234"
            every { identifier } returns null
            every { id } returns null
            every { decomposedType() } returns null
            every { decomposedId() } returns null
        }
        val reference = Reference.from(ehrReference, tenant)
        assertNull(reference.id)
        assertNull(reference.reference)
        assertNull(reference.identifier)
        assertNull(reference.type)
        assertEquals("Patient 1234", reference.display)
    }

    @Test
    fun `can build from null EHR reference`() {
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        val reference = Reference.from(null, tenant)
        assertNull(reference.id)
        assertNull(reference.reference)
        assertNull(reference.identifier)
        assertNull(reference.type)
        assertNull(reference.display)
    }

    @Test
    fun `created from R4 reference with just reference sets id and type as well`() {
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        val ehrReference = mockk<R4Reference> {
            every { reference } returns "Patient/1234"
            every { type } returns null
            every { display } returns null
            every { identifier } returns null
            every { id } returns null
            every { decomposedType() } returns "Patient"
            every { decomposedId() } returns "1234"
        }
        val reference = Reference.from(ehrReference, tenant)
        assertEquals("tenant-1234", reference.id)
        assertEquals("Patient/tenant-1234", reference.reference)
        assertNull(reference.identifier)
        assertEquals("Patient", reference.type)
        assertNull(reference.display)
    }

    @Test
    fun `created from R4 reference does not override type and id if already present`() {
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        val ehrReference = mockk<R4Reference> {
            every { reference } returns "Patient/1234"
            every { type } returns Uri("Practitioner")
            every { display } returns null
            every { identifier } returns null
            every { id } returns "5678"
            every { decomposedType() } returns "Practitioner"
            every { decomposedId() } returns "5678"
        }
        val reference = Reference.from(ehrReference, tenant)
        assertEquals("tenant-5678", reference.id)
        assertEquals("Patient/tenant-1234", reference.reference)
        assertNull(reference.identifier)
        assertEquals("Practitioner", reference.type)
        assertNull(reference.display)
    }
}
