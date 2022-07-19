package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.hl7.ProcessingID
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.data.model.TenantServerDO
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

class TenantServerControllerTest {
    private lateinit var dao: TenantServerDAO
    private lateinit var controller: TenantServerController
    private lateinit var tenantService: TenantService
    private val tenantDO = mockk<TenantDO> {
        every { id } returns 1
        every { mnemonic } returns "tenant"
        every { name } returns "full name"
    }

    private val tenantServerDO = mockk<TenantServerDO> {
        every { tenant } returns tenantDO
        every { id } returns 1
        every { address } returns "127.0.0.1"
        every { port } returns 80
        every { messageType } returns MessageType.MDM
        every { serverType } returns ProcessingID.VALIDATION
    }
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
        controller = TenantServerController(dao, tenantService)
    }

    @Test
    fun `get works with mnemonic`() {
        every { dao.getTenantServers("tenant") } returns listOf(tenantServerDO)
        val results = controller.get("tenant")
        assertEquals(1, results.body?.size)
        assertEquals("127.0.0.1", results.body?.first()?.address)
    }

    @Test
    fun `get fails with bad mnemonic`() {
        every { dao.getTenantServers("tenant") } returns emptyList()
        val results = controller.get("tenant")
        assertEquals(HttpStatus.NOT_FOUND, results.statusCode)
    }

    @Test
    fun `get works with mnemonic and type`() {
        every { dao.getTenantServers("tenant", MessageType.MDM) } returns listOf(tenantServerDO)
        val results = controller.getWithType("tenant", "MDM")
        assertEquals("127.0.0.1", results.body?.address)
    }

    @Test
    fun `get fails with bad mnemonic and type`() {
        every { dao.getTenantServers("tenant", MessageType.MDM) } returns emptyList()
        val result = controller.getWithType("tenant", "MDM")
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `insert works`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.insertTenantServer(any()) } returns tenantServerDO
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "127.0.0.1",
            port = 80,
            serverType = "V"
        )
        val result = controller.insert("first", tenantServer)
        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(tenantServer, result.body)
    }

    @Test
    fun `insert fails with bad mnemonic`() {
        every { tenantService.getTenantForMnemonic("first") } returns null
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "127.0.0.1",
            port = 80,
            serverType = "V"
        )
        assertThrows<NoTenantFoundException> { controller.insert("first", tenantServer) }
    }

    @Test
    fun `update works`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.updateTenantServer(any()) } returns tenantServerDO
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "127.0.0.1",
            port = 80,
            serverType = "V"
        )
        val result = controller.update("first", tenantServer)
        assertEquals(HttpStatus.OK, result.statusCode)
    }

    @Test
    fun `update yells when nothing is in the database`() {
        every { tenantService.getTenantForMnemonic("first") } returns mockTenantServiceTenant
        every { dao.updateTenantServer(any()) } returns null
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "127.0.0.1",
            port = 80,
            serverType = "V"
        )
        val result = controller.update("first", tenantServer)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `put fails with bad mnemonic`() {
        every { tenantService.getTenantForMnemonic("first") } returns null
        val tenantServer = TenantServer(
            id = 1,
            messageType = "MDM",
            address = "127.0.0.1",
            port = 80,
            serverType = "V"
        )
        assertThrows<NoTenantFoundException> { controller.update("first", tenantServer) }
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
