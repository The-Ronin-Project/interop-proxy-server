package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.common.vendor.VendorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EhrTest {
    @Test
    fun `check getters`() {
        val ehr =
            Ehr(
                VendorType.EPIC,
                "clientId",
                "public",
                "private"
            )
        assertEquals(VendorType.EPIC, ehr.vendorType)
        assertEquals("clientId", ehr.clientId)
        assertEquals("public", ehr.publicKey)
        assertEquals("private", ehr.privateKey)
    }
}
