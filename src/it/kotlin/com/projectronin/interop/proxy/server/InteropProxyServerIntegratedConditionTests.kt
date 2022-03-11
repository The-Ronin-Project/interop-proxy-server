package com.projectronin.interop.proxy.server

import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.net.URI

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc") // prevent Exception in AuthService.kt
class InteropProxyServerIntegratedConditionTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `server handles condition query`() {
        val query = this::class.java.getResource("/graphql/epicAOTestCondition.graphql")!!.readText()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject.size() > 0)
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
    }

    @Test
    fun `server handles missing field`() {
        val tenantId = "apposnd"
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

    @Test
    fun `server handles no conditions found`() {
        val tenantId = "apposnd"
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

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val conditionSearchJSONArray = resultJSONObject["data"]["conditionsByPatientAndCategory"]

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, conditionSearchJSONArray.size())
    }
}
