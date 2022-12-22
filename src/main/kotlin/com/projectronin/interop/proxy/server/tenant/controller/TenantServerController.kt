package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenant
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenantServer
import com.projectronin.interop.proxy.server.tenant.model.converters.toTenantServerDO
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantServerDAO
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
@RequestMapping("/tenants/{tenantMnemonic}/tenant-server")
class TenantServerController(private val tenantServerDAO: TenantServerDAO, private val tenantService: TenantService) {
    private val logger = KotlinLogging.logger { }

    @GetMapping("/{type}")
    @Trace
    fun getWithType(
        @PathVariable("tenantMnemonic") tenantMnemonic: String,
        @PathVariable("type") type: String
    ): ResponseEntity<TenantServer> {
        logger.info { "Retrieving TenantServer with mnemonic $tenantMnemonic and type $type" }
        val tenantServerList = tenantServerDAO.getTenantServers(tenantMnemonic, MessageType.valueOf(type))
        if (tenantServerList.isEmpty()) return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(tenantServerList.single().toProxyTenantServer(), HttpStatus.OK)
    }

    @GetMapping
    @Trace
    fun get(@PathVariable("tenantMnemonic") tenantMnemonic: String): ResponseEntity<List<TenantServer>> {
        logger.info { "Retrieving TenantServer with mnemonic $tenantMnemonic" }
        val tenantServerList = tenantServerDAO.getTenantServers(tenantMnemonic)
        if (tenantServerList.isEmpty()) return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(tenantServerList.map { it.toProxyTenantServer() }, HttpStatus.OK)
    }

    @PostMapping
    @Trace
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantServer: TenantServer
    ): ResponseEntity<TenantServer> {
        logger.info { "Inserting new TenantServer for $tenantMnemonic and type ${tenantServer.messageType}" }
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")
        val tenantServerDO = tenantServer.toTenantServerDO(tenant.toProxyTenant())
        val inserted = tenantServerDAO.insertTenantServer(tenantServerDO)
        return ResponseEntity(inserted.toProxyTenantServer(), HttpStatus.CREATED)
    }

    @PutMapping
    @Trace
    fun update(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantServer: TenantServer
    ): ResponseEntity<TenantServer> {
        logger.info { "Updating new TenantServer for $tenantMnemonic and type ${tenantServer.messageType}" }
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")
        val tenantServerDO = tenantServer.toTenantServerDO(tenant.toProxyTenant())
        val updated = tenantServerDAO.updateTenantServer(tenantServerDO)
        val status = updated?.let { HttpStatus.OK } ?: HttpStatus.NOT_FOUND

        return ResponseEntity(updated?.toProxyTenantServer(), status)
    }

    @ExceptionHandler(value = [(NoTenantFoundException::class)])
    fun handleTenantException(e: NoTenantFoundException): ResponseEntity<String> {
        logger.warn(e) { e.message }
        return ResponseEntity(e.message, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.warn(e) { "Unspecified error occurred during TenantServerController ${e.message}" }
        return ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
