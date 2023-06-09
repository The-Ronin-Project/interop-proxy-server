package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MirthTenantIT : BaseTenantControllerIT() {
    private val url = "$serverUrl/tenants/%s/mirth-config/"
    private val mirthTenantConfigDAO = MirthTenantConfigDAO(tenantDB)

    @BeforeEach
    fun insertBaseData() {
        populateTenantData()
    }

    private fun insert() {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        mirthTenantConfigDAO.insertConfig(
            MirthTenantConfigDO {
                tenant = tenantDO
                locationIds = "1,2,3"
            }
        )
    }

    @Test
    fun `get works`() {
        insert()
        val response = ProxyClient.get(url.format("epic"))

        val body = runBlocking { response.body<MirthTenantConfig>() }
        assertEquals(listOf("1", "2", "3"), body.locationIds)
    }

    @Test
    fun `get single fails`() {
        insert()

        val response = ProxyClient.get(url.format("notreal"))

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `insert works`() {
        val insertMirthConfig = MirthTenantConfig(
            locationIds = listOf("inserted", "inserted2")
        )

        val response = ProxyClient.post(url.format("epic"), insertMirthConfig)

        val body = runBlocking { response.body<MirthTenantConfig>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(insertMirthConfig.locationIds, body.locationIds)
    }

    @Test
    fun `insert dup fails`() {
        insert()
        val insertMirthConfig = MirthTenantConfig(
            locationIds = listOf("inserted", "inserted2")
        )
        val response = ProxyClient.post(url.format("epic"), insertMirthConfig)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `update works`() {
        insert()
        val updated = MirthTenantConfig(
            locationIds = listOf("updated", "updated2")
        )

        val response = ProxyClient.put(url.format("epic"), updated)

        val body = runBlocking { response.body<MirthTenantConfig>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updated.locationIds, body.locationIds)
    }

    @Test
    fun `update fails`() {
        insert()

        val updated = MirthTenantConfig(
            locationIds = listOf("updated", "updated2")
        )
        val response = ProxyClient.put(url.format("notreal"), updated)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
