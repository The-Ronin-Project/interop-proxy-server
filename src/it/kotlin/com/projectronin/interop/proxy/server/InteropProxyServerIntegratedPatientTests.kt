package com.projectronin.interop.proxy.server

import com.nimbusds.jose.PlainHeader
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import java.net.URI

class InteropProxyServerIntegratedPatientTests : InteropProxyServerIntegratedTestsBase() {

    override val resourcesToAdd = listOf(
        ResourceToAdd("Patient", "/mockEHR/r4Patient1.json", "PatientFHIRID1")
    )

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

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles patient query`(testTenant: String) {

        val query = this::class.java.getResource("/graphql/patientByNameAndDOB.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/roninTestPatientGraphQLResults.json")!!.readText()
        val responseEntity = multiVendorQuery(query, testTenant)
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
        val tenantId = "ronin"
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

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles no patient found`(testTenant: String) {
        val tenantId = "ronin"
        val family = "Fake Name"
        val given = "Fake Name"
        val birthdate = "1900-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$tenantId", family: "$family", given: "$given", birthdate: "$birthdate") 
            |   {id}
            |}""".trimMargin()

        val response = multiVendorQuery(query, testTenant)

        val resultJSONObject = objectMapper.readTree(response.body)
        val patientSearchJSONArray = resultJSONObject["data"]["patientsByNameAndDOB"]

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(0, patientSearchJSONArray.size())
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles patient query with m2m auth`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/patientByNameAndDOB.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/roninTestPatientGraphQLResults.json")!!.readText()

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val responseEntity = multiVendorQuery(query, testTenant, m2mHeaders)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONObject.toString())
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `server handles invalid m2m auth by falling back to user auth`(testTenant: String) {
        val query = this::class.java.getResource("/graphql/patientByNameAndDOB.graphql")!!.readText()
        val expectedJSON = this::class.java.getResource("/roninTestPatientGraphQLResults.json")!!.readText()

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder()
            .parsedBase64URL(Base64URL.from("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ii1uRW9uWlZYMGsxbFZZN0VSYjV1diJ9"))
            .build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } throws (JwtException("no auth"))

        val responseEntity = multiVendorQuery(query, testTenant, m2mHeaders)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertFalse(resultJSONObject.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONObject.toString())
    }

    @Test
    fun `patientsByTenants can support searching multiple tenants`() {
        val query = this::class.java.getResource("/graphql/patientsByTenants.graphql")!!.readText()

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val roninTenant = resultJSONObject["data"]["patientsByTenants"]
        assertEquals(2, roninTenant.size())
    }

    @Test
    fun `patientsByTenants handles unknown tenant and known tenant together`() {
        val query =
            this::class.java.getResource("/graphql/patientsByTenants.graphql")!!.readText().replace("cerner", "unknown")

        val m2mHeaders = HttpHeaders()

        val header = PlainHeader.Builder().contentType("JWT").build()
        val payload = JWTClaimsSet.Builder().issuer("https://dev-euweyz5a.us.auth0.com/").audience("proxy").build()
        val jwtM2M = PlainJWT(header, payload).serialize()

        m2mHeaders.set("Content-Type", "application/graphql")
        m2mHeaders.set("Authorization", "Bearer $jwtM2M")

        every { m2mJwtDecoder.decode(jwtM2M) } returns (mockk<Jwt>())

        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)

        val resultJSONObject = objectMapper.readTree(responseEntity.body)

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        assertEquals("404 Invalid Tenant: unknown", resultJSONObject["errors"][0]["message"].asText())

        val roninTenant = resultJSONObject["data"]["patientsByTenants"][0]
        assertEquals(1, roninTenant["patients"].size())
    }
}
