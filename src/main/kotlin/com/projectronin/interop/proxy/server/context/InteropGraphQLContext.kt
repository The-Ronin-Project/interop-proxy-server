package com.projectronin.interop.proxy.server.context

import com.expediagroup.graphql.server.spring.execution.SpringGraphQLContext
import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Data class allowing for more direct support for Interops-related header values.
 */
data class InteropGraphQLContext(val aidboxAuth: String?, val ehrFhirAuth: String?, val request: ServerRequest) :
    SpringGraphQLContext(request)
