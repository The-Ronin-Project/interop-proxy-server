package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
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
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.net.URI
import javax.sql.DataSource

private var setupDone = false

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@AidboxData(
    "aidbox/practitioner1.yaml",
    "aidbox/practitioner2.yaml",
    "aidbox/practitionerPool.yaml",
    "aidbox/patient2.yaml"
)
@AidboxTest
class InteropProxyServerIntegratedMessageTests {
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

    @BeforeEach
    fun setup() {
        if (!setupDone) {
            // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
            val connection = ehrDatasource.connection
            val statement = connection.createStatement()
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1002;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1002;")

            val createPat = this::class.java.getResource("/mockEHR/r4Patient.json")!!.readText()
            mockEHR.addR4Resource("Patient", createPat, "PatientFHIRID1")
            setupDone = true
        }
    }

    @Test
    fun `server handles message mutation`() {
        val tenantId = "ronin"
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
        val tenantId = "ronin"
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
        val tenantId = "ronin"
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

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

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
        val tenantId = "ronin"
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
        val tenantId = "ronin"
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
    fun `server accepts valid m2m auth`() {
        val tenantId = "ronin"
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
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/json")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val httpEntity = HttpEntity(query, m2mHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles provider tenant mismatch`() {
        val tenantId = "ronin"
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
                .contains("No practitioner user identifier with system 'mockEHRUserSystem' found for resource with FHIR id 'ronin-7e52ab01-0393-4e97-afd8-5b0649ab49e2")
        )
    }
}
