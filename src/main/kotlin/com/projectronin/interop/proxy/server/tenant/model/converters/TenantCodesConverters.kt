package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.proxy.server.tenant.model.TenantCodes
import com.projectronin.interop.tenant.config.data.model.TenantCodesDO

/**
 * Converts from the data object representation of a code object to the server's object.
 */
fun TenantCodesDO.toProxyTenantCodes(): TenantCodes {
    return TenantCodes(bsaCode = bsaCode, bmiCode = bmiCode, stageCodes = stageCodes)
}

/**
 * Converts from the service's representation of a code object to the data object.
 */
fun TenantCodes.toTenantCodesDO(tenant: Int): TenantCodesDO {
    return TenantCodesDO {
        tenantId = tenant
        bsaCode = this@toTenantCodesDO.bsaCode
        bmiCode = this@toTenantCodesDO.bmiCode
        stageCodes = this@toTenantCodesDO.stageCodes
    }
}
