package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MirthTenantConfigTest {
    @Test
    fun `can get data`() {
        val tenantConfig = MirthTenantConfig(
            locationIds = listOf("bleep", "blorp", "bloop")
        )

        assertEquals(listOf("bleep", "blorp", "bloop"), tenantConfig.locationIds)
    }

    @Test
    fun `can serialize and deserialize`() {
        val tenantConfig = MirthTenantConfig(
            locationIds = listOf("bleep", "blorp", "bloop")
        )
        val json = JacksonManager.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConfig)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON = """
                {
                  "locationIds" : [ "bleep", "blorp", "bloop" ]
                }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = JacksonManager.objectMapper.readValue<MirthTenantConfig>(json)
        assertEquals(tenantConfig, deserializedEpic)
    }
}
