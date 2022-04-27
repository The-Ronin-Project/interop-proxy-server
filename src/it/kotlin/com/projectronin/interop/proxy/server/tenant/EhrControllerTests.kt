package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.InteropProxyServerAuthInitializer
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.net.URI
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
class EhrControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ehrDatasource: DataSource

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `get`() {
        val httpEntity = HttpEntity("", httpHeaders)
        val responseEntity =
            restTemplate.exchange("http://localhost:$port/ehrs", HttpMethod.GET, httpEntity, Array<Ehr>::class.java)
        val responseObject = responseEntity.body
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(responseObject!!.isNotEmpty())
    }

    @Test
    fun `get if db is empty`() {
        emptyDb()
        val httpEntity = HttpEntity("", httpHeaders)
        val responseEntity =
            restTemplate.exchange("http://localhost:$port/ehrs", HttpMethod.GET, httpEntity, Array<Ehr>::class.java)
        val responseObject = responseEntity.body
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(responseObject!!.isEmpty())

        populateDb()
    }

    @Test
    fun `post`() {
        emptyDb()
        val query = """
            {
                "vendorType": "EPIC", 
                "clientId": "clientID",
                "publicKey": "public",
                "privateKey": "private"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(
                URI("http://localhost:$port/ehrs"),
                httpEntity,
                Ehr::class.java
            )
        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertTrue(responseEntity.body!!.clientId == "clientID")

        emptyDb()
        populateDb()
    }

    @Test
    fun `post fails`() {
        val query = """
            {
                "vendorType": "EPIC", 
                "clientId": "clientID",
                "publicKey": "public",
                "privateKey": "private"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(
                URI("http://localhost:$port/ehrs"),
                httpEntity,
                String::class.java
            )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.statusCode)
    }

    @Test
    fun `put`() {
        val query = """
            {
                "vendorType": "EPIC", 
                "clientId": "UpdatedClientID",
                "publicKey": "public",
                "privateKey": "private"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.exchange(
                "http://localhost:$port/ehrs",
                HttpMethod.PUT,
                httpEntity,
                Ehr::class.java
            )
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(responseEntity.body!!.clientId == "UpdatedClientID")

        emptyDb()
        populateDb()
    }

    @Test
    fun `put fails`() {
        emptyDb()

        val query = """
            {
                "vendorType": "EPIC", 
                "clientId": "UpdatedClientID",
                "publicKey": "public",
                "privateKey": "private"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.exchange(
                "http://localhost:$port/ehrs",
                HttpMethod.PUT,
                httpEntity,
                String::class.java
            )
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)

        populateDb()
    }

    private fun emptyDb() {
        val connection = ehrDatasource.connection
        val query1 = """delete from io_tenant_epic where io_tenant_id = 1001"""
        val query2 = """delete from io_tenant_provider_pool where io_tenant_provider_pool_id = 10001"""
        val query3 = """delete from io_tenant where io_tenant_id = 1001"""
        val query4 = """delete from io_ehr where public_key = "public""""
        val sql = listOf(query1, query2, query3, query4)

        val sqlStatement = connection.createStatement()
        for (i in sql.indices) {
            sqlStatement.addBatch(sql[i])
        }
        sqlStatement.executeBatch()
    }

    private fun populateDb() {
        val connection = ehrDatasource.connection
        val AOSandboxKey = System.getenv("AO_SANDBOX_KEY")
        val query1 =
            """insert into io_ehr values (101, 'EPIC', 'a3da9a08-4fd4-443b-b0f5-6226547a98db', 'public', '$AOSandboxKey') """
        val query2 = """insert into io_tenant values (1001, 'apposnd', 101, '22:00:00', '06:00:00')"""
        val query3 = """insert into io_tenant_provider_pool values (10001, 1001, 'ProviderWithPool', '14600')"""
        val query4 =
            """insert into io_tenant_epic values (1001, '1.0', 'https://apporchard.epic.com/interconnect-aocurprd-oauth', '1', '1', 'urn:oid:1.2.840.114350.1.13.0.1.7.2.836982', 'urn:oid:1.2.840.114350.1.13.0.1.7.2.697780', 'urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14', NULL, 'https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token')"""

        val sql = listOf(query1, query2, query3, query4)
        val sqlStatement = connection.createStatement()
        for (i in sql.indices) {
            sqlStatement.addBatch(sql[i])
        }
        sqlStatement.executeBatch()
    }
}
