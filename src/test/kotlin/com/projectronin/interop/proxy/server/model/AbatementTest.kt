package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Age as EHRAge
import com.projectronin.interop.ehr.model.Condition.AgeAbatement as EHRAgeAbatement
import com.projectronin.interop.ehr.model.Condition.DateTimeAbatement as EHRDateTimeAbatement
import com.projectronin.interop.ehr.model.Condition.PeriodAbatement as EHRPeriodAbatement
import com.projectronin.interop.ehr.model.Condition.RangeAbatement as EHRRangeAbatement
import com.projectronin.interop.ehr.model.Condition.StringAbatement as EHRStringAbatement
import com.projectronin.interop.ehr.model.Period as EHRPeriod
import com.projectronin.interop.ehr.model.Range as EHRRange

class AbatementTest {
    @Test
    fun `creates date time abatement`() {
        val ehrDateTimeAbatement = mockk<EHRDateTimeAbatement> {
            every { value } returns "2022-03-09"
        }
        val abatement = DateTimeAbatement(ehrDateTimeAbatement)
        assertEquals("2022-03-09", abatement.value)
    }

    @Test
    fun `creates age abatement`() {
        val ehrAge = relaxedMockk<EHRAge>()
        val ehrAgeAbatement = mockk<EHRAgeAbatement> {
            every { value } returns ehrAge
        }
        val abatement = AgeAbatement(ehrAgeAbatement)
        assertNotNull(abatement.value)
    }

    @Test
    fun `creates period abatement`() {
        val ehrPeriod = relaxedMockk<EHRPeriod>()
        val ehrPeriodAbatement = mockk<EHRPeriodAbatement> {
            every { value } returns ehrPeriod
        }
        val abatement = PeriodAbatement(ehrPeriodAbatement)
        assertNotNull(abatement.value)
    }

    @Test
    fun `creates range abatement`() {
        val ehrRange = relaxedMockk<EHRRange>()
        val ehrRangeAbatement = mockk<EHRRangeAbatement> {
            every { value } returns ehrRange
        }
        val abatement = RangeAbatement(ehrRangeAbatement)
        assertNotNull(abatement.value)
    }

    @Test
    fun `creates string abatement`() {
        val ehrStringAbatement = mockk<EHRStringAbatement> {
            every { value } returns "recently"
        }
        val abatement = StringAbatement(ehrStringAbatement)
        assertEquals("recently", abatement.value)
    }
}
