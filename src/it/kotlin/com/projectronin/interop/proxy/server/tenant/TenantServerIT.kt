package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.hl7.ProcessingID
import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.TenantServer
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import com.projectronin.interop.tenant.config.data.model.TenantServerDO
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TenantServerIT : BaseTenantControllerIT() {
    private val urlPart = "/tenants/%s/tenant-server"
    private val url = "$serverUrl$urlPart"
    private val urlFull = "$serverUrl$urlPart/%s"

    private val tenantServerDAO = TenantServerDAO(tenantDB)

    @BeforeEach
    fun insertBaseData() {
        populateTenantData()
    }
    private fun insert() {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        tenantServerDAO.insertTenantServer(
            TenantServerDO {
                tenant = tenantDO
                messageType = MessageType.MDM
                address = "google.com"
                port = 1010
                serverType = ProcessingID.NONPRODUCTIONTESTING
            }
        )
    }

    @Test
    fun `get works`() {
        insert()

        val response = ProxyClient.get(url.format("epic"))

        val body = runBlocking { response.body<List<TenantServer>>() }
        assertEquals("google.com", body.first().address)
    }

    @Test
    fun `get works with type`() {
        insert()
        val response = ProxyClient.get(urlFull.format("epic", "MDM"))

        val body = runBlocking { response.body<TenantServer>() }
        assertEquals("google.com", body.address)
    }

    @Test
    fun `get works with type fails for bad tenant`() {
        insert()
        val response = ProxyClient.get(urlFull.format("fake", "MDM"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get works with type fails for bad type`() {
        insert()
        val response = ProxyClient.get(urlFull.format("epic", "AAA"))

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `insert works`() {
        val tenantServer = TenantServer(
            messageType = "MDM",
            address = "google.com",
            port = 1010,
            serverType = "N"
        )

        val response = ProxyClient.post(url.format("epic"), tenantServer)

        val body = runBlocking { response.body<TenantServer>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("google.com", body.address)
    }

    @Test
    fun `insert fails with duplicate`() {
        insert()
        val tenantServer = TenantServer(
            messageType = "MDM",
            address = "google.com",
            port = 1010,
            serverType = "N"
        )
        val response = ProxyClient.post(url.format("epic"), tenantServer)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `update works`() {
        insert()
        val existing = tenantServerDAO.getTenantServers("epic", MessageType.MDM).first()
        val tenantServer = TenantServer(
            id = existing.id,
            messageType = "MDM",
            address = "new",
            port = 1010,
            serverType = "N"
        )

        val response = ProxyClient.put(url.format("epic"), tenantServer)

        val body = runBlocking { response.body<TenantServer>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("new", body.address)
    }

    @Test
    fun `update fails when it's not already present`() {
        val tenantServer = TenantServer(
            messageType = "MDM",
            address = "new",
            port = 1010,
            serverType = "N"
        )

        val response = ProxyClient.put(url.format("epic"), tenantServer)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
