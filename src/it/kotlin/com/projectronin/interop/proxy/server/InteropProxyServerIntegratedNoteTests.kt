package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.text.SimpleDateFormat

@AidboxData("aidbox/practitioner3.yaml", "aidbox/patient1.yaml")
@AidboxTest
class InteropProxyServerIntegratedNoteTests : InteropProxyServerIntegratedTestsBase() {

    override val resourcesToAdd = listOf(
        ResourceToAdd("Patient", "/mockEHR/r4Patient1.json", "PatientFHIRID1"),
        ResourceToAdd("Practitioner", "/mockEHR/r4Practitioner.json", "PractitionerFHIRID1")
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

    @Test
    fun `server handles note mutation with patient UDP ID`() {
        val notetext = "Test Note"
        val patientid = "ronin-654321"
        val practitionerid = "ronin-654321"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `server handles note mutation with patient UDP ID and M2M auth`() {
        val notetext = "Test Note"
        val patientid = "ronin-654321"
        val practitionerid = "ronin-654321"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
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

        val responseEntity = multiVendorQuery(query, "epic", m2mHeaders)
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
        val practitionerid = "ronin-654321"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner UDP ID not found in Aidbox, no EHR fallback for practitioner UDP ID not found`() {
        val notetext = "Test Note"
        val patientid = "ronin-654321"
        val practitionerid = "ronin-123456"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("404 Not Found") // Aidbox
        )
    }

    @Test
    fun `practitioner UDP ID found in Aidbox, patient UDP ID not found in Aidbox, no EHR fallback for patient UDP ID not found`() {
        val notetext = "Test Note"
        val patientid = "ronin-123456"
        val practitionerid = "ronin-654321"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("404 Not Found") // Aidbox
        )
    }

    @Test
    fun `practitioner non-UDP ID not found in Aidbox, practitioner not found in MockEHR`() {
        val notetext = "Test Note"
        val patientid = "PatientFHIRID1"
        val practitionerid = "PractitionerFHIRID999"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("Received 404") // MockEHR
        )
    }

    @Test
    fun `practitioner non-UDP ID not found in Aidbox, practitioner found in MockEHR, patient UDP ID found in Aidbox`() {
        val notetext = "Test Note"
        val patientid = "ronin-654321"
        val practitionerid = "PractitionerFHIRID1"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner non-UDP ID not found in Aidbox, practitioner found in MockEHR, patient UDP ID not found in Aidbox, no EHR fallback for this case`() {
        val notetext = "Test Note"
        val patientid = "PatientFHIRID1999"
        val practitionerid = "PractitionerFHIRID1"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("404 Not Found") // Aidbox
        )
    }

    @Test
    fun `practitioner non-UDP ID not found in Aidbox, practitioner found in MockEHR, patient MRN not found in Aidbox, patient MRN found in MockEHR`() {
        val notetext = "Test Note"
        val patientid = "202497"
        val practitionerid = "PractitionerFHIRID1"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner non-UDP ID not found in Aidbox, practitioner found in MockEHR, patient MRN not found in Aidbox, patient MRN not found in MockEHR`() {
        val notetext = "Test Note"
        val patientid = "123"
        val practitionerid = "PractitionerFHIRID1"
        val tenantId = "ronin"
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
        val resultJSONObject = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        println(resultJSONObject)
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No FHIR ID found for patient") // MockEHR
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
            |         "noteText": "$notetext",
            |         "noteSender": "PRACTITIONER",
            |         "isAlert" : "False"
            |      },
            |      "tenantId": "$tenantId"
            |   }
            |}
        """.trimMargin()
        val responseEntity = multiVendorQuery(query, "epic")
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
