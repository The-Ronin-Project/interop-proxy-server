package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.proxy.server.tenant.model.Cerner
import com.projectronin.interop.proxy.server.tenant.model.Epic
import com.projectronin.interop.tenant.config.model.BatchConfig
import com.projectronin.interop.tenant.config.model.CernerAuthenticationConfig
import com.projectronin.interop.tenant.config.model.EpicAuthenticationConfig
import com.projectronin.interop.tenant.config.model.Tenant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId

class TenantConvertersTest {

    @Test
    fun `toProxyTenant - cerner`() {
        val tenantServerCerner = com.projectronin.interop.tenant.config.model.vendor.Cerner(
            serviceEndpoint = "serviceEndpoint",
            patientMRNSystem = "patientMRNSystem",
            instanceName = "instanceName",
            clientId = "notLeaked",
            authenticationConfig = CernerAuthenticationConfig("serviceEndpoint", "notleaked", "notleaked"),
        )
        val tenantServiceTenant = Tenant(
            internalId = 1,
            mnemonic = "mnemonic",
            name = "name",
            timezone = ZoneId.of("America/Los_Angeles"),
            batchConfig = BatchConfig(availableStart = LocalTime.of(12, 0), availableEnd = LocalTime.of(12, 0)),
            vendor = tenantServerCerner
        )
        val proxyTenant = tenantServiceTenant.toProxyTenant()
        assertNotNull(proxyTenant)
        val cerner = proxyTenant.vendor as Cerner
        assertEquals("serviceEndpoint", cerner.serviceEndpoint)
        assertEquals("patientMRNSystem", cerner.patientMRNSystem)
        assertEquals("instanceName", cerner.instanceName)
    }

    @Test
    fun `toProxyTenant - epic`() {
        val tenantServerEpic = com.projectronin.interop.tenant.config.model.vendor.Epic(
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
        val tenantServiceTenant = Tenant(
            internalId = 1,
            mnemonic = "mnemonic",
            name = "name",
            timezone = ZoneId.of("America/Los_Angeles"),
            batchConfig = null,
            vendor = tenantServerEpic
        )
        val proxyTenant = tenantServiceTenant.toProxyTenant()
        assertNotNull(proxyTenant)
        val epic = proxyTenant.vendor as Epic
        assertEquals("serviceEndpoint", epic.serviceEndpoint)
        assertEquals("mrnSystemExample", epic.patientMRNSystem)
        assertEquals("instanceName", epic.instanceName)
    }

    @Test
    fun `toTenantServer - cerner`() {
        val proxyTenant = com.projectronin.interop.proxy.server.tenant.model.Tenant(
            id = 1,
            mnemonic = "mnemonic",
            name = "name",
            availableStart = LocalTime.of(12, 0),
            availableEnd = LocalTime.of(12, 0),
            timezone = "America/Los_Angeles",
            vendor = Cerner(
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                instanceName = "instanceName",
                patientMRNSystem = "patientMRNSystem"
            )
        )
        val tenantServerTenant = proxyTenant.toTenantServerTenant()
        assertEquals(1, tenantServerTenant.internalId)
        val cerner = tenantServerTenant.vendor as com.projectronin.interop.tenant.config.model.vendor.Cerner
        assertEquals("serviceEndpoint", cerner.serviceEndpoint)
        assertEquals("authEndpoint", cerner.authenticationConfig.authEndpoint)
    }

    @Test
    fun `toTenantServer - epic`() {
        val proxyTenant = com.projectronin.interop.proxy.server.tenant.model.Tenant(
            id = 1,
            mnemonic = "mnemonic",
            name = "name",
            availableStart = null,
            availableEnd = null,
            timezone = "America/Los_Angeles",
            vendor = Epic(
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
        val tenantServerTenant = proxyTenant.toTenantServerTenant()
        assertEquals(1, tenantServerTenant.internalId)
        val epic = tenantServerTenant.vendor as com.projectronin.interop.tenant.config.model.vendor.Epic
        assertEquals("serviceEndpoint", epic.serviceEndpoint)
    }

    // code cov test
    @Test
    fun `toTenantServerTenant() - batch start but no end and new id`() {
        val proxyTenant = com.projectronin.interop.proxy.server.tenant.model.Tenant(
            id = 1,
            mnemonic = "mnemonic",
            name = "name",
            availableStart = LocalTime.of(12, 0),
            availableEnd = null,
            timezone = "America/Los_Angeles",
            vendor = Cerner(
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                instanceName = "instanceName",
                patientMRNSystem = "patientMRNSystem"
            )
        )
        val newTenantServerTenant = proxyTenant.toTenantServerTenant(999)
        assertEquals(999, newTenantServerTenant.internalId)
        val newCerner = newTenantServerTenant.vendor as com.projectronin.interop.tenant.config.model.vendor.Cerner
        assertEquals("serviceEndpoint", newCerner.serviceEndpoint)
        assertEquals("authEndpoint", newCerner.authenticationConfig.authEndpoint)
    }
}
