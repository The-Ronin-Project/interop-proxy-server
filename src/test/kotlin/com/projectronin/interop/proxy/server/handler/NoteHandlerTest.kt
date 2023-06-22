package com.projectronin.interop.proxy.server.handler

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.exceptions.RequestFailureException
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.Address
import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
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
import com.projectronin.interop.ehr.PatientService as EHRPatientService
import com.projectronin.interop.ehr.PractitionerService as EHRPractitionerService

class NoteHandlerTest {
    private lateinit var noteHandler: NoteHandler
    private lateinit var ehrDataAuthorityClient: EHRDataAuthorityClient
    private lateinit var queueService: QueueService
    private lateinit var tenantService: TenantService
    private lateinit var mdmService: MDMService
    private lateinit var ehrFactory: EHRFactory
    private lateinit var dfe: DataFetchingEnvironment
    private lateinit var vendorFactory: VendorFactory
    private lateinit var ehrPatientService: EHRPatientService
    private lateinit var ehrPractitionerService: EHRPractitionerService

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

    @BeforeEach
    fun initTest() {
        ehrDataAuthorityClient = mockk()
        queueService = mockk {
            every { enqueueMessages(any()) } just Runs
        }
        tenantService = mockk()
        mdmService = mockk()
        ehrFactory = mockk()
        noteHandler =
            NoteHandler(queueService, tenantService, mdmService, ehrFactory, ehrDataAuthorityClient)
        dfe = mockk()
        vendorFactory = mockk()
        ehrPractitionerService = mockk()
        ehrPatientService = mockk()
    }

    /**
     * General test cases
     */
    @Test
    fun `accepts note with provider UDP ID and patient UDP ID`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns oncologyPatient
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
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PATIENT,
            true
        )
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `accepts addendum note with provider UDP ID and patient UDP ID`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns oncologyPatient
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
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PATIENT,
            true
        )
        val response = noteHandler.sendNoteAddendum(noteInput, "apposnd", "parentDocId", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `addendum note with null parent document ID is sent as a new note`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Patient",
                "apposnd-PatientTestId"
            )
        } returns oncologyPatient
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
            "apposnd-PatientTestId",
            PatientIdType.FHIR,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PATIENT,
            true
        )
        val response = noteHandler.sendNoteAddendum(noteInput, "apposnd", "parentDocId", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `accepts note with provider UDP ID and patient MRN`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResource(
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
                "202206011250",
                NoteSender.PRACTITIONER,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient

        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "AU"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `accepts note with provider UDP ID and patient MRN but not an alert`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResource(
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
                "202206011250",
                NoteSender.PATIENT,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient

        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "AU"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `handles bad tenant`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns null

        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "apposnd-PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PRACTITIONER,
            false
        )
        assertThrows<HttpClientErrorException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
    }

    @Test
    fun `RequestFailureException getting Practitioner from Aidbox with non-UDP FHIR ID, success getting Practitioner from EHR`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } throws RequestFailureException(Throwable(), "y", "z")

        // success: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
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
            "202206011250",
            NoteSender.PRACTITIONER,
            false
        )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient

        // success: generateMDM()
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "AU"
            )
        } returns Pair("mock", "uniqueId")

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `RequestFailureException getting Practitioner from Aidbox with UDP FHIR ID, success getting Practitioner from EHR`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResource(
                "apposnd",
                "Practitioner",
                "apposnd-PractitionerTestId"
            )
        } throws RequestFailureException(Throwable(), "y", "z")

        // success: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
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
            "202206011250",
            NoteSender.PRACTITIONER,
            false
        )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient

        // success: generateMDM()
        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "AU"
            )
        } returns Pair("mock", "uniqueId")

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `ClientFailureException (HttpException) getting Practitioner from Aidbox with non-UDP FHIR ID, RequestFailureException getting Practitioner from EHR`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // failure: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResource(
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

        // success: NoteInput()
        val noteInput = NoteInput(
            "PatientMRNId",
            PatientIdType.MRN,
            "PractitionerTestId",
            "Example Note Text",
            "202206011250",
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
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // success: practitionerService.getPractitionerByUDPId()
        coEvery {
            ehrDataAuthorityClient.getResource(
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
            "202206011250",
            NoteSender.PRACTITIONER,
            false
        )

        // failure: ehrPatientService.getPatient()
        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } throws NullPointerException("z")

        // failure: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertNull(response.data)
        assertEquals(1, response.errors.size)
        assertEquals("z", response.errors[0].message)
    }

    @Test
    fun `handles invalid NoteInput`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
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
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertNull(response.data)
        assertEquals(1, response.errors.size)
        assertEquals(
            "datetime must be of form \"yyyyMMddHHmm[ss]\" but was \"20220601 125000\"",
            response.errors[0].message
        )
    }

    @Test
    fun `mrn is padded in less than 7 characters`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResource(
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
                "202206011250",
                NoteSender.PRACTITIONER,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "0012345") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient

        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) && it.mrn == "0012345" },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "AU"
            )
        } returns Pair("mock", "uniqueId")

        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }

    @Test
    fun `mrn is not padded if disabled and less than 7 characters`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        coEvery {
            ehrDataAuthorityClient.getResource(
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
                "202206011250",
                NoteSender.PRACTITIONER,
                false
            )

        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "12345") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } returns oncologyPatient

        every {
            mdmService.generateMDM(
                "apposnd",
                match { it.name == listOf(testname) && it.mrn == "12345" },
                match { it.name == listOf(testname) },
                "Example Note Text",
                "202206011250",
                null,
                "AU"
            )
        } returns Pair("mock", "uniqueId")

        val noteHandler =
            NoteHandler(queueService, tenantService, mdmService, ehrFactory, ehrDataAuthorityClient, padMRNs = "no")
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response.data)
        assertEquals(0, response.errors.size)
    }
}
