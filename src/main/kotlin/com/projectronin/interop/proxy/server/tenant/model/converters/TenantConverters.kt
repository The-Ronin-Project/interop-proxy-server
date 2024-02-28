package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.tenant.config.model.BatchConfig
import com.projectronin.interop.tenant.config.model.CernerAuthenticationConfig
import com.projectronin.interop.tenant.config.model.EpicAuthenticationConfig
import java.time.ZoneId
import com.projectronin.interop.proxy.server.tenant.model.Cerner as ProxyCerner
import com.projectronin.interop.proxy.server.tenant.model.Epic as ProxyEpic
import com.projectronin.interop.proxy.server.tenant.model.Tenant as ProxyTenant
import com.projectronin.interop.proxy.server.tenant.model.Vendor as ProxyVendor
import com.projectronin.interop.tenant.config.model.Tenant as TenantServiceTenant
import com.projectronin.interop.tenant.config.model.vendor.Cerner as TenantServiceCerner
import com.projectronin.interop.tenant.config.model.vendor.Epic as TenantServiceEpic
import com.projectronin.interop.tenant.config.model.vendor.Vendor as TenantServerVendor

fun TenantServiceTenant.toProxyTenant(): ProxyTenant {
    return ProxyTenant(
        id = internalId,
        mnemonic = mnemonic,
        name = name,
        timezone = timezone.id,
        availableStart = batchConfig?.availableStart,
        availableEnd = batchConfig?.availableEnd,
        vendor = vendor.toProxyVendor(),
        monitoredIndicator = monitoredIndicator,
    )
}

private fun TenantServerVendor.toProxyVendor(): ProxyVendor {
    return when (this.type) {
        VendorType.EPIC -> (this as TenantServiceEpic).toProxyEpic()
        VendorType.CERNER -> (this as TenantServiceCerner).toProxyCerner()
    }
}

private fun TenantServiceEpic.toProxyEpic(): ProxyEpic {
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
        departmentInternalSystem = departmentInternalSystem,
        patientOnboardedFlagId = patientOnboardedFlagId,
        orderSystem = orderSystem,
        appVersion = appVersion,
    )
}

private fun TenantServiceCerner.toProxyCerner(): ProxyCerner {
    return ProxyCerner(
        serviceEndpoint = serviceEndpoint,
        authEndpoint = authenticationConfig.authEndpoint,
        patientMRNSystem = patientMRNSystem,
        instanceName = instanceName,
        messagePractitioner = messagePractitioner,
        messageTopic = messageTopic,
        messageCategory = messageCategory,
        messagePriority = messagePriority,
    )
}

/**
 * Creates a new [TenantServiceTenant], but gives it the passed [newId]
 */
fun ProxyTenant.toTenantServerTenant(newId: Int = this.id): TenantServiceTenant {
    return TenantServiceTenant(
        internalId = newId,
        mnemonic = mnemonic,
        name = name,
        timezone = ZoneId.of(timezone),
        batchConfig =
            availableStart?.let { start ->
                availableEnd?.let { end ->
                    BatchConfig(start, end)
                }
            },
        vendor = vendor.toTenantServerTenant(),
        monitoredIndicator = monitoredIndicator,
    )
}

fun ProxyVendor.toTenantServerTenant(): TenantServerVendor {
    return when (this.vendorType) {
        VendorType.EPIC -> (this as ProxyEpic).toTenantServerEpic()
        VendorType.CERNER -> (this as ProxyCerner).toTenantServerCerner()
    }
}

private fun ProxyEpic.toTenantServerEpic(): TenantServiceEpic {
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
        authenticationConfig = EpicAuthenticationConfig(authEndpoint, "", ""),
        departmentInternalSystem = departmentInternalSystem,
        patientOnboardedFlagId = patientOnboardedFlagId,
        orderSystem = orderSystem,
        appVersion = appVersion,
    )
}

private fun ProxyCerner.toTenantServerCerner(): TenantServiceCerner {
    return TenantServiceCerner(
        serviceEndpoint = serviceEndpoint,
        patientMRNSystem = patientMRNSystem,
        instanceName = instanceName,
        clientId = "",
        messagePractitioner = messagePractitioner,
        messageTopic = messageTopic,
        authenticationConfig =
            CernerAuthenticationConfig(
                authEndpoint = authEndpoint,
                accountId = "",
                secret = "",
            ),
        messageCategory = messageCategory,
        messagePriority = messagePriority,
    )
}
