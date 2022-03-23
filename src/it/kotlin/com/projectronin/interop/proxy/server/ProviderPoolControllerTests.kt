package com.projectronin.interop.proxy.server

import com.projectronin.interop.proxy.server.model.ProviderPool
import org.junit.jupiter.api.Assertions
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@SetEnvironmentVariable(key = "SERVICE_CALL_JWT_SECRET", value = "abc") // prevent Exception in AuthService.kt
class ProviderPoolControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `get`() {
        // TODO
        val httpEntity = HttpEntity("", httpHeaders)
        val tenantId = 1
        val providerIDs = listOf("123", "456")
        val responseEntity =
            restTemplate.exchange("http://localhost:$port/tenants/$tenantId/pools/?providerIds=$providerIDs", HttpMethod.GET, httpEntity, Array<ProviderPool>::class.java)
        val responseObject = responseEntity.body
        Assertions.assertEquals(HttpStatus.OK, responseEntity.statusCode)
        if (responseObject != null) {
            Assertions.assertTrue(responseObject.isNotEmpty())
        } else {
            Assertions.fail("responseObject null, should not be null")
        }
    }
    @Test
    fun `post`() {
        // TODO
        val tenantId = 1
        val query = """
            {
                "providerPoolId": 12345,
                "providerId": "1234",
                "poolId": "6789"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/tenants/$tenantId/pools"), httpEntity, String::class.java)
        Assertions.assertEquals(HttpStatus.OK, responseEntity.statusCode)
        Assertions.assertTrue(responseEntity.body == "success, id: ?")
    }

    @Test
    fun `put`() {
        // TODO
        val tenantId = 1
        val providerPoolId = 2
        val query = """
            {
                "providerPoolId": 12345,
                "providerId": "1234",
                "poolId": "6789"
            }
        """.trimIndent()
        val httpEntity = HttpEntity(query, httpHeaders)
        val responseEntity =
            restTemplate.exchange("http://localhost:$port/tenants/$tenantId/pools/$providerPoolId", HttpMethod.PUT, httpEntity, String::class.java)
        Assertions.assertEquals(HttpStatus.OK, responseEntity.statusCode)
        Assertions.assertTrue(responseEntity.body == "success, row ? updated")
    }

    @Test
    fun `delete`() {
        // TODO
        val id = 1
        val tenantId = 2
        val httpEntity = HttpEntity("", httpHeaders)
        val responseEntity =
            restTemplate.exchange("http://localhost:$port/tenants/$tenantId/pools/$id", HttpMethod.DELETE, httpEntity, String::class.java)
        Assertions.assertEquals(HttpStatus.OK, responseEntity.statusCode)
        Assertions.assertTrue(responseEntity.body == "success, row ? deleted")
    }
}
