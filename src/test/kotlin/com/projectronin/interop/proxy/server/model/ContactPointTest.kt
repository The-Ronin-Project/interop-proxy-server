package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.valueset.ContactPointSystem
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.ContactPoint as R4ContactPoint

class ContactPointTest {
    @Test
    fun `can get system`() {
        val ehrContactPoint = relaxedMockk<R4ContactPoint> {
            every { system } returns ContactPointSystem.EMAIL
        }
        val contactPoint = ContactPoint(ehrContactPoint)
        assertEquals("email", contactPoint.system)
    }

    @Test
    fun `can get null system`() {
        val ehrContactPoint = relaxedMockk<R4ContactPoint> {
            every { system } returns null
        }
        val contactPoint = ContactPoint(ehrContactPoint)
        assertNull(contactPoint.system)
    }

    @Test
    fun `can get use`() {
        val ehrContactPoint = relaxedMockk<R4ContactPoint> {
            every { use } returns ContactPointUse.WORK
        }
        val contactPoint = ContactPoint(ehrContactPoint)
        assertEquals("work", contactPoint.use)
    }

    @Test
    fun `can get null use`() {
        val ehrContactPoint = relaxedMockk<R4ContactPoint> {
            every { use } returns null
        }
        val contactPoint = ContactPoint(ehrContactPoint)
        assertNull(contactPoint.use)
    }

    @Test
    fun `can get value`() {
        val ehrContactPoint = relaxedMockk<R4ContactPoint> {
            every { value } returns "value"
        }
        val contactPoint = ContactPoint(ehrContactPoint)
        assertEquals("value", contactPoint.value)
    }
}
