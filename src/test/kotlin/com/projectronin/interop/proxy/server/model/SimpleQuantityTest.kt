package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Decimal
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.SimpleQuantity as R4SimpleQuantity

internal class SimpleQuantityTest {
    @Test
    fun `can get value`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { value } returns Decimal(21.0)
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals(21.0, simpleQuantity.value)
    }

    @Test
    fun `can get null value`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { value } returns null
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertNull(simpleQuantity.value)
    }

    @Test
    fun `can get value with null value`() {
        val doubleValue: Double? = null
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { value } returns Decimal(doubleValue)
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertNull(simpleQuantity.value)
    }

    @Test
    fun `can get unit`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { unit } returns "unit".asFHIR()
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals("unit", simpleQuantity.unit)
    }

    @Test
    fun `can get null unit`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { unit } returns null
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertNull(simpleQuantity.unit)
    }

    @Test
    fun `can get unit with null value`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { unit } returns FHIRString(null)
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertNull(simpleQuantity.unit)
    }

    @Test
    fun `can get system`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { system?.value } returns "system"
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals("system", simpleQuantity.system)
    }

    @Test
    fun `null system`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { system } returns null
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertNull(simpleQuantity.system)
    }

    @Test
    fun `can get code`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { code?.value } returns "code"
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertEquals("code", simpleQuantity.code)
    }

    @Test
    fun `null code`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity> {
            every { code } returns null
        }
        val simpleQuantity = SimpleQuantity(ehrSimpleQuantity)
        assertNull(simpleQuantity.code)
    }
}
