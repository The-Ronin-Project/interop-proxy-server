package com.projectronin.interop.proxy.server

import com.projectronin.interop.proxy.server.model.Epic
import com.projectronin.interop.proxy.server.model.Tenant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
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
import java.time.LocalTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc") // prevent Exception in AuthService.kt
class TenantControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val httpHeaders = HttpHeaders()

    // Dummy values the TenantController is currently returning.  Will need to change once it's implemented.
    private val vendor = Epic(
        release = "release",
        serviceEndpoint = "serviceEndpoint",
        ehrUserId = "ehrUserId",
        messageType = "messageType",
        practitionerProviderSystem = "practitionerProviderSystem",
        practitionerUserSystem = "practitionerUserSystem",
        hsi = "hsi"
    )

    private val tenant1 = Tenant(
        id = 1,
        mnemonic = "mnemonic1",
        availableStart = LocalTime.of(22, 0),
        availableEnd = LocalTime.of(23, 0),
        vendor = vendor
    )

    private val tenant2 = Tenant(
        id = 2,
        mnemonic = "mnemonic2",
        availableStart = LocalTime.of(22, 0),
        availableEnd = LocalTime.of(23, 0),
        vendor = vendor
    )

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `can read all tenants`() {
        // TODO once controller implemented
        val httpEntity = HttpEntity("", httpHeaders)

        val responseEntity =
            restTemplate.exchange("http://localhost:$port/tenants", HttpMethod.GET, httpEntity, Array<Tenant>::class.java)
        val tenantArray = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(2, tenantArray.size)
        assertEquals(tenant1, tenantArray[0])
        assertEquals(tenant2, tenantArray[1])
    }

    @Test
    fun `can read a specific tenant by mnemonic`() {
        // TODO once controller implemented
        val httpEntity = HttpEntity("", httpHeaders)

        val responseEntity =
            restTemplate.exchange("http://localhost:$port/tenants/testTenant", HttpMethod.GET, httpEntity, Tenant::class.java)
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(tenant1, tenant)
    }

    @Test
    fun `can insert a tenant`() {
        // TODO once controller implemented
        val httpEntity = HttpEntity(tenant1, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/tenants"), httpEntity, Tenant::class.java)
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(tenant1, tenant)
    }

    @Test
    fun `can update a tenant`() {
        // TODO once controller implemented
        val httpEntity = HttpEntity(tenant1, httpHeaders)

        val responseEntity =
            restTemplate.exchange("http://localhost:$port/tenants/testTenant", HttpMethod.PUT, httpEntity, Int::class.java)
        val rowsUpdated = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(1, rowsUpdated)
    }
}
