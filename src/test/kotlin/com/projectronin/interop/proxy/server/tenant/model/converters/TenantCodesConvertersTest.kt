package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.proxy.server.tenant.model.TenantCodes
import com.projectronin.interop.tenant.config.data.binding.TenantCodesDOs.bmiCode
import com.projectronin.interop.tenant.config.data.binding.TenantCodesDOs.bsaCode
import com.projectronin.interop.tenant.config.data.binding.TenantCodesDOs.stageCodes
import com.projectronin.interop.tenant.config.data.model.TenantCodesDO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TenantCodesConvertersTest {

    @Test
    fun `ensure DO to Proxy works`() {
        val tenantCodesDO = TenantCodesDO {
            bmiCode = "123"
            bsaCode = "456"
            stageCodes = "910,322"
        }

        val actualProxyCodes = tenantCodesDO.toProxyTenantCodes()

        assertEquals("123", actualProxyCodes.bmiCode)
        assertEquals("456", actualProxyCodes.bsaCode)
        assertEquals("910,322", actualProxyCodes.stageCodes)
    }

    @Test
    fun `ensure  Proxy to DO works`() {
        val proxyCodes = TenantCodes(bmiCode = "123", bsaCode = "456", stageCodes = "910,322")

        val actualTenantCodesDO = proxyCodes.toTenantCodesDO(12232)

        assertEquals(12232, actualTenantCodesDO.tenantId)
        assertEquals("123", actualTenantCodesDO.bmiCode)
        assertEquals("456", actualTenantCodesDO.bsaCode)
        assertEquals("910,322", actualTenantCodesDO.stageCodes)
    }
}
