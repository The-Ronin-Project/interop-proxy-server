package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.TenantCodes
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.TenantCodesDAO
import com.projectronin.interop.tenant.config.data.model.TenantCodesDO
import com.projectronin.interop.tenant.config.exception.NoTenantFoundException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

class TenantCodesControllerTest {
    private val testMnemonic = "test_tenant"
    private val tenantService = mockk<TenantService> {
        every { getTenantForMnemonic(testMnemonic) } returns
            mockk<Tenant> {
                every { internalId } returns 7734
            }
    }
    private val tenantCodesDAO = mockk<TenantCodesDAO>()
    private val tenantCodesController = TenantCodesController(tenantService, tenantCodesDAO)

    @Test
    fun `ensure tenant controller can read codes and return success`() {
        every { tenantCodesDAO.getByTenantMnemonic(testMnemonic) } returns TenantCodesDO {
            bsaCode = "2222"
            bmiCode = "33333"
            stageCodes = "2342,23432"
        }

        val result = tenantCodesController.get(testMnemonic)
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)

        val resultBody = result.body!!
        assertEquals("2222", resultBody.bsaCode)
        assertEquals("33333", resultBody.bmiCode)
        assertEquals("2342,23432", resultBody.stageCodes)
    }

    @Test
    fun `ensure tenant controller throws exception for unknown tenant in get`() {
        every { tenantCodesDAO.getByTenantMnemonic("not_real") } returns null

        assertThrows<NoTenantFoundException> {
            tenantCodesController.get("not_real")
        }
    }

    @Test
    fun `ensure tenant controller can insert codes and return success`() {
        val expectedCodesDO = TenantCodesDO {
            tenantId = 7734
            bsaCode = "2222"
            bmiCode = "33333"
            stageCodes = "2342,23432"
        }
        every { tenantCodesDAO.insertCodes(expectedCodesDO) } returns TenantCodesDO {
            bsaCode = "2222"
            bmiCode = "33333"
            stageCodes = "2342,23432"
        }

        val result = tenantCodesController.insert(
            testMnemonic,
            TenantCodes(bsaCode = "2222", bmiCode = "33333", stageCodes = "2342,23432")
        )
        assertNotNull(result)
        assertEquals(HttpStatus.CREATED, result.statusCode)

        val resultBody = result.body!!
        assertEquals("2222", resultBody.bsaCode)
        assertEquals("33333", resultBody.bmiCode)
        assertEquals("2342,23432", resultBody.stageCodes)
    }

    @Test
    fun `ensure tenant controller throws exception for unknown tenant in insert`() {
        every { tenantService.getTenantForMnemonic("not_real") } returns null

        assertThrows<NoTenantFoundException> {
            tenantCodesController.insert("not_real", TenantCodes(null, null, null))
        }
    }

    @Test
    fun `ensure tenant controller can update codes and return success`() {
        val expectedCodesDO = TenantCodesDO {
            tenantId = 7734
            bsaCode = "2222"
            bmiCode = "33333"
            stageCodes = "2342,23432"
        }
        every { tenantCodesDAO.updateCodes(expectedCodesDO) } returns TenantCodesDO {
            bsaCode = "2222"
            bmiCode = "33333"
            stageCodes = "2342,23432"
        }

        val result = tenantCodesController.update(
            testMnemonic,
            TenantCodes(bsaCode = "2222", bmiCode = "33333", stageCodes = "2342,23432")
        )
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)

        val resultBody = result.body!!
        assertEquals("2222", resultBody.bsaCode)
        assertEquals("33333", resultBody.bmiCode)
        assertEquals("2342,23432", resultBody.stageCodes)
    }

    @Test
    fun `ensure tenant controller update returns 400 for no existing codes`() {
        val expectedCodesDO = TenantCodesDO {
            tenantId = 7734
            bsaCode = "2222"
            bmiCode = "33333"
            stageCodes = "2342,23432"
        }
        every { tenantCodesDAO.updateCodes(expectedCodesDO) } returns null

        val result = tenantCodesController.update(
            testMnemonic,
            TenantCodes(bsaCode = "2222", bmiCode = "33333", stageCodes = "2342,23432")
        )
        assertNotNull(result)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)

        assertNull(result.body)
    }

    @Test
    fun `ensure tenant controller throws exception for unknown tenant on update`() {
        every { tenantService.getTenantForMnemonic("not_real") } returns null

        assertThrows<NoTenantFoundException> {
            tenantCodesController.update("not_real", TenantCodes(null, null, null))
        }
    }
}
