package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.Epic
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalTime

@RestController
@RequestMapping("tenants")
class TenantController(private val tenantService: TenantService) {
    @GetMapping
    fun read(): List<Tenant> {
        // TODO implement this
        return listOf(tenant1, tenant2)
    }

    @GetMapping("/{mnemonic}")
    fun read(@PathVariable("mnemonic") tenantMnemonic: String): Tenant? {
        // TODO implement this
        return tenant1
    }

    @PostMapping
    fun insert(@RequestBody tenant: Tenant): Tenant {
        // TODO implement this
        return tenant
    }

    @PutMapping("/{mnemonic}")
    fun update(@PathVariable("mnemonic") tenantMnemonic: String, @RequestBody tenant: Tenant): Int {
        // TODO implement this
        return 1
    }

    // These are only used for stubbing return values and can be deleted once this class is implemented.
    private val vendor = Epic(
        release = "release",
        serviceEndpoint = "serviceEndpoint",
        ehrUserId = "ehrUserId",
        messageType = "messageType",
        practitionerProviderSystem = "practitionerProviderSystem",
        practitionerUserSystem = "practitionerUserSystem",
        hsi = "hsi"
    )

    private val tenant1 = Tenant(
        id = 1,
        mnemonic = "mnemonic1",
        availableStart = LocalTime.of(22, 0),
        availableEnd = LocalTime.of(23, 0),
        vendor = vendor
    )

    private val tenant2 = Tenant(
        id = 2,
        mnemonic = "mnemonic2",
        availableStart = LocalTime.of(22, 0),
        availableEnd = LocalTime.of(23, 0),
        vendor = vendor
    )
}
