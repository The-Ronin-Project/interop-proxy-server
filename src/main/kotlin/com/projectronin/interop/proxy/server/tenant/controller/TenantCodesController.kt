package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.TenantCodes
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenantCodes
import com.projectronin.interop.proxy.server.tenant.model.converters.toTenantCodesDO
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantCodesDAO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import datadog.trace.api.Trace
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for interacting with the supplemental codes associated to tenants.
 */
@RestController
@RequestMapping("/tenants/{tenantMnemonic}/codes")
class TenantCodesController(
    private val tenantService: TenantService,
    private val tenantCodesDAO: TenantCodesDAO,
) {
    /**
     * Retrieves the bsaCode, bmiCode, and list of staging codes configured for a tenant.
     */
    @GetMapping
    @Trace
    fun get(
        @PathVariable tenantMnemonic: String,
    ): ResponseEntity<TenantCodes> {
        val tenantCodes =
            tenantCodesDAO.getByTenantMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant Codes Found with that mnemonic")

        return ResponseEntity(tenantCodes.toProxyTenantCodes(), HttpStatus.OK)
    }

    /**
     * Inserts or replaces the bsaCode, bmiCode, and list of staging codes configured for a tenant.
     */
    @PostMapping
    @Trace
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantCodes: TenantCodes,
    ): ResponseEntity<TenantCodes> {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val insertedCodeConfig = tenantCodesDAO.insertCodes(tenantCodes.toTenantCodesDO(tenant = tenant.internalId))

        return ResponseEntity(insertedCodeConfig.toProxyTenantCodes(), HttpStatus.CREATED)
    }

    /**
     * Updates an existing set of bsaCode, bmiCode, and list of staging codes configured for a tenant.
     */
    @PutMapping
    @Trace
    fun update(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantCodes: TenantCodes,
    ): ResponseEntity<TenantCodes> {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val result = tenantCodesDAO.updateCodes(tenantCodes.toTenantCodesDO(tenant = tenant.internalId))

        return result?.let { ResponseEntity(it.toProxyTenantCodes(), HttpStatus.OK) } ?: ResponseEntity(
            null,
            HttpStatus.NOT_FOUND,
        )
    }
}
