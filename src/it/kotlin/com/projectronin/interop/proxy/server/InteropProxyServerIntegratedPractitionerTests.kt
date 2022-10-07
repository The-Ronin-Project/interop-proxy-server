package com.projectronin.interop.proxy.server

import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.testcontainer.AidboxData
import com.projectronin.interop.aidbox.testcontainer.AidboxTest
import com.projectronin.interop.aidbox.testcontainer.container.AidboxContainer
import com.projectronin.interop.aidbox.testcontainer.container.AidboxDatabaseContainer
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.net.URI
import javax.sql.DataSource

private var setupDone = false

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@AidboxData("aidbox/practitioners.yaml")
@AidboxTest
class InteropProxyServerIntegratedPractitionerTests {
    companion object {
        @Container
        val aidboxDatabaseContainer = AidboxDatabaseContainer()

        @Container
        val aidbox = AidboxContainer(aidboxDatabaseContainer, version = "2206-lts")

        // allows us to dynamically change the aidbox port to the testcontainer instance
        @JvmStatic
        @DynamicPropertySource
        fun aidboxUrlProperties(registry: DynamicPropertyRegistry) {
            registry.add("aidbox.url") { aidbox.baseUrl() }
        }
    }

    @Autowired
    private lateinit var mockEHR: MockEHRTestcontainer

    @Autowired
    private lateinit var ehrDatasource: DataSource

    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockkBean
    private lateinit var m2mJwtDecoder: JwtDecoder

    private val httpHeaders = HttpHeaders()

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @BeforeEach
    fun setup() {
        if (!setupDone) {
            // we need to change the service address of "Epic" after instantiation since the Testcontainer has a dynamic port
            val connection = ehrDatasource.connection
            val statement = connection.createStatement()
            statement.execute("update io_tenant_epic set service_endpoint = '${mockEHR.getURL()}/epic' where io_tenant_id = 1002;")
            statement.execute("update io_tenant_epic set auth_endpoint = '${mockEHR.getURL()}/epic/oauth2/token' where io_tenant_id = 1002;")

            mockEHR.addR4Resource(
                "Practitioner",
                this::class.java.getResource("/mockEHR/r4Practitioner.json")!!.readText(),
                "PractitionerFHIRID1"
            )
            setupDone = true
        }
    }

    @Test
    fun `server handles practitioner by FHIR ID query`() {
        val query = this::class.java.getResource("/graphql/practitionerById.graphql")!!.readText()
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val resultJSONNode = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals("ronin-PractitionerFHIRID1", resultJSONNode["data"]["getPractitionerById"]["id"].asText())
    }

    @Test
    fun `server handles practitioner by provider query`() {
        val query = this::class.java.getResource("/graphql/practitionerByProvider.graphql")!!.readText()
        val httpEntity = HttpEntity(query, httpHeaders)

        val responseEntity =
            restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        assertEquals(HttpStatus.OK, responseEntity.statusCode)

        val resultJSONNode = JacksonManager.objectMapper.readTree(responseEntity.body)
        assertEquals("ronin-PractitionerFHIRID1", resultJSONNode["data"]["getPractitionerByProvider"]["id"].asText())
    }
}
