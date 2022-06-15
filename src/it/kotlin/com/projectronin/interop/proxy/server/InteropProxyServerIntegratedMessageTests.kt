package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.BaseAidboxTest
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import javax.sql.DataSource

private var setupDone = false

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@AidboxData("aidbox/practitioner1.yaml", "aidbox/practitioner2.yaml", "aidbox/practitionerPool.yaml")
class InteropProxyServerIntegratedMessageTests : BaseAidboxTest() {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var mockEHR: MockEHRTestcontainer

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockkBean
    private lateinit var m2mJwtDecoder: JwtDecoder

    @Autowired
    private lateinit var ehrDatasource: DataSource

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

    @BeforeEach
    fun setup() {
        if (!setupDone) {
            // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
            val connection = ehrDatasource.connection
            val statement = connection.createStatement()
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1001;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1001;")

            val createPat = this::class.java.getResource("/mockEHR/r4Patient.json")!!.readText()
            mockEHR.addR4Resource("Patient", createPat, "eJzlzKe3KPzAV5TtkxmNivQ3")
            setupDone = true
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

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
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

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
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

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val errorJSONObject = resultJSONObject["errors"][0]
        println(errorJSONObject)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("Requested Tenant '$tenantId' does not match authorized Tenant 'apposnd'")
        )
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

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
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

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @Test
    fun `server rejects valid m2m auth`() {
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

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val httpEntity = HttpEntity(query, m2mHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        println(resultJSONObject.toPrettyString())
    }

    @Test
    fun `server handles provider tenant mismatch`() {
        val tenantId = "apposnd"
        val mrn = "202497"
        val id = "7e52ab01-0393-4e97-afd8-5b0649ab49e2"
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

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val errorJSONObject = resultJSONObject["errors"][0]
        println(errorJSONObject)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No practitioner user identifier with system 'urn:oid:1.2.840.114350.1.13.0.1.7.2.697780' found for resource with FHIR id '7e52ab01-0393-4e97-afd8-5b0649ab49e2")
        )
    }
}
