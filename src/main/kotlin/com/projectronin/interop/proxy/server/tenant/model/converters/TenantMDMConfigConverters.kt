package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.proxy.server.tenant.model.TenantMDMConfig
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.data.model.TenantMDMConfigDO

fun TenantMDMConfigDO.toProxyTenantMDMConfig(): TenantMDMConfig {
    return TenantMDMConfig(
        mdmDocumentTypeID = mdmDocumentTypeID,
        providerIdentifierSystem = providerIdentifierSystem,
        receivingSystem = receivingSystem
    )
}

fun TenantMDMConfig.toTenantMDMConfigDO(proxyTenant: Tenant): TenantMDMConfigDO {
    return TenantMDMConfigDO {
        mdmDocumentTypeID = this@toTenantMDMConfigDO.mdmDocumentTypeID
        tenant = TenantDO {
            id = proxyTenant.id
        }
        providerIdentifierSystem = this@toTenantMDMConfigDO.providerIdentifierSystem
        receivingSystem = this@toTenantMDMConfigDO.receivingSystem
    }
}
