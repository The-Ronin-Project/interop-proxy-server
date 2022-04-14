package com.projectronin.interop.proxy.server

import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.BaseAidboxTest
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@AidboxData("aidbox/practitioners.yaml")
class InteropProxyServerIntegratedAppointmentTests : BaseAidboxTest() {
    companion object {
        // allows us to dynamically change the aidbox port to the testcontainer instance
        @JvmStatic
        @DynamicPropertySource
        fun aidboxUrlProperties(registry: DynamicPropertyRegistry) {
            registry.add("aidbox.url") { aidbox.baseUrl() }
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
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `server handles appointment query`() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
        val startDate = today.minusMonths(4).format(formatter)
        val endDate = today.plusMonths(1).format(formatter)
        val query = this::class.java.getResource("/graphql/epicAOTestAppointment.graphql")!!.readText()
            .replace("__START_DATE__", startDate).replace("__END_DATE__", endDate)

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

                // This is the only practitioner loaded in Aidbox, the rest should have nulls for id and reference
                if (actor["display"].asText() == "Physician Family Medicine, MD") {
                    assertEquals("fhirId1", actor["id"].textValue())
                    assertEquals("Provider/fhirId1", actor["reference"].textValue())
                } else {
                    assertTrue(actor["id"].isNull)
                    assertTrue(actor["reference"].isNull)
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
    fun `server handles bad dates`() {
        val tenantId = "apposnd"
        val startDate = "12-01-2021"
        val endDate = "01-01-2020" // endDate before startDate
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
