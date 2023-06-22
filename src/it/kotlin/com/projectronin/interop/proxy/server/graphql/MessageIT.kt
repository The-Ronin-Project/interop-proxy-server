package com.projectronin.interop.proxy.server.graphql

import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.client.AidboxClient
import com.projectronin.interop.proxy.server.client.MockEHRClient
import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.model.ProviderPoolDO
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MessageIT : BaseGraphQLIT() {

    fun getBaseHeaders(token: String): StringValues {
        return StringValues.build {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.ContentType, "application/json")
        }
    }

    @AfterEach
    fun `delete all`() {
        MockEHRClient.deleteAllResources("Patient")
        tenantMnemonics().forEach {
            AidboxClient.deleteAllResources("Practitioner", it)
            AidboxClient.deleteAllResources("Patient", it)
        }
    }

    private fun addTenantData(testTenant: String) {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        val providerPoolDAO = ProviderPoolDAO(tenantDB)
        providerPoolDAO.insert(
            ProviderPoolDO {
                tenant = tenantDO
                providerId = "ProviderWithPool"
                poolId = "14600"
            }
        )
        val tenantIdentifier = identifier {
            value of testTenant
            system of "http://projectronin.com/id/tenantId"
        }

        val patient = patient {
            id of Id("patientFHIRID3")
            identifier of listOf(
                identifier {
                    value of "111"
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
            gender of "male"
            birthDate of Date("1990-01-01")
        }
        val patientFHirId = MockEHRClient.addResourceWithID(patient, "patientFHIRID3")
        val aidboxPatient = patient.copy(
            id = Id("$testTenant-patientFHIRID3"),
            identifier = patient.identifier +
                tenantIdentifier +
                fhirIdentifier(patientFHirId) +
                Identifier(
                    system = Uri("http://projectronin.com/id/mrn"),
                    value = "111".asFHIR()
                )
        )
        AidboxClient.addResource(aidboxPatient)
        val practitionerPool = practitioner {
            id of Id("$testTenant-PractitionerPoolFHIRID1")
            identifier of listOf(
                identifier {
                    value of "ProviderWithPool"
                    system of "mockEHRUserSystem"
                    type of CodeableConcept(text = "External".asFHIR())
                },
                tenantIdentifier,
                fhirIdentifier("PractitionerPoolFHIRID1")
            )
        }
        val practitioner1 = practitioner {
            id of Id("$testTenant-PractitionerFHIRID1")
            identifier of listOf(
                identifier {
                    value of "IPMD2"
                    system of "mockEHRUserSystem"
                    type of CodeableConcept(text = "External".asFHIR())
                },
                tenantIdentifier,
                fhirIdentifier("PractitionerFHIRID1")
            )
        }
        val practitioner2 = practitioner {
            id of Id("$testTenant-PractitionerFHIRID2")
            identifier of listOf(
                identifier {
                    value of "IPMD2"
                    system of "mockEHRUserSystem"
                    type of CodeableConcept(text = "External".asFHIR())
                },
                tenantIdentifier,
                fhirIdentifier("PractitionerFHIRID2")
            )
        }

        AidboxClient.addResource(practitionerPool)
        AidboxClient.addResource(practitioner1)
        AidboxClient.addResource(practitioner2)
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles message mutation`(testTenant: String) {
        addTenantData(testTenant)
        val mrn = "111"
        val id = "$testTenant-PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles pool provider`(testTenant: String) {
        addTenantData(testTenant)
        val mrn = "111"
        val id = "$testTenant-PractitionerPoolFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles pool and non-pool providers`(testTenant: String) {
        addTenantData(testTenant)
        val mrn = "111"
        val idPool = "$testTenant-PractitionerPoolFHIRID1"
        val idNotPool = "$testTenant-PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()

        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles epic bad data response`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val mrn = "fake"
        val id = "$testTenant-PractitionerFHIRIDI1"
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
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONObject.has("errors"))
    }

    @Test
    fun `server handles epic missing data`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val mrn = "111"
        val id = "$testTenant-PractitionerFHIRIDI1"
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
        |      "tenantId": "$testTenant"
        |   },
        |   "query": "$mutation"
        |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONObject.has("errors"))
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server accepts valid m2m auth`(testTenant: String) {
        addTenantData(testTenant)
        val mrn = "111"
        val id = "$testTenant-PractitionerFHIRID1"
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
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""
        val m2mToken = getM2MAuthentication()
        val response = ProxyClient.query(query, m2mToken, getBaseHeaders(m2mToken))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }

    @Test
    fun `server handles missing provider`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val mrn = "111"
        val id = "$testTenant-7e52ab01-0393-4e97-afd8-5b0649ab49e2"
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
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        val errorJSONObject = resultJSONObject["errors"][0]
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            errorJSONObject["message"].asText()
                .contains("Received 404  when calling EHR Data Authority")
        )
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles patient FHIR ID`(testTenant: String) {
        addTenantData(testTenant)
        val id = "$testTenant-PractitionerFHIRID1"
        val patientId = "$testTenant-patientFHIRID3"
        val message = "Test message"
        val mutation =
            """mutation sendMessage (${'$'}message: MessageInput!, ${'$'}tenantId: String!) {sendMessage (message: ${'$'}message, tenantId: ${'$'}tenantId)}"""

        val query = """
            |{
            |   "variables": {
            |      "message": {
            |          "patient": {
            |              "patientFhirId": "$patientId"
            |          },
            |         "recipients": {
            |             "fhirId": "$id"
            |         },
            |         "text": "$message"
            |      },
            |      "tenantId": "$testTenant"
            |   },
            |   "query": "$mutation"
            |}
        """.trimMargin()
        val expectedJSON = """{"data":{"sendMessage":"sent"}}"""

        val response = ProxyClient.query(query, testTenant, getBaseHeaders(testTenant))

        val body = runBlocking { response.body<String>() }

        val resultJSONObject = objectMapper.readTree(body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject, resultJSONObject)
    }
}
