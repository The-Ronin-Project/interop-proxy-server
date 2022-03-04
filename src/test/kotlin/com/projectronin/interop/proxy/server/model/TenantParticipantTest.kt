package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Participant as EHRParticipant

class TenantParticipantTest {
    @Test
    fun `check getters`() {
        val mockkTenant = mockk<Tenant>()
        val mockParticipant = mockk<EHRParticipant>()
        val testTenant = TenantParticipant(mockkTenant, mockParticipant)
        assertEquals(mockkTenant, testTenant.tenant)
        assertEquals(mockParticipant, testTenant.participant)
    }
}
