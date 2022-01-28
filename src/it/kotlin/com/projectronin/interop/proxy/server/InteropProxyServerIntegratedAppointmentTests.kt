package com.projectronin.interop.proxy.server

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.net.URI
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc") // prevent Exception in AuthService.kt
class InteropProxyServerIntegratedAppointmentTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `server handles appointment query`() {
        val today = LocalDate.now()
        val startDate = "${today.monthValue}-1-${today.year}"
        val endDate =
            "${if (today.monthValue == 12) 1 else today.monthValue + 1}-1-${if (today.monthValue == 12) today.year + 1 else today.year}"
        val query = this::class.java.getResource("/graphql/epicAOTestAppointment.graphql")!!.readText()
            .replace("__START_DATE__", startDate).replace("__END_DATE__", endDate)

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        val appointmentsJSONObject =
            (resultJSONObject["data"] as JsonObject)["appointmentsByMRNAndDate"] as JsonArray<*>

        /**
         * Appointments in the sandbox keep changing, so instead of checking for a specific response we're checking
         * that appointments were returned and there were no errors.
         */
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertTrue(appointmentsJSONObject.size > 0)
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
        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        val errorJSONObject = (resultJSONObject["errors"] as JsonArray<*>)[0] as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals("Exception while fetching data (/appointmentsByMRNAndDate) : Requested Tenant 'fake' does not match authorized Tenant 'apposnd'", errorJSONObject["message"])
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
        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.map.containsKey("errors"))
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
        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.map.containsKey("errors"))
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
        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.map.containsKey("errors"))
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

        val resultJSONObject = Parser.default().parse(StringBuilder(response.body)) as JsonObject
        val dataJSONObject = (resultJSONObject["data"] as JsonObject)
        val appointmentSearchJSONArray = dataJSONObject["appointmentsByMRNAndDate"] as JsonArray<*>

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertEquals(0, appointmentSearchJSONArray.size)
    }
}
