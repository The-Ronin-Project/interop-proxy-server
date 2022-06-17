package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.tenant.config.model.AuthenticationConfig
import com.projectronin.interop.tenant.config.model.BatchConfig
import com.projectronin.interop.proxy.server.tenant.model.Epic as ProxyEpic
import com.projectronin.interop.proxy.server.tenant.model.Tenant as ProxyTenant
import com.projectronin.interop.tenant.config.model.Tenant as TenantServiceTenant
import com.projectronin.interop.tenant.config.model.vendor.Epic as TenantServiceEpic

fun TenantServiceTenant.toProxyTenant(): ProxyTenant {
    return ProxyTenant(
        id = internalId,
        mnemonic = mnemonic,
        availableStart = batchConfig?.availableStart,
        availableEnd = batchConfig?.availableEnd,
        vendor = (vendor as TenantServiceEpic).toProxyEpic(),
        name = name
    )
}

fun ProxyTenant.toTenantServerTenant(): TenantServiceTenant {
    return TenantServiceTenant(
        name = name,
        internalId = id,
        mnemonic = mnemonic,
        batchConfig = availableStart?.let { start ->
            availableEnd?.let { end ->
                BatchConfig(start, end)
            }
        },
        vendor = (vendor as ProxyEpic).toTenantServerEpic()
    )
}

fun ProxyEpic.toTenantServerEpic(): TenantServiceEpic {
    return TenantServiceEpic(
        release = release,
        serviceEndpoint = serviceEndpoint,
        ehrUserId = ehrUserId,
        messageType = messageType,
        practitionerProviderSystem = practitionerProviderSystem,
        practitionerUserSystem = practitionerUserSystem,
        patientMRNSystem = mrnSystem,
        patientMRNTypeText = "MRN",
        hsi = hsi,
        clientId = "",
        instanceName = instanceName,
        authenticationConfig = AuthenticationConfig(authEndpoint, "", ""),
        // TODO: Add reference to patientInternalSystem
        patientInternalSystem = ""
    )
}

fun TenantServiceEpic.toProxyEpic(): ProxyEpic {
    return ProxyEpic(
        release = release,
        serviceEndpoint = serviceEndpoint,
        authEndpoint = authenticationConfig.authEndpoint,
        ehrUserId = ehrUserId,
        messageType = messageType,
        practitionerProviderSystem = practitionerProviderSystem,
        practitionerUserSystem = practitionerUserSystem,
        mrnSystem = patientMRNSystem,
        hsi = hsi,
        instanceName = instanceName

    )
}
