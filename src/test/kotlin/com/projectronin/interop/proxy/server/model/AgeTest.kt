package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.ehr.model.enums.QuantityComparator
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Age as EHRAge

internal class AgeTest {
    @Test
    fun `can get value`() {
        val ehrAge = relaxedMockk<EHRAge> {
            every { value } returns 21.0
        }
        val age = Age(ehrAge)
        assertEquals(21.0, age.value)
    }

    @Test
    fun `can get comparator`() {
        val ehrAge = relaxedMockk<EHRAge> {
            every { comparator } returns QuantityComparator.LESS_THAN
        }
        val age = Age(ehrAge)
        assertEquals("<", age.comparator)
    }

    @Test
    fun `can get null comparator`() {
        val ehrAge = relaxedMockk<EHRAge> {
            every { comparator } returns null
        }
        val age = Age(ehrAge)
        assertNull(age.comparator)
    }

    @Test
    fun `can get unit`() {
        val ehrAge = relaxedMockk<EHRAge> {
            every { unit } returns "unit"
        }
        val age = Age(ehrAge)
        assertEquals("unit", age.unit)
    }

    @Test
    fun `can get system`() {
        val ehrAge = relaxedMockk<EHRAge> {
            every { system } returns "system"
        }
        val age = Age(ehrAge)
        assertEquals("system", age.system)
    }

    @Test
    fun `can get code`() {
        val ehrAge = relaxedMockk<EHRAge> {
            every { code } returns "code"
        }
        val age = Age(ehrAge)
        assertEquals("code", age.code)
    }
}
