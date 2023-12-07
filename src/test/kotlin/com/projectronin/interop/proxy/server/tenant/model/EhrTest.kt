package com.projectronin.interop.proxy.server.tenant.model

import com.projectronin.interop.common.vendor.VendorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EhrTest {
    @Test
    fun `check getters`() {
        val ehr =
            Ehr(
                VendorType.EPIC,
                "instanceName",
                "clientId",
                "public",
                "private",
                "accountId",
                "secret",
            )
        assertEquals(VendorType.EPIC, ehr.vendorType)
        assertEquals("clientId", ehr.clientId)
        assertEquals("public", ehr.publicKey)
        assertEquals("private", ehr.privateKey)
        assertEquals("accountId", ehr.accountId)
        assertEquals("secret", ehr.secret)
    }
}
