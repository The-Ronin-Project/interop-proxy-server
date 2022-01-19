package com.projectronin.interop.proxy.server.context

import com.projectronin.interop.proxy.server.filter.AUTHZ_TENANT_HEADER
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.reactive.function.server.MockServerRequest

class InteropGraphQLContextFactoryTest {
    @Test
    fun `creates context without headers`() {
        val request = MockServerRequest.builder().build()
        val contextMap = runBlocking { InteropGraphQLContextFactory().generateContextMap(request) }
        val context = contextMap?.get(INTEROP_CONTEXT_KEY) as InteropGraphQLContext

        assertNotNull(context)
        assertEquals(request, context.request)
        assertNull(context.aidboxAuth)
        assertNull(context.ehrFhirAuth)
        assertNull(context.authzTenantId)
    }

    @Test
    fun `creates context with aidbox auth`() {
        val request = MockServerRequest.builder().header("AIDBOX_AUTH", "MyAuthForAidbox").build()
        val contextMap = runBlocking { InteropGraphQLContextFactory().generateContextMap(request) }
        val context = contextMap?.get(INTEROP_CONTEXT_KEY) as InteropGraphQLContext

        assertNotNull(context)
        assertEquals(request, context.request)
        assertEquals("MyAuthForAidbox", context.aidboxAuth)
        assertNull(context.ehrFhirAuth)
    }

    @Test
    fun `creates context with EHR auth`() {
        val request = MockServerRequest.builder().header("EHR_AUTH", "MyAuthForEHR").build()
        val contextMap = runBlocking { InteropGraphQLContextFactory().generateContextMap(request) }
        val context = contextMap?.get(INTEROP_CONTEXT_KEY) as InteropGraphQLContext

        assertNotNull(context)
        assertEquals(request, context.request)
        assertNull(context.aidboxAuth)
        assertEquals("MyAuthForEHR", context.ehrFhirAuth)
    }

    @Test
    fun `creates context with authorized Tenant`() {
        val request = MockServerRequest.builder().header(AUTHZ_TENANT_HEADER, "MyAuthzTenant").build()
        val contextMap = runBlocking { InteropGraphQLContextFactory().generateContextMap(request) }
        val context = contextMap?.get(INTEROP_CONTEXT_KEY) as InteropGraphQLContext

        assertNotNull(context)
        assertEquals(request, context.request)
        assertNull(context.aidboxAuth)
        assertEquals("MyAuthzTenant", context.authzTenantId)
    }

    @Test
    fun `creates context with aidbox and EHR auth and authorized tenant`() {
        val request =
            MockServerRequest.builder().header("AIDBOX_AUTH", "MyAuthForAidbox").header("EHR_AUTH", "MyAuthForEHR")
                .header(
                    AUTHZ_TENANT_HEADER, "MyAuthzTenant"
                )
                .build()
        val contextMap = runBlocking { InteropGraphQLContextFactory().generateContextMap(request) }
        val context = contextMap?.get(INTEROP_CONTEXT_KEY) as InteropGraphQLContext

        assertNotNull(context)
        assertEquals(request, context.request)
        assertEquals("MyAuthForAidbox", context.aidboxAuth)
        assertEquals("MyAuthForEHR", context.ehrFhirAuth)
        assertEquals("MyAuthzTenant", context.authzTenantId)
    }
}
