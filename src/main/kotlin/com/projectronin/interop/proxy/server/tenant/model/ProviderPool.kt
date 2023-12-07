package com.projectronin.interop.proxy.server.tenant.model

/**
 * Configuration associated to Epic Provider Pools.
 * @property providerPoolId The ID for the entry associated with the providerId and Provider Pool
 * @property providerId The ID for the provider
 * @property poolId The ID of the pool the provider is associated with
 */
data class ProviderPool(
    val providerPoolId: Int,
    val providerId: String,
    val poolId: String,
)
