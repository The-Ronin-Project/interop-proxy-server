package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.model.ProviderPoolDO
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProviderPoolIT : BaseTenantControllerIT() {
    private val url = "$serverUrl/tenants/%s/pools?providerIds=%s"
    private val baseUrl = "$serverUrl/tenants/%s/pools"
    private val urlByPath = "$serverUrl/tenants/%s/pools/%s"
    private val providerPoolDAO = ProviderPoolDAO(tenantDB)

    @BeforeEach
    fun insertBaseData() {
        populateTenantData()
    }

    private fun insert() {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        providerPoolDAO.insert(
            ProviderPoolDO {
                tenant = tenantDO
                providerId = "ProviderWithPool"
                poolId = "14600"
            },
        )
    }

    fun getCurrentProviderPoolID(): Int {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        return providerPoolDAO.getAll(tenantDO.id).first().id
    }

    @Test
    fun `get provider pools`() {
        insert()
        val response = ProxyClient.get(url.format("epic", "ProviderWithPool"))

        val body = runBlocking { response.body<List<ProviderPool>>() }
        assertEquals(1, body.size)
        assertEquals("ProviderWithPool", body[0].providerId)
        assertEquals("14600", body[0].poolId)
    }

    @Test
    fun `get all provider pools`() {
        insert()
        val response = ProxyClient.get(baseUrl.format("epic"))

        val body = runBlocking { response.body<List<ProviderPool>>() }
        assertEquals(1, body.size)
        assertEquals("ProviderWithPool", body[0].providerId)
        assertEquals("14600", body[0].poolId)
    }

    @Test
    fun `get provider pools for bad provider`() {
        insert()
        val response = ProxyClient.get(url.format("epic", "Fake"))

        val body = runBlocking { response.body<List<ProviderPool>>() }
        assertEquals(0, body.size)
    }

    @Test
    fun `get provider pools for good and bad providers`() {
        val providerIds = listOf("FakeProvider", "ProviderWithPool").joinToString(",")
        insert()
        val response = ProxyClient.get(url.format("epic", providerIds))

        val body = runBlocking { response.body<List<ProviderPool>>() }
        assertEquals(1, body.size)
        assertEquals("ProviderWithPool", body[0].providerId)
        assertEquals("14600", body[0].poolId)
    }

    @Test
    fun `insert provider pool`() {
        insert()
        val insertProviderPool =
            ProviderPool(
                providerPoolId = 0,
                providerId = "NewProviderId",
                poolId = "NewPoolId",
            )
        val response = ProxyClient.post(baseUrl.format("epic"), insertProviderPool)

        val body = runBlocking { response.body<ProviderPool>() }
        assertEquals("NewProviderId", body.providerId)
        assertEquals("NewPoolId", body.poolId)
    }

    @Test
    fun `insert duplicate provider for tenant`() {
        insert()
        val insertProviderPool =
            ProviderPool(
                providerPoolId = 0,
                providerId = "ProviderWithPool",
                poolId = "NewPoolId",
            )
        val response = ProxyClient.post(baseUrl.format("epic"), insertProviderPool)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `update existing providers pool`() {
        insert()
        val providerPoolId = getCurrentProviderPoolID()
        val updateProviderPool =
            ProviderPool(
                providerPoolId = providerPoolId,
                providerId = "NewProviderId",
                poolId = "NewPoolId",
            )
        val response = ProxyClient.put(urlByPath.format("epic", providerPoolId), updateProviderPool)

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `update non-existing providers pool`() {
        insert()
        val insertProviderPool =
            ProviderPool(
                providerPoolId = 0,
                providerId = "NewProviderId",
                poolId = "NewPoolId",
            )
        val response = ProxyClient.put(urlByPath.format("epic", 54321), insertProviderPool)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete existing provider pool`() {
        insert()
        val poolId = getCurrentProviderPoolID()

        val response = ProxyClient.delete(urlByPath.format("epic", poolId))

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `delete non-existing provider pool`() {
        val response = ProxyClient.delete(urlByPath.format("epic", 1231231))
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
