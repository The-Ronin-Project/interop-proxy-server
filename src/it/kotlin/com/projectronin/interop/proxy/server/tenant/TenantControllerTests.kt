package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.InteropProxyServerAuthInitializer
import com.projectronin.interop.proxy.server.tenant.model.Cerner
import com.projectronin.interop.proxy.server.tenant.model.Epic
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.proxy.server.test.util.backupTables
import com.projectronin.interop.proxy.server.test.util.removeBackupTables
import com.projectronin.interop.proxy.server.test.util.restoreTables
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import org.springframework.test.context.ContextConfiguration
import java.net.URI
import java.time.LocalTime
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ContextConfiguration(initializers = [(InteropProxyServerAuthInitializer::class)])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantControllerTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ehrDatasource: DataSource

    // provider pools is just there or else the delete from io_tenant will fail
    private val modifiedTables =
        listOf("io_tenant_provider_pool", "io_tenant_epic", "io_tenant_cerner", "io_tenant_codes", "io_tenant")

    private val httpHeaders = HttpHeaders()

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

    init {
        httpHeaders.set("Content-Type", "application/json")
        httpHeaders.set("Authorization", "Fake Token")
    }

    @Test
    fun `can read all tenants`() {
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)
        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants",
            HttpMethod.GET,
            httpEntity,
            Array<Tenant>::class.java
        )
        val tenantArray = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(3, tenantArray.size)
        assertEquals(1001, tenantArray[0].id)
        assertEquals("apposnd", tenantArray[0].mnemonic)
        assertEquals(VendorType.EPIC, tenantArray[0].vendor.vendorType)
        assertTrue(tenantArray[0].monitoredIndicator!!)
        assertEquals(1002, tenantArray[1].id)
        assertEquals("epic", tenantArray[1].mnemonic)
        assertFalse(tenantArray[1].monitoredIndicator!!)
        assertEquals(2002, tenantArray[2].id)
        assertEquals("cerner", tenantArray[2].mnemonic)
        assertEquals(VendorType.CERNER, tenantArray[2].vendor.vendorType)
        assertNull(tenantArray[2].monitoredIndicator)
    }

    @Test
    fun `can read a specific tenant by mnemonic`() {
        val mnemonic = "apposnd"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)
        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$mnemonic",
            HttpMethod.GET,
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(1001, tenant.id)
        assertEquals("EPIC", tenant.vendor.vendorType.toString())
    }

    @Test
    fun `404 for fake tenant`() {
        val mnemonic = "fullyFhirCompliant"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)
        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$mnemonic",
            HttpMethod.GET,
            httpEntity,
            Tenant::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.statusCode)
        assertNull(responseEntity.body)
    }

    @Test
    fun `can insert a tenant - epic`() {
        val vendor = Epic(
            release = "1.0",
            serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
            authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
            ehrUserId = "1",
            messageType = "1",
            practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
            practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
            patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
            patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
            encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
            patientMRNTypeText = "MRN",
            hsi = null,
            instanceName = "Epic Sandbox",
            departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
            patientOnboardedFlagId = null
        )

        val newTenant = Tenant(
            id = 0,
            mnemonic = "CoolNewBoi",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "coolest boi hospital",
            timezone = "America/Chicago"
        )
        val httpEntity = HttpEntity(newTenant, httpHeaders)

        val responseEntity = restTemplate.postForEntity(
            URI("http://localhost:$port/tenants"),
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertEquals(newTenant.mnemonic, tenant.mnemonic)
        assertEquals(newTenant.vendor, tenant.vendor)
    }

    @Test
    fun `can insert a tenant - cerner`() {
        val vendor = Cerner(
            serviceEndpoint = "serviceEndpoint",
            authEndpoint = "authEndpoint",
            patientMRNSystem = "patientMRNSystem",
            instanceName = "Cerner Sandbox",
            messagePractitioner = "Practitioner1",
            messageTopic = "Ronin Alert",
            messageCategory = "alert",
            messagePriority = "routine"
        )

        val newTenant = Tenant(
            id = 0,
            mnemonic = "CoolNewBoi",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "coolest boi hospital",
            timezone = "America/Chicago"
        )
        val httpEntity = HttpEntity(newTenant, httpHeaders)

        val responseEntity = restTemplate.postForEntity(
            URI("http://localhost:$port/tenants"),
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertEquals(newTenant.mnemonic, tenant.mnemonic)
        assertEquals(newTenant.vendor, tenant.vendor)
    }

    @Test
    fun `can insert a tenant with custom MRN`() {
        val vendor = Epic(
            release = "1.0",
            serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
            authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
            ehrUserId = "1",
            messageType = "1",
            practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
            practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
            patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
            patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
            encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
            patientMRNTypeText = "Custom MRN",
            hsi = null,
            instanceName = "Epic Sandbox",
            departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
            patientOnboardedFlagId = null
        )

        val newTenant = Tenant(
            id = 0,
            mnemonic = "CoolNewBoi",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "coolest boi hospital",
            timezone = "America/New_York"
        )
        val httpEntity = HttpEntity(newTenant, httpHeaders)

        val responseEntity = restTemplate.postForEntity(
            URI("http://localhost:$port/tenants"),
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertEquals(newTenant.mnemonic, tenant.mnemonic)
        assertEquals(newTenant.vendor, tenant.vendor)
    }

    @Test
    fun `can update a tenant - epic`() {
        val vendor = Epic(
            release = "2.0",
            serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
            authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
            ehrUserId = "1",
            messageType = "1",
            practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
            practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
            patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
            patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
            encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
            patientMRNTypeText = "MRN",
            hsi = null,
            instanceName = "Epic Sandbox",
            departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
            patientOnboardedFlagId = "135124"
        )

        val updatedTenant = Tenant(
            id = 1001,
            mnemonic = "apposnd",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "App Orchard Test",
            timezone = "America/Denver",
            monitoredIndicator = false
        )
        val httpEntity = HttpEntity(updatedTenant, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/${updatedTenant.mnemonic}",
            HttpMethod.PUT,
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(updatedTenant, tenant)
    }

    @Test
    fun `can update a tenant - cerner`() {
        val vendor = Cerner(
            serviceEndpoint = "new serviceEndpoint",
            authEndpoint = "new authEndpoint",
            patientMRNSystem = "new patientMRNSystem",
            instanceName = "Cerner Sandbox",
            messagePractitioner = "NewPractitioner1",
            messageTopic = null,
            messageCategory = null,
            messagePriority = null
        )

        val updatedTenant = Tenant(
            id = 2002,
            mnemonic = "cerner",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "App Orchard Test",
            timezone = "America/Denver",
            monitoredIndicator = true
        )
        val httpEntity = HttpEntity(updatedTenant, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/${updatedTenant.mnemonic}",
            HttpMethod.PUT,
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(updatedTenant, tenant)
    }

    @Test
    fun `can update a tenant with custom MRN`() {
        val vendor = Epic(
            release = "2.0",
            serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
            authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
            ehrUserId = "1",
            messageType = "1",
            practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
            practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
            patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
            patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
            encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
            patientMRNTypeText = "Custom MRN",
            hsi = null,
            instanceName = "Epic Sandbox",
            departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
            patientOnboardedFlagId = null
        )

        val updatedTenant = Tenant(
            id = 1001,
            mnemonic = "apposnd",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "App Orchard Test",
            timezone = "America/Los_Angeles",
            monitoredIndicator = false
        )
        val httpEntity = HttpEntity(updatedTenant, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/${updatedTenant.mnemonic}",
            HttpMethod.PUT,
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertEquals(updatedTenant, tenant)
    }

    @Test
    fun `inserting without important flag defaults to true`() {
        val vendor = Epic(
            release = "1.0",
            serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
            authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
            ehrUserId = "1",
            messageType = "1",
            practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
            practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
            patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
            patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
            encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
            patientMRNTypeText = "MRN",
            hsi = null,
            instanceName = "Epic Sandbox",
            departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
            patientOnboardedFlagId = null
        )

        val newTenant = Tenant(
            id = 0,
            mnemonic = "CoolNewBoi",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "coolest boi hospital",
            timezone = "America/Chicago"
        )
        val httpEntity = HttpEntity(newTenant, httpHeaders)

        val responseEntity = restTemplate.postForEntity(
            URI("http://localhost:$port/tenants"),
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.CREATED, responseEntity.statusCode)
        assertTrue(tenant.monitoredIndicator!!)
    }

    @Test
    fun `updating without the important flag defaults to true`() {
        val vendor = Cerner(
            serviceEndpoint = "new serviceEndpoint",
            authEndpoint = "new authEndpoint",
            patientMRNSystem = "new patientMRNSystem",
            instanceName = "Cerner Sandbox",
            messagePractitioner = "NewPractitioner1",
            messageTopic = null,
            messageCategory = null,
            messagePriority = null
        )

        val updatedTenant = Tenant(
            id = 2002,
            mnemonic = "cerner",
            availableStart = LocalTime.of(22, 0),
            availableEnd = LocalTime.of(6, 0),
            vendor = vendor,
            name = "App Orchard Test",
            timezone = "America/Denver"
        )
        val httpEntity = HttpEntity(updatedTenant, httpHeaders)

        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/${updatedTenant.mnemonic}",
            HttpMethod.PUT,
            httpEntity,
            Tenant::class.java
        )
        val tenant = responseEntity.body!!

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
        assertTrue(tenant.monitoredIndicator!!)
    }

    @Test
    fun `can retrieve tenant codes`() {
        val mnemonic = "apposnd"
        val httpEntity = HttpEntity<HttpHeaders>(httpHeaders)
        val responseType = object : ParameterizedTypeReference<Map<String, String>>() {}
        val responseEntity = restTemplate.exchange(
            "http://localhost:$port/tenants/$mnemonic/codes",
            HttpMethod.GET,
            httpEntity,
            responseType
        )
        val validCodes = mapOf("bmiCode" to "bmi_code_value", "bsaCode" to "bsa_code_value")
        assertTrue(responseEntity.hasBody())
        assertEquals(2, responseEntity.body?.size)
        responseEntity.body?.forEach {
            assertTrue(it.key in validCodes.keys)
            assertEquals(validCodes[it.key], it.value)
        }

        assertEquals(HttpStatus.OK, responseEntity.statusCode)
    }
}
