package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.logmarkers.LogMarkers
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenant
import com.projectronin.interop.proxy.server.tenant.model.converters.toTenantServerTenant
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
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
@RequestMapping("tenants")
class TenantController(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    @Trace
    fun read(): ResponseEntity<List<Tenant>> {
        logger.info { "Retrieving all tenants" }
        val tenants = tenantService.getAllTenants().map { it.toProxyTenant() }
        return ResponseEntity(tenants, HttpStatus.OK)
    }

    @GetMapping("/{mnemonic}")
    @Trace
    fun read(
        @PathVariable("mnemonic") tenantMnemonic: String,
    ): ResponseEntity<Tenant> {
        logger.info { "Retrieving tenant with mnemonic $tenantMnemonic" }
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(tenant.toProxyTenant(), HttpStatus.OK)
    }

    @GetMapping("health")
    @Trace
    fun health(): ResponseEntity<Map<String, Boolean>> {
        logger.info { "Retrieving health for all tenants" }
        val tenants = tenantService.getMonitoredTenants()
        val tenantsHealth =
            tenants.associate {
                val healthy = ehrFactory.getVendorFactory(it).healthCheckService.healthCheck(it)
                if (!healthy) {
                    logger.error(LogMarkers.EXCLUDE_FROM_GENERAL_ALERTS) { "Client ${it.mnemonic} reporting unhealthy" }
                }

                it.mnemonic to healthy
            }
        return ResponseEntity(tenantsHealth, HttpStatus.OK)
    }

    @GetMapping("/{mnemonic}/health")
    @Trace
    fun health(
        @PathVariable("mnemonic") tenantMnemonic: String,
    ): ResponseEntity<Void> {
        logger.info { "Retrieving health for tenant with mnemonic $tenantMnemonic" }
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        val isHealthy = ehrFactory.getVendorFactory(tenant).healthCheckService.healthCheck(tenant)
        return if (isHealthy) {
            ResponseEntity(HttpStatus.OK)
        } else {
            logger.warn { "Health check failed for tenant with mnemonic $tenantMnemonic" }
            ResponseEntity(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }

    @PostMapping
    @Trace
    fun insert(
        @RequestBody tenant: Tenant,
    ): ResponseEntity<Tenant> {
        logger.info { "Inserting new tenant with mnemonic ${tenant.mnemonic}" }
        val newTenant = tenantService.insertTenant(tenant.toTenantServerTenant())
        return ResponseEntity(newTenant.toProxyTenant(), HttpStatus.CREATED)
    }

    @PutMapping("/{mnemonic}")
    @Trace
    fun update(
        @PathVariable("mnemonic") tenantMnemonic: String,
        @RequestBody tenant: Tenant,
    ): ResponseEntity<Tenant> {
        logger.info { "Updating tenant with mnemonic $tenantMnemonic" }

        val tenantToUpdate =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No tenant found for mnemonic $tenantMnemonic")

        val newTenant = tenantService.updateTenant(tenant.toTenantServerTenant(tenantToUpdate.internalId))
        return ResponseEntity(newTenant.toProxyTenant(), HttpStatus.OK)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.warn(e) { "Unspecified error occurred during TenantController" }
        return ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    // this should only happen when we've been able to serialize the object (i.e. it's a valid VendorType enum)
    // but we can't find that type in the DB. It's really hard for that to be the caller's fault
    @ExceptionHandler(value = [(NoEHRFoundException::class)])
    fun handleEHRException(e: NoEHRFoundException): ResponseEntity<String> {
        logger.warn(e) { "Unable to find EHR" }
        return ResponseEntity("Unable to find EHR", HttpStatus.INTERNAL_SERVER_ERROR)
    }

    // likely the callers fault getting a wrong ID on an update
    @ExceptionHandler(value = [(NoTenantFoundException::class)])
    fun handleTenantException(e: NoTenantFoundException): ResponseEntity<String> {
        logger.warn(e) { "Unable to find tenant" }
        return ResponseEntity("Unable to find tenant", HttpStatus.NOT_FOUND)
    }
}
