package com.projectronin.interop.proxy.server.dataloaders

import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.ehr.model.ReferenceTypes
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier
import com.projectronin.interop.ehr.model.Participant as EHRParticipant
import com.projectronin.interop.ehr.model.Reference as EHRReference
import com.projectronin.interop.proxy.server.model.Reference as ProxyReference

class ParticipantServiceTest {
    private lateinit var practitionerService: PractitionerService
    private lateinit var service: ParticipantService

    private val testIdentifier = mockk<EHRIdentifier> {
        every { system } returns "test"
        every { value } returns "123"
    }
    private val testReferenceWithIdentifier = mockk<EHRReference> {
        every { identifier } returns testIdentifier
        every { display } returns "Blah"
        every { id } returns null
        every { reference } returns null
        every { type } returns ReferenceTypes.PRACTITIONER
    }

    private val testParticipantWithIdentifier = mockk<EHRParticipant> {
        every { actor } returns testReferenceWithIdentifier
    }
    private val tenantId = "tenantId"

    @BeforeEach
    fun initTest() {
        practitionerService = mockk()
        service = ParticipantService(practitionerService)
    }

    @Test
    fun `service builds`() {
        every {
            practitionerService.getPractitionerFHIRIds(
                tenantId,
                emptyMap<EHRParticipant, SystemValue>()
            )
        } returns emptyMap<EHRParticipant, String>()
        service.getParticipants(setOf(), tenantId)
        assertTrue(true)
    }

    @Test
    fun `returns id from aidbox`() {
        val testIdentifierSearch = mapOf(
            testParticipantWithIdentifier to SystemValue(
                value = testIdentifier.value,
                system = testIdentifier.system ?: ""
            )
        )
        every {
            practitionerService.getPractitionerFHIRIds(
                tenantId,
                testIdentifierSearch

            )
        } returns mapOf(testParticipantWithIdentifier to "FHIR_ID")
        val results = service.getParticipants(setOf(testParticipantWithIdentifier), tenantId)
        val firstReference = results[testParticipantWithIdentifier]?.actor
        val expectedReference = ProxyReference(
            identifier = null,
            display = "Blah",
            id = "FHIR_ID",
            reference = "Provider/FHIR_ID",
            type = "Provider"
        )
        assertEquals(expectedReference.display, firstReference?.display)
        assertEquals(expectedReference.id, firstReference?.id)
        assertEquals(expectedReference.reference, firstReference?.reference)
    }

    @Test
    fun `preserves id if already present`() {
        val testReferenceWithID = mockk<EHRReference> {
            every { identifier } returns null
            every { display } returns "Blah"
            every { id } returns "ExistingID"
            every { reference } returns null
            every { type } returns ReferenceTypes.PRACTITIONER
        }

        val testParticipantWithExitingID = mockk<EHRParticipant> {
            every { actor } returns testReferenceWithID
        }
        every {
            practitionerService.getPractitionerFHIRIds(
                tenantId,
                emptyMap<EHRParticipant, SystemValue>()
            )
        } returns emptyMap<EHRParticipant, String>()
        val results = service.getParticipants(setOf(testParticipantWithExitingID), tenantId)
        val firstReference = results.get(testParticipantWithExitingID)?.actor
        val expectedReference = ProxyReference(
            identifier = null,
            display = "Blah",
            id = "ExistingID",
            reference = "Provider/ExistingID",
            type = "Provider"
        )
        assertEquals(expectedReference.display, firstReference?.display)
        assertEquals(expectedReference.id, firstReference?.id)
        assertEquals(expectedReference.reference, firstReference?.reference)
    }

    @Test
    fun `no Id found in aidbox`() {
        val testIdentifierSearch = mapOf(
            testParticipantWithIdentifier to SystemValue(
                value = testIdentifier.value,
                system = testIdentifier.system ?: ""
            )
        )
        every {
            practitionerService.getPractitionerFHIRIds(
                tenantId,
                testIdentifierSearch
            )
        } returns emptyMap()
        val results = service.getParticipants(setOf(testParticipantWithIdentifier), tenantId)
        val firstReference = results[testParticipantWithIdentifier]?.actor
        val expectedReference = ProxyReference(
            identifier = null,
            display = "Blah",
            id = null,
            reference = null,
            type = "Provider"
        )
        assertEquals(expectedReference.display, firstReference?.display)
        assertEquals(expectedReference.id, firstReference?.id)
        assertEquals(expectedReference.reference, firstReference?.reference)
    }

    @Test
    fun `blank identifier`() {
        val testBlankIdentifier = mockk<EHRIdentifier> {
            every { system } returns null
            every { value } returns ""
        }
        val testReferenceWithBlankIdentifier = mockk<EHRReference> {
            every { identifier } returns testBlankIdentifier
            every { display } returns "Blah"
            every { id } returns null
            every { reference } returns null
            every { type } returns ReferenceTypes.PRACTITIONER
        }

        val testParticipantWithBlankIdentifier = mockk<EHRParticipant> {
            every { actor } returns testReferenceWithBlankIdentifier
        }
        val testIdentifierSearch = mapOf(
            testParticipantWithBlankIdentifier to SystemValue(
                value = "",
                system = ""
            )
        )

        every {
            practitionerService.getPractitionerFHIRIds(
                tenantId,
                testIdentifierSearch
            )
        } returns emptyMap()
        val results = service.getParticipants(setOf(testParticipantWithBlankIdentifier), tenantId)
        val firstReference = results[testParticipantWithBlankIdentifier]?.actor
        val expectedReference = ProxyReference(
            identifier = null,
            display = "Blah",
            id = null,
            reference = null,
            type = "Provider"
        )
        assertEquals(expectedReference.display, firstReference?.display)
        assertEquals(expectedReference.id, firstReference?.id)
        assertEquals(expectedReference.reference, firstReference?.reference)
    }

    @Test
    fun `no identifier`() {
        val testReferenceWithNoIdentifier = mockk<EHRReference> {
            every { identifier } returns null
            every { display } returns "Blah"
            every { id } returns null
            every { reference } returns null
            every { type } returns ReferenceTypes.PRACTITIONER
        }
        val testParticipantWithNoIdentifier = mockk<EHRParticipant> {
            every { actor } returns testReferenceWithNoIdentifier
        }
        val testIdentifierSearch = mapOf(
            testParticipantWithNoIdentifier to SystemValue(
                value = "",
                system = ""
            )
        )

        every {
            practitionerService.getPractitionerFHIRIds(
                tenantId,
                testIdentifierSearch
            )
        } returns emptyMap()

        val results = service.getParticipants(setOf(testParticipantWithNoIdentifier), tenantId)
        val firstReference = results[testParticipantWithNoIdentifier]?.actor
        val expectedReference = ProxyReference(
            identifier = null,
            display = "Blah",
            id = null,
            reference = null,
            type = "Provider"
        )
        assertEquals(expectedReference.display, firstReference?.display)
        assertEquals(expectedReference.id, firstReference?.id)
        assertEquals(expectedReference.reference, firstReference?.reference)
    }
}
