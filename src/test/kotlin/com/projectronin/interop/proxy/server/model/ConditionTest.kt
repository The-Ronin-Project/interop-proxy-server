package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
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
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age
import com.projectronin.interop.fhir.r4.datatype.Annotation as R4Annotation
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept as R4CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference
import com.projectronin.interop.fhir.r4.resource.Condition as R4Condition
import com.projectronin.interop.fhir.r4.resource.ConditionEvidence as R4ConditionEvidence
import com.projectronin.interop.fhir.r4.resource.ConditionStage as R4ConditionStage

internal class ConditionTest {
    private val mockTenant =
        mockk<Tenant> {
            every { mnemonic } returns "ten"
        }

    @Test
    fun `can get id`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { id } returns Id("6789")
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals("ten-6789", condition.id)
    }

    @Test
    fun `can get identifier`() {
        val ehrIdentifier1 = relaxedMockk<R4Identifier>()
        val ehrIdentifier2 = relaxedMockk<R4Identifier>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { identifier } returns listOf(ehrIdentifier1, ehrIdentifier2)
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.identifier.size)
    }

    @Test
    fun `can get clinical status`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { clinicalStatus } returns ehrCodeableConcept
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.clinicalStatus)
    }

    @Test
    fun `can get null clinical status`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { clinicalStatus } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.clinicalStatus)
    }

    @Test
    fun `can get verification status`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { verificationStatus } returns ehrCodeableConcept
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.verificationStatus)
    }

    @Test
    fun `can get null verification status`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { verificationStatus } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.verificationStatus)
    }

    @Test
    fun `can get category`() {
        val ehrCodeableConcept1 = relaxedMockk<R4CodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<R4CodeableConcept>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { category } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.category.size)
    }

    @Test
    fun `can get severity`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { severity } returns ehrCodeableConcept
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.severity)
    }

    @Test
    fun `can get null severity`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { severity } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.severity)
    }

    @Test
    fun `can get code`() {
        val ehrCodeableConcept = relaxedMockk<R4CodeableConcept>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { code } returns ehrCodeableConcept
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.code)
    }

    @Test
    fun `can get null code`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { code } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.code)
    }

    @Test
    fun `can get body site`() {
        val ehrCodeableConcept1 = relaxedMockk<R4CodeableConcept>()
        val ehrCodeableConcept2 = relaxedMockk<R4CodeableConcept>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { bodySite } returns listOf(ehrCodeableConcept1, ehrCodeableConcept2)
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.bodySite.size)
    }

    @Test
    fun `can get subject`() {
        val ehrReference = relaxedMockk<R4Reference>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { subject } returns ehrReference
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.subject)
    }

    @Test
    fun `can get encounter`() {
        val ehrReference = relaxedMockk<R4Reference>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { encounter } returns ehrReference
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.encounter)
    }

    @Test
    fun `can get null encounter`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { encounter } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.encounter)
    }

    @Test
    fun `can get null onset`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.onset)
    }

    @Test
    fun `can get date time onset`() {
        val ehrDateTimeOnset =
            mockk<DynamicValue<DateTime>> {
                every { value } returns DateTime("2022-03-09")
                every { type } returns DynamicValueType.DATE_TIME
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset } returns ehrDateTimeOnset
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is DateTimeOnset)
    }

    @Test
    fun `can get age onset`() {
        val ehrAgeOnset =
            mockk<DynamicValue<R4Age>> {
                every { value } returns relaxedMockk()
                every { type } returns DynamicValueType.AGE
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset } returns ehrAgeOnset
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is AgeOnset)
    }

    @Test
    fun `can get period onset`() {
        val ehrPeriodOnset =
            mockk<DynamicValue<R4Period>> {
                every { value } returns relaxedMockk()
                every { type } returns DynamicValueType.PERIOD
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset } returns ehrPeriodOnset
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is PeriodOnset)
    }

    @Test
    fun `can get range onset`() {
        val ehrRangeOnset =
            mockk<DynamicValue<R4Range>> {
                every { value } returns relaxedMockk()
                every { type } returns DynamicValueType.RANGE
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset } returns ehrRangeOnset
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is RangeOnset)
    }

    @Test
    fun `can get string onset`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset?.value } returns "recently"
                every { onset?.type } returns DynamicValueType.STRING
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.onset is StringOnset)
    }

    @Test
    fun `throws exception on unknown onset type`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { onset?.value } returns relaxedMockk()
                every { onset?.type } returns DynamicValueType.MONEY
            }
        val condition = Condition(ehrCondition, mockTenant)
        val exception =
            assertThrows<RuntimeException> {
                condition.onset
            }
        assertEquals("Unknown condition onset type encountered", exception.message)
    }

    @Test
    fun `can get null abatement`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.abatement)
    }

    @Test
    fun `can get date time abatement`() {
        val ehrDateTimeAbatement =
            mockk<DynamicValue<DateTime>> {
                every { value } returns DateTime("2022-03-09")
                every { type } returns DynamicValueType.DATE_TIME
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement } returns ehrDateTimeAbatement
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is DateTimeAbatement)
    }

    @Test
    fun `can get age abatement`() {
        val ehrAgeAbatement =
            mockk<DynamicValue<R4Age>> {
                every { value } returns relaxedMockk()
                every { type } returns DynamicValueType.AGE
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement } returns ehrAgeAbatement
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is AgeAbatement)
    }

    @Test
    fun `can get period abatement`() {
        val ehrPeriodAbatement =
            mockk<DynamicValue<R4Period>> {
                every { value } returns relaxedMockk()
                every { type } returns DynamicValueType.PERIOD
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement } returns ehrPeriodAbatement
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is PeriodAbatement)
    }

    @Test
    fun `can get range abatement`() {
        val ehrRangeAbatement =
            mockk<DynamicValue<R4Range>> {
                every { value } returns relaxedMockk()
                every { type } returns DynamicValueType.RANGE
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement } returns ehrRangeAbatement
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is RangeAbatement)
    }

    @Test
    fun `can get string abatement`() {
        val ehrStringAbatement =
            mockk<DynamicValue<String>> {
                every { value } returns "recently"
                every { type } returns DynamicValueType.STRING
            }
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement } returns ehrStringAbatement
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertTrue(condition.abatement is StringAbatement)
    }

    @Test
    fun `throws exception on unknown abatement type`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { abatement?.value } returns relaxedMockk()
                every { abatement?.type } returns DynamicValueType.MONEY
            }
        val condition = Condition(ehrCondition, mockTenant)
        val exception =
            assertThrows<RuntimeException> {
                condition.abatement
            }
        assertEquals("Unknown condition abatement type encountered", exception.message)
    }

    @Test
    fun `can get recorded date`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { recordedDate } returns DateTime("2022-03-09")
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals("2022-03-09", condition.recordedDate)
    }

    @Test
    fun `null date`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { recordedDate } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.recordedDate)
    }

    @Test
    fun `can get recorder`() {
        val ehrReference = relaxedMockk<R4Reference>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { recorder } returns ehrReference
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.recorder)
    }

    @Test
    fun `can get null recorder`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { recorder } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.recorder)
    }

    @Test
    fun `can get asserter`() {
        val ehrReference = relaxedMockk<R4Reference>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { asserter } returns ehrReference
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNotNull(condition.asserter)
    }

    @Test
    fun `can get null asserter`() {
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { asserter } returns null
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertNull(condition.asserter)
    }

    @Test
    fun `can get stage`() {
        val ehrStage1 = relaxedMockk<R4ConditionStage>()
        val ehrStage2 = relaxedMockk<R4ConditionStage>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { stage } returns listOf(ehrStage1, ehrStage2)
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.stage.size)
    }

    @Test
    fun `can get evidence`() {
        val ehrEvidence1 = relaxedMockk<R4ConditionEvidence>()
        val ehrEvidence2 = relaxedMockk<R4ConditionEvidence>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { evidence } returns listOf(ehrEvidence1, ehrEvidence2)
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.evidence.size)
    }

    @Test
    fun `can get note`() {
        val ehrAnnotation1 = relaxedMockk<R4Annotation>()
        val ehrAnnotation2 = relaxedMockk<R4Annotation>()
        val ehrCondition =
            relaxedMockk<R4Condition> {
                every { note } returns listOf(ehrAnnotation1, ehrAnnotation2)
            }
        val condition = Condition(ehrCondition, mockTenant)
        assertEquals(2, condition.note.size)
    }
}
