package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier

class IdentifierTest {
    @Test
    fun `can get system`() {
        val ehrIdentifier = relaxedMockk<EHRIdentifier> {
            every { system } returns "system"
        }
        val identifier = Identifier(ehrIdentifier)
        assertEquals("system", identifier.system)
    }

    @Test
    fun `can get value`() {
        val ehrIdentifier = relaxedMockk<EHRIdentifier> {
            every { value } returns "value"
        }
        val identifier = Identifier(ehrIdentifier)
        assertEquals("value", identifier.value)
    }
}
