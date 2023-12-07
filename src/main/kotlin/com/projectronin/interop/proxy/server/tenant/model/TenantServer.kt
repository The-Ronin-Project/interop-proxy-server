package com.projectronin.interop.proxy.server.tenant.model

/**
 * Represents a tenant server in the proxy server REST model.
 */
data class TenantServer(
    val id: Int = 0,
    val messageType: String,
    val address: String,
    val port: Int,
    val serverType: String,
)
