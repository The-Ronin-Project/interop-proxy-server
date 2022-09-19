package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.hl7.ProcessingID
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.data.model.TenantServerDO
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
@RequestMapping("/tenants/{tenantMnemonic}/tenant-server")
class TenantServerController(private val tenantServerDAO: TenantServerDAO, private val tenantService: TenantService) {
    private val logger = KotlinLogging.logger { }

    @GetMapping("/{type}")
    fun getWithType(
        @PathVariable("tenantMnemonic") tenantMnemonic: String,
        @PathVariable("type") type: String
    ): ResponseEntity<TenantServer> {
        logger.info { "Retrieving TenantServer with mnemonic $tenantMnemonic and type $type" }
        val tenantServerList = tenantServerDAO.getTenantServers(tenantMnemonic, MessageType.valueOf(type))
        if (tenantServerList.isEmpty()) return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(tenantServerList.single().toTenantServer(), HttpStatus.OK)
    }

    @GetMapping
    fun get(@PathVariable("tenantMnemonic") tenantMnemonic: String): ResponseEntity<List<TenantServer>> {
        logger.info { "Retrieving TenantServer with mnemonic $tenantMnemonic" }
        val tenantServerList = tenantServerDAO.getTenantServers(tenantMnemonic)
        if (tenantServerList.isEmpty()) return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(tenantServerList.map { it.toTenantServer() }, HttpStatus.OK)
    }

    @PostMapping
    fun insert(
        @PathVariable tenantMnemonic: String,
        @RequestBody tenantServer: TenantServer
    ): ResponseEntity<TenantServer> {
        logger.info { "Inserting new TenantServer for $tenantMnemonic and type ${tenantServer.messageType}" }
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw NoTenantFoundException("No Tenant With that mnemonic")
        val tenantServerDO = tenantServer.toTenantServerDO(tenant.toProxyTenant())
        val inserted = tenantServerDAO.insertTenantServer(tenantServerDO)
        return ResponseEntity(inserted.toTenantServer(), HttpStatus.CREATED)
    }

    @PutMapping
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

        return ResponseEntity(updated?.toTenantServer(), status)
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

    fun TenantServerDO.toTenantServer(): TenantServer {
        return TenantServer(
            id = id,
            messageType = messageType.name,
            address = address,
            port = port,
            serverType = serverType.abbreviation
        )
    }

    fun TenantServer.toTenantServerDO(proxyTenant: Tenant): TenantServerDO {
        return TenantServerDO {
            id = this@toTenantServerDO.id
            messageType = MessageType.valueOf(this@toTenantServerDO.messageType)
            address = this@toTenantServerDO.address
            port = this@toTenantServerDO.port
            serverType = ProcessingID.values().first { it.abbreviation == this@toTenantServerDO.serverType }
            tenant = TenantDO {
                id = proxyTenant.id
            }
        }
    }
}
