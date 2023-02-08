package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container

@AidboxData(
    "aidbox/practitioner1.yaml",
    "aidbox/practitioner2.yaml",
    "aidbox/practitionerPool.yaml",
    "aidbox/patient2.yaml"
)
@AidboxTest
class InteropProxyServerIntegratedMessageTests : InteropProxyServerIntegratedTestsBase() {

    override val resourcesToAdd = listOf(
        ResourceToAdd("Patient", "/mockEHR/r4Patient.json", "PatientFHIRID1")
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
            registry.add("aidbox.url") { "http://localhost:${aidbox.port()}" }
        }
    }

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles message mutation`(testTenant: String) {
        val mrn = "202497"
        val id = "ronin-PractitionerFHIRID1"
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
            |      "tenantId": "ronin"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""

        val responseEntity = multiVendorQuery(query, testTenant)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles pool provider`(testTenant: String) {
        val mrn = "202497"
        val id = "ronin-PractitionerPoolFHIRID1"
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
            |      "tenantId": "ronin"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val responseEntity = multiVendorQuery(query, testTenant)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles pool and non-pool providers`(testTenant: String) {
        val mrn = "202497"
        val idPool = "ronin-PractitionerPoolFHIRID1"
        val idNotPool = "ronin-PractitionerFHIRID1"
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
            |      "tenantId": "ronin"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val responseEntity = multiVendorQuery(query, testTenant)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles bad tenant`() {
        val tenantId = "fake"
        val mrn = "202497"
        val id = "ronin-PractitionerFHIRID1"
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

        val responseEntity = multiVendorQuery(query, "epic")

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val errorJSONObject = resultJSONObject["errors"][0]
        println(errorJSONObject)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("Requested Tenant '$tenantId' does not match authorized Tenant 'ronin'")
        )
    }

    @Test
    fun `server handles epic bad data response`() {
        val mrn = "fake"
        val id = "ronin-PractitionerFHIRIDI1"
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
            |      "tenantId": "ronin"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val responseEntity = multiVendorQuery(query, "epic")

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @Test
    fun `server handles epic missing data`() {
        val mrn = "202497"
        val id = "ronin-PractitionerFHIRIDI1"
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
        |      "tenantId": "ronin"
        |   },
        |   "query": "$mutation"
        |}
    """.trimMargin()

        val responseEntity = multiVendorQuery(query, "epic")

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server accepts valid m2m auth`(testTenant: String) {
        val mrn = "202497"
        val id = "ronin-PractitionerFHIRID1"
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
            |      "tenantId": "ronin"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/json")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val responseEntity = multiVendorQuery(query, testTenant, m2mHeaders)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles provider tenant mismatch`() {
        val mrn = "202497"
        val id = "ronin-7e52ab01-0393-4e97-afd8-5b0649ab49e2"
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
            |      "tenantId": "ronin"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val responseEntity = multiVendorQuery(query, "epic")
        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val errorJSONObject = resultJSONObject["errors"][0]
        println(errorJSONObject)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No practitioner user identifier with system 'mockEHRUserSystem' found for resource with FHIR id 'ronin-7e52ab01-0393-4e97-afd8-5b0649ab49e2")
        )
    }
}
