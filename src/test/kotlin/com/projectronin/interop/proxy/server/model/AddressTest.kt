package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AddressTest {
    @Test
    fun `check getters`() {
        val address = Address(
            use = "home",
            line = listOf("1234 Main St"),
            city = "Anywhere",
            state = "FL",
            postalCode = "37890"
        )
        assertEquals("home", address.use)
        assertEquals(listOf("1234 Main St"), address.line)
        assertEquals("Anywhere", address.city)
        assertEquals("FL", address.state)
        assertEquals("37890", address.postalCode)
    }

    @Test
    fun `address can be created with default values`() {
        val address = Address(
            use = "home",
            city = "Anywhere",
            state = "FL",
            postalCode = "37890"
        )
        assertEquals("home", address.use)
        assertEquals(listOf<String>(), address.line)
        assertEquals("Anywhere", address.city)
        assertEquals("FL", address.state)
        assertEquals("37890", address.postalCode)
    }
}
