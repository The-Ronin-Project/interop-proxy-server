package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Coding as EHRCoding

class CodingTest {
    @Test
    fun `can get use`() {
        val ehrCoding = relaxedMockk<EHRCoding> {
            every { system } returns "system"
        }
        val coding = Coding(ehrCoding)
        assertEquals("system", coding.system)
    }

    @Test
    fun `can get version`() {
        val ehrCoding = relaxedMockk<EHRCoding> {
            every { version } returns "version"
        }
        val coding = Coding(ehrCoding)
        assertEquals("version", coding.version)
    }

    @Test
    fun `can get code`() {
        val ehrCoding = relaxedMockk<EHRCoding> {
            every { code } returns "code"
        }
        val coding = Coding(ehrCoding)
        assertEquals("code", coding.code)
    }

    @Test
    fun `can get display`() {
        val ehrCoding = relaxedMockk<EHRCoding> {
            every { display } returns "display"
        }
        val coding = Coding(ehrCoding)
        assertEquals("display", coding.display)
    }

    @Test
    fun `can get user selected`() {
        val ehrCoding = relaxedMockk<EHRCoding> {
            every { userSelected } returns true
        }
        val coding = Coding(ehrCoding)
        assertEquals(true, coding.userSelected)
    }
}
