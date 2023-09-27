package com.projectronin.interop.proxy.server.handler

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.exceptions.RequestFailureException
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.Address
import com.projectronin.interop.fhir.r4.datatype.Attachment
import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Base64Binary
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Instant
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.fhir.r4.resource.DocumentReferenceContent
import com.projectronin.interop.fhir.r4.resource.DocumentReferenceRelatesTo
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.CompositionStatus
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.fhir.r4.valueset.DocumentReferenceStatus
import com.projectronin.interop.fhir.r4.valueset.DocumentRelationshipType
import com.projectronin.interop.proxy.server.context.getAuthorizedTenantId
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.proxy.server.input.NoteSender
import com.projectronin.interop.proxy.server.input.PatientIdType
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime
import com.projectronin.interop.ehr.NoteService as EHRNoteService
import com.projectronin.interop.ehr.PatientService as EHRPatientService
import com.projectronin.interop.ehr.PractitionerService as EHRPractitionerService
import com.projectronin.interop.ehr.inputs.NoteInput as EhrNoteInput
import com.projectronin.interop.ehr.inputs.NoteSender as EhrNoteSender

class NoteHandlerTest {
    private lateinit var noteHandler: NoteHandler
    private lateinit var ehrDataAuthorityClient: EHRDataAuthorityClient
    private lateinit var queueService: QueueService
    private lateinit var tenantService: TenantService
    private lateinit var ehrFactory: EHRFactory
    private lateinit var dfe: DataFetchingEnvironment
    private lateinit var vendorFactory: VendorFactory
    private lateinit var ehrPatientService: EHRPatientService
    private lateinit var ehrPractitionerService: EHRPractitionerService
    private lateinit var ehrNoteService: EHRNoteService

    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "apposnd"
    }
    private val testidentifier = relaxedMockk<Identifier> {
        every { system } returns Uri("test")
        every { value } returns "123".asFHIR()
        every { use } returns Code("usual")
    }
    private val testname = relaxedMockk<HumanName> {
        every { family } returns "family".asFHIR()
        every { given } returns listOf("given", "given2").asFHIR()
        every { use } returns Code("official")
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
    private val testAttachment = mockk<Attachment> {
        every { data } returns Base64Binary("RXhhbXBsZSBOb3RlIFRleHQ=")
    }
    private val documentReferenceContent = mockk<DocumentReferenceContent> {
        every { attachment } returns testAttachment
    }
    private val documentReference = mockk<DocumentReference> {
        every { status } returns DocumentReferenceStatus.CURRENT.asCode()
        every { date } returns Instant("2023-08-07T13:28:17.000Z")
        every { content } returns listOf(documentReferenceContent)
        every { docStatus } returns CompositionStatus.PRELIMINARY.asCode()
    }
    private val documentReferenceRelatesTo = mockk<DocumentReferenceRelatesTo> {
        every { code } returns DocumentRelationshipType.APPENDS.asCode()
        every { target } returns Reference(reference = "DocumentReference/parentID".asFHIR())
    }
    private val documentReferenceAddendum = mockk<DocumentReference> {
        every { status } returns DocumentReferenceStatus.CURRENT.asCode()
        every { date } returns Instant("2023-08-07T13:28:17.000Z")
        every { content } returns listOf(documentReferenceContent)
        every { docStatus } returns CompositionStatus.PRELIMINARY.asCode()
        every { relatesTo } returns listOf(documentReferenceRelatesTo)
    }

    @BeforeEach
    fun initTest() {
        ehrDataAuthorityClient = mockk()
        queueService = mockk {
            every { enqueueMessages(any()) } just Runs
        }
        tenantService = mockk()
        ehrFactory = mockk()
        noteHandler =
            NoteHandler(tenantService, ehrFactory, ehrDataAuthorityClient)
        dfe = mockk()
        vendorFactory = mockk()
        ehrPractitionerService = mockk()
        ehrPatientService = mockk()
        ehrNoteService = mockk()
    }

    /**
     * General test cases
     */
    @Test
    fun `accepts note with provider UDP ID and patient UDP ID`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Patient>(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns oncologyPatient
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        val noteInput = NoteInput(
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PATIENT,
            true
        )
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PATIENT,
            isAlert = true,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { vendorFactory.noteService } returns ehrNoteService
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `accepts addendum note with provider UDP ID and patient UDP ID`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Patient>(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns oncologyPatient
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        val noteInput = NoteInput(
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PATIENT,
            true
        )
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PATIENT,
            isAlert = true,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { vendorFactory.noteService } returns ehrNoteService
        every {
            ehrNoteService.sendPatientNoteAddendum(tenant, ehrNoteInput, "parentDocId")
        } returns "uniqueId"
        val response = noteHandler.sendNoteAddendum(noteInput, "apposnd", "parentDocId", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `addendum note with null parent document ID is sent as a new note`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Patient>(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns oncologyPatient
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        val noteInput = NoteInput(
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PATIENT,
            true
        )
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PATIENT,
            isAlert = true,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { vendorFactory.noteService } returns ehrNoteService
        every {
            ehrNoteService.sendPatientNoteAddendum(tenant, ehrNoteInput, "parentDocId")
        } returns "uniqueId"
        val response = noteHandler.sendNoteAddendum(noteInput, "apposnd", "parentDocId", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `accepts note with provider UDP ID and patient MRN`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        val noteInput =
            NoteInput(
                "PatientMRNId",
                PatientIdType.MRN,
                "apposnd-PractitionerTestId",
                "Example Note Text",
                "20230807132817",
                NoteSender.PRACTITIONER,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { vendorFactory.noteService } returns ehrNoteService
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PRACTITIONER,
            isAlert = false,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `accepts note with provider UDP ID and patient MRN but not an alert`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        val noteInput =
            NoteInput(
                "PatientMRNId",
                PatientIdType.MRN,
                "apposnd-PractitionerTestId",
                "Example Note Text",
                "20230807132817",
                NoteSender.PATIENT,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        every { vendorFactory.noteService } returns ehrNoteService
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PATIENT,
            isAlert = false,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `handles bad tenant`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns null

        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PRACTITIONER,
            false
        )
        assertThrows<HttpClientErrorException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
    }

    @Test
    fun `RequestFailureException getting Practitioner from EHR Data Authority with non-UDP FHIR ID, success getting Practitioner from EHR`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } throws RequestFailureException(Throwable(), "y", "z")

        // success: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every {
            ehrPractitionerService.getPractitioner(
                tenant,
                "PractitionerTestId"
            )
        } returns oncologyPractitioner

        // success: NoteInput()
        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PRACTITIONER,
            false
        )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        every { vendorFactory.noteService } returns ehrNoteService
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PRACTITIONER,
            isAlert = false,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `RequestFailureException getting Practitioner from EHR Data Authority with UDP FHIR ID, success getting Practitioner from EHR`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } throws RequestFailureException(Throwable(), "y", "z")

        // success: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every {
            ehrPractitionerService.getPractitioner(
                tenant,
                "PractitionerTestId"
            )
        } returns oncologyPractitioner

        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PRACTITIONER,
            false
        )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        every { vendorFactory.noteService } returns ehrNoteService
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PRACTITIONER,
            isAlert = false,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `practitioner not found in EHR Data Authority with UDP FHIR ID, success getting Practitioner from EHR`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns null

        // success: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every {
            ehrPractitionerService.getPractitioner(
                tenant,
                "PractitionerTestId"
            )
        } returns oncologyPractitioner

        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PRACTITIONER,
            false
        )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        every { vendorFactory.noteService } returns ehrNoteService
        // success: generateMDM()
        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PRACTITIONER,
            isAlert = false,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `ClientFailureException (HttpException) getting Practitioner from EHR Data Authority with non-UDP FHIR ID, RequestFailureException getting Practitioner from EHR`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } throws (ClientFailureException(HttpStatusCode(401, "x"), "y", "z"))

        // failure: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every {
            ehrPractitionerService.getPractitioner(
                tenant,
                "PractitionerTestId"
            )
        } throws (RequestFailureException(Throwable(), "a", "b"))

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        every { vendorFactory.noteService } returns ehrNoteService
        // success: NoteInput()
        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PRACTITIONER,
            false
        )

        // failure: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertNull(response.data)
        assertEquals(1, response.errors.size)
        assertEquals("Received exception when calling a (b): null", response.errors[0].message)
    }

    @Test
    fun `unexpected exception getting Patient from EHR by MRN`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // success: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner

        // success: NoteInput()
        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PRACTITIONER,
            false
        )

        // failure: ehrPatientService.getPatient()
        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } throws NullPointerException("z")
        every { vendorFactory.noteService } returns ehrNoteService

        // failure: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertNull(response.data)
        assertEquals(1, response.errors.size)
        assertEquals("z", response.errors[0].message)
    }

    @Test
    fun `handles invalid NoteInput`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20220601 125000",
            NoteSender.PRACTITIONER,
            false
        )
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { vendorFactory.noteService } returns ehrNoteService
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertNull(response.data)
        assertEquals(1, response.errors.size)
        assertEquals(
            """'20220601 125000' is not in a recognized date format, datetime must be of form "yyyyMMddHHmm[ss]"""",
            response.errors[0].message
        )
    }

    @Test
    fun `mrn is padded in less than 7 characters`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        val noteInput =
            NoteInput(
                "12345",
                PatientIdType.MRN,
                "apposnd-PractitionerTestId",
                "Example Note Text",
                "20230807132817",
                NoteSender.PRACTITIONER,
                false
            )
        val oncologyPatient1 = relaxedMockk<Patient> {
            every { identifier } returns listOf(testidentifier)
            every { name } returns listOf(testname)
            every { gender } returns AdministrativeGender.FEMALE.asCode()
            every { birthDate } returns Date("2022-06-01")
            every { address } returns listOf(testaddress)
            every { telecom } returns listOf(testphone)
        }
        val paddedMrn = relaxedMockk<Identifier> {
            every { system } returns CodeSystem.RONIN_MRN.uri
            every { value } returns "0012345".asFHIR()
            every { type } returns CodeableConcepts.RONIN_MRN
        }
        val mrnPatient = mockk<Patient> {
            every { identifier } returns listOf(testidentifier, paddedMrn)
            every { name } returns listOf(testname)
            every { gender } returns AdministrativeGender.FEMALE.asCode()
            every { birthDate } returns Date("2022-06-01")
            every { address } returns listOf(testaddress)
            every { telecom } returns listOf(testphone)
        }
        // every { oncologyPatient.copy(identifier = oncologyPatient.identifier + Identifier(system = CodeSystem.RONIN_MRN.uri, value = "0012345".asFHIR(), type = CodeableConcepts.RONIN_MRN)) } returns mrnPatient
        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "0012345") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient1
        every { vendorFactory.noteService } returns ehrNoteService

        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PRACTITIONER,
            isAlert = false,
            patient = oncologyPatient1.copy(
                identifier = oncologyPatient1.identifier +
                    Identifier(
                        system = CodeSystem.RONIN_MRN.uri,
                        value = "0012345".asFHIR(),
                        type = CodeableConcepts.RONIN_MRN
                    )
            ),
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `mrn is not padded if disabled and less than 7 characters`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        val noteInput =
            NoteInput(
                "12345",
                PatientIdType.MRN,
                "apposnd-PractitionerTestId",
                "Example Note Text",
                "20230807132817",
                NoteSender.PRACTITIONER,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "12345") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient
        every { vendorFactory.noteService } returns ehrNoteService

        val ehrNoteInput = EhrNoteInput(
            noteText = "Example Note Text",
            dateTime = LocalDateTime.of(2023, 8, 7, 13, 28, 17),
            noteSender = EhrNoteSender.PRACTITIONER,
            isAlert = false,
            patient = oncologyPatient,
            practitioner = oncologyPractitioner
        )
        every {
            ehrNoteService.sendPatientNote(tenant, ehrNoteInput)
        } returns "uniqueId"

        val noteHandler =
            NoteHandler(tenantService, ehrFactory, ehrDataAuthorityClient, padMRNs = "no")
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `throws exception when patient not found for UDP ID`() {
        every { dfe.getAuthorizedTenantId() } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Patient>(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns null

        val noteInput = NoteInput(
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "20230807132817",
            NoteSender.PATIENT,
            true
        )
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("No Patient found for apposnd-PatientTestId", response.errors.first().message)
    }
}
