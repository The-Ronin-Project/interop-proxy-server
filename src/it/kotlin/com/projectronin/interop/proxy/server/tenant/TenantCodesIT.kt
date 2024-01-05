package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.TenantCodes
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
                stageCodes = "staging_code_1,stage_code_2"
            },
        )
        val response = ProxyClient.get("$serverUrl/tenants/epic/codes")

        val body = runBlocking { response.body<Map<String, String>>() }
        val validCodes =
            mapOf(
                "bmiCode" to "bmi_code_value",
                "bsaCode" to "bsa_code_value",
                "stageCodes" to "staging_code_1,stage_code_2",
            )
        assertEquals(3, body.size)
        body.forEach {
            assertTrue(it.key in validCodes.keys)
            assertEquals(validCodes[it.key], it.value)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `can insert tenant codes`() {
        populateTenantData()
        val response =
            ProxyClient.post(
                "$serverUrl/tenants/epic/codes",
                TenantCodes(
                    bsaCode = "bsa_code_value",
                    bmiCode = "bmi_code_value",
                    stageCodes = "staging_code_value_1,stage_code_value_2",
                ),
            )

        val body = runBlocking { response.body<TenantCodes>() }
        assertEquals("bsa_code_value", body.bsaCode)
        assertEquals("bmi_code_value", body.bmiCode)
        assertEquals("staging_code_value_1,stage_code_value_2", body.stageCodes)
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `can update tenant codes`() {
        populateTenantData()
        val tenantCodesDAO = TenantCodesDAO(tenantDB)
        val tenantDo = tenantDAO.getTenantForMnemonic("epic")!!
        tenantCodesDAO.insertCodes(
            TenantCodesDO {
                tenantId = tenantDo.id
                bsaCode = "bsa_code_value"
                bmiCode = "bmi_code_value"
                stageCodes = "staging_code_1,stage_code_2"
            },
        )
        val response =
            ProxyClient.put(
                "$serverUrl/tenants/epic/codes",
                TenantCodes(
                    bsaCode = "bsa_code_value_2",
                    bmiCode = "bmi_code_value_2",
                    stageCodes = "staging_code_value_A,stage_code_value_B",
                ),
            )

        val body = runBlocking { response.body<TenantCodes>() }
        assertEquals("bsa_code_value_2", body.bsaCode)
        assertEquals("bmi_code_value_2", body.bmiCode)
        assertEquals("staging_code_value_A,stage_code_value_B", body.stageCodes)
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
