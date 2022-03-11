package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Period as EHRPeriod

internal class PeriodTest {
    @Test
    fun `can get start`() {
        val ehrPeriod = relaxedMockk<EHRPeriod> {
            every { start } returns "yesterday"
        }
        val period = Period(ehrPeriod)
        assertEquals("yesterday", period.start)
    }

    @Test
    fun `can get end`() {
        val ehrPeriod = relaxedMockk<EHRPeriod> {
            every { end } returns "tomorrow"
        }
        val period = Period(ehrPeriod)
        assertEquals("tomorrow", period.end)
    }
}
