package com.projectronin.interop.proxy.server

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.BaseAidboxTest
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc") // prevent Exception in AuthService.kt
@AidboxData("aidbox/practitioner1.yaml", "aidbox/practitionerPool.yaml")
class InteropProxyServerIntegratedMessageTests : BaseAidboxTest() {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    companion object {
        // allows us to dynamically change the aidbox port to the testcontainer instance
        @JvmStatic
        @DynamicPropertySource
        fun aidboxUrlProperties(registry: DynamicPropertyRegistry) {
            registry.add("aidbox.url") { "http://localhost:${aidbox.port()}" }
        }
    }

    @Test
    fun `server handles message mutation`() {
        val tenantId = "apposnd"
        val mrn = "202497"
        val id = "3566c140-dafb-4db6-95f1-fb23a72c7b25"
        val message = "Test message"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "mrn": "$mrn"
            |          },
            |         "recipients": {
            |             "fhirId": "$id"
            |         },
            |         "text": "$message"
            |      },
            |      "tenantId": "$tenantId"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        val expectedJSONObject = Parser.default().parse(StringBuilder(expectedJSON)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles pool provider`() {
        val tenantId = "apposnd"
        val mrn = "202497"
        val id = "c305eedb-96e4-401e-be0f-b34995638d42"
        val message = "Test pool message"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "mrn": "$mrn"
            |          },
            |         "recipients": {
            |             "fhirId": "$id"
            |         },
            |         "text": "$message"
            |      },
            |      "tenantId": "$tenantId"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        val expectedJSONObject = Parser.default().parse(StringBuilder(expectedJSON)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles pool and non-pool providers`() {
        val tenantId = "apposnd"
        val mrn = "202497"
        val idPool = "c305eedb-96e4-401e-be0f-b34995638d42"
        val idNotPool = "3566c140-dafb-4db6-95f1-fb23a72c7b25"
        val message = "Test pool message"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "mrn": "$mrn"
            |          },
            |         "recipients": [
            |            {
            |                "fhirId": "$idPool"
            |            },
            |            {
            |                "fhirId": "$idNotPool"
            |            }
            |         ],
            |         "text": "$message"
            |      },
            |      "tenantId": "$tenantId"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        val expectedJSONObject = Parser.default().parse(StringBuilder(expectedJSON)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles bad tenant`() {
        val tenantId = "fake"
        val mrn = "202497"
        val id = "3566c140-dafb-4db6-95f1-fb23a72c7b25"
        val message = "Test message"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "mrn": "$mrn"
            |          },
            |         "recipients": {
            |             "fhirId": "$id"
            |         },
            |         "text": "$message"
            |      },
            |      "tenantId": "$tenantId"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        val errorJSONObject = (resultJSONObject["errors"] as JsonArray<*>)[0] as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(errorJSONObject["message"].toString().contains("Tenant not found: $tenantId"))
    }

    @Test
    fun `server handles epic bad data response`() {
        val tenantId = "apposnd"
        val mrn = "fake"
        val id = "3566c140-dafb-4db6-95f1-fb23a72c7b25"
        val message = "Test message"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "mrn": "$mrn"
            |          },
            |         "recipients": {
            |             "fhirId": "$id"
            |         },
            |         "text": "$message"
            |      },
            |      "tenantId": "$tenantId"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.map.containsKey("errors"))
    }

    @Test
    fun `server handles epic missing data`() {
        val tenantId = "apposnd"
        val mrn = "202497"
        val id = "3566c140-dafb-4db6-95f1-fb23a72c7b25"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
        |{
        |   "variables": {
        |      "message": {
        |          "patient": {
        |              "mrn": "$mrn"
        |          },
        |         "recipients": {
        |             "fhirId": "$id"
        |         }
        |      },
        |      "tenantId": "$tenantId"
        |   },
        |   "query": "$mutation"
        |}
    """.trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.map.containsKey("errors"))
    }
}
