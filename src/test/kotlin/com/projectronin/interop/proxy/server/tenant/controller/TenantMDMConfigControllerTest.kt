package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.TenantMDMConfig
import com.projectronin.interop.proxy.server.tenant.model.converters.toProxyTenant
import com.projectronin.interop.proxy.server.util.generateMetadata
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantMDMConfigDAO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.data.model.TenantMDMConfigDO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException
import java.time.ZoneId
import com.projectronin.interop.proxy.server.tenant.model.Tenant as ProxyTenant

class TenantMDMConfigControllerTest {
    private lateinit var dao: TenantMDMConfigDAO
    private lateinit var tenantService: TenantService
    private lateinit var controller: TenantMDMConfigController

    private val tenantDO = mockk<TenantDO> {
        every { id } returns 1
        every { mnemonic } returns "first"
        every { name } returns "full name"
    }
    private val configDO = mockk<TenantMDMConfigDO> {
        every { tenant } returns tenantDO
        every { mdmDocumentTypeID } returns "typeid1"
        every { providerIdentifierSystem } returns "idsystem1"
        every { receivingSystem } returns "rsystem1"
    }

    private val mockProxyTenant = mockk<ProxyTenant> {
        every { id } returns 1
    }
    private val mockTenantServiceTenant = mockk<Tenant> {
        every { internalId } returns 1
        every { mnemonic } returns "first"
        every { name } returns "full name"
        every { batchConfig } returns null
        every { vendor } returns mockk()
        every { name } returns "Epic Tenant"
        every { timezone } returns ZoneId.of("America/New_York")
    }

    @BeforeEach
    fun setup() {
        mockkStatic("com.projectronin.interop.proxy.server.util.MetadataUtilKt")
        every { generateMetadata() } returns mockk()

        dao = mockk()
        tenantService = mockk()
        controller = TenantMDMConfigController(dao, tenantService)
        mockkStatic("com.projectronin.interop.proxy.server.tenant.model.converters.TenantConvertersKt")
        every { mockTenantServiceTenant.toProxyTenant() } returns mockProxyTenant
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `get works with mnemonic`() {
        every { dao.getByTenantMnemonic("first") } returns configDO
        val results = controller.get("first")
        assertEquals("typeid1", results.body?.mdmDocumentTypeID)
        assertEquals("idsystem1", results.body?.providerIdentifierSystem)
        assertEquals("rsystem1", results.body?.receivingSystem)
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
        val tenantMDMConfig = TenantMDMConfig(
            "typeid1",
            "idsystem1",
            "rsystem1"
        )
        val result = controller.insert("first", tenantMDMConfig)
        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(tenantMDMConfig, result.body)
    }

    @Test
    fun `insert can return empty properties`() {
        val emptyConfigDO = mockk<TenantMDMConfigDO> {
            every { tenant } returns tenantDO
            every { mdmDocumentTypeID } returns ""
            every { providerIdentifierSystem } returns ""
            every { receivingSystem } returns ""
        }

        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.insertConfig(any()) } returns emptyConfigDO

        val tenantMDMConfig = TenantMDMConfig("", "", "")
        val result = controller.insert("first", tenantMDMConfig)

        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals("", result.body?.mdmDocumentTypeID)
        assertEquals("", result.body?.providerIdentifierSystem)
        assertEquals("", result.body?.receivingSystem)
    }

    @Test
    fun `insert fails with bad mnemonic`() {
        every { tenantService.getTenantForMnemonic("first") } returns null
        val tenantMDMConfig = TenantMDMConfig("typeid1", "idsystem1", "rsystem1")
        assertThrows<NoTenantFoundException> { controller.insert("first", tenantMDMConfig) }
    }

    @Test
    fun `update works`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.updateConfig(any()) } returns configDO
        val tenantMDMConfig = TenantMDMConfig("typeid2", "idsystem2", "rsystem2")
        val result = controller.update("first", tenantMDMConfig)
        assertEquals(HttpStatus.OK, result.statusCode)
    }

    @Test
    fun `update yells when nothing is in the database`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.updateConfig(any()) } returns null
        val tenantMDMConfig = TenantMDMConfig("typeid2", "idsystem2", "rsystem2")
        val result = controller.update("first", tenantMDMConfig)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `put fails with bad mnemonic`() {
        every { tenantService.getTenantForMnemonic("first") } returns null
        val tenantMDMConfig = TenantMDMConfig("typeid2", "idsystem2", "rsystem2")
        assertThrows<NoTenantFoundException> { controller.update("first", tenantMDMConfig) }
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
