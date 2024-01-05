package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriUtils

class EhrIT : BaseTenantControllerIT() {
    private val urlPart = "/ehrs"
    private val url = "$serverUrl$urlPart"

    @Test
    fun `get if db is empty`() {
        val response = ProxyClient.get(url)
        val body = runBlocking { response.body<List<Ehr>>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, body.size)
    }

    @Test
    fun `get`() {
        populateTenantData()
        val response = ProxyClient.get(url)
        val body = runBlocking { response.body<List<Ehr>>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, body.size)
    }

    @Test
    fun `post ehr when none exists - epic`() {
        val testEhr =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "Epic Sandbox",
                clientId = "clientID",
                publicKey = "publicKey",
                privateKey = "privateKey",
            )
        val response = ProxyClient.post(url, testEhr)

        val body = runBlocking { response.body<Ehr>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("clientID", body.clientId)
    }

    @Test
    fun `post ehr when none exists - cerner`() {
        val testEhr =
            Ehr(
                vendorType = VendorType.CERNER,
                instanceName = "Cerner Sandbox",
                clientId = "clientID",
                accountId = "accountId",
                secret = "secret",
            )
        val response = ProxyClient.post(url, testEhr)
        val body = runBlocking { response.body<Ehr>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("clientID", body.clientId)
    }

    @Test
    fun `post ehr when none exists - cerner system auth`() {
        val testEhr =
            Ehr(
                vendorType = VendorType.CERNER,
                instanceName = "Cerner Sandbox",
                accountId = "accountId",
                secret = "secret",
            )
        val response = ProxyClient.post(url, testEhr)
        val body = runBlocking { response.body<Ehr>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertNull(body.clientId)
    }

    @Test
    fun `post fails when ehr exists`() {
        populateTenantData()
        val testEhr =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "Epic Sandbox",
                clientId = "clientID",
                publicKey = "publicKey",
                privateKey = "privateKey",
            )
        val response = ProxyClient.post(url, testEhr)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `post fails when missing needed values`() {
        val testEhr =
            Ehr(
                vendorType = VendorType.CERNER,
                instanceName = "Epic Sandbox",
                clientId = "clientID",
                publicKey = "publicKey",
                privateKey = "privateKey",
            )
        val response = ProxyClient.post(url, testEhr)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `put updates existing ehr`() {
        populateTenantData()
        val updatedEhr =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "Epic Sandbox",
                clientId = "updated",
                publicKey = "publicKey",
                privateKey = "privateKey",
            )
        val fullUrl = "$url/${UriUtils.encodePathSegment("Epic Sandbox", Charsets.UTF_8)}"
        val response = ProxyClient.put(fullUrl, updatedEhr)

        val body = runBlocking { response.body<Ehr>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("updated", body.clientId)
    }

    @Test
    fun `put fails when ehr does not exist`() {
        val testEhr =
            Ehr(
                vendorType = VendorType.EPIC,
                instanceName = "Epic Sandbox",
                clientId = "updated",
                publicKey = "publicKey",
                privateKey = "privateKey",
            )
        val fullUrl = "$url/${UriUtils.encodePathSegment("Epic Sandbox", Charsets.UTF_8)}"
        val response = ProxyClient.put(fullUrl, testEhr)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
