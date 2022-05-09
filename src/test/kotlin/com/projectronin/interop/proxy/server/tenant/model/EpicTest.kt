package com.projectronin.interop.proxy.server.tenant.model

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
            authEndpoint = "authEndpoint",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            mrnSystem = "mrnSystem",
            hsi = "hsi",
            instanceName = "instanceName"
        )

        assertEquals(VendorType.EPIC, epic.vendorType)
        assertEquals("release", epic.release)
        assertEquals("serviceEndpoint", epic.serviceEndpoint)
        assertEquals("authEndpoint", epic.authEndpoint)
        assertEquals("ehrUserId", epic.ehrUserId)
        assertEquals("messageType", epic.messageType)
        assertEquals("practitionerProviderSystem", epic.practitionerProviderSystem)
        assertEquals("practitionerUserSystem", epic.practitionerUserSystem)
        assertEquals("mrnSystem", epic.mrnSystem)
        assertEquals("hsi", epic.hsi)
        assertEquals("instanceName", epic.instanceName)
    }

    @Test
    fun `can serialize and deserialize`() {
        val epic = Epic(
            release = "release",
            serviceEndpoint = "serviceEndpoint",
            authEndpoint = "authEndpoint",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            mrnSystem = "mrnSystem",
            hsi = "hsi",
            instanceName = "instanceName"
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(epic)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON = """
            {
              "type" : "EPIC",
              "release" : "release",
              "serviceEndpoint" : "serviceEndpoint",
              "authEndpoint" : "authEndpoint",
              "ehrUserId" : "ehrUserId",
              "messageType" : "messageType",
              "practitionerProviderSystem" : "practitionerProviderSystem",
              "practitionerUserSystem" : "practitionerUserSystem",
              "mrnSystem" : "mrnSystem",
              "hsi" : "hsi",
              "instanceName" : "instanceName",
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
            authEndpoint = "authEndpoint",
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            mrnSystem = "mrnSystem",
            hsi = null,
            instanceName = "instanceName"
        )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(epic)

        // Type is added by @JsonTypeInfo annotation in Vendor interface
        val expectedJSON = """
            {
              "type" : "EPIC",
              "release" : "release",
              "serviceEndpoint" : "serviceEndpoint",
              "authEndpoint" : "authEndpoint",
              "ehrUserId" : "ehrUserId",
              "messageType" : "messageType",
              "practitionerProviderSystem" : "practitionerProviderSystem",
              "practitionerUserSystem" : "practitionerUserSystem",
              "mrnSystem" : "mrnSystem",
              "instanceName" : "instanceName",
              "vendorType" : "EPIC"
            }
        """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedEpic = objectMapper.readValue<Epic>(json)
        assertEquals(epic, deserializedEpic)
    }
}
