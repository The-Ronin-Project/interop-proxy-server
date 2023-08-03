package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.TenantMDMConfig
import com.projectronin.interop.tenant.config.data.TenantMDMConfigDAO
import com.projectronin.interop.tenant.config.data.model.TenantMDMConfigDO
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TenantMDMIT : BaseTenantControllerIT() {
    private val url = "$serverUrl/tenants/%s/hl7v2/mdm/"
    private val tenantMDMConfigDAO = TenantMDMConfigDAO(tenantDB)

    @BeforeEach
    fun insertBaseData() {
        populateTenantData()
    }

    private fun insert() {
        val tenantDO = tenantDAO.getTenantForMnemonic("epic")!!
        tenantMDMConfigDAO.insertConfig(
            TenantMDMConfigDO {
                tenant = tenantDO
                mdmDocumentTypeID = "1,2,3"
                providerIdentifierSystem = "4,5,6,7"
                receivingSystem = "8,9"
            }
        )
    }

    @Test
    fun `get works`() {
        insert()
        val response = ProxyClient.get(url.format("epic"))

        val body = runBlocking { response.body<TenantMDMConfig>() }
        assertEquals("1,2,3", body.mdmDocumentTypeID)
        assertEquals("4,5,6,7", body.providerIdentifierSystem)
        assertEquals("8,9", body.receivingSystem)
    }

    @Test
    fun `get single fails`() {
        insert()

        val response = ProxyClient.get(url.format("notreal"))

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `insert works`() {
        val insertMDMConfig = TenantMDMConfig(
            mdmDocumentTypeID = "typeidinsert",
            providerIdentifierSystem = "idsysteminsert",
            receivingSystem = "rsysteminsert"
        )

        val response = ProxyClient.post(url.format("epic"), insertMDMConfig)

        val body = runBlocking { response.body<TenantMDMConfig>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(insertMDMConfig.mdmDocumentTypeID, body.mdmDocumentTypeID)
        assertEquals(insertMDMConfig.providerIdentifierSystem, body.providerIdentifierSystem)
        assertEquals(insertMDMConfig.receivingSystem, body.receivingSystem)
    }

    @Test
    fun `insert dup fails`() {
        insert()
        val insertMDMConfig = TenantMDMConfig(
            mdmDocumentTypeID = "typeidinsert",
            providerIdentifierSystem = "idsysteminsert",
            receivingSystem = "rsysteminsert"
        )
        val response = ProxyClient.post(url.format("epic"), insertMDMConfig)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `update works`() {
        insert()
        val updated = TenantMDMConfig(
            mdmDocumentTypeID = "typeidinsert2",
            providerIdentifierSystem = "idsysteminsert2",
            receivingSystem = "rsysteminsert2"
        )

        val response = ProxyClient.put(url.format("epic"), updated)

        val body = runBlocking { response.body<TenantMDMConfig>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updated.mdmDocumentTypeID, body.mdmDocumentTypeID)
        assertEquals(updated.providerIdentifierSystem, body.providerIdentifierSystem)
        assertEquals(updated.receivingSystem, body.receivingSystem)
    }

    @Test
    fun `update fails`() {
        insert()

        val updated = TenantMDMConfig(
            mdmDocumentTypeID = "typeidinsert2",
            providerIdentifierSystem = "idsysteminsert2",
            receivingSystem = "rsysteminsert2"
        )
        val response = ProxyClient.put(url.format("notreal"), updated)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
