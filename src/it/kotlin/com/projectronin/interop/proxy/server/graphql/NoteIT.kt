package com.projectronin.interop.proxy.server.graphql

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.client.AidboxClient
import com.projectronin.interop.proxy.server.client.MockEHRClient
import com.projectronin.interop.proxy.server.client.ProxyClient
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.StringValues
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat

class NoteIT : BaseGraphQLIT() {
    fun getBaseHeaders(token: String): StringValues {
        return StringValues.build {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.ContentType, "application/json")
        }
    }

    @AfterEach
    fun `delete all`() {
        MockEHRClient.deleteAllResources("Patient")
        MockEHRClient.deleteAllResources("Practitioner")
        tenantMnemonics().forEach {
            AidboxClient.deleteAllResources("Practitioner", it)
            AidboxClient.deleteAllResources("Patient", it)
        }
    }

    // the compiler yells that this is always epic, but eventually we'll want to test cerner,
    // so I'm hedging my bets here that this will eventually make it easier
    private fun addTenantData(testTenant: String) {
        val tenantIdentifier = identifier {
            value of testTenant
            system of "http://projectronin.com/id/tenantId"
        }

        val patient = patient {
            id of Id("654321")
            identifier of listOf(
                identifier {
                    value of "0202497"
                    system of "mockEHRMRNSystem"
                },
                identifier {
                    value of "mockPatientInternalSystem"
                    system of "     Z4572"
                }
            )
            name of listOf(
                name {
                    use of "usual" // required
                }
            )
            gender of "female"
            birthDate of Date("1973-07-21")
        }
        val patientFHirId = MockEHRClient.addResourceWithID(patient, "654321")
        val aidboxPatient = patient.copy(
            id = Id("$testTenant-654321"),
            identifier = patient.identifier +
                tenantIdentifier +
                fhirIdentifier(patientFHirId) +
                Identifier(
                    system = Uri("http://projectronin.com/id/mrn"),
                    value = "0202497".asFHIR()
                )
        )
        AidboxClient.addResource(aidboxPatient)

        val practitioner = practitioner {
            id of Id("PractitionerFHIRID1")
            identifier of listOf(
                identifier {
                    value of "E1000"
                    system of "mockEHRUserSystem"
                }
            )
        }
        val practitionerFhirID = MockEHRClient.addResourceWithID(practitioner, "PractitionerFHIRID1")
        val aidboxPractitioner = practitioner.copy(
            id = Id("$testTenant-654321"),
            identifier = practitioner.identifier +
                tenantIdentifier +
                fhirIdentifier(practitionerFhirID)
        )
        AidboxClient.addResource(aidboxPractitioner)
    }

    @Test
    fun `server handles note mutation with patient UDP ID`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "$testTenant-654321"
        val practitionerid = "$testTenant-654321"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()

        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())

        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `server handles note mutation with patient UDP ID and M2M auth`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "$testTenant-654321"
        val practitionerid = "$testTenant-654321"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val m2mToken = getM2MAuthentication()
        val response = ProxyClient.query(query, m2mToken, getBaseHeaders(m2mToken))

        val body = runBlocking { response.body<String>() }
        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())

        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `server handles note mutation with patient MRN`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "0202497"
        val practitionerid = "$testTenant-654321"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())

        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner UDP ID found in EHR Data Authority, patient UDP ID not found in EHR Data Authority, no EHR fallback for patient UDP ID not found`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "$testTenant-123456"
        val practitionerid = "$testTenant-654321"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No Patient found for $patientid")
        )
    }

    @Test
    fun `practitioner non-UDP ID not found in EHR Data Authority, practitioner not found in MockEHR`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "PatientFHIRID1"
        val practitionerid = "PractitionerFHIRID999"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No Patient found for") // MockEHR
        )
    }

    @Test
    fun `practitioner non-UDP ID not found in EHR Data Authority, practitioner found in MockEHR, patient UDP ID found in EHR Data Authority`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "$testTenant-654321"
        val practitionerid = "PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val body = runBlocking { response.body<String>() }
        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner UDP ID not found in EHR Data Authority, practitioner found in MockEHR, patient UDP ID found in EHR Data Authority`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "$testTenant-654321"
        val practitionerid = "PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())

        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner non-UDP ID not found in EHR Data Authority, practitioner found in MockEHR, patient UDP ID not found in EHR Data Authority, no EHR fallback for this case`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "PatientFHIRID1999"
        val practitionerid = "PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No Patient found for $patientid")
        )
    }

    @Test
    fun `practitioner non-UDP ID not found in EHR Data Authority, practitioner found in MockEHR, patient MRN found in MockEHR`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "0202497"
        val practitionerid = "PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }

    @Test
    fun `practitioner non-UDP ID not found in EHR Data Authority, practitioner found in MockEHR, patient MRN not found in MockEHR`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "123"
        val practitionerid = "PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertTrue(resultJSONObject.has("errors"))
        val errorJSONObject = resultJSONObject["errors"][0]
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("No FHIR ID found for patient") // MockEHR
        )
    }

    @Test
    fun `patient ID (less than 7 digits) needs padding, practitioner found in MockEHR, padded patient MRN found in MockEHR`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val notetext = "Test Note"
        val patientid = "202497"
        val practitionerid = "PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   }
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }
        val dateformat = SimpleDateFormat("yyyyMM")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        val resultJSONObject = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertTrue(resultJSONObject["data"]["sendNote"].asText().startsWith(docId))
    }
}
