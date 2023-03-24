package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.InteropProxyServerAuthInitializer
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.proxy.server.test.util.backupTables
import com.projectronin.interop.proxy.server.test.util.removeBackupTables
import com.projectronin.interop.proxy.server.test.util.restoreTables
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantServerControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ehrDatasource: DataSource
    private val httpHeaders = HttpHeaders()
    private val modifiedTables = listOf("io_tenant_server")

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @BeforeAll
    fun initTest() {
        backupTables(ehrDatasource, modifiedTables)
    }

    @AfterEach
    fun postTest() {
        restoreTables(ehrDatasource, modifiedTables)
    }

    @AfterAll
    fun cleanUpTests() {
        removeBackupTables(ehrDatasource, modifiedTables)
    }

    @Test
    fun `get works`() {
        populateDb()
        val tenantMnemonic = "apposnd"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server",
            HttpMethod.GET,
            httpEntity,
            Array<TenantServer>::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val tenantServer = responseEntity.body!!
        assertEquals("google.com", tenantServer.first().address)
        emptyDb()
    }

    @Test
    fun `get works with type`() {
        populateDb()
        val tenantMnemonic = "apposnd"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server/MDM",
            HttpMethod.GET,
            httpEntity,
            TenantServer::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val tenantServer = responseEntity.body!!
        assertEquals("google.com", tenantServer.address)
        emptyDb()
    }

    @Test
    fun `get works with type fails for bad tenant`() {
        populateDb()
        val tenantMnemonic = "not real"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server/MDM",
            HttpMethod.GET,
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)

        emptyDb()
    }

    @Test
    fun `get works with type fails for bad type`() {
        populateDb()
        val tenantMnemonic = "apposnd"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server/AAA",
            HttpMethod.GET,
            httpEntity,
            String::class.java
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.statusCode)
        emptyDb()
    }

    @Test
    fun `insert works`() {
        val tenantMnemonic = "apposnd"
        val tenantServer = TenantServer(
            messageType = "MDM",
            address = "google.com",
            port = 1010,
            serverType = "N"
        )
        val httpEntity = HttpEntity(tenantServer, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server",
            HttpMethod.POST,
            httpEntity,
            TenantServer::class.java
        )
        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertEquals(tenantServer.address, responseEntity.body?.address)
        emptyDb()
    }

    @Test
    fun `insert fails with duplicate`() {
        populateDb()
        val tenantMnemonic = "apposnd"
        val tenantServer = TenantServer(
            messageType = "MDM",
            address = "google.com",
            port = 1010,
            serverType = "N"
        )
        val httpEntity = HttpEntity(tenantServer, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server",
            HttpMethod.POST,
            httpEntity,
            String::class.java
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.statusCode)
        emptyDb()
    }

    @Test
    fun `update works`() {
        populateDb()
        val tenantMnemonic = "apposnd"
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "new!",
            port = 1010,
            serverType = "N"
        )
        val httpEntity = HttpEntity(tenantServer, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server",
            HttpMethod.PUT,
            httpEntity,
            TenantServer::class.java
        )
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(tenantServer.address, responseEntity.body?.address)
        emptyDb()
    }

    @Test
    fun `update fails when it's not already present`() {
        val tenantMnemonic = "apposnd"
        val tenantServer = TenantServer(
            messageType = "MDM",
            address = "new!",
            port = 1010,
            serverType = "N"
        )
        val httpEntity = HttpEntity(tenantServer, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/tenant-server",
            HttpMethod.PUT,
            httpEntity,
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)
        emptyDb()
    }

    private fun populateDb() {
        val connection = ehrDatasource.connection
        val query =
            """insert into io_tenant_server values (1, 1001, 'MDM', 'google.com', 80, 'TRAINING')"""
        val sqlStatement = connection.createStatement()
        sqlStatement.addBatch(query)
        sqlStatement.executeBatch()
        connection.close()
    }

    private fun emptyDb() {
        val connection = ehrDatasource.connection
        val query = """delete from io_tenant_server where io_tenant_id = 1001"""
        val sqlStatement = connection.createStatement()
        sqlStatement.addBatch(query)
        sqlStatement.executeBatch()
        connection.close()
    }
}
