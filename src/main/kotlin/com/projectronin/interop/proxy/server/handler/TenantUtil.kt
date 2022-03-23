package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.client.HttpClientErrorException

/**
 * Checks a Tenant mnemonic, [requestedTenantId], for validity and authorization, returning a [Tenant] object.
 * Additionally, the [requireTenantAuth] can be set to make the tenant authorization optional for workflows that are
 * applicable for Machine to Machine calls.
 */
fun findAndValidateTenant(
    dfe: DataFetchingEnvironment,
    tenantService: TenantService,
    requestedTenantId: String,
    requireTenantAuth: Boolean = true
): Tenant {
    val authorizedTenantId = dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId

    if (requireTenantAuth && authorizedTenantId == null) {
        // Authorized tenet is required
        throw HttpClientErrorException(FORBIDDEN, "No Tenants authorized for request.")
    }

    if (authorizedTenantId != null && requestedTenantId != authorizedTenantId) {
        // Always check the authorized tenant (always sent from User Auth) against the requested.
        throw HttpClientErrorException(
            FORBIDDEN, "Requested Tenant '$requestedTenantId' does not match authorized Tenant '$authorizedTenantId'"
        )
    }

    return tenantService.getTenantForMnemonic(requestedTenantId) ?: throw HttpClientErrorException(
        NOT_FOUND, "Invalid Tenant: $requestedTenantId"
    )
}
