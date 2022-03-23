package com.projectronin.interop.proxy.server.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.common.vendor.VendorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class EpicTest {
    @Test
    fun `can get data`() {
        val epic = Epic(
            release = "release",
            serviceEndpoint = "serviceEndpoint",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            hsi = "hsi"
        )

        assertEquals(VendorType.EPIC, epic.vendorType)
        assertEquals("release", epic.release)
        assertEquals("serviceEndpoint", epic.serviceEndpoint)
        assertEquals("ehrUserId", epic.ehrUserId)
        assertEquals("messageType", epic.messageType)
        assertEquals("practitionerProviderSystem", epic.practitionerProviderSystem)
        assertEquals("practitionerUserSystem", epic.practitionerUserSystem)
        assertEquals("hsi", epic.hsi)
    }

    @Test
    fun `can serialize and deserialize`() {
        val epic = Epic(
            release = "release",
            serviceEndpoint = "serviceEndpoint",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            hsi = "hsi"
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(epic)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON = """
            {
              "type" : "EPIC",
              "release" : "release",
              "serviceEndpoint" : "serviceEndpoint",
              "ehrUserId" : "ehrUserId",
              "messageType" : "messageType",
              "practitionerProviderSystem" : "practitionerProviderSystem",
              "practitionerUserSystem" : "practitionerUserSystem",
              "hsi" : "hsi",
              "vendorType" : "EPIC"
            }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = objectMapper.readValue<Epic>(json)
        assertEquals(epic, deserializedEpic)
    }

    @Test
    fun `can serialize and deserialize with null values`() {
        val epic = Epic(
            release = "release",
            serviceEndpoint = "serviceEndpoint",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            hsi = null
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(epic)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON = """
            {
              "type" : "EPIC",
              "release" : "release",
              "serviceEndpoint" : "serviceEndpoint",
              "ehrUserId" : "ehrUserId",
              "messageType" : "messageType",
              "practitionerProviderSystem" : "practitionerProviderSystem",
              "practitionerUserSystem" : "practitionerUserSystem",
              "vendorType" : "EPIC"
            }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = objectMapper.readValue<Epic>(json)
        assertEquals(epic, deserializedEpic)
    }
}
