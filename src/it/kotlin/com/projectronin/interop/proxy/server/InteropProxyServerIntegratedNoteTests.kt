package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.net.URI
import java.text.SimpleDateFormat

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@AidboxData("aidbox/practitioner3.yaml", "aidbox/patient1.yaml")
@AidboxTest
class InteropProxyServerIntegratedNoteTests {
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

    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockkBean
    private lateinit var m2mJwtDecoder: JwtDecoder

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `server handles note mutation with patient FHIR Id`() {
        val notetext = "Test Note"
        val patientid = "654321"
        val practitionerid = "654321"
        val tenantId = "apposnd"
        val mutation =
            """mutation sendNote(${'$'}noteInput: NoteInput!, ${'$'}tenantId: String!) {sendNote(noteInput: ${'$'}noteInput, tenantId: ${'$'}tenantId)}"""
        val query = """
            |{
            |   "query": "$mutation",
            |   "variables": {
            |      "noteInput": {
            |         "datetime": "202206011250",
            |         "patientId":  "$patientid",
            |         "patientIdType":  "FHIR",
            |         "practitionerFhirId": "$practitionerid",
            |         "noteText": "$notetext"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `server handles note mutation with patient FHIR Id and M2M auth`() {
        val notetext = "Test Note"
        val patientid = "654321"
        val practitionerid = "654321"
        val tenantId = "apposnd"
        val mutation =
            """mutation sendNote(${'$'}noteInput: NoteInput!, ${'$'}tenantId: String!) {sendNote(noteInput: ${'$'}noteInput, tenantId: ${'$'}tenantId)}"""
        val query = """
            |{
            |   "query": "$mutation",
            |   "variables": {
            |      "noteInput": {
            |         "datetime": "202206011250",
            |         "patientId":  "$patientid",
            |         "patientIdType":  "FHIR",
            |         "practitionerFhirId": "$practitionerid",
            |         "noteText": "$notetext"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/json")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk())

        val httpEntity = HttpEntity(query, m2mHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `server handles note mutation with patient MRN`() {
        val notetext = "Test Note"
        val patientid = "123456"
        val practitionerid = "654321"
        val tenantId = "apposnd"
        val mutation =
            """mutation sendNote(${'$'}noteInput: NoteInput!, ${'$'}tenantId: String!) {sendNote(noteInput: ${'$'}noteInput, tenantId: ${'$'}tenantId)}"""
        val query = """
            |{
            |   "query": "$mutation",
            |   "variables": {
            |      "noteInput": {
            |         "datetime": "202206011250",
            |         "patientId":  "$patientid",
            |         "patientIdType":  "MRN",
            |         "practitionerFhirId": "$practitionerid",
            |         "noteText": "$notetext"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner not found`() {
        val notetext = "Test Note"
        val patientid = "654321"
        val practitionerid = "123456"
        val tenantId = "apposnd"
        val mutation =
            """mutation sendNote(${'$'}noteInput: NoteInput!, ${'$'}tenantId: String!) {sendNote(noteInput: ${'$'}noteInput, tenantId: ${'$'}tenantId)}"""
        val query = """
            |{
            |   "query": "$mutation",
            |   "variables": {
            |      "noteInput": {
            |         "datetime": "202206011250",
            |         "patientId":  "$patientid",
            |         "patientIdType":  "FHIR",
            |         "practitionerFhirId": "$practitionerid",
            |         "noteText": "$notetext"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("404 Not Found")
        )
    }

    @Test
    fun `patient not found`() {
        val notetext = "Test Note"
        val patientid = "123456"
        val practitionerid = "654321"
        val tenantId = "apposnd"
        val mutation =
            """mutation sendNote(${'$'}noteInput: NoteInput!, ${'$'}tenantId: String!) {sendNote(noteInput: ${'$'}noteInput, tenantId: ${'$'}tenantId)}"""
        val query = """
            |{
            |   "query": "$mutation",
            |   "variables": {
            |      "noteInput": {
            |         "datetime": "202206011250",
            |         "patientId":  "$patientid",
            |         "patientIdType":  "FHIR",
            |         "practitionerFhirId": "$practitionerid",
            |         "noteText": "$notetext"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("404 Not Found")
        )
    }

    @Test
    fun `server handles note mutation with bad tenant`() {
        val notetext = "Test Note"
        val patientid = "654321"
        val practitionerid = "654321"
        val tenantId = "fake-tenant"
        val mutation =
            """mutation sendNote(${'$'}noteInput: NoteInput!, ${'$'}tenantId: String!) {sendNote(noteInput: ${'$'}noteInput, tenantId: ${'$'}tenantId)}"""
        val query = """
            |{
            |   "query": "$mutation",
            |   "variables": {
            |      "noteInput": {
            |         "datetime": "202206011250",
            |         "patientId":  "$patientid",
            |         "patientIdType":  "FHIR",
            |         "practitionerFhirId": "$practitionerid",
            |         "noteText": "$notetext"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("403 Requested Tenant")
        )
    }
}
