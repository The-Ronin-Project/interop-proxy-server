package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.util.stream.Stream
import javax.sql.DataSource

private var setupDone = false

// These tests are separated from the rest of the TenantController to ensure they work without authorization.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantControllerHealthCheckTests {
    companion object {
        @JvmStatic
        fun tenantMnemonics(): Stream<String> {
            return Stream.of("epic", "cerner")
        }
    }

    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var ehrDatasource: DataSource

    @Autowired
    lateinit var mockEHR: MockEHRTestcontainer

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/json")
    }

    @BeforeEach
    fun beforeEach() {
        if (!setupDone) { // if you hate this, you are not alone. blame kotlin, junit5, or spring (not me though)

            // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
            val connection = ehrDatasource.connection
            val statement = connection.createStatement()
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1002;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1002;")
            statement.execute("update io_tenant_cerner set service_endpoint = '${mockEHR.getURL()}/cerner/fhir/r4' where io_tenant_id = 2002;")
            statement.execute("update io_tenant_cerner set auth_endpoint = '${mockEHR.getURL()}/cerner/oauth2/token' where io_tenant_id = 2002;")
            connection.close()
            setupDone = true
        }
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `can health check each tenant`(mnemonic: String) {
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)
        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$mnemonic/health",
            HttpMethod.GET,
            httpEntity,
            Any::class.java
        )
        assertFalse(responseEntity.hasBody())
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
    }

    @Test
    fun `can check health of all monitored tenants`() {
        val validIds = mapOf("apposnd" to false)
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)
        val responseType = object : ParameterizedTypeReference<Map<String, Boolean>>() {}
        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/health",
            HttpMethod.GET,
            httpEntity,
            responseType
        )
        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(responseEntity.hasBody())
        assertEquals(1, responseEntity.body?.size)
        responseEntity.body?.forEach {
            assertTrue(it.key in validIds.keys)
            assertEquals(validIds[it.key], it.value)
        }
    }
}
