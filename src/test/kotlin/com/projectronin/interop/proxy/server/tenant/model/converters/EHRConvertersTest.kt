package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import com.projectronin.interop.tenant.config.data.model.EhrDO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EHRConvertersTest {
    @Test
    fun `toProxyEhr - cerner`() {
        val ehrDo =
            EhrDO {
                vendorType = VendorType.CERNER
                instanceName = "instanceName"
                clientId = "clientId"
                accountId = "accountId"
                secret = "secret"
            }
        val proxyEhr = ehrDo.toProxyEHR()
        assertNotNull(proxyEhr)
        assertEquals("instanceName", proxyEhr.instanceName)
        assertEquals("accountId", proxyEhr.accountId)
        assertNull(proxyEhr.publicKey)
    }

    @Test
    fun `toProxyEhr - epic`() {
        val ehrDo =
            EhrDO {
                vendorType = VendorType.EPIC
                instanceName = "instanceName"
                clientId = "clientId"
                publicKey = "publicKey"
                privateKey = "privateKey"
            }
        val proxyEhr = ehrDo.toProxyEHR()
        assertNotNull(proxyEhr)
        assertEquals("instanceName", proxyEhr.instanceName)
        assertNull(proxyEhr.accountId)
    }

    @Test
    fun `toEhrDO - epic`() {
        val ehr =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "instanceName",
                clientId = "clientId1",
                publicKey = "publicKey1",
                privateKey = "privateKey1",
            )
        val proxyEHR = ehr.toEhrDO()
        assertNotNull(proxyEHR)
        assertEquals("publicKey1", proxyEHR.publicKey)
    }

    @Test
    fun `toEhrDO - cerner`() {
        val ehr =
            Ehr(
                vendorType = VendorType.CERNER,
                instanceName = "instanceName",
                clientId = "clientId1",
                accountId = "accountId",
                secret = "secret",
            )
        val proxyEHR = ehr.toEhrDO()
        assertNotNull(proxyEHR)
        assertEquals("accountId", proxyEHR.accountId)
    }

    @Test
    fun `bad toEhrDO throw errors`() {
        val cernerEHR =
            Ehr(
                vendorType = VendorType.CERNER,
                instanceName = "instanceName",
                clientId = "clientId1",
                publicKey = "publicKey1",
                privateKey = "privateKey1",
            )
        val cernerEHR2 =
            Ehr(
                vendorType = VendorType.CERNER,
                instanceName = "instanceName",
                clientId = "clientId1",
                accountId = "accountId",
            )
        val epicEhr =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "instanceName",
                clientId = "clientId1",
                accountId = "accountId",
                secret = "secret",
            )
        val epicEhr2 =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "instanceName",
                clientId = "clientId1",
                publicKey = "publicKey1",
            )

        assertThrows<IllegalStateException> { cernerEHR.toEhrDO() }
        assertThrows<IllegalStateException> { cernerEHR2.toEhrDO() }
        assertThrows<IllegalStateException> { epicEhr.toEhrDO() }
        assertThrows<IllegalStateException> { epicEhr2.toEhrDO() }
    }
}
