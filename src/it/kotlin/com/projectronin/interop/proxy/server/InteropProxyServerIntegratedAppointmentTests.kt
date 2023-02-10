package com.projectronin.interop.proxy.server

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.net.URI

@AidboxData("aidbox/practitioners.yaml", "aidbox/patient2.yaml", "aidbox/location1.yaml")
@AidboxTest
class InteropProxyServerIntegratedAppointmentTests : InteropProxyServerIntegratedTestsBase() {

    override val resourcesToAdd = listOf(
        ResourceToAdd(
            "Location",
            "/mockEHR/r4Location.json",
            "LocationFHIRID1"
        ),
        ResourceToAdd(
            "Appointment",
            "/mockEHR/r4Appointment1.json",
            "AppointmentFHIRID1"
        ),
        ResourceToAdd(
            "Appointment",
            "/mockEHR/r4Appointment2.json",
            "AppointmentFHIRID2"
        ),
        ResourceToAdd(
            "Appointment",
            "/mockEHR/r4Appointment3.json",
            "AppointmentFHIRID3"
        ),
        ResourceToAdd(
            "Patient",
            "/mockEHR/r4Patient1.json",
            "PatientFHIRID1"
        ),
        ResourceToAdd(
            "Practitioner",
            "/mockEHR/r4Practitioner.json",
            "PractitionerFHIRID1"
        )
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
            registry.add("aidbox.url") { aidbox.baseUrl() }
        }
    }

    private fun changeTimeZone(timezone: String) {
        val connection = ehrDatasource.connection
        val statement = connection.createStatement()
        statement.execute("update io_tenant set timezone = '$timezone' where io_tenant_id = 1002;")
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles appointment by MRN query`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/appointmentsByMRN.graphql")!!.readText()
            .replace("__START_DATE__", "01-01-2022").replace("__END_DATE__", "02-02-2022")
        val responseEntity = multiVendorQuery(query, testTenant)

        val resultJSONNode = objectMapper.readTree(responseEntity.body)
        val node = resultJSONNode["data"]["appointmentsByMRNAndDate"]
        validateAppointmentResponse(node)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles appointment by FHIR ID query`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/appointmentsByFHIR.graphql")!!.readText()
            .replace("__START_DATE__", "01-01-2022").replace("__END_DATE__", "02-02-2022")

        val responseEntity = multiVendorQuery(query, testTenant)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val resultJSONNode = objectMapper.readTree(responseEntity.body)
        val node = resultJSONNode["data"]["appointmentsByPatientAndDate"]
        validateAppointmentResponse(node)
    }

    @Test
    fun `server handles appointment with differing timezone`() {
        changeTimeZone("America/Chicago")

        val query = this::class.java.getResource("/graphql/appointmentsByFHIR.graphql")!!.readText()
            .replace("__START_DATE__", "01-01-2022").replace("__END_DATE__", "02-02-2022")

        val responseEntity =
            multiVendorQuery(query, "epic")

        val resultJSONNode = objectMapper.readTree(responseEntity.body)
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

    private fun validateAppointmentResponse(node: JsonNode) {
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
                        assertEquals("Patient/ronin-PatientFHIRID1", actor["reference"].asText())
                    }
                    if (type == "Practitioner") {
                        assertEquals("Practitioner/ronin-PractitionerFHIRID1", actor["reference"].asText())
                    }
                    if (type == "Location") {
                        assertEquals("Location/ronin-LocationFHIRID1", actor["reference"].asText())
                    }
                }
            }
            if (appointment["id"].asText().contains("AppointmentFHIRID2")) {
                assertEquals("2022-01-01T10:00:00Z", appointment["start"].asText())
            }
        }
    }

    @Test
    fun `server handles bad tenant`() {
        val tenantId = "fake"
        val startDate = "12-01-2021"
        val endDate = "01-01-2022"
        val mrn = "202497"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(endDate: "$endDate", mrn: "$mrn", startDate: "$startDate", tenantId: "$tenantId")
            |   {id}
            |}""".trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val errorJSONObject = resultJSONObject["errors"].get(0)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(
            "Exception while fetching data (/appointmentsByMRNAndDate) : 403 Requested Tenant 'fake' does not match authorized Tenant 'ronin'",
            errorJSONObject["message"].asText()
        )
    }

    @Test
    fun `server handles bad mrn`() {
        val tenantId = "ronin"
        val startDate = "12-01-2021"
        val endDate = "01-01-2022"
        val mrn = "FAKE_MRN"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(endDate: "$endDate", mrn: "$mrn", startDate: "$startDate", tenantId: "$tenantId")
            |   {id}
            |}""".trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @Test
    fun `server handles missing data`() {
        val tenantId = "ronin"
        val startDate = "12-01-2021"
        val mrn = "202497"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(mrn: "$mrn", startDate: "$startDate", tenantId: "$tenantId")
            |   {id}
            |}""".trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles no appointment found`(testTenant: String) {
        val tenantId = "ronin"
        val startDate = "01-01-2001"
        val endDate = "12-01-2001"
        val mrn = "202497"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(endDate: "$endDate", mrn: "$mrn", startDate: "$startDate", tenantId: "$tenantId")
            |   {id}
            |}""".trimMargin()

        val response = multiVendorQuery(query, testTenant)

        val resultJSONObject = objectMapper.readTree(response.body)
        val appointmentSearchJSONArray = resultJSONObject["data"]["appointmentsByMRNAndDate"]

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, appointmentSearchJSONArray.size())
    }
}
