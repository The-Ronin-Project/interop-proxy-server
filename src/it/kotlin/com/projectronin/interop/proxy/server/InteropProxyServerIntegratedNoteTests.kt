package com.projectronin.interop.proxy.server

import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager
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
}
