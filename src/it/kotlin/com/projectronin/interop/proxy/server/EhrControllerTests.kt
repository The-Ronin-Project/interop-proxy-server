package com.projectronin.interop.proxy.server

import com.projectronin.interop.proxy.server.model.Ehr
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
class EhrControllerTests {
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
        val responseEntity =
            restTemplate.exchange("http://localhost:$port/ehrs", HttpMethod.GET, httpEntity, Array<Ehr>::class.java)
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
            restTemplate.postForEntity(URI("http://localhost:$port/ehrs"), httpEntity, Ehr::class.java)
        Assertions.assertEquals(HttpStatus.OK, responseEntity.statusCode)
        Assertions.assertTrue(responseEntity.body!!.clientId == "clientID")
    }

    @Test
    fun `put`() {
        // TODO
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
            restTemplate.exchange("http://localhost:$port/ehrs", HttpMethod.PUT, httpEntity, Ehr::class.java)
        Assertions.assertEquals(HttpStatus.OK, responseEntity.statusCode)
        Assertions.assertTrue(responseEntity.body!!.clientId == "UpdatedClientID")
    }
}
