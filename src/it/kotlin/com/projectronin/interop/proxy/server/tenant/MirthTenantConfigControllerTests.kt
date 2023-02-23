package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.InteropProxyServerAuthInitializer
import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
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
import org.springframework.boot.web.server.LocalServerPort
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
class MirthTenantConfigControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ehrDatasource: DataSource

    private val httpHeaders = HttpHeaders()
    private val modifiedTables = listOf("io_mirth_tenant_config")

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
            "http://localhost:$port/tenants/$tenantMnemonic/mirth-config/",
            HttpMethod.GET,
            httpEntity,
            MirthTenantConfig::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val mirthConfigs = responseEntity.body!!
        assertEquals(listOf("1", "2", "3"), mirthConfigs.locationIds)
        emptyDb()
    }

    @Test
    fun `get single fails`() {
        populateDb()
        val tenantMnemonic = "notreal"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/mirth-config/",
            HttpMethod.GET,
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)
        emptyDb()
    }

    @Test
    fun `insert works`() {
        val tenantMnemonic = "apposnd"
        val insertMirthConfig = MirthTenantConfig(
            locationIds = listOf("inserted", "inserted2")
        )
        val httpEntity = HttpEntity(insertMirthConfig, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/mirth-config/",
            HttpMethod.POST,
            httpEntity,
            MirthTenantConfig::class.java
        )

        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertEquals(insertMirthConfig.locationIds, responseEntity.body?.locationIds)
        emptyDb()
    }

    @Test
    fun `insert dup fails`() {
        populateDb()
        val tenantMnemonic = "apposnd"

        val insertMirthConfig = MirthTenantConfig(
            locationIds = listOf("updated")
        )
        val httpEntity = HttpEntity(insertMirthConfig, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/mirth-config/",
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

        val updated = MirthTenantConfig(
            locationIds = listOf("updated", "updated2")
        )
        val httpEntity = HttpEntity(updated, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/mirth-config/",
            HttpMethod.PUT,
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        emptyDb()
    }

    @Test
    fun `update fails`() {
        populateDb()
        val tenantMnemonic = "faaake"

        val updated = MirthTenantConfig(
            locationIds = listOf("updated")
        )
        val httpEntity = HttpEntity(updated, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/mirth-config/",
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
            """insert into io_mirth_tenant_config values (1001, '1,2,3', null)"""
        val sqlStatement = connection.createStatement()
        sqlStatement.addBatch(query)
        sqlStatement.executeBatch()
        connection.close()
    }

    private fun emptyDb() {
        val connection = ehrDatasource.connection
        val query = """delete from io_mirth_tenant_config where io_tenant_id = 1001"""
        val sqlStatement = connection.createStatement()
        sqlStatement.addBatch(query)
        sqlStatement.executeBatch()
        connection.close()
    }
}
