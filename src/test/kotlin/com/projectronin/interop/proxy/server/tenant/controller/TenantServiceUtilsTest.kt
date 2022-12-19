package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.tenant.config.model.BatchConfig
import com.projectronin.interop.tenant.config.model.CernerAuthenticationConfig
import com.projectronin.interop.tenant.config.model.EpicAuthenticationConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId
import com.projectronin.interop.proxy.server.tenant.model.Cerner as ProxyCerner
import com.projectronin.interop.proxy.server.tenant.model.Epic as ProxyEpic
import com.projectronin.interop.proxy.server.tenant.model.Tenant as ProxyTenant
import com.projectronin.interop.tenant.config.model.Tenant as TenantServiceTenant
import com.projectronin.interop.tenant.config.model.vendor.Cerner as TenantServiceCerner
import com.projectronin.interop.tenant.config.model.vendor.Epic as TenantServiceEpic
class TenantServiceUtilsTest {

    @Test
    fun `toProxyTenant - cerner`() {
        val tenantServerCerner = TenantServiceCerner(
            serviceEndpoint = "serviceEndpoint",
            patientMRNSystem = "patientMRNSystem",
            instanceName = "instanceName",
            clientId = "notLeaked",
            authenticationConfig = CernerAuthenticationConfig("serviceEndpoint", "notleaked", "notleaked"),
        )
        val tenantServiceTenant = TenantServiceTenant(
            internalId = 1,
            mnemonic = "mnemonic",
            name = "name",
            timezone = ZoneId.of("America/Los_Angeles"),
            batchConfig = BatchConfig(availableStart = LocalTime.of(12, 0), availableEnd = LocalTime.of(12, 0)),
            vendor = tenantServerCerner
        )
        val proxyTenant = tenantServiceTenant.toProxyTenant()
        assertNotNull(proxyTenant)
        val cerner = proxyTenant.vendor as ProxyCerner
        assertEquals("serviceEndpoint", cerner.serviceEndpoint)
        assertEquals("patientMRNSystem", cerner.patientMRNSystem)
        assertEquals("instanceName", cerner.instanceName)
    }

    @Test
    fun `toProxyTenant - epic`() {
        val tenantServerEpic = TenantServiceEpic(
            clientId = "notLeaked",
            instanceName = "instanceName",
            authenticationConfig = EpicAuthenticationConfig(
                authEndpoint = "authEndpoint",
                publicKey = "notLeaked",
                privateKey = "notLeaked"
            ),
            serviceEndpoint = "serviceEndpoint",
            release = "release",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "providerSystemExample",
            practitionerUserSystem = "userSystemExample",
            patientMRNSystem = "mrnSystemExample",
            patientInternalSystem = "internalSystemExample",
            encounterCSNSystem = "encounterCSNSystem",
            patientMRNTypeText = "patientMRNTypeText",
            departmentInternalSystem = "departmentInternalSystem"
        )
        val tenantServiceTenant = TenantServiceTenant(
            internalId = 1,
            mnemonic = "mnemonic",
            name = "name",
            timezone = ZoneId.of("America/Los_Angeles"),
            batchConfig = null,
            vendor = tenantServerEpic
        )
        val proxyTenant = tenantServiceTenant.toProxyTenant()
        assertNotNull(proxyTenant)
        val epic = proxyTenant.vendor as ProxyEpic
        assertEquals("serviceEndpoint", epic.serviceEndpoint)
        assertEquals("mrnSystemExample", epic.patientMRNSystem)
        assertEquals("instanceName", epic.instanceName)
    }

    @Test
    fun `toTenantServer - cerner`() {
        val proxyTenant = ProxyTenant(
            id = 1,
            mnemonic = "mnemonic",
            name = "name",
            availableStart = LocalTime.of(12, 0),
            availableEnd = LocalTime.of(12, 0),
            timezone = "America/Los_Angeles",
            vendor = ProxyCerner(
                "serviceEndpoint",
                instanceName = "instanceName",
                patientMRNSystem = "patientMRNSystem"
            )
        )
        val tenantServerTenant = proxyTenant.toTenantServerVendor()
        assertEquals(1, tenantServerTenant.internalId)
        val cerner = tenantServerTenant.vendor as TenantServiceCerner
        assertEquals("serviceEndpoint", cerner.serviceEndpoint)
    }

    @Test
    fun `toTenantServer - epic`() {
        val proxyTenant = ProxyTenant(
            id = 1,
            mnemonic = "mnemonic",
            name = "name",
            availableStart = null,
            availableEnd = null,
            timezone = "America/Los_Angeles",
            vendor = ProxyEpic(
                release = "release",
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                ehrUserId = "ehrUserId",
                messageType = "messageType",
                practitionerProviderSystem = "providerSystemExample",
                practitionerUserSystem = "userSystemExample",
                patientMRNSystem = "mrnSystemExample",
                patientInternalSystem = "internalSystemExample",
                encounterCSNSystem = "encounterCSNSystem",
                patientMRNTypeText = "patientMRNTypeText",
                hsi = null,
                instanceName = "instanceName",
                departmentInternalSystem = "departmentInternalSystem"
            )
        )
        val tenantServerTenant = proxyTenant.toTenantServerVendor()
        assertEquals(1, tenantServerTenant.internalId)
        val epic = tenantServerTenant.vendor as TenantServiceEpic
        assertEquals("serviceEndpoint", epic.serviceEndpoint)
    }

    // code cov test
    @Test
    fun `toTenantServerTenant() - batch start but no end and new id`() {
        val proxyTenant = ProxyTenant(
            id = 1,
            mnemonic = "mnemonic",
            name = "name",
            availableStart = LocalTime.of(12, 0),
            availableEnd = null,
            timezone = "America/Los_Angeles",
            vendor = ProxyCerner(
                "serviceEndpoint",
                instanceName = "instanceName",
                patientMRNSystem = "patientMRNSystem"
            )
        )
        val newTenantServerTenant = proxyTenant.toTenantServerVendor(999)
        assertEquals(999, newTenantServerTenant.internalId)
        val newCerner = newTenantServerTenant.vendor as TenantServiceCerner
        assertEquals("serviceEndpoint", newCerner.serviceEndpoint)
    }
}
