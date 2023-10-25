package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.hl7.ProcessingID
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.tenant.config.data.model.TenantServerDO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TenantServerConvertersTest {
    @Test
    fun `toProxyTenantServer works`() {
        val tenantServer = TenantServerDO {
            id = 123
            messageType = MessageType.MDM
            address = "google"
            port = 123
            serverType = ProcessingID.VALIDATION
        }
        val proxyTenantServer = tenantServer.toProxyTenantServer()
        assertNotNull(proxyTenantServer)
        assertEquals("google", proxyTenantServer.address)
    }

    @Test
    fun `toTenantServerDO works `() {
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "127.0.0.1",
            port = 80,
            serverType = "V"
        )
        val tenantServerDO = tenantServer.toTenantServerDO(mockk { every { id } returns 1 })
        assertNotNull(tenantServerDO)
        assertEquals("127.0.0.1", tenantServerDO.address)
    }
}
