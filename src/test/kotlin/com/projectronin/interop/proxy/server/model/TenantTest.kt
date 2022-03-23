package com.projectronin.interop.proxy.server.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TenantTest {
    @Test
    fun `can get data`() {
        val tenant = Tenant(
            id = 1,
            mnemonic = "mnemonic1",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(23, 0),
            vendor = Epic(
                release = "release",
                serviceEndpoint = "serviceEndpoint",
                ehrUserId = "ehrUserId",
                messageType = "messageType",
                practitionerProviderSystem = "practitionerProviderSystem",
                practitionerUserSystem = "practitionerUserSystem",
                hsi = null
            )
        )

        assertEquals(1, tenant.id)
        assertEquals("mnemonic1", tenant.mnemonic)
        assertEquals(LocalTime.of(22, 0), tenant.availableStart)
        assertEquals(LocalTime.of(23, 0), tenant.availableEnd)
    }

    @Test
    fun `can serialize and deserialize`() {
        val tenant = Tenant(
            id = 1,
            mnemonic = "mnemonic1",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(23, 0),
            vendor = Epic(
                release = "release",
                serviceEndpoint = "serviceEndpoint",
                ehrUserId = "ehrUserId",
                messageType = "messageType",
                practitionerProviderSystem = "practitionerProviderSystem",
                practitionerUserSystem = "practitionerUserSystem",
                hsi = null
            )
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenant)

        val expectedJSON = """
            {
              "id" : 1,
              "mnemonic" : "mnemonic1",
              "availableStart" : "22:00:00",
              "availableEnd" : "23:00:00",
              "vendor" : {
                "type" : "EPIC",
                "release" : "release",
                "serviceEndpoint" : "serviceEndpoint",
                "ehrUserId" : "ehrUserId",
                "messageType" : "messageType",
                "practitionerProviderSystem" : "practitionerProviderSystem",
                "practitionerUserSystem" : "practitionerUserSystem",
                "vendorType" : "EPIC"
              }
            }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedTenant = objectMapper.readValue<Tenant>(json)
        assertEquals(tenant, deserializedTenant)
    }
}
