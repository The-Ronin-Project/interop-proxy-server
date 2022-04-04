package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.ProviderPool
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.model.ProviderPoolDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException

class ProviderPoolControllerTest {
    private lateinit var dao: ProviderPoolDAO
    private lateinit var controller: ProviderPoolController

    @BeforeEach
    fun setup() {
        dao = mockk()
        controller = ProviderPoolController(dao)
    }

    @Test
    fun `handles get`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        every { dao.getPoolsForProviders(tenantId, listOf(providerId)) } returns listOf(
            ProviderPoolDO {
                id = 1
                tenant = TenantDO {
                    id = tenantId
                }
                this.providerId = providerId
                this.poolId = poolId
            }
        )
        val providerPool = ProviderPool(providerPoolId.toLong(), providerId, poolId)

        val response = controller.get(tenantId, listOf(providerId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(providerPool), response.body)
    }

    @Test
    fun `handles get all`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        every { dao.getAll(tenantId) } returns listOf(
            ProviderPoolDO {
                id = 1
                tenant = TenantDO {
                    id = tenantId
                }
                this.providerId = providerId
                this.poolId = poolId
            }
        )
        val providerPool = ProviderPool(providerPoolId.toLong(), providerId, poolId)

        val response = controller.get(tenantId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(providerPool), response.body)
    }

    @Test
    fun `handles successful insert`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }
        val providerPool = ProviderPool(providerPoolId.toLong(), providerId, poolId)

        every { dao.insert(providerPoolDO) } returns providerPoolDO
        val response = controller.insert(providerPoolId, providerPool)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(providerPool, response.body)
    }

    @Test
    fun `handles failed insert due to integrity violation`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }
        val providerPool = ProviderPool(providerPoolId.toLong(), providerId, poolId)

        every { dao.insert(providerPoolDO) } throws SQLIntegrityConstraintViolationException("error")
        val response = controller.insert(providerPoolId, providerPool)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }

    @Test
    fun `handles failed insert due to unknown exception`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }
        val providerPool = ProviderPool(providerPoolId.toLong(), providerId, poolId)

        every { dao.insert(providerPoolDO) } throws Exception("error")
        val response = controller.insert(providerPoolId, providerPool)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `handles successful update`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }

        every { dao.update(providerPoolDO) } returns 1
        val response =
            controller.update(tenantId, providerPoolId, ProviderPool(providerPoolId.toLong(), providerId, poolId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Success, row 1 updated", response.body)
    }

    @Test
    fun `handles bad update due to mis-matched provider pool`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val response = controller.update(tenantId, providerPoolId, ProviderPool(2, providerId, poolId))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Pool ID in path must match pool ID in request body.", response.body)
    }

    @Test
    fun `handles failed update due to pool not found`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }

        every { dao.update(providerPoolDO) } returns 0
        val response =
            controller.update(tenantId, providerPoolId, ProviderPool(providerPoolId.toLong(), providerId, poolId))

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("Failed to update row", response.body)
    }

    @Test
    fun `handles bad update due to constraint violation`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }

        every { dao.update(providerPoolDO) } throws SQLIntegrityConstraintViolationException("error")
        val response = controller.update(tenantId, providerPoolId, ProviderPool(providerPoolId.toLong(), providerId, poolId))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Update violates data integrity constraint.", response.body)
    }

    @Test
    fun `handles bad update due to multiple pools found`() {
        val tenantId = 1
        val providerPoolId = 1
        val providerId = "providerId"
        val poolId = "poolid"

        val providerPoolDO = ProviderPoolDO {
            id = providerPoolId.toLong()
            tenant = TenantDO {
                id = tenantId
            }
            this.providerId = providerId
            this.poolId = poolId
        }

        every { dao.update(providerPoolDO) } returns 2
        val response = controller.update(tenantId, providerPoolId, ProviderPool(providerPoolId.toLong(), providerId, poolId))

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("Failed to update row", response.body)
    }

    @Test
    fun `handles successful delete`() {
        every { dao.delete(1) } returns 1
        val response = controller.delete(1, 1)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Success, row 1 deleted", response.body)
    }

    @Test
    fun `handles non-successful delete due to pool not found`() {
        every { dao.delete(1) } returns 0
        val response = controller.delete(1, 1)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("Failed to delete row", response.body)
    }

    @Test
    fun `handles non-successful delete that violates integrity`() {
        every { dao.delete(1) } throws SQLIntegrityConstraintViolationException("error")
        val response = controller.delete(1, 1)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Update violates data integrity constraint.", response.body)
    }

    @Test
    fun `handles non-successful delete due to multiple pools found`() {
        every { dao.delete(1) } returns 2
        val response = controller.delete(1, 1)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("Failed to delete row", response.body)
    }
}
