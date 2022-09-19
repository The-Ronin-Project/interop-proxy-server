package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
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
@RequestMapping("/tenants/{tenantMnemonic}/mirth-config")
class MirthTenantConfigController(private val mirthTenantConfigDAO: MirthTenantConfigDAO, private val tenantService: TenantService) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    fun get(
        @PathVariable tenantMnemonic: String,
    ): ResponseEntity<MirthTenantConfig?> {

        val mirthTenantConfigs = mirthTenantConfigDAO.getByTenantMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Mirth Config Found with that mnemonic")

        return ResponseEntity(mirthTenantConfigs.toMirthTenantConfig(), HttpStatus.OK)
    }

    @PostMapping
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody mirthTenantConfig: MirthTenantConfig
    ): ResponseEntity<MirthTenantConfig> {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val insertMirthTenantConfig = mirthTenantConfig.toMirthTenantConfigDO(tenant.toProxyTenant())

        val inserted = mirthTenantConfigDAO.insertConfig(insertMirthTenantConfig)
        return ResponseEntity(inserted.toMirthTenantConfig(), HttpStatus.CREATED)
    }

    @PutMapping
    fun update(
        @PathVariable tenantMnemonic: String,
        @RequestBody mirthTenantConfig: MirthTenantConfig
    ): ResponseEntity<MirthTenantConfig?> {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val updatedMirthTenantConfig = mirthTenantConfig.toMirthTenantConfigDO(tenant.toProxyTenant())
        val result = mirthTenantConfigDAO.updateConfig(updatedMirthTenantConfig)
        val status = result?.let { HttpStatus.OK } ?: HttpStatus.NOT_FOUND

        return ResponseEntity(result?.toMirthTenantConfig(), status)
    }

    @ExceptionHandler(value = [(NoTenantFoundException::class)])
    fun handleTenantException(e: NoTenantFoundException): ResponseEntity<String> {
        logger.warn(e) { e.message }
        return ResponseEntity(e.message, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.warn(e) { "Unspecified error occurred during MirthTenantConfigController ${e.message}" }
        return ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    fun MirthTenantConfigDO.toMirthTenantConfig(): MirthTenantConfig {
        return MirthTenantConfig(
            locationIds =
            if (locationIds.isEmpty()) {
                emptyList()
            } else {
                locationIds.split(",")
            }
        )
    }

    fun MirthTenantConfig.toMirthTenantConfigDO(proxyTenant: Tenant): MirthTenantConfigDO {
        return MirthTenantConfigDO {
            locationIds = this@toMirthTenantConfigDO.locationIds.joinToString(",")
            tenant = TenantDO {
                id = proxyTenant.id
            }
        }
    }
}
