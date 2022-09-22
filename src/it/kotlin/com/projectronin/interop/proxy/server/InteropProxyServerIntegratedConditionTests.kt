package com.projectronin.interop.proxy.server

import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
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
import java.net.URI
import javax.sql.DataSource

private var setupDone = false

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
class InteropProxyServerIntegratedConditionTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var mockEHR: MockEHRTestcontainer

    @Autowired
    private lateinit var ehrDatasource: DataSource

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
    fun beforeEach() {
        if (!setupDone) { // if you hate this, you are not alone. blame kotlin, junit5, or spring (not me though)

            // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
            val connection = ehrDatasource.connection
            val statement = connection.createStatement()
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1002;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1002;")
            // insert testing patient to MockEHR
            val createPat = this::class.java.getResource("/mockEHR/r4Patient.json")!!.readText()
            mockEHR.addR4Resource("Patient", createPat, "PatientFHIRID1")
            val createCondition = this::class.java.getResource("/mockEHR/r4Condition.json")!!.readText()
            mockEHR.addR4Resource("Condition", createCondition, "ConditionFHIRID1")
            setupDone = true
        }
    }

    @Test
    fun `server handles condition query`() {
        val query = this::class.java.getResource("/graphql/conditionsByPatient.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/epicAOTestConditionGraphQLResults.json")!!.readText()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONObject.toString())
    }

    @Test
    fun `server handles bad tenant`() {
        val tenantId = "fake"
        val patientFhirId = "eovSKnwDlsv-8MsEzCJO3BA3"
        val conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$tenantId", 
                patientFhirId:"$patientFhirId", 
                conditionCategoryCode:$conditionCategoryCode)
               {id}
            }
        """.trimIndent()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
        assertEquals(
            "Exception while fetching data (/conditionsByPatientAndCategory) : 403 Requested Tenant 'fake' does not match authorized Tenant 'ronin'",
            resultJSONObject["errors"][0]["message"].asText()
        )
    }

    @Test
    fun `server handles missing field`() {
        val tenantId = "ronin"
        val patientFhirId = "eovSKnwDlsv-8MsEzCJO3BA3"

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$tenantId", 
                patientFhirId:"$patientFhirId"
               {id}
            }
        """.trimIndent()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
    }

    @Test
    fun `server handles no conditions found`() {
        val tenantId = "ronin"
        val patientFhirId = "e9Bi2yhKnvFU8rsjpJpPMCw3" // Test patient with no conditions
        val conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$tenantId", 
                patientFhirId:"$patientFhirId", 
                conditionCategoryCode:$conditionCategoryCode)
               {id}
            }
        """.trimIndent()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val conditionSearchJSONArray = resultJSONObject["data"]["conditionsByPatientAndCategory"]

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, conditionSearchJSONArray.size())
    }
}
