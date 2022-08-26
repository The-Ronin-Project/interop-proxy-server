package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period

internal class PeriodTest {
    @Test
    fun `can get start`() {
        val ehrPeriod = relaxedMockk<R4Period> {
            every { start?.value } returns "yesterday"
        }
        val period = Period(ehrPeriod)
        assertEquals("yesterday", period.start)
    }

    @Test
    fun `null start`() {
        val ehrPeriod = relaxedMockk<R4Period> {
            every { start } returns null
        }
        val period = Period(ehrPeriod)
        assertNull(period.start)
    }

    @Test
    fun `can get end`() {
        val ehrPeriod = relaxedMockk<R4Period> {
            every { end?.value } returns "tomorrow"
        }
        val period = Period(ehrPeriod)
        assertEquals("tomorrow", period.end)
    }

    @Test
    fun `null end`() {
        val ehrPeriod = relaxedMockk<R4Period> {
            every { end } returns null
        }
        val period = Period(ehrPeriod)
        assertNull(period.end)
    }
}
