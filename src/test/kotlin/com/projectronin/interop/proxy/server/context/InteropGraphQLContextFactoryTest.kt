package com.projectronin.interop.proxy.server.context

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
        val context = runBlocking { InteropGraphQLContextFactory().generateContext(request) }

        assertNotNull(context)
        assertEquals(request, context.request)
        assertNull(context.aidboxAuth)
        assertNull(context.ehrFhirAuth)
    }

    @Test
    fun `creates context with aidbox auth`() {
        val request = MockServerRequest.builder().header("AIDBOX_AUTH", "MyAuthForAidbox").build()
        val context = runBlocking { InteropGraphQLContextFactory().generateContext(request) }

        assertNotNull(context)
        assertEquals(request, context.request)
        assertEquals("MyAuthForAidbox", context.aidboxAuth)
        assertNull(context.ehrFhirAuth)
    }

    @Test
    fun `creates context with EHR auth`() {
        val request = MockServerRequest.builder().header("EHR_AUTH", "MyAuthForEHR").build()
        val context = runBlocking { InteropGraphQLContextFactory().generateContext(request) }

        assertNotNull(context)
        assertEquals(request, context.request)
        assertNull(context.aidboxAuth)
        assertEquals("MyAuthForEHR", context.ehrFhirAuth)
    }

    @Test
    fun `creates context with aidbox and EHR auth`() {
        val request =
            MockServerRequest.builder().header("AIDBOX_AUTH", "MyAuthForAidbox").header("EHR_AUTH", "MyAuthForEHR")
                .build()
        val context = runBlocking { InteropGraphQLContextFactory().generateContext(request) }

        assertNotNull(context)
        assertEquals(request, context.request)
        assertEquals("MyAuthForAidbox", context.aidboxAuth)
        assertEquals("MyAuthForEHR", context.ehrFhirAuth)
    }
}
