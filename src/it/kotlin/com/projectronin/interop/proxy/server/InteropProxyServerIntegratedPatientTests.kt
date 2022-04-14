package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.net.URI
import javax.sql.DataSource

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InteropProxyServerIntegratedPatientTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var mockEHR: MockEHRTestcontainer

    @MockkBean
    private lateinit var m2mJwtDecoder: JwtDecoder

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ehrDatasource: DataSource

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @BeforeAll
    internal fun beforeClass() {
        // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
        val connection = ehrDatasource.connection
        val statement = connection.createStatement()
        statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1001;")

        // insert testing patient to MockEHR
        val createPat = this::class.java.getResource("/mockEHR/r4Patient.json")!!.readText()
        mockEHR.addR4Resource("Patient", createPat, "eJzlzKe3KPzAV5TtkxmNivQ3")
    }

    /**
     * This test is only here to help with debugging.  If the private key isn't set other tests will fail, but this
     * will quickly let us know why.
     */
    @Test
    fun `check private key is set correctly`() {
        val connection = ehrDatasource.connection

        val resultSet =
            connection.createStatement().executeQuery("select private_key from io_ehr where io_ehr_id = 101;")
        resultSet.next()

        assertEquals(System.getenv("AO_SANDBOX_KEY"), resultSet.getString("private_key"))
    }

    @Test
    fun `server handles patient query`() {
        val query = this::class.java.getResource("/graphql/epicAOTestPatient.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/epicAOTestPatientGraphQLResults.json")!!.readText()

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONObject.toString())
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
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
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
        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(resultJSONObject.has("errors"))
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

        val resultJSONObject = objectMapper.readTree(response.body)
        val patientSearchJSONArray = resultJSONObject["data"]["patientsByNameAndDOB"]

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, patientSearchJSONArray.size())
    }

    @Test
    fun `server handles patient query with m2m auth`() {
        val query = this::class.java.getResource("/graphql/epicAOTestPatient.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/epicAOTestPatientGraphQLResults.json")!!.readText()

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val httpEntity = HttpEntity(query, m2mHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONObject.toString())
    }

    @Test
    fun `server handles invalid m2m auth`() {
        val query = this::class.java.getResource("/graphql/epicAOTestPatient.graphql")!!.readText()

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder()
            .parsedBase64URL(Base64URL.from("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ii1uRW9uWlZYMGsxbFZZN0VSYjV1diJ9"))
            .build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } throws (JwtException("no auth"))

        val httpEntity = HttpEntity(query, m2mHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, responseEntity.statusCode)
    }
}
