package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.common.vendor.VendorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CernerTest {
    @Test
    fun `can get data`() {
        val cerner =
            Cerner(
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                patientMRNSystem = "patientMrnSystem",
                instanceName = "instanceName",
                messagePractitioner = "practitioner",
                messageTopic = "topic",
                messageCategory = "category",
                messagePriority = "priority",
            )

        assertEquals(VendorType.CERNER, cerner.vendorType)
        assertEquals("serviceEndpoint", cerner.serviceEndpoint)
        assertEquals("authEndpoint", cerner.authEndpoint)
        assertEquals("patientMrnSystem", cerner.patientMRNSystem)
        assertEquals("instanceName", cerner.instanceName)
        assertEquals("practitioner", cerner.messagePractitioner)
        assertEquals("topic", cerner.messageTopic)
        assertEquals("category", cerner.messageCategory)
        assertEquals("priority", cerner.messagePriority)
    }

    @Test
    fun `can serialize and deserialize`() {
        val cerner =
            Cerner(
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                patientMRNSystem = "patientMrnSystem",
                instanceName = "instanceName",
                messagePractitioner = "practitioner",
                messageTopic = "topic",
                messageCategory = "category",
                messagePriority = "priority",
            )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cerner)

        val expectedJSON =
            """
            {
              "type" : "CERNER",
              "serviceEndpoint" : "serviceEndpoint",
              "authEndpoint" : "authEndpoint",
              "patientMRNSystem" : "patientMrnSystem",
              "instanceName" : "instanceName",
              "messagePractitioner" : "practitioner",
              "messageTopic" : "topic",
              "messageCategory" : "category",
              "messagePriority" : "priority",
              "vendorType" : "CERNER"
            }
            """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedCerner = objectMapper.readValue<Cerner>(json)
        assertEquals(cerner, deserializedCerner)
    }

    @Test
    fun `can serialize and deserialize with null values`() {
        val cerner =
            Cerner(
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                patientMRNSystem = "patientMrnSystem",
                instanceName = "instanceName",
                messagePractitioner = "practitioner",
                messageTopic = null,
                messageCategory = null,
                messagePriority = null,
            )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cerner)

        val expectedJSON =
            """
            {
              "type" : "CERNER",
              "serviceEndpoint" : "serviceEndpoint",
              "authEndpoint" : "authEndpoint",
              "patientMRNSystem" : "patientMrnSystem",
              "instanceName" : "instanceName",
              "messagePractitioner" : "practitioner",
              "vendorType" : "CERNER"
            }
            """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedCerner = objectMapper.readValue<Cerner>(json)
        assertEquals(cerner, deserializedCerner)
    }
}
