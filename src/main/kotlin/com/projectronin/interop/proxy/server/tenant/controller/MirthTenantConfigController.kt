package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
import com.projectronin.interop.proxy.server.tenant.model.converters.toMirthTenantConfigDO
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyMirthTenantConfig
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenant
import com.projectronin.interop.proxy.server.util.generateMetadata
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import datadog.trace.api.Trace
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
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
class MirthTenantConfigController(
    private val mirthTenantConfigDAO: MirthTenantConfigDAO,
    private val tenantService: TenantService,
    private val loadService: KafkaLoadService,
    @Value("\${proxy.load.locations.on.tenant.config.update:no}") private val loadLocations: String = "no" // temp
) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    @Trace
    fun get(
        @PathVariable tenantMnemonic: String
    ): ResponseEntity<MirthTenantConfig?> {
        val mirthTenantConfigs = mirthTenantConfigDAO.getByTenantMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Mirth Config Found with that mnemonic")

        return ResponseEntity(mirthTenantConfigs.toProxyMirthTenantConfig(), HttpStatus.OK)
    }

    @PostMapping
    @Trace
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody mirthTenantConfig: MirthTenantConfig
    ): ResponseEntity<MirthTenantConfig> {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val insertMirthTenantConfig = mirthTenantConfig.toMirthTenantConfigDO(tenant.toProxyTenant())

        val inserted = mirthTenantConfigDAO.insertConfig(insertMirthTenantConfig)
        sendLocationLoadEvent(tenantMnemonic, mirthTenantConfig.locationIds)
        return ResponseEntity(inserted.toProxyMirthTenantConfig(), HttpStatus.CREATED)
    }

    @PutMapping
    @Trace
    fun update(
        @PathVariable tenantMnemonic: String,
        @RequestBody mirthTenantConfig: MirthTenantConfig
    ): ResponseEntity<MirthTenantConfig?> {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")

        val updatedMirthTenantConfig = mirthTenantConfig.toMirthTenantConfigDO(tenant.toProxyTenant())
        val result = mirthTenantConfigDAO.updateConfig(updatedMirthTenantConfig)
        val status = result?.let {
            sendLocationLoadEvent(tenantMnemonic, mirthTenantConfig.locationIds)
            HttpStatus.OK
        } ?: HttpStatus.NOT_FOUND

        return ResponseEntity(result?.toProxyMirthTenantConfig(), status)
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

    private fun sendLocationLoadEvent(tenantMnemonic: String, locationIds: List<String>) {
        if (loadLocations == "yes") {
            val metadata = generateMetadata()

            loadService.pushLoadEvent(
                tenantMnemonic,
                DataTrigger.AD_HOC,
                locationIds,
                ResourceType.Location,
                metadata
            )
        }
    }
}
