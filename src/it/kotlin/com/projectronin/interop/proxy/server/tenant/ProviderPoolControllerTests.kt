package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.InteropProxyServerAuthInitializer
import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.proxy.server.test.util.backupTables
import com.projectronin.interop.proxy.server.test.util.removeBackupTables
import com.projectronin.interop.proxy.server.test.util.restoreTables
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.net.URI
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProviderPoolControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ehrDatasource: DataSource

    private val httpHeaders = HttpHeaders()
    private val modifiedTables = listOf("io_tenant_provider_pool")

    private val tenantMnemonic = "apposnd"

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
    fun `get provider pools`() {
        val providerIds = "ProviderWithPool"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools?providerIds=$providerIds",
            HttpMethod.GET,
            httpEntity,
            Array<ProviderPool>::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val providerPools = responseEntity.body!!
        assertEquals(1, providerPools.size)
        assertEquals(10001, providerPools[0].providerPoolId)
        assertEquals("ProviderWithPool", providerPools[0].providerId)
        assertEquals("14600", providerPools[0].poolId)
    }

    @Test
    fun `get all provider pools`() {
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools",
            HttpMethod.GET,
            httpEntity,
            Array<ProviderPool>::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val providerPools = responseEntity.body!!
        assertEquals(1, providerPools.size)
        assertEquals(10001, providerPools[0].providerPoolId)
        assertEquals("ProviderWithPool", providerPools[0].providerId)
        assertEquals("14600", providerPools[0].poolId)
    }

    @Test
    fun `get provider pools for bad provider`() {
        val providerIds = "FakeProvider"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools?providerIds=$providerIds",
            HttpMethod.GET,
            httpEntity,
            Array<ProviderPool>::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val providerPools = responseEntity.body!!
        assertEquals(0, providerPools.size)
    }

    @Test
    fun `get provider pools for good and bad providers`() {
        val providerIds = listOf("FakeProvider", "ProviderWithPool").joinToString(",")
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools?providerIds=$providerIds",
            HttpMethod.GET,
            httpEntity,
            Array<ProviderPool>::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val providerPools = responseEntity.body!!
        assertEquals(1, providerPools.size)
        assertEquals(10001, providerPools[0].providerPoolId)
        assertEquals("ProviderWithPool", providerPools[0].providerId)
        assertEquals("14600", providerPools[0].poolId)
    }

    @Test
    fun `insert provider pool`() {
        val insertProviderPool = ProviderPool(
            providerPoolId = 0,
            providerId = "NewProviderId",
            poolId = "NewPoolId"
        )
        val httpEntity = HttpEntity(insertProviderPool, httpHeaders)

        val responseEntity = restTemplate.postForEntity(
            URI("http://localhost:$port/tenants/$tenantMnemonic/pools"),
            httpEntity,
            ProviderPool::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val responseProviderPool = responseEntity.body!!
        assertEquals(insertProviderPool.providerId, responseProviderPool.providerId)
        assertEquals(insertProviderPool.poolId, responseProviderPool.poolId)
        assertTrue(responseProviderPool.providerPoolId > 0)
    }

    @Test
    fun `insert duplicate provider for tenant`() {
        val insertProviderPool = ProviderPool(
            providerPoolId = 0,
            providerId = "ProviderWithPool",
            poolId = "NewPoolId"
        )
        val httpEntity = HttpEntity(insertProviderPool, httpHeaders)

        val responseEntity = restTemplate.postForEntity(
            URI("http://localhost:$port/tenants/$tenantMnemonic/pools"),
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, responseEntity.statusCode)
    }

    @Test
    fun `update existing providers pool`() {
        val providerPoolId = 10001
        val updateProviderPool = ProviderPool(
            providerPoolId = providerPoolId,
            providerId = "NewProviderId",
            poolId = "NewPoolId"
        )
        val httpEntity = HttpEntity(updateProviderPool, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools/$providerPoolId",
            HttpMethod.PUT,
            httpEntity,
            ProviderPool::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val response = responseEntity.body!!
        assertEquals(updateProviderPool, response)
    }

    @Test
    fun `update non-existing providers pool`() {
        val providerPoolId = 54321
        val updateProviderPool = ProviderPool(
            providerPoolId = providerPoolId,
            providerId = "NewProviderId",
            poolId = "NewPoolId"
        )
        val httpEntity = HttpEntity(updateProviderPool, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools/$providerPoolId",
            HttpMethod.PUT,
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)
    }

    @Test
    fun `bad update request due to mismatched provider pool ids`() {
        val providerPoolId = 10001
        val updateProviderPool = ProviderPool(
            providerPoolId = 54321,
            providerId = "NewProviderId",
            poolId = "NewPoolId"
        )
        val httpEntity = HttpEntity(updateProviderPool, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools/$providerPoolId",
            HttpMethod.PUT,
            httpEntity,
            ProviderPool::class.java
        )

        val response = responseEntity.body!!
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(providerPoolId, response.providerPoolId)
    }

    @Test
    fun `delete existing provider pool`() {
        val providerPoolId = 10001
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools/$providerPoolId",
            HttpMethod.DELETE,
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
    }

    @Test
    fun `delete non-existing provider pool`() {
        val providerPoolId = 54321
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$tenantMnemonic/pools/$providerPoolId",
            HttpMethod.DELETE,
            httpEntity,
            String::class.java
        )

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
    }
}
