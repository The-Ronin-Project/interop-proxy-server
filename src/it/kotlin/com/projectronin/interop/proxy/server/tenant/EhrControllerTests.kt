package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.InteropProxyServerAuthInitializer
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import com.projectronin.interop.proxy.server.test.util.backupTables
import com.projectronin.interop.proxy.server.test.util.removeBackupTables
import com.projectronin.interop.proxy.server.test.util.restoreTables
import com.projectronin.interop.proxy.server.test.util.truncateTables
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.web.util.UriUtils
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

    @BeforeEach
    fun setup() {
        backupTables(ehrDatasource, listOf("io_tenant_epic", "io_tenant_cerner", "io_tenant_provider_pool", "io_tenant", "io_ehr"))
    }

    @AfterEach
    fun tearDown() {
        populateDb()
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
    }

    @Test
    fun `post ehr when none exists - epic`() {
        emptyDb()
        val query = """
            {
                "vendorType": "EPIC", 
                "instanceName": "Epic Sandbox",
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
    }

    @Test
    fun `post ehr when none exists - cerner`() {
        emptyDb()
        val query = """
            {
                "vendorType": "CERNER", 
                "instanceName": "Cerner Sandbox",
                "clientId": "clientID",
                "accountId": "accountId",
                "secret": "secret"
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
        (responseEntity.body!!.clientId == "clientID")

        emptyDb()
    }

    @Test
    fun `post ehr when none exists - cerner system auth`() {
        emptyDb()
        val query = """
            {
                "vendorType": "CERNER", 
                "instanceName": "Cerner Sandbox",
                "accountId": "accountId",
                "secret": "secret"
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
        (responseEntity.body!!.clientId == "clientID")

        emptyDb()
    }

    @Test
    fun `post fails when ehr exists`() {
        val query = """
            {
                "vendorType": "EPIC", 
                "instanceName": "Epic Sandbox",
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
    fun `post fails when missing needed values`() {
        emptyDb()
        val query = """
            {
                "vendorType": "CERNER", 
                "instanceName": "Epic Sandbox",
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
    fun `put updates existing ehr`() {
        val query = """
            {
                "vendorType": "EPIC",
                "instanceName": "Epic Sandbox",
                "clientId": "UpdatedClientID",
                "publicKey": "public",
                "privateKey": "private"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.exchange(
                "http://localhost:$port/ehrs/${UriUtils.encodePathSegment("Epic Sandbox", Charsets.UTF_8)}",
                HttpMethod.PUT,
                httpEntity,
                Ehr::class.java
            )
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(responseEntity.body!!.clientId == "UpdatedClientID")

        emptyDb()
    }

    @Test
    fun `put fails when ehr does not exist`() {
        emptyDb()

        val query = """
            {
                "vendorType": "EPIC", 
                "instanceName": "Epic Sandbox",
                "clientId": "UpdatedClientID",
                "publicKey": "public",
                "privateKey": "private"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.exchange(
                "http://localhost:$port/ehrs/${UriUtils.encodePathSegment("Epic Sandbox", Charsets.UTF_8)}",
                HttpMethod.PUT,
                httpEntity,
                String::class.java
            )
        println(responseEntity)
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)
    }

    private fun emptyDb() {
        truncateTables(ehrDatasource, listOf("io_tenant_epic", "io_tenant_cerner", "io_tenant_provider_pool", "io_tenant", "io_ehr"))
    }

    private fun populateDb() {
        restoreTables(ehrDatasource, listOf("io_tenant_epic", "io_tenant_cerner", "io_tenant_provider_pool", "io_tenant", "io_ehr"))
        removeBackups()
    }

    private fun removeBackups() {
        removeBackupTables(ehrDatasource, listOf("io_tenant_epic", "io_tenant_cerner", "io_tenant_provider_pool", "io_tenant", "io_ehr"))
    }
}
