package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.TenantMDMConfig
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenant
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenantMDMConfig
import com.projectronin.interop.proxy.server.tenant.model.converters.toTenantMDMConfigDO
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantMDMConfigDAO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import datadog.trace.api.Trace
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tenants/{tenantMnemonic}/hl7v2/mdm")
class TenantMDMConfigController(
    private val tenantMDMConfigDAO: TenantMDMConfigDAO,
    private val tenantService: TenantService
) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    @Trace
    fun get(
        @PathVariable tenantMnemonic: String
    ): ResponseEntity<TenantMDMConfig?> {
        val tenantMDMConfigs = tenantMDMConfigDAO.getByTenantMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant MDM Config Found with that mnemonic")

        return ResponseEntity(tenantMDMConfigs.toProxyTenantMDMConfig(), HttpStatus.OK)
    }

    @PostMapping
    @Trace
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantMDMConfig: TenantMDMConfig
    ): ResponseEntity<TenantMDMConfig> {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val insertTenantMDMConfig = tenantMDMConfig.toTenantMDMConfigDO(tenant.toProxyTenant())

        val inserted = tenantMDMConfigDAO.insertConfig(insertTenantMDMConfig)
        return ResponseEntity(inserted.toProxyTenantMDMConfig(), HttpStatus.CREATED)
    }

    @PutMapping
    @Trace
    fun update(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantMDMConfig: TenantMDMConfig
    ): ResponseEntity<TenantMDMConfig?> {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val updatedTenantMDMConfig = tenantMDMConfig.toTenantMDMConfigDO(tenant.toProxyTenant())
        val result = tenantMDMConfigDAO.updateConfig(updatedTenantMDMConfig)
        val status = result?.let {
            HttpStatus.OK
        } ?: HttpStatus.NOT_FOUND

        return ResponseEntity(result?.toProxyTenantMDMConfig(), status)
    }

    @ExceptionHandler(value = [(NoTenantFoundException::class)])
    fun handleTenantException(e: NoTenantFoundException): ResponseEntity<String> {
        logger.warn(e) { e.message }
        return ResponseEntity(e.message, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.warn(e) { "Unspecified error occurred during TenantMDMConfigController ${e.message}" }
        return ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
