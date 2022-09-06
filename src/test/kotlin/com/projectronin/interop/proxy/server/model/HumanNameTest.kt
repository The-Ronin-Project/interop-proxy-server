package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.valueset.NameUse
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.HumanName as R4HumanName

class HumanNameTest {
    @Test
    fun `can get use`() {
        val ehrHumanName = relaxedMockk<R4HumanName> {
            every { use } returns NameUse.USUAL.asCode()
        }
        val humanName = HumanName(ehrHumanName)
        assertEquals("usual", humanName.use)
    }

    @Test
    fun `can get null use`() {
        val ehrHumanName = relaxedMockk<R4HumanName> {
            every { use } returns null
        }
        val humanName = HumanName(ehrHumanName)
        assertNull(humanName.use)
    }

    @Test
    fun `can get family`() {
        val ehrHumanName = relaxedMockk<R4HumanName> {
            every { family } returns "Public"
        }
        val humanName = HumanName(ehrHumanName)
        assertEquals("Public", humanName.family)
    }

    @Test
    fun `can get given`() {
        val ehrHumanName = relaxedMockk<R4HumanName> {
            every { given } returns listOf("John", "Q")
        }
        val humanName = HumanName(ehrHumanName)
        assertEquals(listOf("John", "Q"), humanName.given)
    }
}
