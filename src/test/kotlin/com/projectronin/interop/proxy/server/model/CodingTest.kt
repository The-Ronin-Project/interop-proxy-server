package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRBoolean
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Coding as R4Coding

class CodingTest {
    @Test
    fun `can get use`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { system?.value } returns "system"
            }
        val coding = Coding(ehrCoding)
        assertEquals("system", coding.system)
    }

    @Test
    fun `null system`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { system } returns null
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.system)
    }

    @Test
    fun `can get version`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { version } returns "version".asFHIR()
            }
        val coding = Coding(ehrCoding)
        assertEquals("version", coding.version)
    }

    @Test
    fun `can get null version`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { version } returns null
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.version)
    }

    @Test
    fun `can get version with null value`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { version } returns FHIRString(null)
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.version)
    }

    @Test
    fun `can get code`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { code?.value } returns "code"
            }
        val coding = Coding(ehrCoding)
        assertEquals("code", coding.code)
    }

    @Test
    fun `null code`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { code } returns null
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.code)
    }

    @Test
    fun `can get display`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { display } returns "display".asFHIR()
            }
        val coding = Coding(ehrCoding)
        assertEquals("display", coding.display)
    }

    @Test
    fun `can get null display`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { display } returns null
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.display)
    }

    @Test
    fun `can get display with null value`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { display } returns FHIRString(null)
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.display)
    }

    @Test
    fun `can get user selected`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { userSelected } returns FHIRBoolean.TRUE
            }
        val coding = Coding(ehrCoding)
        assertEquals(true, coding.userSelected)
    }

    @Test
    fun `can get null user selected`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { userSelected } returns null
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.userSelected)
    }

    @Test
    fun `can get user selected with null value`() {
        val ehrCoding =
            relaxedMockk<R4Coding> {
                every { userSelected } returns FHIRBoolean(null)
            }
        val coding = Coding(ehrCoding)
        assertNull(coding.userSelected)
    }
}
