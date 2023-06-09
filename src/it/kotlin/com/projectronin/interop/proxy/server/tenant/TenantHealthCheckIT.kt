package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.client.ProxyClient
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TenantHealthCheckIT : BaseTenantControllerIT() {

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `can health check each tenant`(mnemonic: String) {
        populateTenantData()

        val response = ProxyClient.get("$serverUrl/tenants/$mnemonic/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `can check health of all monitored tenants`() {
        populateTenantData()
        val response = ProxyClient.get("$serverUrl/tenants/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
