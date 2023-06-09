package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.tenant.config.data.TenantCodesDAO
import com.projectronin.interop.tenant.config.data.model.TenantCodesDO
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TenantCodesIT : BaseTenantControllerIT() {
    @Test
    fun `can retrieve tenant codes`() {
        populateTenantData()
        val tenantCodesDAO = TenantCodesDAO(tenantDB)
        val tenantDo = tenantDAO.getTenantForMnemonic("epic")!!
        tenantCodesDAO.insertCodes(
            TenantCodesDO {
                tenantId = tenantDo.id
                bsaCode = "bsa_code_value"
                bmiCode = "bmi_code_value"
            }
        )
        val response = ProxyClient.get("$serverUrl/tenants/epic/codes")

        val body = runBlocking { response.body<Map<String, String>>() }
        val validCodes = mapOf("bmiCode" to "bmi_code_value", "bsaCode" to "bsa_code_value")
        assertEquals(2, body.size)
        body.forEach {
            assertTrue(it.key in validCodes.keys)
            assertEquals(validCodes[it.key], it.value)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
