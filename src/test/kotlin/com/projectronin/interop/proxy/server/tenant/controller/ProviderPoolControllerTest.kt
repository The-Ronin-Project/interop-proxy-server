package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderPoolControllerTest {
    private lateinit var controller: ProviderPoolController

    @Test
    fun `read test`() {
        // TODO
        val dao = mockk<ProviderPoolDAO>()
        val providerIdList: List<String> = listOf("Id1", "Id2", "Id3")
        controller = ProviderPoolController(dao)
        val get: List<ProviderPool> = controller.get(1, providerIdList)
        assertTrue(get.isNotEmpty())
        assertTrue(get.size == 2)
    }

    @Test
    fun `insert test`() {
        // TODO
        val dao = mockk<ProviderPoolDAO>()
        controller = ProviderPoolController(dao)
        val providerPool = ProviderPool(
            12345,
            "providerId1",
            "poolId1"
        )
        val inserted = controller.insert(1, providerPool)
        assertTrue(inserted.isNotEmpty())
        assertEquals("success, id: ?", inserted)
    }

    @Test
    fun `update test`() {
        // TODO
        val dao = mockk<ProviderPoolDAO>()
        controller = ProviderPoolController(dao)
        val providerPool = ProviderPool(
            12345,
            "providerId1",
            "poolId1"
        )
        val updated = controller.update(1, 2, providerPool)
        assertTrue(updated.isNotEmpty())
        assertEquals("success, row ? updated", updated)
    }

    @Test
    fun `delete test`() {
        // TODO
        val dao = mockk<ProviderPoolDAO>()
        controller = ProviderPoolController(dao)
        val deleted = controller.delete(1, 1)
        assertTrue(deleted.isNotEmpty())
        assertEquals("success, row ? deleted", deleted)
    }
}
