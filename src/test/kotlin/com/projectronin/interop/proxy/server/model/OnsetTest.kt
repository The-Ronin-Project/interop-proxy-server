package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Age as EHRAge
import com.projectronin.interop.ehr.model.Condition.AgeOnset as EHRAgeOnset
import com.projectronin.interop.ehr.model.Condition.DateTimeOnset as EHRDateTimeOnset
import com.projectronin.interop.ehr.model.Condition.PeriodOnset as EHRPeriodOnset
import com.projectronin.interop.ehr.model.Condition.RangeOnset as EHRRangeOnset
import com.projectronin.interop.ehr.model.Condition.StringOnset as EHRStringOnset
import com.projectronin.interop.ehr.model.Period as EHRPeriod
import com.projectronin.interop.ehr.model.Range as EHRRange

class OnsetTest {
    @Test
    fun `creates date time onset`() {
        val ehrDateTimeOnset = mockk<EHRDateTimeOnset> {
            every { value } returns "2022-03-09"
        }
        val onset = DateTimeOnset(ehrDateTimeOnset)
        assertEquals("2022-03-09", onset.value)
    }

    @Test
    fun `creates age onset`() {
        val ehrAge = relaxedMockk<EHRAge>()
        val ehrAgeOnset = mockk<EHRAgeOnset> {
            every { value } returns ehrAge
        }
        val onset = AgeOnset(ehrAgeOnset)
        assertNotNull(onset.value)
    }

    @Test
    fun `creates period onset`() {
        val ehrPeriod = relaxedMockk<EHRPeriod>()
        val ehrPeriodOnset = mockk<EHRPeriodOnset> {
            every { value } returns ehrPeriod
        }
        val onset = PeriodOnset(ehrPeriodOnset)
        assertNotNull(onset.value)
    }

    @Test
    fun `creates range onset`() {
        val ehrRange = relaxedMockk<EHRRange>()
        val ehrRangeOnset = mockk<EHRRangeOnset> {
            every { value } returns ehrRange
        }
        val onset = RangeOnset(ehrRangeOnset)
        assertNotNull(onset.value)
    }

    @Test
    fun `creates string onset`() {
        val ehrStringOnset = mockk<EHRStringOnset> {
            every { value } returns "recently"
        }
        val onset = StringOnset(ehrStringOnset)
        assertEquals("recently", onset.value)
    }
}
