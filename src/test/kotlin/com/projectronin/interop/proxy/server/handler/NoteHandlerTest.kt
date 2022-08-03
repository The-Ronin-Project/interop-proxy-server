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
import com.projectronin.interop.fhir.r4.ronin.resource.OncologyPatient
import com.projectronin.interop.fhir.r4.ronin.resource.OncologyPractitioner
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.proxy.server.input.PatientIdType
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.queue.QueueService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat

class NoteHandlerTest {
    private lateinit var noteHandler: NoteHandler
    private lateinit var practitionerService: PractitionerService
    private lateinit var patientService: PatientService
    private lateinit var queueService: QueueService

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
        every { use } returns ContactPointUse.HOME
    }
    private val oncologyPatient = mockk<OncologyPatient> {
        every { identifier } returns listOf(testidentifier)
        every { name } returns listOf(testname)
        every { gender } returns AdministrativeGender.FEMALE
        every { birthDate } returns Date("2022-06-01")
        every { address } returns listOf(testaddress)
        every { telecom } returns listOf(testphone)
    }
    private val oncologyPractitioner = mockk<OncologyPractitioner> {
        every { identifier } returns listOf(testidentifier)
        every { name } returns listOf(testname)
    }

    @BeforeEach
    fun initTest() {
        practitionerService = mockk()
        patientService = mockk()
        queueService = mockk()
        noteHandler = NoteHandler(patientService, practitionerService, queueService)
    }

    @Test
    fun `accepts note with patient FHIR Id`() {
        every { practitionerService.getOncologyPractitioner("apposnd", "PractitionerTestId") } returns oncologyPractitioner
        every { patientService.getOncologyPatient("apposnd", "PatientTestId") } returns oncologyPatient
        val noteInput = NoteInput("PatientTestId", PatientIdType.FHIR, "PractitionerTestId", "Example Note Text", "202206011250")
        val response = noteHandler.sendNote(noteInput, "apposnd")
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        assertTrue(response.startsWith(docId))
    }

    @Test
    fun `accepts note with patient MRN`() {
        every { practitionerService.getOncologyPractitioner("apposnd", "PractitionerTestId") } returns oncologyPractitioner
        val noteInput = NoteInput("PatientMRNId", PatientIdType.MRN, "PractitionerTestId", "Example Note Text", "202206011250")
        every { patientService.getPatientFHIRIds("apposnd", mapOf("key" to SystemValue(system = CodeSystem.MRN.uri.value, value = noteInput.patientId))).getValue("key") } returns "PatientFhirId"
        every { patientService.getOncologyPatient("apposnd", "PatientFhirId") } returns oncologyPatient
        val response = noteHandler.sendNote(noteInput, "apposnd")
        val dateformat = SimpleDateFormat("yyyyMMdd")
        val docId = "RoninNote" + dateformat.format(java.util.Date())
        assertTrue(response.startsWith(docId))
    }
}
