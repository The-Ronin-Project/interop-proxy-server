package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Decimal
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.valueset.QuantityComparator
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age

internal class AgeTest {
    @Test
    fun `can get value`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { value } returns Decimal(21.0)
        }
        val age = Age(ehrAge)
        assertEquals(21.0, age.value)
    }

    @Test
    fun `can get null value`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { value } returns null
        }
        val age = Age(ehrAge)
        assertNull(age.value)
    }

    @Test
    fun `can get value with null value`() {
        val doubleValue: Double? = null
        val ehrAge = relaxedMockk<R4Age> {
            every { value } returns Decimal(doubleValue)
        }
        val age = Age(ehrAge)
        assertNull(age.value)
    }

    @Test
    fun `can get comparator`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { comparator } returns QuantityComparator.LESS_THAN.asCode()
        }
        val age = Age(ehrAge)
        assertEquals("<", age.comparator)
    }

    @Test
    fun `can get null comparator`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { comparator } returns null
        }
        val age = Age(ehrAge)
        assertNull(age.comparator)
    }

    @Test
    fun `can get unit`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { unit } returns "unit".asFHIR()
        }
        val age = Age(ehrAge)
        assertEquals("unit", age.unit)
    }

    @Test
    fun `can get null unit`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { unit } returns null
        }
        val age = Age(ehrAge)
        assertNull(age.unit)
    }

    @Test
    fun `can get unit with null value`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { unit } returns FHIRString(null)
        }
        val age = Age(ehrAge)
        assertNull(age.unit)
    }

    @Test
    fun `can get system`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { system?.value } returns "system"
        }
        val age = Age(ehrAge)
        assertEquals("system", age.system)
    }

    @Test
    fun `null system`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { system } returns null
        }
        val age = Age(ehrAge)
        assertNull(age.system)
    }

    @Test
    fun `can get code`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { code?.value } returns "code"
        }
        val age = Age(ehrAge)
        assertEquals("code", age.code)
    }

    @Test
    fun `null code`() {
        val ehrAge = relaxedMockk<R4Age> {
            every { code } returns null
        }
        val age = Age(ehrAge)
        assertNull(age.code)
    }
}
