package com.projectronin.interop.proxy.server.tenant.model

import java.time.LocalTime

/**
 * Represents a tenant in the proxy server REST model.
 */
data class Tenant(
    val id: Int,
    val mnemonic: String,
    val name: String,
    val availableStart: LocalTime?,
    val availableEnd: LocalTime?,
    val vendor: Vendor,
    val timezone: String,

    // If the caller doesn't include the 'monitoredIndicator' flag, set it to true.  They can always turn it off later
    // and better safe than sorry.
    val monitoredIndicator: Boolean? = true
)
