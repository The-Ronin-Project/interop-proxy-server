package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    private var tenantController = TenantController(tenantService)

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
        departmentInternalSystem = "departmentInternalSystem"
    )
    private val proxyTenant = ProxyTenant(
        id = 1,
        mnemonic = "mnemonic1",
        availableStart = LocalTime.of(20, 0),
        availableEnd = LocalTime.of(6, 0),
        vendor = proxyVendor,
        name = "test tenant",
        timezone = "America/Los_Angeles"
    )
    private val tenantServiceTenant = mockk<TenantServiceTenant> {
        every { internalId } returns 1
    }
    private val proxyTenantNoTimes = ProxyTenant(
        id = 2,
        mnemonic = "mnemonic2",
        availableStart = null,
        availableEnd = null,
        vendor = proxyVendor,
        name = "test tenant2",
        timezone = "America/Denver"
    )
    private val tenantServiceTenantNoBatch = mockk<TenantServiceTenant> {}
    @BeforeEach
    fun setup() {
        mockkStatic("com.projectronin.interop.proxy.server.tenant.controller.TenantServiceMappingUtilKt")
        every { tenantServiceTenant.toProxyTenant() } returns proxyTenant
        every { tenantServiceTenantNoBatch.toProxyTenant() } returns proxyTenantNoTimes
        every { proxyTenant.toTenantServerVendor() } returns tenantServiceTenant
        every { proxyTenantNoTimes.toTenantServerVendor() } returns tenantServiceTenantNoBatch
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
            timezone = "America/Denver"
        )
        every { proxyTenantNoStart.toTenantServerVendor() } returns mockk()
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
            timezone = "America/Denver"
        )
        every { proxyTenantNoEnd.toTenantServerVendor() } returns mockk()
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
        every { tenant.toTenantServerVendor(1) } returns tenantServiceTenant
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
