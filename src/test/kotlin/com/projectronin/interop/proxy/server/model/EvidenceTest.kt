package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.CodeableConcept as EHRCodeableConcept
import com.projectronin.interop.ehr.model.Condition.Evidence as EHREvidence
import com.projectronin.interop.ehr.model.Reference as EHRReference

internal class EvidenceTest {
    private val mockTenant = relaxedMockk<Tenant>()

    @Test
    fun `can get code`() {
        val ehrCodeableConcept1 = relaxedMockk<EHRCodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<EHRCodeableConcept>()
        val ehrEvidence = relaxedMockk<EHREvidence> {
            every { code } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
        }
        val evidence = Evidence(ehrEvidence, mockTenant)
        assertEquals(2, evidence.code.size)
    }

    @Test
    fun `can get detail`() {
        val ehrReference1 = relaxedMockk<EHRReference>()
        val ehrReference2 = relaxedMockk<EHRReference>()
        val ehrEvidence = relaxedMockk<EHREvidence> {
            every { detail } returns listOf(ehrReference1, ehrReference2)
        }
        val evidence = Evidence(ehrEvidence, mockTenant)
        assertEquals(2, evidence.detail.size)
    }
}
