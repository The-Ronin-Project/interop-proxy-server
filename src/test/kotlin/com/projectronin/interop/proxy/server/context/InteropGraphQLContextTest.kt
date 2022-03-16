package com.projectronin.interop.proxy.server.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.reactive.function.server.MockServerRequest

class InteropGraphQLContextTest {
    @Test
    fun `ensure toString is overwritten`() {
        val context = InteropGraphQLContext(
            authzTenantId = "authzTenantId",
            request = MockServerRequest.builder().build()
        )
        assertEquals("InteropGraphQLContext", context.toString())
    }
}
