package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.model.ProviderPoolDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import mu.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tenants/{tenantId}/pools")
class ProviderPoolController(private val providerPoolDAO: ProviderPoolDAO) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    fun get(
        @PathVariable tenantId: Int,
        @RequestParam(required = false) providerIds: List<String>? = null,
    ): ResponseEntity<List<ProviderPool>> {
        val providerPools = if (providerIds != null) {
            providerPoolDAO.getPoolsForProviders(tenantId, providerIds)
        } else {
            providerPoolDAO.getAll(tenantId)
        }.map {
            ProviderPool(
                providerPoolId = it.id,
                providerId = it.providerId,
                poolId = it.poolId
            )
        }

        return ResponseEntity(providerPools, HttpStatus.OK)
    }

    @PostMapping
    fun insert(@PathVariable tenantId: Int, @RequestBody providerPool: ProviderPool): ResponseEntity<ProviderPool> {
        val insertProviderPoolDO = ProviderPoolDO {
            id = providerPool.providerPoolId
            providerId = providerPool.providerId
            poolId = providerPool.poolId
            tenant = TenantDO {
                id = tenantId
            }
        }

        return try {
            providerPoolDAO.insert(insertProviderPoolDO).let {
                ResponseEntity(ProviderPool(it.id, it.providerId, it.poolId), HttpStatus.OK)
            }
        } catch (e: DataIntegrityViolationException) {
            logger.warn { "Constraint violation on insert provider pool $insertProviderPoolDO" }
            ResponseEntity(HttpStatus.UNPROCESSABLE_ENTITY)
        } catch (e: Exception) {
            logger.error { "Error on insert provider pool $insertProviderPoolDO" }
            ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    @PutMapping("/{providerPoolId}")
    fun update(
        @PathVariable tenantId: Int,
        @PathVariable providerPoolId: Int,
        @RequestBody providerPool: ProviderPool,
    ): ResponseEntity<String> {

        // Make sure the providerPoolIds in the path and in the request body match
        if (providerPoolId.toLong() != providerPool.providerPoolId) {
            logger.debug { "Error on mis-matched pool id" }
            return ResponseEntity("Pool ID in path must match pool ID in request body.", HttpStatus.BAD_REQUEST)
        }

        val updateProviderPoolDO = ProviderPoolDO {
            id = providerPool.providerPoolId
            providerId = providerPool.providerId
            poolId = providerPool.poolId
            tenant = TenantDO {
                id = tenantId
            }
        }

        val rowsUpdated = try {
            providerPoolDAO.update(updateProviderPoolDO)
        } catch (e: DataIntegrityViolationException) {
            logger.warn { "Constraint violation on update provider pool $providerPoolId and $tenantId" }
            return ResponseEntity("Update violates data integrity constraint.", HttpStatus.BAD_REQUEST)
        }

        return if (rowsUpdated == 1) {
            ResponseEntity("Success, row $providerPoolId updated", HttpStatus.OK)
        } else {
            if (rowsUpdated > 1) {
                logger.error { "Multiple rows updated for $providerPoolId and $tenantId" }
            } else {
                logger.warn { "Attempted to update non-existant provider pool id $providerPoolId for $tenantId" }
            }
            ResponseEntity("Failed to update row", HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @DeleteMapping("/{providerPoolId}")
    fun delete(@PathVariable tenantId: Int, @PathVariable providerPoolId: Long): ResponseEntity<String> {
        val rowsUpdated = try {
            providerPoolDAO.delete(providerPoolId)
        } catch (e: DataIntegrityViolationException) {
            logger.error { "Constraint violation on delete provider pool $providerPoolId for $tenantId" }
            return ResponseEntity("Update violates data integrity constraint.", HttpStatus.BAD_REQUEST)
        }

        return if (rowsUpdated == 1) {
            ResponseEntity("Success, row $providerPoolId deleted", HttpStatus.OK)
        } else {
            if (rowsUpdated > 1) {
                logger.error { "Multiple rows deleted for $providerPoolId" }
            } else {
                logger.warn { "Attempted to delete non-existant provider pool id $providerPoolId" }
            }
            ResponseEntity("Failed to delete row", HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }
}
