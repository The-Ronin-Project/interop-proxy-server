package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment

/**
 * Checks a Tenant mnemonic for validity, returning a [Tenant] object.
 */
fun findAndValidateTenant(
    dfe: DataFetchingEnvironment,
    tenantService: TenantService,
    requestedTenantId: String
): Tenant {
    val authorizedTenantId = dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId
        ?: throw IllegalArgumentException("No Tenants authorized for request.")
    if (requestedTenantId != authorizedTenantId)
        throw IllegalArgumentException("Requested Tenant '$requestedTenantId' does not match authorized Tenant '$authorizedTenantId'")
    return tenantService.getTenantForMnemonic(requestedTenantId)
        ?: throw IllegalArgumentException("Invalid Tenant: $requestedTenantId")
}
