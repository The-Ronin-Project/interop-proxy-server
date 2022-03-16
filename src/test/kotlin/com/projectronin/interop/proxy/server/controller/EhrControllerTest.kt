package com.projectronin.interop.proxy.server.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EhrControllerTest {
    private lateinit var controller: EhrController

    @Test
    fun `read test`() {
        // TODO
        val dao = mockk<EhrDAO>()
        val ehrDO = mockk<EhrDO> {
            every { id } returns 1
            every { vendorType } returns VendorType.EPIC
            every { clientId } returns "clientId"
            every { publicKey } returns "publicKey"
            every { privateKey } returns "privateKey"
        }
        val ehrDO2 = mockk<EhrDO> {
            every { id } returns 2
            every { vendorType } returns VendorType.EPIC
            every { clientId } returns "clientId2"
            every { publicKey } returns "publicKey2"
            every { privateKey } returns "privateKey3"
        }
        val foo: List<EhrDO> = listOf(ehrDO, ehrDO2)
        every { dao.read() } returns foo
        controller = EhrController(dao)
        val get: List<Ehr> = controller.read()
        assertTrue(get.isNotEmpty())
        assertTrue(get.size == 2)
    }

    @Test
    fun `insert test`() {
        // TODO
        val ehr = Ehr(
            VendorType.EPIC,
            "client",
            "public",
            "private"
        )
        val post: Ehr = controller.insert(ehr)
        assertTrue(post.clientId == "clientID")
    }

    @Test
    fun `update test`() {
        // TODO
        val ehr = Ehr(
            VendorType.EPIC,
            "client",
            "public",
            "private"
        )
        val post: Ehr = controller.update(ehr)
        assertTrue(post.clientId == "UpdatedClientID")
    }
}
