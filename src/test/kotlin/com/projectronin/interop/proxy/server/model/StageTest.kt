package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Condition.Stage as EHRStage
import com.projectronin.interop.ehr.model.Reference as EHRReference

internal class StageTest {
    private val mockTenant = relaxedMockk<Tenant>()

    @Test
    fun `can get summary`() {
        val ehrStage = relaxedMockk<EHRStage> {
            every { summary } returns relaxedMockk()
        }
        val stage = Stage(ehrStage, mockTenant)
        assertNotNull(stage.summary)
    }

    @Test
    fun `can get null summary`() {
        val ehrStage = relaxedMockk<EHRStage> {
            every { summary } returns null
        }
        val stage = Stage(ehrStage, mockTenant)
        assertNull(stage.summary)
    }

    @Test
    fun `can get assessment`() {
        val ehrReference1 = relaxedMockk<EHRReference>()
        val ehrReference2 = relaxedMockk<EHRReference>()
        val ehrStage = relaxedMockk<EHRStage> {
            every { assessment } returns listOf(ehrReference1, ehrReference2)
        }
        val stage = Stage(ehrStage, mockTenant)
        assertEquals(2, stage.assessment.size)
    }

    @Test
    fun `can get type`() {
        val ehrStage = relaxedMockk<EHRStage> {
            every { type } returns relaxedMockk()
        }
        val stage = Stage(ehrStage, mockTenant)
        assertNotNull(stage.type)
    }

    @Test
    fun `can get null type`() {
        val ehrStage = relaxedMockk<EHRStage> {
            every { type } returns null
        }
        val stage = Stage(ehrStage, mockTenant)
        assertNull(stage.type)
    }
}
