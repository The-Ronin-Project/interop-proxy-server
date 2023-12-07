package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.model.ProviderPoolDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import datadog.trace.api.Trace
import mu.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tenants/{tenantMnemonic}/pools")
class ProviderPoolController(private val providerPoolDAO: ProviderPoolDAO, private val tenantService: TenantService) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    @Trace
    fun get(
        @PathVariable tenantMnemonic: String,
        @RequestParam(required = false) providerIds: List<String>? = null,
    ): ResponseEntity<List<ProviderPool>> {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val providerPools =
            if (providerIds != null) {
                providerPoolDAO.getPoolsForProviders(tenant.internalId, providerIds)
            } else {
                providerPoolDAO.getAll(tenant.internalId)
            }.map {
                ProviderPool(
                    providerPoolId = it.id,
                    providerId = it.providerId,
                    poolId = it.poolId,
                )
            }
        return ResponseEntity(providerPools, HttpStatus.OK)
    }

    @PostMapping
    @Trace
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody providerPool: ProviderPool,
    ): ResponseEntity<ProviderPool> {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val insertProviderPoolDO =
            ProviderPoolDO {
                this.id = providerPool.providerPoolId
                this.providerId = providerPool.providerId
                this.poolId = providerPool.poolId
                this.tenant =
                    TenantDO {
                        id = tenant.internalId
                    }
            }

        return providerPoolDAO.insert(insertProviderPoolDO).let {
            ResponseEntity(ProviderPool(it.id, it.providerId, it.poolId), HttpStatus.OK)
        }
    }

    @PutMapping("/{providerPoolId}")
    @Trace
    fun update(
        @PathVariable tenantMnemonic: String,
        @PathVariable providerPoolId: Int,
        @RequestBody providerPool: ProviderPool,
    ): ResponseEntity<ProviderPool?> {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        // Use providerPoolId from path, regardless of what's in request body
        val updateProviderPoolDO =
            ProviderPoolDO {
                this.id = providerPoolId
                this.providerId = providerPool.providerId
                this.poolId = providerPool.poolId
                this.tenant =
                    TenantDO {
                        id = tenant.internalId
                    }
            }

        val result = providerPoolDAO.update(updateProviderPoolDO)
        val status = result?.let { HttpStatus.OK } ?: HttpStatus.NOT_FOUND
        return ResponseEntity(result?.toProviderPool(), status)
    }

    @DeleteMapping("/{providerPoolId}")
    @Trace
    fun delete(
        @PathVariable tenantMnemonic: String,
        @PathVariable providerPoolId: Int,
    ): ResponseEntity<String> {
        // Does the providerPoolId belong to the right tenant?
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val providerPoolDO =
            providerPoolDAO.getPoolById(providerPoolId)
                ?: return ResponseEntity("Provider pool doesn't exist", HttpStatus.OK)

        if (providerPoolDO.tenant.mnemonic != tenant.mnemonic) {
            throw Exception("Attempted to delete provider pool $providerPoolId from wrong tenant $tenantMnemonic")
        }

        providerPoolDAO.delete(providerPoolId)
        return ResponseEntity("Provider pool deleted", HttpStatus.OK)
    }

    @ExceptionHandler(value = [(DataIntegrityViolationException::class)])
    fun handleDataIntegrityException(e: DataIntegrityViolationException): ResponseEntity<String> {
        logger.warn { "Constraint violation ${e.message}" }
        return ResponseEntity(e.message, HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.warn(e) { "Unspecified error occurred during ProviderPoolController ${e.message}" }
        return ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun ProviderPoolDO.toProviderPool(): ProviderPool {
        return ProviderPool(
            providerPoolId = this@toProviderPool.id,
            poolId = this@toProviderPool.poolId,
            providerId = this@toProviderPool.providerId,
        )
    }
}
