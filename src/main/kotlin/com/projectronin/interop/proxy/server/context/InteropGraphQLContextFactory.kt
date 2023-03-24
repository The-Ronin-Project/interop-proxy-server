package com.projectronin.interop.proxy.server.context

import com.expediagroup.graphql.server.spring.execution.DefaultSpringGraphQLContextFactory
import com.projectronin.interop.proxy.server.filter.AUTHZ_TENANT_HEADER
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest

const val AUTHZ_TENANT_ID = "authorizedTenantId"

/**
 * Factory for creating a GraphQL context map and [InteropGraphQLContext] from a request.
 */
@Component
class InteropGraphQLContextFactory : DefaultSpringGraphQLContextFactory() {
    override suspend fun generateContext(request: ServerRequest): GraphQLContext {
        val authzTenantId = request.headers().firstHeader(AUTHZ_TENANT_HEADER) // populated in AuthFilter.kt

        val context = super.generateContext(request)

        return authzTenantId?.let { context.putAll(mapOf(AUTHZ_TENANT_ID to authzTenantId)) } ?: context
    }
}

fun DataFetchingEnvironment.getAuthorizedTenantId(): String? = this.graphQlContext.getOrDefault(AUTHZ_TENANT_ID, null)
