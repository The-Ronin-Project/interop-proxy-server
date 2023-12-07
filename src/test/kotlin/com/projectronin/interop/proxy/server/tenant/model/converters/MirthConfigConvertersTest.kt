package com.projectronin.interop.proxy.server.tenant.model.converters
import com.projectronin.interop.proxy.server.tenant.model.MirthTenantConfig
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MirthConfigConvertersTest {
    @Test
    fun `toProxyMirthTenantConfig works`() {
        val config =
            MirthTenantConfigDO {
                locationIds = "123,123,123"
                lastUpdated =
                    OffsetDateTime.of(
                        2023,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        ZoneOffset.UTC,
                    )
                blockedResources = "beep,boop,bop"
                tenant =
                    TenantDO {
                        id = 1
                    }
            }
        val proxyConfig = config.toProxyMirthTenantConfig()
        assertNotNull(proxyConfig)
        assertEquals(3, proxyConfig.locationIds.size)
        assertEquals(config.lastUpdated, proxyConfig.lastUpdated)
        assertEquals(3, proxyConfig.blockedResources.size)
    }

    @Test
    fun `toProxyMirthTenantConfig works for empty String`() {
        val config =
            MirthTenantConfigDO {
                locationIds = ""
                tenant =
                    TenantDO {
                        id = 1
                    }
                lastUpdated = null
                blockedResources = ""
            }
        val proxyConfig = config.toProxyMirthTenantConfig()
        assertNotNull(proxyConfig)
        assertEquals(0, proxyConfig.locationIds.size)
        assertNull(proxyConfig.lastUpdated)
        assertEquals(0, proxyConfig.blockedResources.size)
    }

    @Test
    fun `toMirthTenantConfigDO works `() {
        val tenant =
            mockk<Tenant> {
                every { id } returns 1
            }
        val proxyConfig =
            MirthTenantConfig(
                locationIds = listOf("123", "1231", "123123"),
                lastUpdated =
                    OffsetDateTime.of(
                        2023,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        ZoneOffset.UTC,
                    ),
                blockedResources = listOf("beep", "boop", "bop"),
            )
        val configDo = proxyConfig.toMirthTenantConfigDO(tenant)
        assertNotNull(configDo)
        assertEquals("123,1231,123123", configDo.locationIds)
        assertEquals(proxyConfig.lastUpdated, configDo.lastUpdated)
        assertEquals("beep,boop,bop", configDo.blockedResources)
    }
}
