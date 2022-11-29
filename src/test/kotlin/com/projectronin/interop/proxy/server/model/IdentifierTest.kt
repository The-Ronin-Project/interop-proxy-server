package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier

class IdentifierTest {
    @Test
    fun `can get system`() {
        val ehrIdentifier = relaxedMockk<R4Identifier> {
            every { system?.value } returns "system"
        }
        val identifier = Identifier(ehrIdentifier)
        assertEquals("system", identifier.system)
    }

    @Test
    fun `null system`() {
        val ehrIdentifier = relaxedMockk<R4Identifier> {
            every { system } returns null
        }
        val identifier = Identifier(ehrIdentifier)
        assertNull(identifier.system)
    }

    @Test
    fun `can get value`() {
        val ehrIdentifier = relaxedMockk<R4Identifier> {
            every { value } returns "value".asFHIR()
        }
        val identifier = Identifier(ehrIdentifier)
        assertEquals("value", identifier.value)
    }

    @Test
    fun `null value`() {
        val ehrIdentifier = relaxedMockk<R4Identifier> {
            every { value } returns null
        }
        val identifier = Identifier(ehrIdentifier)
        assertEquals("", identifier.value)
    }
}
