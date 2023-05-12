package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenant
import com.projectronin.interop.proxy.server.tenant.model.converters.toTenantServerTenant
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalTime
import com.projectronin.interop.proxy.server.tenant.model.Epic as ProxyEpic
import com.projectronin.interop.proxy.server.tenant.model.Tenant as ProxyTenant
import com.projectronin.interop.tenant.config.model.Tenant as TenantServiceTenant

class TenantControllerTest {
    private var tenantService = mockk<TenantService>()
    private var ehrFactory = mockk<EHRFactory>()
    private var tenantController = TenantController(tenantService, ehrFactory)

    private val proxyVendor = ProxyEpic(
        release = "release",
        serviceEndpoint = "serviceEndpoint",
        authEndpoint = "authEndpoint",
        ehrUserId = "ehrUserId",
        messageType = "messageType",
        practitionerProviderSystem = "providerSystemExample",
        practitionerUserSystem = "userSystemExample",
        patientMRNSystem = "mrnSystemExample",
        patientInternalSystem = "internalSystemExample",
        encounterCSNSystem = "encounterCSNSystem",
        patientMRNTypeText = "patientMRNTypeText",
        hsi = null,
        instanceName = "instanceName",
        departmentInternalSystem = "departmentInternalSystem",
        patientOnboardedFlagId = "flagId"
    )
    private val proxyTenant = ProxyTenant(
        id = 1,
        mnemonic = "mnemonic1",
        availableStart = LocalTime.of(20, 0),
        availableEnd = LocalTime.of(6, 0),
        vendor = proxyVendor,
        name = "test tenant",
        timezone = "America/Los_Angeles",
        monitoredIndicator = null
    )
    private val tenantServiceTenant = mockk<TenantServiceTenant> {
        every { internalId } returns 1
        every { mnemonic } returns "mnemonic1"
    }
    private val proxyTenantNoTimes = ProxyTenant(
        id = 2,
        mnemonic = "mnemonic2",
        availableStart = null,
        availableEnd = null,
        vendor = proxyVendor,
        name = "test tenant2",
        timezone = "America/Denver",
        monitoredIndicator = null
    )
    private val tenantServiceTenantNoBatch = mockk<TenantServiceTenant> {
        every { mnemonic } returns "mnemonic2"
    }

    @BeforeEach
    fun setup() {
        mockkStatic("com.projectronin.interop.proxy.server.tenant.model.converters.TenantConvertersKt")
        every { tenantServiceTenant.toProxyTenant() } returns proxyTenant
        every { tenantServiceTenantNoBatch.toProxyTenant() } returns proxyTenantNoTimes
        every { proxyTenant.toTenantServerTenant() } returns tenantServiceTenant
        every { proxyTenantNoTimes.toTenantServerTenant() } returns tenantServiceTenantNoBatch
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `can read all tenants`() {
        every { tenantService.getAllTenants() } returns listOf(tenantServiceTenant, tenantServiceTenantNoBatch)
        val response = tenantController.read()
        assertEquals(HttpStatus.OK, response.statusCode)
        val responseTenants = response.body
        assertEquals(2, responseTenants?.size)
        assertEquals(proxyTenant, responseTenants?.first())
        assertEquals(proxyTenantNoTimes, responseTenants?.get(1))
    }

    @Test
    fun `ok when no tenants`() {
        every { tenantService.getAllTenants() } returns emptyList()
        val response = tenantController.read()
        assertEquals(HttpStatus.OK, response.statusCode)
        val responseTenants = response.body
        assertEquals(0, responseTenants?.size)
    }

    @Test
    fun `can read a specific tenant by mnemonic`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns tenantServiceTenant
        val response = tenantController.read("mnemonic1")
        assertEquals(HttpStatus.OK, response.statusCode)
        val responseTenant = response.body
        assertEquals(proxyTenant, responseTenant)
    }

