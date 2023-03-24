package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Address
import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.proxy.server.context.getAuthorizedTenantId
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
        every { value } returns "123".asFHIR()
    }
    private val testname = relaxedMockk<HumanName> {
        every { family } returns "family".asFHIR()
        every { given } returns listOf("given", "given2").asFHIR()
    }
    private val testaddress = relaxedMockk<Address> {
        every { line } returns listOf("123 ABC Street", "Unit 1").asFHIR()
        every { city } returns "Anytown".asFHIR()
        every { state } returns "CA".asFHIR()
        every { postalCode } returns "12345".asFHIR()
        every { country } returns "USA".asFHIR()
    }
    private val testphone = relaxedMockk<ContactPoint> {
        every { value } returns "1234567890".asFHIR()
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

        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "PractitionerTestId"
            )
        } returns oncologyPractitioner
        every { patientService.getPatientByUDPId("apposnd", "PatientTestId") } returns oncologyPatient
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
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
    fun `accepts addendum note with patient FHIR Id`() {
        val tenant = mockk<Tenant>()

        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "PractitionerTestId"
            )
        } returns oncologyPractitioner
        every { patientService.getPatientByUDPId("apposnd", "PatientTestId") } returns oncologyPatient
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                "parentDocId",
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
        val response = noteHandler.sendNoteAddendum(noteInput, "apposnd", "parentDocId", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `accepts note with patient MRN`() {
        val tenant = mockk<Tenant>()

        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "PractitionerTestId"
            )
        } returns oncologyPractitioner
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
                mapOf("key" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("key")
        } returns "PatientFhirId"
        every { patientService.getPatientByFHIRId("apposnd", "PatientFhirId") } returns oncologyPatient
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "DO"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `accepts note with patient MRN but not an alert`() {
        val tenant = mockk<Tenant>()

        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "PractitionerTestId"
            )
        } returns oncologyPractitioner
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
                mapOf("key" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("key")
        } returns "PatientFhirId"
        every { patientService.getPatientByFHIRId("apposnd", "PatientFhirId") } returns oncologyPatient
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "DO"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `handles bad tenant`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
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
