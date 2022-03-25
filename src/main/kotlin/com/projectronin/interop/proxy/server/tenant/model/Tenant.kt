package com.projectronin.interop.proxy.server.tenant.model

import java.time.LocalTime

/**
 * Represents a tenant in the proxy server REST model.
 */
data class Tenant(
    val id: Int,
    val mnemonic: String,
    val availableStart: LocalTime,
    val availableEnd: LocalTime,
    val vendor: Vendor,
)
