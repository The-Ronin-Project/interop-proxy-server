package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TenantServerTest {
    @Test
    fun `can get data`() {
        val tenantServer =
            TenantServer(
                id = 1,
                messageType = "MDM",
                address = "google.com",
                port = 1010,
                serverType = "N",
            )

        assertEquals(1, tenantServer.id)
        assertEquals("MDM", tenantServer.messageType)
        assertEquals("google.com", tenantServer.address)
        assertEquals(1010, tenantServer.port)
        assertEquals("N", tenantServer.serverType)
    }

    @Test
    fun `default works`() {
        val tenantServer =
            TenantServer(
                messageType = "MDM",
                address = "google.com",
                port = 1010,
                serverType = "N",
            )

        assertEquals(0, tenantServer.id)
    }

    @Test
    fun `can serialize and deserialize`() {
        val tenantServer =
            TenantServer(
                id = 1,
                messageType = "MDM",
                address = "google.com",
                port = 1010,
                serverType = "N",
            )
        val json = JacksonManager.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantServer)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON =
            """
            {
              "id" : 1,
              "messageType" : "MDM",
              "address" : "google.com",
              "port" : 1010,
              "serverType" : "N"
            }
            """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = JacksonManager.objectMapper.readValue<TenantServer>(json)
        assertEquals(tenantServer, deserializedEpic)
    }
}
