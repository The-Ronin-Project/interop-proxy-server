package com.projectronin.interop.proxy.server

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
@AidboxData("aidbox/practitioners.yaml")
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
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1001;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1001;")

            // insert testing data to MockEHR
            val createAppt = this::class.java.getResource("/mockEHR/r4Appointment.json")!!.readText()
            mockEHR.addR4Resource("Appointment", createAppt, "06d7feb3-3326-4276-9535-83a622d8e216")
            val createPat = this::class.java.getResource("/mockEHR/r4Patient.json")!!.readText()
            mockEHR.addR4Resource("Patient", createPat, "eJzlzKe3KPzAV5TtkxmNivQ3")
            setupDone = true
        }
    }

    @Test
    fun `server handles appointment query`() {
        val query = this::class.java.getResource("/graphql/epicAOTestAppointment.graphql")!!.readText()
            .replace("__START_DATE__", "01-01-2021").replace("__END_DATE__", "01-01-2023")

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONNode = objectMapper.readTree(responseEntity.body)
        val appointmentsJSONNode = resultJSONNode["data"]["appointmentsByMRNAndDate"]

        /*
         * Appointments in the sandbox keep changing, so instead of checking for a specific response we're checking
         * that appointments were returned with participants, and there were no errors.
         */
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONNode.has("errors"))
        assertTrue(appointmentsJSONNode.size() > 0)

        // Check participants on each appointment
        appointmentsJSONNode.forEach { appointment ->
            val participants = appointment["participants"]
            assertTrue(participants.size() > 0)
            participants.forEach { participant ->
                val actor = participant["actor"]
                assertEquals("Practitioner/apposnd-fhirId1", actor["reference"].textValue())
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
            "Exception while fetching data (/appointmentsByMRNAndDate) : 403 Requested Tenant 'fake' does not match authorized Tenant 'apposnd'",
            errorJSONObject["message"].asText()
        )
    }

    @Test
    fun `server handles bad mrn`() {
        val tenantId = "apposnd"
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
        val tenantId = "apposnd"
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
        val tenantId = "apposnd"
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
