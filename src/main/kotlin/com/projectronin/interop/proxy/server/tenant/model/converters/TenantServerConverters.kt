package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.hl7.ProcessingID
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.data.model.TenantServerDO

fun TenantServerDO.toProxyTenantServer(): TenantServer {
    return TenantServer(
        id = id,
        messageType = messageType.name,
        address = address,
        port = port,
        serverType = serverType.abbreviation,
    )
}

fun TenantServer.toTenantServerDO(proxyTenant: Tenant): TenantServerDO {
    return TenantServerDO {
        id = this@toTenantServerDO.id
        messageType = MessageType.valueOf(this@toTenantServerDO.messageType)
        address = this@toTenantServerDO.address
        port = this@toTenantServerDO.port
        serverType = ProcessingID.values().first { it.abbreviation == this@toTenantServerDO.serverType }
        tenant =
            TenantDO {
                id = proxyTenant.id
            }
    }
}
