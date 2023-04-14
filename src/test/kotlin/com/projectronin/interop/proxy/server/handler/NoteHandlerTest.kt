package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.common.exceptions.VendorIdentifierNotFoundException
import com.projectronin.interop.common.http.exceptions.ClientFailureException
import com.projectronin.interop.common.http.exceptions.RequestFailureException
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
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
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.lang.NullPointerException
import com.projectronin.interop.ehr.PatientService as EHRPatientService
import com.projectronin.interop.ehr.PractitionerService as EHRPractitionerService

class NoteHandlerTest {
    private lateinit var noteHandler: NoteHandler
    private lateinit var practitionerService: PractitionerService
    private lateinit var patientService: PatientService
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
        ehrFactory = mockk()
        noteHandler =
            NoteHandler(patientService, practitionerService, queueService, tenantService, mdmService, ehrFactory)
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
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        every { patientService.getPatientByUDPId("apposnd", "apposnd-PatientTestId") } returns oncologyPatient
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
        assertEquals("uniqueId", response)
    }

    @Test
    fun `accepts addendum note with provider UDP ID and patient UDP ID`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        every { patientService.getPatientByUDPId("apposnd", "apposnd-PatientTestId") } returns oncologyPatient
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
        assertEquals("uniqueId", response)
    }

    @Test
    fun `addendum note with null parent document ID is sent as a new note`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner
        every { patientService.getPatientByUDPId("apposnd", "apposnd-PatientTestId") } returns oncologyPatient
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
        assertEquals("uniqueId", response)
    }

    @Test
    fun `accepts note with provider UDP ID and patient MRN`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
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
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("patientFhirId" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("patientFhirId")
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
    fun `accepts note with provider UDP ID and patient MRN but not an alert`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
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
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("patientFhirId" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("patientFhirId")
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
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
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

        // success: patientService.getPatientFHIRIds() for MRN
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("patientFhirId" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("patientFhirId")
        } returns "PatientFhirId"
        every { patientService.getPatientByFHIRId("apposnd", "PatientFhirId") } returns oncologyPatient

        // success: generateMDM()
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

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `RequestFailureException getting Practitioner from Aidbox with UDP FHIR ID, no EHR fallback for this case`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // failure: practitionerService.getPractitionerByUDPId()
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "apposnd-PractitionerTestId"
            )
        } throws RequestFailureException(Throwable(), "y", "z")

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

        // failure: sendNote()
        val exception = assertThrows<RequestFailureException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
        assertEquals("Received exception when calling y (z): null", exception.message)
    }

    @Test
    fun `ClientFailureException (HttpException) getting Practitioner from Aidbox with non-UDP FHIR ID, RequestFailureException getting Practitioner from EHR`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // failure: practitionerService.getPractitionerByUDPId()
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "apposnd-PractitionerTestId"
            )
        } throws(ClientFailureException(HttpStatusCode(401, "x"), "y", "z"))

        // failure: ehrPractitionerService.getPractitioner()
        every { vendorFactory.practitionerService } returns ehrPractitionerService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every {
            ehrPractitionerService.getPractitioner(
                tenant,
                "PractitionerTestId"
            )
        } throws(RequestFailureException(Throwable(), "a", "b"))

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
        val exception = assertThrows<RequestFailureException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
        assertEquals("Received exception when calling a (b): null", exception.message)
    }

    @Test
    fun `HttpClientErrorException (RestClientException) getting Patient from Aidbox by UDP ID, no EHR fallback for this case`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // success: getPractitionerByUDPId()
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
                "apposnd-PractitionerTestId"
            )
        } returns oncologyPractitioner

        // failure: getPatientByUDPId
        every {
            patientService.getPatientByUDPId("apposnd", "PatientTestId")
        } throws HttpClientErrorException(HttpStatus.NOT_FOUND, "y")

        // success: ehrPatientService.getPatient()
        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every {
            ehrPatientService.getPatient(
                tenant,
                "PatientTestId"
            )
        } returns oncologyPatient

        // success: NoteInput()
        val noteInput = NoteInput(
            "PatientTestId",
            PatientIdType.FHIR,
            "PractitionerTestId",
            "Example Note Text",
            "202206011250",
            NoteSender.PATIENT,
            true
        )

        // failure: sendNote()
        val exception = assertThrows<HttpClientErrorException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
        assertEquals("404 y", exception.message)
    }

    @Test
    fun `HttpClientErrorException (RestClientException) getting Patient from Aidbox by MRN, success getting Patient from EHR by MRN`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // success: practitionerService.getPractitionerByUDPId()
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
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

        // success: patientService.getPatientFHIRIds() for MRN
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("patientFhirId" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("patientFhirId")
        } returns "PatientFhirId"

        // failure: patientService.getPatientByFHIRId()
        every {
            patientService.getPatientByFHIRId(
                "apposnd",
                "PatientFhirId"
            )
        } throws HttpClientErrorException(HttpStatus.NOT_FOUND, "y")

        // success: ehrPatientService.getPatient()
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
                "DO"
            )
        } returns Pair("mock", "uniqueId")

        // success: sendNote()
        val response = noteHandler.sendNote(noteInput, "apposnd", dfe)
        assertEquals("uniqueId", response)
    }

    @Test
    fun `VendorIdentifierNotFoundException getting Patient from Aidbox by MRN, unexpected exception getting Patient from EHR by MRN`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "apposnd"
        every { tenantService.getTenantForMnemonic("apposnd") } returns tenant

        // success: practitionerService.getPractitionerByUDPId()
        every {
            practitionerService.getPractitionerByUDPId(
                "apposnd",
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

        // success: patientService.getPatientFHIRIds() for MRN
        every {
            patientService.getPatientFHIRIds(
                "apposnd",
                mapOf("patientFhirId" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
            ).getValue("patientFhirId")
        } returns "PatientFhirId"

        // failure: patientService.getPatientByFHIRId()
        every {
            patientService.getPatientByFHIRId(
                "apposnd",
                "PatientFhirId"
            )
        } throws (VendorIdentifierNotFoundException())

        // failure: ehrPatientService.getPatient()
        every { vendorFactory.patientService } returns ehrPatientService
        every { ehrFactory.getVendorFactory(tenant) } returns vendorFactory
        every { ehrPatientService.getPatientFHIRId(tenant, "PatientMRNId") } returns "PatientFHIRId"
        every { ehrPatientService.getPatient(tenant, "PatientFHIRId") } throws NullPointerException("z")

        // failure: sendNote()
        val exception = assertThrows<NullPointerException> {
            noteHandler.sendNote(noteInput, "apposnd", dfe)
        }
        assertEquals("z", exception.message)
    }
}
