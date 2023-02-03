package com.projectronin.interop.proxy.server

import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container

@AidboxData("aidbox/practitioners.yaml")
@AidboxTest
class InteropProxyServerIntegratedPractitionerTests : InteropProxyServerIntegratedTestsBase() {

    override val resourcesToAdd = listOf(
        ResourceToAdd(
            "Practitioner",
            "/mockEHR/r4Practitioner.json",
            "PractitionerFHIRID1"
        )
    )

    companion object {
        @Container
        val aidboxDatabaseContainer = AidboxDatabaseContainer()

        @Container
        val aidbox = AidboxContainer(aidboxDatabaseContainer, version = "2206-lts")

        // allows us to dynamically change the aidbox port to the testcontainer instance
        @JvmStatic
        @DynamicPropertySource
        fun aidboxUrlProperties(registry: DynamicPropertyRegistry) {
            registry.add("aidbox.url") { aidbox.baseUrl() }
        }
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles practitioner by FHIR ID query`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/practitionerById.graphql")!!.readText()
        val responseEntity = multiVendorQuery(query, testTenant)
        val resultJSONNode = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals("ronin-PractitionerFHIRID1", resultJSONNode["data"]["getPractitionerById"]["id"].asText())
    }

    @Test
    fun `server handles practitioner by provider query`() {
        val query = this::class.java.getResource("/graphql/practitionerByProvider.graphql")!!.readText()

        val responseEntity = multiVendorQuery(query, "epic")

        val resultJSONNode = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals("ronin-PractitionerFHIRID1", resultJSONNode["data"]["getPractitionerByProvider"]["id"].asText())
    }
}
