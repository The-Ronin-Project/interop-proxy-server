package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.tenant.config.data.model.ProviderPoolDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import com.projectronin.interop.tenant.config.model.EpicAuthenticationConfig
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.tenant.config.model.vendor.Epic
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import java.time.ZoneId

class ProviderPoolControllerTest {
    private lateinit var dao: ProviderPoolDAO
    private lateinit var controller: ProviderPoolController
    private lateinit var tenantService: TenantService

    private val tenant = Tenant(
        internalId = 1,
        mnemonic = "tenantMnemonic",
        name = "full name",
        timezone = ZoneId.of("America/Los_Angeles"),
        batchConfig = null,
        vendor = Epic(
            clientId = "clientId",
            release = "release",
            serviceEndpoint = "serviceEndpoint",
            authenticationConfig = EpicAuthenticationConfig(
                authEndpoint = "authEndpoint",
                publicKey = "publicKey",
                privateKey = "privateKey"
            ),
            ehrUserId = "ehrUserId",
            messageType = "messageType",
            practitionerProviderSystem = "practitionerProviderSystem",
            practitionerUserSystem = "practitionerUserSystem",
            patientMRNSystem = "patientMRNSystem",
            patientInternalSystem = "patientInternalSystem",
            encounterCSNSystem = "encounterCSNSystem",
            patientMRNTypeText = "patientMRNTypeText",
            hsi = "hsi",
            instanceName = "instanceName",
            departmentInternalSystem = "departmentInternalSystem",
            patientOnboardedFlagId = "flagId",
            orderSystem = "orderSystem"
        ),
        monitoredIndicator = null
    )

    private val providerPool = ProviderPool(
        providerPoolId = 1,
        providerId = "providerId",
        poolId = "poolId"
    )

    private val tenantDO = TenantDO {
        val vendor = tenant.vendorAs<Epic>()
        id = 1
        mnemonic = tenant.mnemonic
        name = tenant.name
        ehr = EhrDO {
            instanceName = vendor.instanceName
            vendorType = VendorType.EPIC
            clientId = vendor.clientId
            publicKey = vendor.authenticationConfig.publicKey
            privateKey = vendor.authenticationConfig.privateKey
        }
        availableBatchStart = null
        availableBatchEnd = null
        monitoredIndicator = null
    }

    private val providerPoolDO = ProviderPoolDO {
        id = providerPool.providerPoolId
        tenant = tenantDO
        providerId = providerPool.providerId
        poolId = providerPool.poolId
    }

    // Used for mocking the insert
    private val slimTenantDO = TenantDO {
        id = 1
    }

    private val slimProviderPoolDO = ProviderPoolDO {
        id = providerPool.providerPoolId
        tenant = slimTenantDO
        providerId = providerPool.providerId
        poolId = providerPool.poolId
    }

    private val fakeMnemonic = "FakeMnemonic"

    @BeforeEach
    fun setup() {
        dao = mockk()
        tenantService = mockk()
        controller = ProviderPoolController(dao, tenantService)

        every { tenantService.getTenantForMnemonic(tenant.mnemonic) } returns tenant
        every { tenantService.getTenantForMnemonic(fakeMnemonic) } returns null
    }

    @Test
    fun `handles get`() {
        every { dao.getPoolsForProviders(tenant.internalId, listOf(providerPoolDO.providerId)) } returns listOf(
            providerPoolDO
        )
        val response = controller.get(tenant.mnemonic, listOf(providerPoolDO.providerId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(providerPool), response.body)
    }

    @Test
    fun `handles get with bad tenant`() {
        assertThrows<NoTenantFoundException> {
            controller.get(fakeMnemonic, listOf(providerPoolDO.providerId))
        }
    }

    @Test
    fun `handles get all`() {
        every { dao.getAll(tenant.internalId) } returns listOf(providerPoolDO)
        val response = controller.get(tenant.mnemonic)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(providerPool), response.body)
    }

