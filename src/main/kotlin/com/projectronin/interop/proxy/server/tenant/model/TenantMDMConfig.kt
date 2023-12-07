package com.projectronin.interop.proxy.server.tenant.model

data class TenantMDMConfig(
    val mdmDocumentTypeID: String,
    val providerIdentifierSystem: String,
    val receivingSystem: String,
)
