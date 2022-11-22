package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.tenant.config.model.AuthenticationConfig
import com.projectronin.interop.tenant.config.model.BatchConfig
import java.time.ZoneId
import com.projectronin.interop.proxy.server.tenant.model.Epic as ProxyEpic
import com.projectronin.interop.proxy.server.tenant.model.Tenant as ProxyTenant
import com.projectronin.interop.tenant.config.model.Tenant as TenantServiceTenant
import com.projectronin.interop.tenant.config.model.vendor.Epic as TenantServiceEpic

fun TenantServiceTenant.toProxyTenant(): ProxyTenant {
    return ProxyTenant(
        id = internalId,
        mnemonic = mnemonic,
        name = name,
        timezone = timezone.id,
        availableStart = batchConfig?.availableStart,
        availableEnd = batchConfig?.availableEnd,
        vendor = (vendor as TenantServiceEpic).toProxyEpic()
    )
}

/**
 * Creates a new [TenantServiceTenant], but gives it the passed [newId]
 */
fun ProxyTenant.toTenantServerTenant(newId: Int): TenantServiceTenant {
    return TenantServiceTenant(
        internalId = newId,
        mnemonic = mnemonic,
        name = name,
        timezone = ZoneId.of(timezone),
        batchConfig = availableStart?.let { start ->
            availableEnd?.let { end ->
                BatchConfig(start, end)
            }
        },
        vendor = (vendor as ProxyEpic).toTenantServerEpic()
    )
}

fun ProxyTenant.toTenantServerTenant(): TenantServiceTenant {
    return toTenantServerTenant(id)
}

fun ProxyEpic.toTenantServerEpic(): TenantServiceEpic {
    return TenantServiceEpic(
        release = release,
        serviceEndpoint = serviceEndpoint,
        ehrUserId = ehrUserId,
        messageType = messageType,
        practitionerProviderSystem = practitionerProviderSystem,
        practitionerUserSystem = practitionerUserSystem,
        patientMRNSystem = patientMRNSystem,
        patientInternalSystem = patientInternalSystem,
        encounterCSNSystem = encounterCSNSystem,
        patientMRNTypeText = patientMRNTypeText,
        hsi = hsi,
        clientId = "",
        instanceName = instanceName,
        authenticationConfig = AuthenticationConfig(authEndpoint, "", ""),
        departmentInternalSystem = departmentInternalSystem
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
        patientMRNSystem = patientMRNSystem,
        patientInternalSystem = patientInternalSystem,
        patientMRNTypeText = patientMRNTypeText,
        encounterCSNSystem = encounterCSNSystem,
        hsi = hsi,
        instanceName = instanceName,
        departmentInternalSystem = departmentInternalSystem
    )
}
