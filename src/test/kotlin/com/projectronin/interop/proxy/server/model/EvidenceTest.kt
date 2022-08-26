package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept as R4CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.ConditionEvidence as R4Evidence
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

internal class EvidenceTest {
    private val mockTenant = relaxedMockk<Tenant>()

    @Test
    fun `can get code`() {
        val ehrCodeableConcept1 = relaxedMockk<R4CodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<R4CodeableConcept>()
        val ehrEvidence = relaxedMockk<R4Evidence> {
            every { code } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
        }
        val evidence = Evidence(ehrEvidence, mockTenant)
        assertEquals(2, evidence.code.size)
    }

    @Test
    fun `can get detail`() {
        val ehrReference1 = relaxedMockk<R4Reference>()
        val ehrReference2 = relaxedMockk<R4Reference>()
        val ehrEvidence = relaxedMockk<R4Evidence> {
            every { detail } returns listOf(ehrReference1, ehrReference2)
        }
        val evidence = Evidence(ehrEvidence, mockTenant)
        assertEquals(2, evidence.detail.size)
    }
}
