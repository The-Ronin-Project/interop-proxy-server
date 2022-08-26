package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime as R4DateTime

class AbatementTest {
    @Test
    fun `creates date time abatement`() {
        val ehrDateTimeAbatement = mockk<R4DateTime> {
            every { value } returns "2022-03-09"
        }
        val abatement = DateTimeAbatement(ehrDateTimeAbatement)
        assertEquals("2022-03-09", abatement.value)
    }

    @Test
    fun `creates age abatement`() {
        val ehrAge = relaxedMockk<R4Age>()
        every { ehrAge.value } returns 10.0
        val abatement = AgeAbatement(ehrAge)
        assertNotNull(abatement.value)
    }

    @Test
    fun `creates period abatement`() {
        val ehrPeriod = relaxedMockk<R4Period>()
        every { ehrPeriod.id } returns "1"
        val abatement = PeriodAbatement(ehrPeriod)
        assertNotNull(abatement.value)
    }

    @Test
    fun `creates range abatement`() {
        val ehrRange = relaxedMockk<R4Range>()
        val abatement = RangeAbatement(ehrRange)
        assertNotNull(abatement.value)
    }

    @Test
    fun `creates string abatement`() {
        val abatement = StringAbatement("recently")
        assertEquals("recently", abatement.value)
    }
}
