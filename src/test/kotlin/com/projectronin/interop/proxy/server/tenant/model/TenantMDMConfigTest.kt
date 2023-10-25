package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TenantMDMConfigTest {
    @Test
    fun `can get data`() {
        val tenantConfig = TenantMDMConfig(
            mdmDocumentTypeID = "typeid1",
            providerIdentifierSystem = "idsystem1",
            receivingSystem = "rsystem1"
        )

        assertEquals("typeid1", tenantConfig.mdmDocumentTypeID)
        assertEquals("idsystem1", tenantConfig.providerIdentifierSystem)
        assertEquals("rsystem1", tenantConfig.receivingSystem)
    }

    @Test
    fun `can serialize and deserialize`() {
        val tenantConfig = TenantMDMConfig(
            mdmDocumentTypeID = "typeid1",
            providerIdentifierSystem = "idsystem1",
            receivingSystem = "rsystem1"
        )
        val json = JacksonManager.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConfig)

        val expectedJSON = """
                {
                  "mdmDocumentTypeID" : "typeid1",
                  "providerIdentifierSystem" : "idsystem1",
                  "receivingSystem" : "rsystem1"
                }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = JacksonManager.objectMapper.readValue<TenantMDMConfig>(json)
        assertEquals(tenantConfig, deserializedEpic)
    }
}
