package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TenantTest {
    @Test
    fun `can get data`() {
        val tenant =
            Tenant(
                id = 1,
                mnemonic = "mnemonic1",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(23, 0),
                name = "test tenant",
                timezone = "America/Chicago",
                vendor =
                    Epic(
                        release = "release",
                        serviceEndpoint = "serviceEndpoint",
                        authEndpoint = "authEndpoint",
                        ehrUserId = "ehrUserId",
                        messageType = "messageType",
                        practitionerProviderSystem = "practitionerProviderSystem",
                        practitionerUserSystem = "practitionerUserSystem",
                        patientMRNSystem = "patientMRNSystem",
                        patientInternalSystem = "patientInternalSystem",
                        encounterCSNSystem = "encounterCSNSystem",
                        patientMRNTypeText = "patientMRNTypeText",
                        hsi = null,
                        instanceName = "instanceName",
                        departmentInternalSystem = "departmentInternalSystem",
                        patientOnboardedFlagId = null,
                        orderSystem = "orderSystem",
                        appVersion = "appVersion",
                    ),
                monitoredIndicator = null,
            )

        assertEquals(1, tenant.id)
        assertEquals("mnemonic1", tenant.mnemonic)
        assertEquals("test tenant", tenant.name)
        assertEquals(LocalTime.of(22, 0), tenant.availableStart)
        assertEquals(LocalTime.of(23, 0), tenant.availableEnd)
        assertEquals("America/Chicago", tenant.timezone)
        assertNull(tenant.monitoredIndicator)
    }

    @Test
    fun `can serialize and deserialize`() {
        val tenant =
            Tenant(
                id = 1,
                mnemonic = "mnemonic1",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(23, 0),
                name = "test tenant",
                timezone = "America/Chicago",
                vendor =
                    Epic(
                        release = "release",
                        serviceEndpoint = "serviceEndpoint",
                        authEndpoint = "authEndpoint",
                        ehrUserId = "ehrUserId",
                        messageType = "messageType",
                        practitionerProviderSystem = "practitionerProviderSystem",
                        practitionerUserSystem = "practitionerUserSystem",
                        patientMRNSystem = "patientMRNSystem",
                        patientInternalSystem = "patientInternalSystem",
                        encounterCSNSystem = "encounterCSNSystem",
                        patientMRNTypeText = "patientMRNTypeText",
                        hsi = null,
                        instanceName = "Epic Sandbox",
                        departmentInternalSystem = "departmentInternalSystem",
                        patientOnboardedFlagId = null,
                        orderSystem = "orderSystem",
                        appVersion = "appVersion",
                    ),
                monitoredIndicator = true,
            )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenant)

        val expectedJSON =
            """
            {
              "id" : 1,
              "mnemonic" : "mnemonic1",
              "name" : "test tenant",
              "availableStart" : "22:00:00",
              "availableEnd" : "23:00:00",
              "vendor" : {
                "type" : "EPIC",
                "release" : "release",
                "serviceEndpoint" : "serviceEndpoint",
                "authEndpoint" : "authEndpoint",
                "ehrUserId" : "ehrUserId",
                "messageType" : "messageType",
                "practitionerProviderSystem" : "practitionerProviderSystem",
                "practitionerUserSystem" : "practitionerUserSystem",
                "patientMRNSystem" : "patientMRNSystem",
                "patientInternalSystem" : "patientInternalSystem",
                "encounterCSNSystem" : "encounterCSNSystem",
                "patientMRNTypeText" : "patientMRNTypeText",
                "instanceName" : "Epic Sandbox",
                "departmentInternalSystem" : "departmentInternalSystem",
                "orderSystem" : "orderSystem",
                "appVersion" : "appVersion",
                "vendorType" : "EPIC"
              },
              "timezone" : "America/Chicago",
              "monitoredIndicator" : true
            }
            """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedTenant = objectMapper.readValue<Tenant>(json)
        assertEquals(tenant, deserializedTenant)
    }

    @Test
    fun `can serialize and deserialize with default values`() {
        val tenant =
            Tenant(
                id = 1,
                mnemonic = "mnemonic1",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(23, 0),
                name = "test tenant",
                timezone = "America/Chicago",
                vendor =
                    Epic(
                        release = "release",
                        serviceEndpoint = "serviceEndpoint",
                        authEndpoint = "authEndpoint",
                        ehrUserId = "ehrUserId",
                        messageType = "messageType",
                        practitionerProviderSystem = "practitionerProviderSystem",
                        practitionerUserSystem = "practitionerUserSystem",
                        patientMRNSystem = "patientMRNSystem",
                        patientInternalSystem = "patientInternalSystem",
                        encounterCSNSystem = "encounterCSNSystem",
                        patientMRNTypeText = "patientMRNTypeText",
                        hsi = null,
                        instanceName = "Epic Sandbox",
                        departmentInternalSystem = "departmentInternalSystem",
                        patientOnboardedFlagId = null,
                        orderSystem = null,
                        appVersion = "appVersion",
                    ),
            )
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenant)

        val expectedJSON =
            """
            {
              "id" : 1,
              "mnemonic" : "mnemonic1",
              "name" : "test tenant",
              "availableStart" : "22:00:00",
              "availableEnd" : "23:00:00",
              "vendor" : {
                "type" : "EPIC",
                "release" : "release",
                "serviceEndpoint" : "serviceEndpoint",
                "authEndpoint" : "authEndpoint",
                "ehrUserId" : "ehrUserId",
                "messageType" : "messageType",
                "practitionerProviderSystem" : "practitionerProviderSystem",
                "practitionerUserSystem" : "practitionerUserSystem",
                "patientMRNSystem" : "patientMRNSystem",
                "patientInternalSystem" : "patientInternalSystem",
                "encounterCSNSystem" : "encounterCSNSystem",
                "patientMRNTypeText" : "patientMRNTypeText",
                "instanceName" : "Epic Sandbox",
                "departmentInternalSystem" : "departmentInternalSystem",
                "appVersion" : "appVersion",
                "vendorType" : "EPIC"
              },
              "timezone" : "America/Chicago",
              "monitoredIndicator" : true
            }
            """.trimIndent()
        assertEquals(expectedJSON, json)

        val deserializedTenant = objectMapper.readValue<Tenant>(json)
        assertEquals(tenant, deserializedTenant)
    }
}
