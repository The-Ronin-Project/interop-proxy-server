package com.projectronin.interop.proxy.server

import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.mock.ehr.testcontainer.MockEHRTestcontainer
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.net.URI
import java.util.stream.Stream
import javax.sql.DataSource

private var setupDone = false

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
abstract class InteropProxyServerIntegratedTestsBase {
    @LocalServerPort
    var port = 0

    @Autowired
    lateinit var mockEHR: MockEHRTestcontainer

    @MockkBean
    lateinit var m2mJwtDecoder: JwtDecoder

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var ehrDatasource: DataSource

    final val httpHeaders = HttpHeaders()

    abstract val resourcesToAdd: List<ResourceToAdd>

    data class ResourceToAdd(val resourceType: String, val jsonLocation: String, val resourceFHIRID: String)

    init {
        httpHeaders.set("Content-Type", "application/graphql")
        httpHeaders.set("Authorization", "Fake Token")
    }

    companion object {
        @JvmStatic
        fun tenantsToTest(): Stream<String> {
            return Stream.of("epic", "cerner")
        }

        val docker =
            DockerComposeContainer(File(InteropProxyServerIntegratedTestsBase::class.java.getResource("/kafka/docker-compose-kafka.yaml")!!.file)).waitingFor(
                "kafka",
                Wait.forLogMessage(".*\\[KafkaServer id=\\d+\\] started.*", 1)
            ).start()
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
        // insert testing patient to MockEHR
        resourcesToAdd.forEach {
            mockEHR.addR4Resource(
                it.resourceType,
                this::class.java.getResource(it.jsonLocation)!!.readText(),
                it.resourceFHIRID
            )
        }
    }

    fun multiVendorQuery(
        query: String,
        testTenant: String,
        headers: HttpHeaders = httpHeaders
    ): ResponseEntity<String> {
        val httpEntity = HttpEntity(query, headers)
        val tenantId = when (testTenant) {
            "epic" -> 1002
            "cerner" -> 2002
            else -> 1002
        }
        val connection = ehrDatasource.connection
        val statement = connection.createStatement()
        statement.execute("update io_tenant set mnemonic = 'ronin' where io_tenant_id = $tenantId;")
        val ret = restTemplate.postForEntity(URI("http://localhost:$port/graphql"), httpEntity, String::class.java)
        statement.execute("update io_tenant set mnemonic = '$testTenant' where io_tenant_id = $tenantId;")
        connection.close()
        return ret
    }
}
