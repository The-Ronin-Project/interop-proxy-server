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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.net.URI
import javax.sql.DataSource

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc") // prevent Exception in AuthService.kt
class InteropProxyServerIntegratedPatientTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set(
            "Authorization",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE2NDIxMDg1NjUsImlzcyI6IlNla2ki" +
                "LCJqdGkiOiIycjR2MjJpM2hhY2R1cGRyNG8wMHNiZjEiLCJzdWIiOiJkMGEyMDUyMC01MjAzLTQ3Yzkt" +
                "OTFhZS1kMzExZjgzMzllZmYiLCJ0ZW5hbnRpZCI6ImFwcF9vX3NuZCJ9.NtZEm3Zlfr-HmIFEtFQxOBp" +
                "w8PqY0wtczvKHzkxbl_Q"
        )
    }

    /**
     * This test is only here to help with debugging.  If the private key isn't set other tests will fail, but this
     * will quickly let us know why.
     */
    @Test
    fun `check private key is set correctly`() {
        val connection = dataSource.connection

        val resultSet =
            connection.createStatement().executeQuery("select private_key from io_ehr where io_ehr_id = 101;")
        resultSet.next()

        assertEquals(System.getenv("AO_SANDBOX_KEY"), resultSet.getString("private_key"))
    }

    @Test
    fun `server handles patient query`() {
        val query = this::class.java.getResource("/graphql/epicAOTestPatient.graphql")!!.readText()
        // val expectedJSON = this::class.java.getResource("/epicAOTestPatientGraphQLResults.json")!!.readText()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(responseEntity.body)) as JsonObject
        // val expectedJSONObject = Parser.default().parse(StringBuilder(expectedJSON)) as JsonObject
        // let's bring this back when we have a more stable test server

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertTrue(resultJSONObject.size > 0)
    }

    @Test
    fun `server handles bad tenant`() {
        val tenantId = "fake"
        val family = "Mychart"
        val given = "Allison"
        val birthdate = "1987-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$tenantId", family: "$family", given: "$given", birthdate: "$birthdate") 
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
        val given = "Allison"
        val birthdate = "1987-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$tenantId", given: "$given", birthdate: "$birthdate") 
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
    fun `server handles no patient found`() {
        val tenantId = "apposnd"
        val family = "Fake Name"
        val given = "Fake Name"
        val birthdate = "1900-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$tenantId", family: "$family", given: "$given", birthdate: "$birthdate") 
            |   {id}
            |}""".trimMargin()

        val httpEntity = HttpEntity(query, httpHeaders)

        val response = restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = Parser.default().parse(StringBuilder(response.body)) as JsonObject
        val dataJSONObject = (resultJSONObject["data"] as JsonObject)
        val patientSearchJSONArray = dataJSONObject["patientsByNameAndDOB"] as JsonArray<*>

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(resultJSONObject.map.containsKey("errors"))
        assertEquals(0, patientSearchJSONArray.size)
    }
}
