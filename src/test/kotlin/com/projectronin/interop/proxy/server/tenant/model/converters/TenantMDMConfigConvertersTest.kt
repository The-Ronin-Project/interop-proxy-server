package com.projectronin.interop.proxy.server.tenant.model.converters
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.proxy.server.tenant.model.TenantMDMConfig
import com.projectronin.interop.tenant.config.data.model.TenantMDMConfigDO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TenantMDMConfigConvertersTest {

    @Test
    fun `toProxyTenantMDMConfig works`() {
        val config = TenantMDMConfigDO {
            mdmDocumentTypeID = "typeid1"
            providerIdentifierSystem = "idsystem1"
            receivingSystem = "rsystem1"
        }
        val proxyConfig = config.toProxyTenantMDMConfig()
        assertNotNull(proxyConfig)
        assertEquals(config.mdmDocumentTypeID, proxyConfig.mdmDocumentTypeID)
        assertEquals(config.providerIdentifierSystem, proxyConfig.providerIdentifierSystem)
        assertEquals(config.receivingSystem, proxyConfig.receivingSystem)
    }

    @Test
    fun `toTenantMDMConfigDO works `() {
        val tenant = mockk<Tenant> {
            every { id } returns 1
        }
        val proxyConfig = TenantMDMConfig(
            mdmDocumentTypeID = "typeid1",
            providerIdentifierSystem = "idsystem1",
            receivingSystem = "rsystem1"
        )
        val configDo = proxyConfig.toTenantMDMConfigDO(tenant)
        assertNotNull(configDo)
        assertEquals(proxyConfig.mdmDocumentTypeID, configDo.mdmDocumentTypeID)
        assertEquals(proxyConfig.providerIdentifierSystem, configDo.providerIdentifierSystem)
        assertEquals(proxyConfig.receivingSystem, configDo.receivingSystem)
    }
}
