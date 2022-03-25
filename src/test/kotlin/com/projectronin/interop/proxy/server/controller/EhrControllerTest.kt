package com.projectronin.interop.proxy.server.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EhrControllerTest {
    private lateinit var controller: EhrController
    private lateinit var dao: EhrDAO
    private lateinit var ehrDO: EhrDO
    private lateinit var ehrDO2: EhrDO

    @BeforeAll
    fun initTest() {
        dao = mockk<EhrDAO>()
        controller = EhrController(dao)
        ehrDO = mockk<EhrDO> {
            every { id } returns 1
            every { vendorType } returns VendorType.EPIC
            every { clientId } returns "clientId1"
            every { publicKey } returns "publicKey1"
            every { privateKey } returns "privateKey1"
        }
        ehrDO2 = mockk<EhrDO> {
            every { id } returns 2
            every { vendorType } returns VendorType.EPIC
            every { clientId } returns "clientId2"
            every { publicKey } returns "publicKey2"
            every { privateKey } returns "privateKey3"
        }
    }

    @Test
    fun `read test`() {
        val foo: List<EhrDO> = listOf(ehrDO, ehrDO2)
        every { dao.read() } returns foo

        val get: List<Ehr> = controller.read()
        assertTrue(get.isNotEmpty())
        assertTrue(get.size == 2)
    }

    @Test
    fun `read empty test`() {
        val foo: List<EhrDO> = listOf()
        every { dao.read() } returns foo

        val get: List<Ehr> = controller.read()
        assertTrue(get.isEmpty())
    }

    @Test
    fun `insert test`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "clientId1",
            "publicKey1",
            "privateKey1"
        )
        every { dao.insert(any()) } returns ehrDO

        val post: Ehr? = controller.insert(ehr)
        assertTrue(post?.clientId == "clientId1")
    }

    @Test
    fun `insert fails`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "clientId1",
            "publicKey1",
            "privateKey1"
        )
        every { dao.insert(any()) } returns null

        val post: Ehr? = controller.insert(ehr)
        assertNull(post)
    }

    @Test
    fun `update test`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "clientId2",
            "publicKey2",
            "privateKey2"
        )
        every { dao.update(any()) } returns ehrDO2

        val put: Ehr? = controller.update(ehr)
        assertTrue(put?.clientId == "clientId2")
    }
    @Test
    fun `update fails`() {
        val ehr = Ehr(
            VendorType.EPIC,
            "clientId1",
            "publicKey1",
            "privateKey1"
        )
        every { dao.update(any()) } returns null

        val put: Ehr? = controller.update(ehr)
        assertNull(put)
    }
}
