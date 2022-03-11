package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.ehr.model.CodeableConcept
import com.projectronin.interop.ehr.model.Identifier
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.projectronin.interop.ehr.model.Annotation as EHRAnnotation
import com.projectronin.interop.ehr.model.Condition as EHRCondition
import com.projectronin.interop.ehr.model.Condition.Evidence as EHREvidence
import com.projectronin.interop.ehr.model.Condition.Stage as EHRStage
import com.projectronin.interop.ehr.model.Reference as EHRReference

internal class ConditionTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "ten"
    }

    @Test
    fun `can get id`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { id } returns "6789"
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals("ten-6789", condition.id)
    }

    @Test
    fun `can get identifier`() {
        val ehrIdentifier1 = relaxedMockk<Identifier>()
        val ehrIdentifier2 = relaxedMockk<Identifier>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.identifier.size)
    }

    @Test
    fun `can get clinical status`() {
        val ehrCodeableConcept = relaxedMockk<CodeableConcept>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { clinicalStatus } returns ehrCodeableConcept
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.clinicalStatus)
    }

    @Test
    fun `can get null clinical status`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { clinicalStatus } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.clinicalStatus)
    }

    @Test
    fun `can get verification status`() {
        val ehrCodeableConcept = relaxedMockk<CodeableConcept>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { verificationStatus } returns ehrCodeableConcept
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.verificationStatus)
    }

    @Test
    fun `can get null verification status`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { verificationStatus } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.verificationStatus)
    }

    @Test
    fun `can get category`() {
        val ehrCodeableConcept1 = relaxedMockk<CodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<CodeableConcept>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { category } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.category.size)
    }

    @Test
    fun `can get severity`() {
        val ehrCodeableConcept = relaxedMockk<CodeableConcept>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { severity } returns ehrCodeableConcept
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.severity)
    }

    @Test
    fun `can get null severity`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { severity } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.severity)
    }

    @Test
    fun `can get code`() {
        val ehrCodeableConcept = relaxedMockk<CodeableConcept>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { code } returns ehrCodeableConcept
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.code)
    }

    @Test
    fun `can get null code`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { code } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.code)
    }

    @Test
    fun `can get body site`() {
        val ehrCodeableConcept1 = relaxedMockk<CodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<CodeableConcept>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { bodySite } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.bodySite.size)
    }

    @Test
    fun `can get subject`() {
        val ehrReference = relaxedMockk<EHRReference>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { subject } returns ehrReference
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.subject)
    }

    @Test
    fun `can get encounter`() {
        val ehrReference = relaxedMockk<EHRReference>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { encounter } returns ehrReference
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.encounter)
    }

    @Test
    fun `can get null encounter`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { encounter } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.encounter)
    }

    @Test
    fun `can get null onset`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.onset)
    }

    @Test
    fun `can get date time onset`() {
        val ehrDateTimeOnset = mockk<EHRCondition.DateTimeOnset> {
            every { value } returns "2022-03-09"
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns ehrDateTimeOnset
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is DateTimeOnset)
    }

    @Test
    fun `can get age onset`() {
        val ehrAgeOnset = mockk<EHRCondition.AgeOnset> {
            every { value } returns relaxedMockk()
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns ehrAgeOnset
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is AgeOnset)
    }

    @Test
    fun `can get period onset`() {
        val ehrPeriodOnset = mockk<EHRCondition.PeriodOnset> {
            every { value } returns relaxedMockk()
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns ehrPeriodOnset
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is PeriodOnset)
    }

    @Test
    fun `can get range onset`() {
        val ehrRangeOnset = mockk<EHRCondition.RangeOnset> {
            every { value } returns relaxedMockk()
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns ehrRangeOnset
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is RangeOnset)
    }

    @Test
    fun `can get string onset`() {
        val ehrStringOnset = mockk<EHRCondition.StringOnset> {
            every { value } returns "recently"
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns ehrStringOnset
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is StringOnset)
    }

    @Test
    fun `throws exception on unknown onset type`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { onset } returns mockk()
        }
        val condition = Condition(ehrCondition, mockTenant)
        val exception = assertThrows<RuntimeException> {
            condition.onset
        }
        assertEquals("Unknown condition onset type encountered", exception.message)
    }

    @Test
    fun `can get null abatement`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.abatement)
    }

    @Test
    fun `can get date time abatement`() {
        val ehrDateTimeAbatement = mockk<EHRCondition.DateTimeAbatement> {
            every { value } returns "2022-03-09"
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns ehrDateTimeAbatement
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is DateTimeAbatement)
    }

    @Test
    fun `can get age abatement`() {
        val ehrAgeAbatement = mockk<EHRCondition.AgeAbatement> {
            every { value } returns relaxedMockk()
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns ehrAgeAbatement
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is AgeAbatement)
    }

    @Test
    fun `can get period abatement`() {
        val ehrPeriodAbatement = mockk<EHRCondition.PeriodAbatement> {
            every { value } returns relaxedMockk()
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns ehrPeriodAbatement
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is PeriodAbatement)
    }

    @Test
    fun `can get range abatement`() {
        val ehrRangeAbatement = mockk<EHRCondition.RangeAbatement> {
            every { value } returns relaxedMockk()
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns ehrRangeAbatement
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is RangeAbatement)
    }

    @Test
    fun `can get string abatement`() {
        val ehrStringAbatement = mockk<EHRCondition.StringAbatement> {
            every { value } returns "recently"
        }
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns ehrStringAbatement
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is StringAbatement)
    }

    @Test
    fun `throws exception on unknown abatement type`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { abatement } returns mockk()
        }
        val condition = Condition(ehrCondition, mockTenant)
        val exception = assertThrows<RuntimeException> {
            condition.abatement
        }
        assertEquals("Unknown condition abatement type encountered", exception.message)
    }

    @Test
    fun `can get recorded date`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { recordedDate } returns "2022-03-09"
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals("2022-03-09", condition.recordedDate)
    }

    @Test
    fun `can get recorder`() {
        val ehrReference = relaxedMockk<EHRReference>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { recorder } returns ehrReference
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.recorder)
    }

    @Test
    fun `can get null recorder`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { recorder } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.recorder)
    }

    @Test
    fun `can get asserter`() {
        val ehrReference = relaxedMockk<EHRReference>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { asserter } returns ehrReference
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.asserter)
    }

    @Test
    fun `can get null asserter`() {
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { asserter } returns null
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.asserter)
    }

    @Test
    fun `can get stage`() {
        val ehrStage1 = relaxedMockk<EHRStage>()
        val ehrStage2 = relaxedMockk<EHRStage>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { stage } returns listOf(ehrStage1, ehrStage2)
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.stage.size)
    }

    @Test
    fun `can get evidence`() {
        val ehrEvidence1 = relaxedMockk<EHREvidence>()
        val ehrEvidence2 = relaxedMockk<EHREvidence>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { evidence } returns listOf(ehrEvidence1, ehrEvidence2)
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.evidence.size)
    }

    @Test
    fun `can get note`() {
        val ehrAnnotation1 = relaxedMockk<EHRAnnotation>()
        val ehrAnnotation2 = relaxedMockk<EHRAnnotation>()
        val ehrCondition = relaxedMockk<EHRCondition> {
            every { note } returns listOf(ehrAnnotation1, ehrAnnotation2)
        }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.note.size)
    }
}
