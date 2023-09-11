package com.projectronin.interop.proxy.server.graphql

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Instant
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.client.AidboxClient
import com.projectronin.interop.proxy.server.client.MockEHRClient
import com.projectronin.interop.proxy.server.client.ProxyClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneId

class AppointmentIT : BaseGraphQLIT() {

    @AfterEach
    fun `delete all`() {
        MockEHRClient.deleteAllResources("Appointment")
        MockEHRClient.deleteAllResources("Patient")
        tenantMnemonics().forEach {
            AidboxClient.deleteAllResources("Patient", it)
        }
    }

    private fun addTenantData(testTenant: String) {
        val tenantIdentifier = identifier {
            value of testTenant
            system of "http://projectronin.com/id/tenantId"
        }

        val patient = patient {
            id of Id("PatientFHIRID1")
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
            gender of "male"
        }
        val patientFHirId = MockEHRClient.addResourceWithID(patient, "PatientFHIRID1")
        val aidboxPatient = patient.copy(
            id = Id("$testTenant-PatientFHIRID1"),
            identifier = patient.identifier +
                tenantIdentifier +
                fhirIdentifier(patientFHirId) +
                Identifier(
                    system = Uri("http://projectronin.com/id/mrn"),
                    value = "0202497".asFHIR()
                )
        )
        AidboxClient.addResource(aidboxPatient)

        val appointment1 = appointment {
            id of Id("AppointmentFHIRID1")
            status of "booked"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patientFHirId)
                }
            )
            minutesDuration of 8
            start of Instant("2022-01-01T09:00:00Z")
            end of Instant("2022-01-01T10:00:00Z")
        }

        val appointment2 = appointment {
            id of Id("AppointmentFHIRID2")
            status of "booked"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patientFHirId)
                }
            )
            minutesDuration of 8
            start of Instant("2022-01-01T10:00:00Z")
            end of Instant("2022-01-01T11:00:00Z")
        }

        val appointment3 = appointment {
            id of Id("AppointmentFHIRID3")
            status of "booked"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patientFHirId)
                }
            )
            minutesDuration of 8
            start of Instant("2023-01-01T09:00:00Z")
            end of Instant("2023-01-01T10:00:00Z")
        }
        MockEHRClient.addResourceWithID(appointment1, "AppointmentFHIRID1")
        MockEHRClient.addResourceWithID(appointment2, "AppointmentFHIRID2")
        MockEHRClient.addResourceWithID(appointment3, "AppointmentFHIRID3")
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server errors when appointment by MRN query is missing patient data`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/appointmentsByMRN.graphql")!!
            .readText()
            .replace("__START_DATE__", "01-01-2022")
            .replace("__END_DATE__", "02-02-2022")
            .replace("__tenant_mnemonic__", testTenant)
        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertTrue(resultJSONNode.has("errors"))
        assertTrue(resultJSONNode["errors"][0]["message"].toString().contains("No FHIR ID found for patient"))
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles appointment by MRN query`(testTenant: String) {
        addTenantData(testTenant)
        val query = this::class.java.getResource("/graphql/appointmentsByMRN.graphql")!!
            .readText()
            .replace("__START_DATE__", "01-01-2022")
            .replace("__END_DATE__", "02-02-2022")
            .replace("__tenant_mnemonic__", testTenant)
        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val node = resultJSONNode["data"]["appointmentsByMRNAndDate"]
        assertFalse(node.has("errors"))
        assertEquals(2, node.size())
        node.forEach { appointment ->
            if (appointment["id"].asText().contains("AppointmentFHIRID1")) {
                assertEquals("2022-01-01T09:00:00Z", appointment["start"].asText())
                assertEquals("booked", appointment["status"].asText())
                val participants = appointment["participants"]
                participants.forEach { participant ->
                    val actor = participant["actor"]
                    val type = actor["type"].asText()
                    if (type == "Patient") {
                        assertEquals("Patient/$testTenant-PatientFHIRID1", actor["reference"].asText())
                    }
                    // TODO need to add these resources in setup so this resolves
                    // if (type == "Practitioner") {
                    //     assertEquals("Practitioner/ronin-PractitionerFHIRID1", actor["reference"].asText())
                    // }
                    // if (type == "Location") {
                    //     assertEquals("Location/ronin-LocationFHIRID1", actor["reference"].asText())
                    // }
                }
            }
            if (appointment["id"].asText().contains("AppointmentFHIRID2")) {
                assertEquals("2022-01-01T10:00:00Z", appointment["start"].asText())
            }
        }
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles appointment by FHIR ID query`(testTenant: String) {
        addTenantData(testTenant)
        val query = this::class.java.getResource("/graphql/appointmentsByFHIR.graphql")!!
            .readText()
            .replace("__PATIENT_FHIR__", "PatientFHIRID1")
            .replace("__START_DATE__", "01-01-2022")
            .replace("__END_DATE__", "02-02-2022")
            .replace("__tenant_mnemonic__", testTenant)
        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val node = resultJSONNode["data"]["appointmentsByPatientAndDate"]
        assertFalse(node.has("errors"))
        assertEquals(2, node.size())
        node.forEach { appointment ->
            if (appointment["id"].asText().contains("AppointmentFHIRID1")) {
                assertEquals("2022-01-01T09:00:00Z", appointment["start"].asText())
                assertEquals("booked", appointment["status"].asText())
                val participants = appointment["participants"]
                participants.forEach { participant ->
                    val actor = participant["actor"]
                    val type = actor["type"].asText()
                    if (type == "Patient") {
                        assertEquals("Patient/$testTenant-PatientFHIRID1", actor["reference"].asText())
                    }
                    // TODO need to add these resources in setup so this resolves
                    // if (type == "Practitioner") {
                    //     assertEquals("Practitioner/ronin-PractitionerFHIRID1", actor["reference"].asText())
                    // }
                    // if (type == "Location") {
                    //     assertEquals("Location/ronin-LocationFHIRID1", actor["reference"].asText())
                    // }
                }
            }
            if (appointment["id"].asText().contains("AppointmentFHIRID2")) {
                assertEquals("2022-01-01T10:00:00Z", appointment["start"].asText())
            }
        }
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles appointment by Ronin FHIR ID query`(testTenant: String) {
        addTenantData(testTenant)
        val query = this::class.java.getResource("/graphql/appointmentsByFHIR.graphql")!!
            .readText()
            .replace("__PATIENT_FHIR__", "$testTenant-PatientFHIRID1")
            .replace("__START_DATE__", "01-01-2022")
            .replace("__END_DATE__", "02-02-2022")
            .replace("__tenant_mnemonic__", testTenant)
        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val node = resultJSONNode["data"]["appointmentsByPatientAndDate"]
        assertFalse(node.has("errors"))
        assertEquals(2, node.size())
        node.forEach { appointment ->
            if (appointment["id"].asText().contains("AppointmentFHIRID1")) {
                assertEquals("2022-01-01T09:00:00Z", appointment["start"].asText())
                assertEquals("booked", appointment["status"].asText())
                val participants = appointment["participants"]
                participants.forEach { participant ->
                    val actor = participant["actor"]
                    val type = actor["type"].asText()
                    if (type == "Patient") {
                        assertEquals("Patient/$testTenant-PatientFHIRID1", actor["reference"].asText())
                    }
                    // TODO need to add these resources in setup so this resolves
                    // if (type == "Practitioner") {
                    //     assertEquals("Practitioner/ronin-PractitionerFHIRID1", actor["reference"].asText())
                    // }
                    // if (type == "Location") {
                    //     assertEquals("Location/ronin-LocationFHIRID1", actor["reference"].asText())
                    // }
                }
            }
            if (appointment["id"].asText().contains("AppointmentFHIRID2")) {
                assertEquals("2022-01-01T10:00:00Z", appointment["start"].asText())
            }
        }
    }

    @Test
    fun `server handles appointment with differing timezone`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        changeTimeZone("America/Chicago")

        val query = this::class.java.getResource("/graphql/appointmentsByFHIR.graphql")!!
            .readText()
            .replace("__PATIENT_FHIR__", "PatientFHIRID1")
            .replace("__START_DATE__", "01-01-2022")
            .replace("__END_DATE__", "02-02-2022")
            .replace("__tenant_mnemonic__", testTenant)
        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val node = resultJSONNode["data"]["appointmentsByPatientAndDate"]
        assertFalse(node.has("errors"))
        assertEquals(2, node.size())
        node.forEach { appointment ->
            if (appointment["id"].asText().contains("AppointmentFHIRID1")) {
                // The time is represented in UTC, but the source is CT
                assertEquals("2022-01-01T15:00:00Z", appointment["start"].asText())
            } else if (appointment["id"].asText().contains("AppointmentFHIRID2")) {
                assertEquals("2022-01-01T16:00:00Z", appointment["start"].asText())
            }
        }
        // reset the Timezone
        changeTimeZone("Etc/UTC")
    }

    private fun changeTimeZone(timezone: String) {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        tenantDO.timezone = ZoneId.of(timezone)
        tenantDAO.updateTenant(tenantDO)
    }

    @Test
    fun `server handles missing data`() {
        val testTenant = "epic"
        addTenantData(testTenant)

        val startDate = "12-01-2021"
        val mrn = "0202497"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(mrn: "$mrn", startDate: "$startDate", tenantId: "$testTenant")
            |   {id}
            |}
        """.trimMargin()

        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONNode.has("errors"))
    }

    @Test
    fun `server handles no appointment found`() {
        val testTenant = "epic"
        addTenantData(testTenant)
        val startDate = "01-01-2001"
        val endDate = "12-01-2001"
        val mrn = "0202497"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(endDate: "$endDate", mrn: "$mrn", startDate: "$startDate", tenantId: "$testTenant")
            |   {id}
            |}
        """.trimMargin()

        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val appointmentSearchJSONArray = resultJSONNode["data"]["appointmentsByMRNAndDate"]

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(0, appointmentSearchJSONArray.size())
    }
}
