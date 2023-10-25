package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MirthTenantConfigTest {
    @Test
    fun `can get data`() {
        val tenantConfig = MirthTenantConfig(
            locationIds = listOf("bleep", "blorp", "bloop"),
            lastUpdated = OffsetDateTime.of(
                2023,
                1,
                1,
                1,
                1,
                1,
                1,
                ZoneOffset.UTC
            ),
            blockedResources = listOf("beep", "boop", "bop")
        )

        assertEquals(listOf("bleep", "blorp", "bloop"), tenantConfig.locationIds)
        assertEquals(
            OffsetDateTime.of(
                2023,
                1,
                1,
                1,
                1,
                1,
                1,
                ZoneOffset.UTC
            ),
            tenantConfig.lastUpdated
        )
        assertEquals(listOf("beep", "boop", "bop"), tenantConfig.blockedResources)
    }

    @Test
    fun `can serialize and deserialize`() {
        val tenantConfig = MirthTenantConfig(
            locationIds = listOf("bleep", "blorp", "bloop"),
            lastUpdated = OffsetDateTime.of(
                2023,
                1,
                1,
                1,
                1,
                1,
                1,
                ZoneOffset.UTC
            ),
            blockedResources = listOf("beep", "boop", "bop")
        )
        val json = JacksonManager.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConfig)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON = """
                {
                  "locationIds" : [ "bleep", "blorp", "bloop" ],
                  "lastUpdated" : "2023-01-01T01:01:01.000000001Z",
                  "blockedResources" : [ "beep", "boop", "bop" ]
                }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = JacksonManager.objectMapper.readValue<MirthTenantConfig>(json)
        assertEquals(tenantConfig, deserializedEpic)
    }
}
