package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EhrControllerTest {
    private lateinit var controller: EhrController
    private lateinit var dao: EhrDAO
    private lateinit var epicEhrDO: EhrDO
    private lateinit var epicEhrDO2: EhrDO
    private lateinit var cernerEHRDO: EhrDO

    @BeforeAll
    fun initTest() {
        dao = mockk()
        controller = EhrController(dao)
        epicEhrDO = mockk {
            every { id } returns 1
            every { vendorType } returns VendorType.EPIC
            every { instanceName } returns "instanceName1"
            every { clientId } returns "clientId1"
            every { publicKey } returns "publicKey1"
            every { privateKey } returns "privateKey1"
            every { accountId } returns "wontBePopulatedNormally"
        }
        epicEhrDO2 = mockk {
            every { id } returns 2
            every { vendorType } returns VendorType.EPIC
            every { instanceName } returns "instanceName2"
            every { clientId } returns "clientId2"
            every { publicKey } returns "publicKey2"
            every { privateKey } returns "privateKey3"
        }
        cernerEHRDO = mockk {
            every { id } returns 2
            every { vendorType } returns VendorType.CERNER
            every { instanceName } returns "instanceName2"
            every { clientId } returns "clientId2"
            every { publicKey } returns "wontBePopulatedNormally"
            every { accountId } returns "accountId"
            every { secret } returns "secret"
        }
    }

    @Test
    fun `read test`() {
        val foo: List<EhrDO> = listOf(epicEhrDO, epicEhrDO2, cernerEHRDO)
        every { dao.read() } returns foo

        val get = controller.read()
        assertTrue(get.body?.isNotEmpty() == true)
        assertEquals(3, get.body?.size)
        val firstDO = get.body!!.first()
        assertEquals(VendorType.EPIC, firstDO.vendorType)
        assertEquals("publicKey1", firstDO.publicKey)
        assertEquals("privateKey1", firstDO.privateKey)
        assertNull(firstDO.accountId)
        assertNull(firstDO.secret)

        val lastDo = get.body!!.last()
        assertEquals(VendorType.CERNER, lastDo.vendorType)
        assertEquals("accountId", lastDo.accountId)
        assertEquals("secret", lastDo.secret)
        assertNull(lastDo.publicKey)
        assertNull(lastDo.privateKey)
    }

    @Test
    fun `read empty test`() {
        val foo: List<EhrDO> = listOf()
        every { dao.read() } returns foo

        val get = controller.read()
        assertTrue(get.body!!.isEmpty())
    }

    @Test
    fun `insert test - epic`() {
        val ehr = Ehr(
            vendorType = VendorType.EPIC,
            instanceName = "instanceName",
            clientId = "clientId1",
            publicKey = "publicKey1",
            privateKey = "privateKey1"
        )
        every { dao.insert(any()) } returns epicEhrDO

        val post = controller.insert(ehr)
        assertEquals("publicKey1", post.body?.publicKey)
    }

    @Test
    fun `insert test - cerner`() {
        val ehr = Ehr(
            vendorType = VendorType.CERNER,
            instanceName = "instanceName",
            clientId = "clientId1",
            accountId = "accountId",
            secret = "secret"
        )
        every { dao.insert(any()) } returns cernerEHRDO

        val post = controller.insert(ehr)
        assertEquals("accountId", post.body?.accountId)
    }

    @Test
    fun `insert test - cerner no clientId`() {
        val ehr = Ehr(
            vendorType = VendorType.CERNER,
            instanceName = "instanceName",
            accountId = "accountId",
            secret = "secret"
        )
        every { dao.insert(any()) } returns cernerEHRDO

        val post = controller.insert(ehr)
        assertEquals("accountId", post.body?.accountId)
    }

    @Test
    fun `bad inserts throw errors`() {
        val cernerEHR = Ehr(
            vendorType = VendorType.CERNER,
            instanceName = "instanceName",
            clientId = "clientId1",
            publicKey = "publicKey1",
            privateKey = "privateKey1"
        )
        val cernerEHR2 = Ehr(
            vendorType = VendorType.CERNER,
            instanceName = "instanceName",
            clientId = "clientId1",
            accountId = "accountId"
        )
        val epicEhr = Ehr(
            vendorType = VendorType.EPIC,
            instanceName = "instanceName",
            clientId = "clientId1",
            accountId = "accountId",
            secret = "secret"
        )
        val epicEhr2 = Ehr(
            vendorType = VendorType.EPIC,
            instanceName = "instanceName",
            clientId = "clientId1",
            publicKey = "publicKey1"
        )

        assertThrows<IllegalStateException> { controller.insert(cernerEHR) }
        assertThrows<IllegalStateException> { controller.insert(cernerEHR2) }
        assertThrows<IllegalStateException> { controller.insert(epicEhr) }
        assertThrows<IllegalStateException> { controller.insert(epicEhr2) }
    }

    @Test
    fun `update test`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "instanceName",
            "clientId2",
            "publicKey2",
            "privateKey2"
        )
        every { dao.getByInstance(ehr.instanceName) } returns epicEhrDO2
        every { dao.update(any()) } returns epicEhrDO2

        val put = controller.update(ehr.instanceName, ehr)
        assertTrue(put.body?.clientId == "clientId2")
    }

    @Test
    fun `update fails due to missing EHR`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "instanceName",
            "clientId2",
            "publicKey2",
            "privateKey2"
        )
        every { dao.getByInstance(ehr.instanceName) } returns null

        assertThrows<NoEHRFoundException> {
            controller.update(ehr.instanceName, ehr)
        }
    }

    @Test
    fun `no ehr exception is handled`() {
        val exception = NoEHRFoundException("How did this happen")
        val response = controller.handleEHRException(exception)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Unable to find EHR", response.body)
    }

    @Test
    fun `generic exception is handled`() {
        val exception = SQLIntegrityConstraintViolationException("Oops")
        val response = controller.handleException(exception)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(exception.message, response.body)
    }
}
