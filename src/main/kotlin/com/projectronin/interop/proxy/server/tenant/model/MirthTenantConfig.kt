package com.projectronin.interop.proxy.server.tenant.model

import java.time.OffsetDateTime

data class MirthTenantConfig(
    val locationIds: List<String>,
    val lastUpdated: OffsetDateTime? = null,
    val blockedResources: List<String> = listOf(),
)
