package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.fhir.r4.datatype.Address
import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.fhir.ronin.code.RoninCodeSystem
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.hl7.MDMService
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.proxy.server.input.NoteSender
import com.projectronin.interop.proxy.server.input.PatientIdType
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException

class NoteHandlerTest {
    private lateinit var noteHandler: NoteHandler
    private lateinit var practitionerService: PractitionerService
    private lateinit var patientService: PatientService
    private lateinit var queueService: QueueService
    private lateinit var tenantService: TenantService
    private lateinit var mdmService: MDMService
    private lateinit var dfe: DataFetchingEnvironment

    private val testidentifier = relaxedMockk<Identifier> {
        every { system } returns Uri("test")
        every { value } returns "123"
    }
    private val testname = relaxedMockk<HumanName> {
        every { family } returns "family"
        every { given } returns listOf("given", "given2")
    }
    private val testaddress = relaxedMockk<Address> {
        every { line } returns listOf("123 ABC Street", "Unit 1")
        every { city } returns "Anytown"
        every { state } returns "CA"
        every { postalCode } returns "12345"
        every { country } returns "USA"
    }
    private val testphone = relaxedMockk<ContactPoint> {
        every { value } returns "1234567890"
        every { use } returns ContactPointUse.HOME.asCode()
    }
    private val oncologyPatient = mockk<Patient> {
        every { identifier } returns listOf(testidentifier)
        every { name } returns listOf(testname)
        every { gender } returns AdministrativeGender.FEMALE.asCode()
        every { birthDate } returns Date("2022-06-01")
        every { address } returns listOf(testaddress)
        every { telecom } returns listOf(testphone)
    }
    private val oncologyPractitioner = mockk<Practitioner> {
        every { identifier } returns listOf(testidentifier)
        every { name } returns listOf(testname)
    }

    @BeforeEach
    fun initTest() {
        practitionerService = mockk()
        patientService = mockk()
        queueService = mockk()
        tenantService = mockk()
        mdmService = mockk()
        noteHandler = NoteHandler(patientService, practitionerService, queueService, tenantService, mdmService)
        dfe = mockk()
    }

    @Test
    fun `accepts note with patient FHIR Id`() {
        val tenant = mockk<Tenant>()

        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { practitionerService.getPractitioner("apposnd", "PractitionerTestId") } returns oncologyPractitioner
        every { patientService.getPatient("apposnd", "PatientTestId") } returns oncologyPatient
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                "IP"
            )
        } returns Pair("mock", "uniqueId")

        val noteInput = NoteInput(
            "PatientTestId",
            PatientIdType.FHIR,
            "PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PATIENT,
            true
        )
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `accepts note with patient MRN`() {
        val tenant = mockk<Tenant>()

        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every { practitionerService.getPractitioner("apposnd", "PractitionerTestId") } returns oncologyPractitioner
        val noteInput =
            NoteInput(
                "PatientMRNId",
                PatientIdType.MRN,
                "PractitionerTestId",
                "Example Note Text",
                "202206011250",
                NoteSender.PRACTITIONER,
                false
            )
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("key" to SystemValue(system = RoninCodeSystem.MRN.uri.value, value = noteInput.patientId))
            ).getValue("key")
        } returns "PatientFhirId"
        every { patientService.getPatient("apposnd", "PatientFhirId") } returns oncologyPatient
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                "DO"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `accepts note with patient MRN but not an alert`() {
        val tenant = mockk<Tenant>()

        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every { practitionerService.getPractitioner("apposnd", "PractitionerTestId") } returns oncologyPractitioner
        val noteInput =
            NoteInput(
                "PatientMRNId",
                PatientIdType.MRN,
                "PractitionerTestId",
                "Example Note Text",
                "202206011250",
                NoteSender.PATIENT,
                false
            )
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("key" to SystemValue(system = RoninCodeSystem.MRN.uri.value, value = noteInput.patientId))
            ).getValue("key")
        } returns "PatientFhirId"
        every { patientService.getPatient("apposnd", "PatientFhirId") } returns oncologyPatient
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                "DO"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `handles bad tenant`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns null

        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PRACTITIONER,
            false
        )
        assertThrows<HttpClientErrorException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
    }
}
