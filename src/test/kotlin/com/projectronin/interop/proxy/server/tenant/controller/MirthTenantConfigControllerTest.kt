package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.tenant.config.model.vendor.Epic
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException

class MirthTenantConfigControllerTest {
    private lateinit var dao: MirthTenantConfigDAO
    private lateinit var tenantService: TenantService
    private lateinit var controller: MirthTenantConfigController
    private val tenantDO = mockk<TenantDO> {
        every { id } returns 1
        every { mnemonic } returns "first"
        every { name } returns "full name"
    }
    private val configDO = mockk<MirthTenantConfigDO> {
        every { tenant } returns tenantDO
        every { locationIds } returns "bleep,blorp,bloop"
    }

    // these two are just needed to properly mock the tenantService objects
    // potentially there's a way to properly mock the .toProxyEpic and .toProxyTenant,
    // but that doesn't seem to work easily
    private val mockTenantServiceEpic = mockk<Epic> {
        every { release } returns "release"
        every { serviceEndpoint } returns "serviceEndpoint"
        every { authenticationConfig } returns mockk { every { authEndpoint } returns "auth" }
        every { ehrUserId } returns "123"
        every { messageType } returns "123"
        every { practitionerProviderSystem } returns "123"
        every { practitionerUserSystem } returns "123"
        every { patientMRNSystem } returns "123"
        every { patientInternalSystem } returns "123"
        every { encounterCSNSystem } returns "123"
        every { patientMRNTypeText } returns "MRN"
        every { hsi } returns null
        every { instanceName } returns "Epic Instance"
    }
    private val mockTenantServiceTenant = mockk<Tenant> {
        every { internalId } returns 1
        every { mnemonic } returns "first"
        every { name } returns "full name"
        every { batchConfig } returns null
        every { vendor } returns mockTenantServiceEpic
        every { name } returns "Epic Tenant"
    }

    @BeforeEach
    fun setup() {
        dao = mockk()
        tenantService = mockk()
        controller = MirthTenantConfigController(dao, tenantService)
    }

    @Test
    fun `get works with mnemonic`() {
        every { dao.getByTenantMnemonic("first") } returns configDO
        val results = controller.get("first")
        assertEquals(results.body?.locationIds?.size, 3)
    }

    @Test
    fun `get fails with bad mnemonic`() {
        every { dao.getByTenantMnemonic("first") } returns null
        assertThrows<NoTenantFoundException> { controller.get("first") }
    }

    @Test
    fun `insert works`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.insertConfig(any()) } returns configDO
        val mirthTenantConfig = MirthTenantConfig(listOf("bleep", "blorp", "bloop"))
        val result = controller.insert("first", mirthTenantConfig)
        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(mirthTenantConfig, result.body)
    }

    @Test
    fun `insert can return an empty location string`() {
        val emptyConfigDO = mockk<MirthTenantConfigDO> {
            every { tenant } returns tenantDO
            every { locationIds } returns ""
        }

        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.insertConfig(any()) } returns emptyConfigDO

        val mirthTenantConfig = MirthTenantConfig(listOf())
        val result = controller.insert("first", mirthTenantConfig)

        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(emptyList<String>(), result.body?.locationIds)
    }

    @Test
    fun `insert fails with bad mnemonic`() {
        every { tenantService.getTenantForMnemonic("first") } returns null
        val mirthTenantConfig = MirthTenantConfig(listOf("loc"))
        assertThrows<NoTenantFoundException> { controller.insert("first", mirthTenantConfig) }
    }

    @Test
    fun `update works`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.updateConfig(any()) } returns configDO
        val mirthTenantConfig = MirthTenantConfig(listOf("bleep", "blorp", "bloop"))
        val result = controller.update("first", mirthTenantConfig)
        assertEquals(HttpStatus.OK, result.statusCode)
    }

    @Test
    fun `update yells when nothing is in the database`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.updateConfig(any()) } returns null
        val mirthTenantConfig = MirthTenantConfig(listOf("bleep", "blorp", "bloop"))
        val result = controller.update("first", mirthTenantConfig)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `put fails with bad mnemonic`() {
        every { tenantService.getTenantForMnemonic("first") } returns null
        val mirthTenantConfig = MirthTenantConfig(listOf("loc"))
        assertThrows<NoTenantFoundException> { controller.update("first", mirthTenantConfig) }
    }

    @Test
    fun `no tenant exception is handled`() {
        val exception = NoTenantFoundException("")
        val response = controller.handleTenantException(exception)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `generic exception is handled`() {
        val exception = SQLIntegrityConstraintViolationException("Oops")
        val response = controller.handleException(exception)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(exception.message, response.body)
    }
}
