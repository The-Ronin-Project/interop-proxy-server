package com.projectronin.interop.proxy.server.context

import com.expediagroup.graphql.server.spring.execution.SpringGraphQLContext
import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Data class allowing for more direct support for Interops-related header values.
 */
const val INTEROP_CONTEXT_KEY = "InteropContext" // useful when getting context from a DataFetchingEnvironment
data class InteropGraphQLContext(val authzTenantId: String?, val request: ServerRequest) :
    SpringGraphQLContext(request) {

    // Override toString() to prevent accidentally leaking the auth strings
    override fun toString(): String = this::class.simpleName!!
}
