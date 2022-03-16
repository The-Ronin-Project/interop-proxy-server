package com.projectronin.interop.proxy.server.context

import com.expediagroup.graphql.server.spring.execution.SpringGraphQLContextFactory
import com.projectronin.interop.proxy.server.filter.AUTHZ_TENANT_HEADER
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Factory for creating a GraphQL context map and [InteropGraphQLContext] from a request.
 */
@Component
class InteropGraphQLContextFactory : SpringGraphQLContextFactory<InteropGraphQLContext>() {

    override suspend fun generateContextMap(request: ServerRequest): Map<*, Any>? {
        val authzTenantId = request.headers().firstHeader(AUTHZ_TENANT_HEADER) // populated in AuthFilter.kt
        return mapOf(INTEROP_CONTEXT_KEY to InteropGraphQLContext(authzTenantId, request))
    }

    // IDE requires this to be implemented, but it's deprecated.
    override suspend fun generateContext(request: ServerRequest): InteropGraphQLContext? {
        return generateContextMap(request)?.get(INTEROP_CONTEXT_KEY) as InteropGraphQLContext
    }
}