    @Test
    fun `read returns 404`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns null
        val response = tenantController.read("mnemonic1")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val responseTenant = response.body
        assertNull(responseTenant)
    }

    @Test
    fun `can insert a tenant`() {
        every { tenantService.insertTenant(any()) } returns tenantServiceTenant
        val response = tenantController.insert(proxyTenant)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(proxyTenant, response.body)
    }

    @Test
    fun `insert with just a start doesn't create batch config`() {
        val proxyTenantNoStart = ProxyTenant(
            id = 2,
            mnemonic = "mnemonic2",
            availableStart = null,
            availableEnd = LocalTime.of(6, 0),
            vendor = proxyVendor,
            name = "test tenant2",
            timezone = "America/Denver",
            monitoredIndicator = null
        )
        every { proxyTenantNoStart.toTenantServerTenant() } returns mockk()
        every { tenantService.insertTenant(any()) } returns tenantServiceTenantNoBatch
        val response = tenantController.insert(proxyTenantNoStart)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(proxyTenantNoTimes, response.body)
    }

    @Test
    fun `insert with just a end doesn't create batch config`() {
        val proxyTenantNoEnd = ProxyTenant(
            id = 2,
            mnemonic = "mnemonic2",
            availableStart = LocalTime.of(6, 0),
            availableEnd = null,
            vendor = proxyVendor,
            name = "test tenant2",
            timezone = "America/Denver",
            monitoredIndicator = null
        )
        every { proxyTenantNoEnd.toTenantServerTenant() } returns mockk()
        every { tenantService.insertTenant(any()) } returns tenantServiceTenantNoBatch
        val response = tenantController.insert(proxyTenantNoEnd)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(proxyTenantNoTimes, response.body)
    }

    @Test
    fun `can update a tenant`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns tenantServiceTenant
        every { tenantService.updateTenant(any()) } returns tenantServiceTenant
        val tenant = mockk<ProxyTenant> {}
        every { tenant.toTenantServerTenant(1) } returns tenantServiceTenant
        every { tenantServiceTenant.toProxyTenant() } returns tenant
        val response = tenantController.update("mnemonic1", tenant)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(tenant, response.body)
    }

    @Test
    fun `update fails due to no tenant found`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns null
        assertThrows<NoTenantFoundException> {
            tenantController.update("mnemonic1", proxyTenant)
        }
    }

    @Test
    fun `can health check a single healthy tenant`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns tenantServiceTenant
        every { ehrFactory.getVendorFactory(tenantServiceTenant).healthCheckService.healthCheck(tenantServiceTenant) } returns true

        val response = tenantController.health("mnemonic1")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(response.hasBody())
    }

    @Test
    fun `can health check a single unhealthy tenant`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns tenantServiceTenant
        every { ehrFactory.getVendorFactory(tenantServiceTenant).healthCheckService.healthCheck(tenantServiceTenant) } returns false

        val response = tenantController.health("mnemonic1")
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertFalse(response.hasBody())
    }

    @Test
    fun `can health check return 404`() {
        every { tenantService.getTenantForMnemonic("mnemonic1") } returns null

        val response = tenantController.health("mnemonic1")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertFalse(response.hasBody())
    }

    @Test
    fun `can check health of all tenants`() {
        every { tenantService.getMonitoredTenants() } returns listOf(tenantServiceTenant, tenantServiceTenantNoBatch)
        every { ehrFactory.getVendorFactory(tenantServiceTenant).healthCheckService.healthCheck(tenantServiceTenant) } returns false
        every {
            ehrFactory.getVendorFactory(tenantServiceTenantNoBatch).healthCheckService.healthCheck(
                tenantServiceTenantNoBatch
            )
        } returns true

        val response = tenantController.health()
        val tenantsHealth = response.body
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, tenantsHealth?.size)
        assertTrue(tenantsHealth?.keys?.contains(proxyTenant.mnemonic)!!)
        assertTrue(tenantsHealth.keys?.contains(proxyTenantNoTimes.mnemonic) as Boolean)
        assertEquals(tenantsHealth[proxyTenant.mnemonic], false)
        assertEquals(tenantsHealth[proxyTenantNoTimes.mnemonic], true)
    }

    @Test
    fun `can retrieve tenant codes`() {
        val mnemonic = "tenantMnemonic"
        val expectedCodes = mapOf(
            "bsaCode" to "bsaCode",
            "bmiCode" to "bmiCode"
        )
        every { tenantService.getCodesForTenantMnemonic(mnemonic) } returns expectedCodes

        val response = tenantController.codes(mnemonic)
        assertTrue(response.hasBody())
        assertEquals(expectedCodes.size, response.body?.size)
        response.body?.forEach {
            assertTrue(it.key in expectedCodes.keys)
            assertEquals(expectedCodes[it.key], it.value)
        }

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `tenant code check fails when tenant has no codes`() {
        val mnemonic = "tenantMnemonic"
        val expectedCodes = mapOf<String, String>()
        every { tenantService.getCodesForTenantMnemonic(mnemonic) } returns expectedCodes

        val response = tenantController.codes(mnemonic)
        assertFalse(response.hasBody())
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `no tenant exception is handled`() {
        val exception = NoTenantFoundException("Probably didn't get the id right")
        val response = tenantController.handleTenantException(exception)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Unable to find tenant", response.body)
    }

    @Test
    fun `no ehr exception is handled`() {
        val exception = NoEHRFoundException("How did this happen")
        val response = tenantController.handleEHRException(exception)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Unable to find EHR", response.body)
    }

    @Test
    fun `generic exception is handled`() {
        val exception = SQLIntegrityConstraintViolationException("Oops")
        val response = tenantController.handleException(exception)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(exception.message, response.body)
    }
}
