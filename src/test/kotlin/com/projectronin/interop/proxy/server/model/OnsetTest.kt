package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range

class OnsetTest {
    @Test
    fun `creates date time onset`() {
        val ehrDateTimeOnset = mockk<DynamicValue<DateTime>> {
            every { value } returns DateTime("2022-03-09")
        }
        val onset = DateTimeOnset(ehrDateTimeOnset.value)
        assertEquals("2022-03-09", onset.value)
    }

    @Test
    fun `creates age onset`() {
        val ehrAge = relaxedMockk<R4Age>()
        val ehrAgeOnset = mockk<DynamicValue<R4Age>> {
            every { value } returns ehrAge
        }
        val onset = AgeOnset(ehrAgeOnset.value)
        assertNotNull(onset.value)
    }

    @Test
    fun `creates period onset`() {
        val ehrPeriod = relaxedMockk<R4Period>()
        val ehrPeriodOnset = mockk<DynamicValue<R4Period>> {
            every { value } returns ehrPeriod
        }
        val onset = PeriodOnset(ehrPeriodOnset.value)
        assertNotNull(onset.value)
    }

    @Test
    fun `creates range onset`() {
        val ehrRange = relaxedMockk<R4Range>()
        val ehrRangeOnset = mockk<DynamicValue<R4Range>> {
            every { value } returns ehrRange
        }
        val onset = RangeOnset(ehrRangeOnset.value)
        assertNotNull(onset.value)
    }

    @Test
    fun `creates string onset`() {
        val ehrStringOnset = mockk<DynamicValue<String>> {
            every { value } returns "recently"
        }
        val onset = StringOnset(ehrStringOnset.value)
        assertEquals("recently", onset.value)
    }
}
