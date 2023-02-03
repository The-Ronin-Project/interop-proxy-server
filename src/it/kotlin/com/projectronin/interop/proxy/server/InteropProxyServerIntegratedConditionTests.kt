package com.projectronin.interop.proxy.server

import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import java.net.URI

class InteropProxyServerIntegratedConditionTests : InteropProxyServerIntegratedTestsBase() {

    override val resourcesToAdd = listOf(
        ResourceToAdd("Patient", "/mockEHR/r4Patient.json", "PatientFHIRID1"),
        ResourceToAdd("Condition", "/mockEHR/r4Condition.json", "ConditionFHIRID1")
    )

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles condition query`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/conditionsByPatient.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/roninTestConditionGraphQLResults.json")!!.readText()

        val responseEntity =
            multiVendorQuery(query, testTenant)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONObject.toString())
    }

    @Test
    fun `server handles bad tenant`() {
        val tenantId = "fake"
        val patientFhirId = "eovSKnwDlsv-8MsEzCJO3BA3"
        val conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$tenantId", 
                patientFhirId:"$patientFhirId", 
                conditionCategoryCode:$conditionCategoryCode)
               {id}
            }
        """.trimIndent()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        assertEquals(
            "Exception while fetching data (/conditionsByPatientAndCategory) : 403 Requested Tenant 'fake' does not match authorized Tenant 'ronin'",
            resultJSONObject["errors"][0]["message"].asText()
        )
    }

    @Test
    fun `server handles missing field`() {
        val tenantId = "ronin"
        val patientFhirId = "eovSKnwDlsv-8MsEzCJO3BA3"

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$tenantId", 
                patientFhirId:"$patientFhirId"
               {id}
            }
        """.trimIndent()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles no conditions found`(testTenant: String) {
        val tenantId = "ronin"
        val patientFhirId = "e9Bi2yhKnvFU8rsjpJpPMCw3" // Test patient with no conditions
        val conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$tenantId", 
                patientFhirId:"$patientFhirId", 
                conditionCategoryCode:$conditionCategoryCode)
               {id}
            }
        """.trimIndent()

        val responseEntity = multiVendorQuery(query, testTenant)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val conditionSearchJSONArray = resultJSONObject["data"]["conditionsByPatientAndCategory"]

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, conditionSearchJSONArray.size())
    }
}
