package com.projectronin.interop.proxy.server

import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class HealthCheckIT {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `health check returns status`() {
        val responseEntity = restTemplate.getForEntity("http://localhost:$port/actuator/health", String::class.java)
        assertEquals(responseEntity.statusCode, HttpStatus.OK)

        val jsonObject = objectMapper.readTree(responseEntity.body)
        assertEquals(jsonObject["status"].textValue(), "UP")
        assertTrue(jsonObject.has("components"))
    }
}
