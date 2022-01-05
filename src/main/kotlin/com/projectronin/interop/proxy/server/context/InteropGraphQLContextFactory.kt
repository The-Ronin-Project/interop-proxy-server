package com.projectronin.interop.proxy.server.context

import com.expediagroup.graphql.server.spring.execution.SpringGraphQLContextFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Factory for creating [InteropGraphQLContext] from a request.
 */
@Component
class InteropGraphQLContextFactory : SpringGraphQLContextFactory<InteropGraphQLContext>() {
    override suspend fun generateContext(request: ServerRequest): InteropGraphQLContext {
        val aidboxAuth = request.headers().firstHeader("AIDBOX_AUTH")
        val ehrFhirAuth = request.headers().firstHeader("EHR_AUTH")
        return InteropGraphQLContext(aidboxAuth, ehrFhirAuth, request)
    }
}
