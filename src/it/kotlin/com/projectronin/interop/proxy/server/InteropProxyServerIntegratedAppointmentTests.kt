package com.projectronin.interop.proxy.server

import com.fasterxml.jackson.databind.JsonNode
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
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
@AidboxData("aidbox/practitioners.yaml", "aidbox/patient2.yaml", "aidbox/location1.yaml")
@AidboxTest
class InteropProxyServerIntegratedAppointmentTests {
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

    @Autowired
    private lateinit var mockEHR: MockEHRTestcontainer

    @Autowired
    private lateinit var ehrDatasource: DataSource

    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockkBean
    private lateinit var m2mJwtDecoder: JwtDecoder

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @BeforeEach
    fun setup() {
        if (!setupDone) {
            // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
            val connection = ehrDatasource.connection
            val statement = connection.createStatement()
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1002;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1002;")

            // insert testing data to MockEHR
            mockEHR.addR4Resource(
                "Location",
                this::class.java.getResource("/mockEHR/r4Location.json")!!.readText(),
                "LocationFHIRID1"
            )
            mockEHR.addR4Resource(
                "Appointment",
                this::class.java.getResource("/mockEHR/r4Appointment1.json")!!.readText(),
                "AppointmentFHIRID1"
            )
            mockEHR.addR4Resource(
                "Appointment",
                this::class.java.getResource("/mockEHR/r4Appointment2.json")!!.readText(),
                "AppointmentFHIRID2"
            )
            mockEHR.addR4Resource(
                "Appointment",
                this::class.java.getResource("/mockEHR/r4Appointment3.json")!!.readText(),
                "AppointmentFHIRID3"
            )
            mockEHR.addR4Resource(
                "Patient",
                this::class.java.getResource("/mockEHR/r4Patient.json")!!.readText(),
                "PatientFHIRID1"
            )
            mockEHR.addR4Resource(
                "Practitioner",
                this::class.java.getResource("/mockEHR/r4Practitioner.json")!!.readText(),
                "PractitionerFHIRID1"
            )
            setupDone = true
        }
    }

    private fun changeTimeZone(timezone: String) {
        val connection = ehrDatasource.connection
        val statement = connection.createStatement()
        statement.execute("update io_tenant set timezone = '$timezone' where io_tenant_id = 1002;")
    }

    @Test
    fun `server handles appointment by MRN query`() {
        val query = this::class.java.getResource("/graphql/appointmentsByMRN.graphql")!!.readText()
            .replace("__START_DATE__", "01-01-2022").replace("__END_DATE__", "02-02-2022")
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val resultJSONNode = objectMapper.readTree(responseEntity.body)
        val node = resultJSONNode["data"]["appointmentsByMRNAndDate"]
        validateAppointmentResponse(node)
    }

    @Test
    fun `server handles appointment by FHIR ID query`() {
        val query = this::class.java.getResource("/graphql/appointmentsByFHIR.graphql")!!.readText()
            .replace("__START_DATE__", "01-01-2022").replace("__END_DATE__", "02-02-2022")
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
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
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)

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
        changeTimeZone("America/Los_Angeles")
    }

    private fun validateAppointmentResponse(node: JsonNode) {
        assertFalse(node.has("errors"))
        assertEquals(2, node.size())

        node.forEach { appointment ->
            if (appointment["id"].asText().contains("AppointmentFHIRID1")) {
                // The time is represented in UTC, but the source is PT
                assertEquals("2022-01-01T17:00:00Z", appointment["start"].asText())
                assertEquals(
                    "AppointmentFHIRID1",
                    appointment["identifier"].first { it["system"].asText() == "mockEncounterCSNSystem" }["value"].asText()
                )
                assertEquals("booked", appointment["status"].asText())
                val participants = appointment["participants"]
                assertEquals(2, participants.size())
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
                assertEquals("2022-01-01T18:00:00Z", appointment["start"].asText())

                val participants = appointment["participants"]
                assertEquals(2, participants.size())
                participants.forEach { participant ->
                    val actor = participant["actor"]
                    val type = actor["type"].asText()
                    if (type == "Practitioner") {
                        actor["identifier"]?.find { it["system"]?.asText() == "mockEHRProviderSystem" }?.let {
                            assertEquals("NO-INTERNAL-ID", it["value"].asText())
                        }
                    }
                }
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

    @Test
    fun `server handles no appointment found`() {
        val tenantId = "ronin"
        val startDate = "01-01-2001"
        val endDate = "12-01-2001"
        val mrn = "202497"

        val query = """
            |query {
            |   appointmentsByMRNAndDate(endDate: "$endDate", mrn: "$mrn", startDate: "$startDate", tenantId: "$tenantId")
            |   {id}
            |}""".trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val response =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(response.body)
        val appointmentSearchJSONArray = resultJSONObject["data"]["appointmentsByMRNAndDate"]

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, appointmentSearchJSONArray.size())
    }
}
