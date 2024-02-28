package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import com.projectronin.interop.tenant.config.data.model.TenantDO

fun MirthTenantConfigDO.toProxyMirthTenantConfig(): MirthTenantConfig {
    return MirthTenantConfig(
        locationIds =
            if (locationIds.isEmpty()) {
                emptyList()
            } else {
                locationIds.split(",")
            },
        lastUpdated = lastUpdated,
        blockedResources =
            if (blockedResources.isNullOrEmpty()) {
                emptyList()
            } else {
                blockedResources!!.split(",")
            },
        allowedResources =
            if (allowedResources.isNullOrEmpty()) {
                emptyList()
            } else {
                allowedResources!!.split(",")
            },
    )
}

fun MirthTenantConfig.toMirthTenantConfigDO(proxyTenant: Tenant): MirthTenantConfigDO {
    return MirthTenantConfigDO {
        locationIds = this@toMirthTenantConfigDO.locationIds.joinToString(",")
        tenant =
            TenantDO {
                id = proxyTenant.id
            }
        lastUpdated = this@toMirthTenantConfigDO.lastUpdated
        blockedResources = this@toMirthTenantConfigDO.blockedResources.joinToString(",")
        allowedResources = this@toMirthTenantConfigDO.allowedResources.joinToString(",")
    }
}
