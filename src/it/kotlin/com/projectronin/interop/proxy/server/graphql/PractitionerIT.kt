package com.projectronin.interop.proxy.server.graphql

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.proxy.server.client.MockEHRClient
import com.projectronin.interop.proxy.server.client.ProxyClient
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PractitionerIT : BaseGraphQLIT() {
    @AfterEach
    fun `delete all`() {
        MockEHRClient.deleteAllResources("Practitioner")
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles practitioner by FHIR ID query`(testTenant: String) {
        val practitioner = practitioner {}
        val id = MockEHRClient.addResource(practitioner)
        val query = this::class.java.getResource("/graphql/practitionerById.graphql")!!
            .readText()
            .replace("__tenant_mnemonic__", testTenant)
            .replace("__id__", id)
        val response = ProxyClient.query(query, testTenant)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertEquals("$testTenant-$id", resultJSONNode["data"]["getPractitionerById"]["id"].asText())
    }

    @Test
    fun `server handles practitioner by provider query`() {
        val testTenant = "epic"
        val practitioner = practitioner {
            identifier of listOf(
                identifier {
                    system of "mockEHRProviderSystem"
                    value of "E1000"
                }
            )
        }
        val id = MockEHRClient.addResource(practitioner)
        val query = this::class.java.getResource("/graphql/practitionerByProvider.graphql")!!
            .readText()
            .replace("__tenant_mnemonic__", testTenant)
        val response = ProxyClient.query(query, testTenant)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertEquals("$testTenant-$id", resultJSONNode["data"]["getPractitionerByProvider"]["id"].asText())
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles not finding practitioner by FHIR ID query`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/practitionerById.graphql")!!
            .readText()
            .replace("__tenant_mnemonic__", testTenant)
            .replace("__id__", "nonExistent")
        val response = ProxyClient.query(query, testTenant)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertTrue(resultJSONNode["data"]["getPractitionerById"].isNull)
    }
}