    @Test
    fun `handles successful insert`() {
        every { dao.insert(slimProviderPoolDO) } returns providerPoolDO
        val response = controller.insert(tenant.mnemonic, providerPool)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(providerPool, response.body)
    }

    @Test
    fun `handles failed insert due to integrity violation`() {
        every { dao.insert(slimProviderPoolDO) } throws DataIntegrityViolationException("error")

        assertThrows<DataIntegrityViolationException> {
            controller.insert(tenant.mnemonic, providerPool)
        }
    }

    @Test
    fun `handles failed insert due to unknown exception`() {
        every { dao.insert(slimProviderPoolDO) } throws Exception("error")

        assertThrows<Exception> {
            controller.insert(tenant.mnemonic, providerPool)
        }
    }

    @Test
    fun `handles failed insert due to bad tenant`() {
        assertThrows<Exception> {
            controller.insert(fakeMnemonic, providerPool)
        }
    }

    @Test
    fun `handles successful update`() {
        every { dao.update(slimProviderPoolDO) } returns providerPoolDO
        val response = controller.update(tenant.mnemonic, providerPool.providerPoolId, providerPool)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(providerPool, response.body)
    }

    @Test
    fun `handles failed update due to pool not found`() {
        every { dao.update(slimProviderPoolDO) } returns null
        val response = controller.update(tenant.mnemonic, providerPool.providerPoolId, providerPool)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `handles bad update due to constraint violation`() {
        every { dao.update(slimProviderPoolDO) } throws DataIntegrityViolationException("error")

        assertThrows<DataIntegrityViolationException> {
            controller.update(tenant.mnemonic, providerPool.providerPoolId, providerPool)
        }
    }

    @Test
    fun `handles bad update due to multiple pools found`() {
        every { dao.update(slimProviderPoolDO) } returns null
        val response = controller.update(tenant.mnemonic, providerPool.providerPoolId, providerPool)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `handles bad update due to bad tenant`() {
        assertThrows<Exception> {
            controller.update(fakeMnemonic, providerPool.providerPoolId, providerPool)
        }
    }

    @Test
    fun `handles successful delete`() {
        every { dao.getPoolById(providerPoolDO.id) } returns providerPoolDO
        every { dao.delete(providerPool.providerPoolId) } returns 1
        val response = controller.delete(tenant.mnemonic, providerPool.providerPoolId)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `handles non-successful delete due to pool not found`() {
        every { dao.getPoolById(providerPoolDO.id) } returns null

        val response = controller.delete(tenant.mnemonic, providerPool.providerPoolId)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `handles non-successful delete that violates integrity`() {
        every { dao.getPoolById(providerPoolDO.id) } returns providerPoolDO
        every { dao.delete(providerPoolDO.id) } throws DataIntegrityViolationException("error")

        assertThrows<DataIntegrityViolationException> {
            controller.delete(tenant.mnemonic, 1)
        }
    }

    @Test
    fun `handles non-successful delete due to pool belonging to different tenant`() {
        // slimProviderPoolDO has new tenant mnemonic
        every { dao.getPoolById(providerPoolDO.id) } returns slimProviderPoolDO

        assertThrows<Exception> {
            controller.delete(tenant.mnemonic, 1)
        }
    }

    @Test
    fun `handles non-successful delete due to multiple pools found`() {
        every { dao.getPoolById(providerPoolDO.id) } returns providerPoolDO
        every { dao.delete(providerPoolDO.id) } returns 2
        val response = controller.delete(tenant.mnemonic, providerPool.providerPoolId)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `handles non-successful delete due to bad tenant`() {
        assertThrows<Exception> {
            controller.delete(fakeMnemonic, 1)
        }
    }

    @Test
    fun `data integrity exception is handled`() {
        val exception = DataIntegrityViolationException("Woops")
        val response = controller.handleDataIntegrityException(exception)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals(exception.message, response.body)
    }

    @Test
    fun `generic exception is handled`() {
        val exception = Exception("Oops")
        val response = controller.handleException(exception)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(exception.message, response.body)
    }
}
