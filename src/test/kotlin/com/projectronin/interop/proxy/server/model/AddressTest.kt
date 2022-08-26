package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.valueset.AddressUse
import com.projectronin.interop.proxy.server.util.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Address as R4Address

internal class AddressTest {
    @Test
    fun `can get use`() {
        val ehrAddress = relaxedMockk<R4Address> {
            every { use } returns AddressUse.HOME
        }
        val address = Address(ehrAddress)
        assertEquals("home", address.use)
    }

    @Test
    fun `can get null use`() {
        val ehrAddress = relaxedMockk<R4Address> {
            every { use } returns null
        }
        val address = Address(ehrAddress)
        assertNull(address.use)
    }

    @Test
    fun `can get line`() {
        val ehrAddress = relaxedMockk<R4Address> {
            every { line } returns listOf("line1", "line2")
        }
        val address = Address(ehrAddress)
        assertEquals(listOf("line1", "line2"), address.line)
    }

    @Test
    fun `can get city`() {
        val ehrAddress = relaxedMockk<R4Address> {
            every { city } returns "Kansas City"
        }
        val address = Address(ehrAddress)
        assertEquals("Kansas City", address.city)
    }

    @Test
    fun `can get state`() {
        val ehrAddress = relaxedMockk<R4Address> {
            every { state } returns "MO"
        }
        val address = Address(ehrAddress)
        assertEquals("MO", address.state)
    }

    @Test
    fun `can get postal code`() {
        val ehrAddress = relaxedMockk<R4Address> {
            every { postalCode } returns "64117"
        }
        val address = Address(ehrAddress)
        assertEquals("64117", address.postalCode)
    }
}
