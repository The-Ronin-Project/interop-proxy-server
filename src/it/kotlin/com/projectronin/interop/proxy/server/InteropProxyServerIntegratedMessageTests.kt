package com.projectronin.interop.proxy.server

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.net.URI

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class InteropProxyServerIntegratedMessageTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/json")
    }

    @Test
    fun `server handles message mutation`() {
        val query = this::class.java.getResource("/graphql/epicAOTestMessage.json")!!.readText()
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
        val id = "IPMD"
        val poolInd = false
        val message = "Test message"
        val mutation = """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "mrn": "$mrn"
            |          },
            |         "recipients": {
            |             "id": "$id",
            |             "poolInd": $poolInd
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
        val tenantId = "APP_O_SND"
        val mrn = "fake"
        val id = "IPMD"
        val poolInd = false
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
            |             "id": "$id",
            |             "poolInd": $poolInd
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
        val tenantId = "APP_O_SND"
        val mrn = "202497"
        val id = "IPMD"
        val poolInd = false
        val mutation = """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
        |{
        |   "variables": {
        |      "message": {
        |          "patient": {
        |              "mrn": "$mrn"
        |          },
        |         "recipients": {
        |             "id": "$id",
        |             "poolInd": $poolInd
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
