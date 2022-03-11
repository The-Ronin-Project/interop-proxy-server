package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.SimpleQuantity as EHRSimpleQuantity

internal class SimpleQuantityTest {
    @Test
    fun `can get value`() {
        val ehrSimpleQuantity = relaxedMockk<EHRSimpleQuantity> {
            every { value } returns 21.0
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals(21.0, simpleQuantity.value)
    }

    @Test
    fun `can get unit`() {
        val ehrSimpleQuantity = relaxedMockk<EHRSimpleQuantity> {
            every { unit } returns "unit"
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals("unit", simpleQuantity.unit)
    }

    @Test
    fun `can get system`() {
        val ehrSimpleQuantity = relaxedMockk<EHRSimpleQuantity> {
            every { system } returns "system"
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals("system", simpleQuantity.system)
    }

    @Test
    fun `can get code`() {
        val ehrSimpleQuantity = relaxedMockk<EHRSimpleQuantity> {
            every { code } returns "code"
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals("code", simpleQuantity.code)
    }
}
