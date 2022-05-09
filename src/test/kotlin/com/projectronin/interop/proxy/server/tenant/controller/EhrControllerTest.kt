package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EhrControllerTest {
    private lateinit var controller: EhrController
    private lateinit var dao: EhrDAO
    private lateinit var ehrDO: EhrDO
    private lateinit var ehrDO2: EhrDO

    @BeforeAll
    fun initTest() {
        dao = mockk()
        controller = EhrController(dao)
        ehrDO = mockk {
            every { id } returns 1
            every { vendorType } returns VendorType.EPIC
            every { instanceName } returns "instanceName1"
            every { clientId } returns "clientId1"
            every { publicKey } returns "publicKey1"
            every { privateKey } returns "privateKey1"
        }
        ehrDO2 = mockk {
            every { id } returns 2
            every { vendorType } returns VendorType.EPIC
            every { instanceName } returns "instanceName2"
            every { clientId } returns "clientId2"
            every { publicKey } returns "publicKey2"
            every { privateKey } returns "privateKey3"
        }
    }

    @Test
    fun `read test`() {
        val foo: List<EhrDO> = listOf(ehrDO, ehrDO2)
        every { dao.read() } returns foo

        val get = controller.read()
        assertTrue(get.body!!.isNotEmpty())
        assertTrue(get.body!!.size == 2)
    }

    @Test
    fun `read empty test`() {
        val foo: List<EhrDO> = listOf()
        every { dao.read() } returns foo

        val get = controller.read()
        assertTrue(get.body!!.isEmpty())
    }

    @Test
    fun `insert test`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "instanceName",
            "clientId1",
            "publicKey1",
            "privateKey1"
        )
        every { dao.insert(any()) } returns ehrDO

        val post = controller.insert(ehr)
        assertTrue(post.body?.clientId == "clientId1")
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
        every { dao.update(any()) } returns ehrDO2

        val put = controller.update(ehr)
        assertTrue(put.body?.clientId == "clientId2")
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
