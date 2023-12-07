package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range
import com.projectronin.interop.fhir.r4.datatype.SimpleQuantity as R4SimpleQuantity

internal class RangeTest {
    @Test
    fun `can get low`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity>()
        val ehrRange =
            relaxedMockk<R4Range> {
                every { low } returns ehrSimpleQuantity
            }
        val range = Range(ehrRange)
        assertEquals(SimpleQuantity(ehrSimpleQuantity), range.low)
    }

    @Test
    fun `can get null low`() {
        val ehrRange =
            relaxedMockk<R4Range> {
                every { low } returns null
            }
        val range = Range(ehrRange)
        assertNull(range.low)
    }

    @Test
    fun `can get high`() {
        val ehrSimpleQuantity = relaxedMockk<R4SimpleQuantity>()
        val ehrRange =
            relaxedMockk<R4Range> {
                every { high } returns ehrSimpleQuantity
            }
        val range = Range(ehrRange)
        assertEquals(SimpleQuantity(ehrSimpleQuantity), range.high)
    }

    @Test
    fun `can get null high`() {
        val ehrRange =
            relaxedMockk<R4Range> {
                every { high } returns null
            }
        val range = Range(ehrRange)
        assertNull(range.high)
    }
}
