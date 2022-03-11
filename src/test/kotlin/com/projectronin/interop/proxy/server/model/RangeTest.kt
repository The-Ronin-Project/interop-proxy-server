package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Range as EHRRange
import com.projectronin.interop.ehr.model.SimpleQuantity as EHRSimpleQuantity

internal class RangeTest {
    @Test
    fun `can get low`() {
        val ehrSimpleQuantity = relaxedMockk<EHRSimpleQuantity>()
        val ehrRange = relaxedMockk<EHRRange> {
            every { low } returns ehrSimpleQuantity
        }
        val range = Range(ehrRange)
        assertEquals(SimpleQuantity(ehrSimpleQuantity), range.low)
    }

    @Test
    fun `can get null low`() {
        val ehrRange = relaxedMockk<EHRRange> {
            every { low } returns null
        }
        val range = Range(ehrRange)
        assertNull(range.low)
    }

    @Test
    fun `can get high`() {
        val ehrSimpleQuantity = relaxedMockk<EHRSimpleQuantity>()
        val ehrRange = relaxedMockk<EHRRange> {
            every { high } returns ehrSimpleQuantity
        }
        val range = Range(ehrRange)
        assertEquals(SimpleQuantity(ehrSimpleQuantity), range.high)
    }

    @Test
    fun `can get null high`() {
        val ehrRange = relaxedMockk<EHRRange> {
            every { high } returns null
        }
        val range = Range(ehrRange)
        assertNull(range.high)
    }
}
