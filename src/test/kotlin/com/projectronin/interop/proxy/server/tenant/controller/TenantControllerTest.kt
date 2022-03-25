package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.Epic
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TenantControllerTest {
    private var tenantService = mockk<TenantService>()
    private var tenantController = TenantController(tenantService)

    private val vendor = Epic(
        release = "release",
        serviceEndpoint = "serviceEndpoint",
        ehrUserId = "ehrUserId",
        messageType = "messageType",
        practitionerProviderSystem = "practitionerProviderSystem",
        practitionerUserSystem = "practitionerUserSystem",
        hsi = "hsi"
    )

    private val tenant = Tenant(
        id = 1,
        mnemonic = "mnemonic1",
        availableStart = LocalTime.of(22, 0),
        availableEnd = LocalTime.of(23, 0),
        vendor = vendor
    )

    @Test
    fun `can read all tenants`() {
        // TODO once controller implemented
        tenantController.read()
    }

    @Test
    fun `can read a specific tenant by mnemonic`() {
        // TODO once controller implemented
        tenantController.read("testTenant")
    }

    @Test
    fun `can insert a tenant`() {
        // TODO once controller implemented
        tenantController.insert(tenant)
    }

    @Test
    fun `can update a tenant`() {
        // TODO once controller implemented
        tenantController.update("testTenant", tenant)
    }
}
