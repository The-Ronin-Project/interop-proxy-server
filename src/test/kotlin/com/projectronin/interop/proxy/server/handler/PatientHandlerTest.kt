package com.projectronin.interop.proxy.server.handler

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import com.projectronin.interop.common.logmarkers.LogMarkers
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.PatientService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.proxy.server.context.getAuthorizedTenantId
import com.projectronin.interop.proxy.server.model.Patient
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.proxy.server.util.asCode
import com.projectronin.interop.proxy.server.util.generateMetadata
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate
import com.projectronin.interop.fhir.r4.resource.Patient as R4Patient

@TestInstance(Lifecycle.PER_CLASS)
class PatientHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var patientHandler: PatientHandler
    private lateinit var dfe: DataFetchingEnvironment
    private lateinit var roninPatient: RoninPatient

    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    private val logAppender = ListAppender<ILoggingEvent>()

    private val metadata = mockk<Metadata>()

    @BeforeAll
    fun initAllTests() {
        logger.addAppender(logAppender)
        logAppender.start()
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @BeforeEach
    fun initTest() {
        mockkStatic("com.projectronin.interop.proxy.server.util.MetadataUtilKt")
        every { generateMetadata() } returns metadata

        ehrFactory = mockk()
        tenantService = mockk()
        queueService = mockk()
        dfe = mockk()
        roninPatient = mockk()
        patientHandler = PatientHandler(ehrFactory, tenantService, queueService, roninPatient)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns null
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            patientHandler.patientsByNameAndDOB(
                tenantId = "tenantId",
                birthdate = "1984-08-31",
                given = "Josh",
                family = "Smith",
                dfe = dfe
            )
        }

        assertEquals("404 Invalid Tenant: tenantId", exception.message)
    }

    @Test
    fun `unauthorized tenant returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "differentTenantId"

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            patientHandler.patientsByNameAndDOB(
                tenantId = "tenantId",
                birthdate = "1984-08-31",
                given = "Josh",
                family = "Smith",
                dfe = dfe
            )
        }

        assertEquals(
            "403 Requested Tenant 'tenantId' does not match authorized Tenant 'differentTenantId'",
            exception.message
        )
    }

    @Test
    fun `unknown vendor returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } throws IllegalStateException("Error")

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
    }

    @Test
    fun `ensure findPatient exception is returned as error`() {
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = LocalDate.of(1984, 8, 31),
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } throws (IllegalStateException("Error"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
        assertNull(logAppender.list.last().marker)
    }

    @Test
    fun `ensure findPatient service unavailable sets log marker`() {
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = LocalDate.of(1984, 8, 31),
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } throws (ServiceUnavailableException(HttpStatusCode.ServiceUnavailable, "Proxy"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Received 503 Service Unavailable when calling Proxy", result.errors[0].message)
        assertEquals(logAppender.list.last().marker, LogMarkers.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `ensure full patient is correctly returned`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )
        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "Smith",
                givenName = "Josh"
            )
        } returns response

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(patient1) } returns "raw JSON for patient"
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient",
                        metadata = metadata
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        // Assert outcome
        assertNotNull(actualResponse)

        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `ensure full patient is correctly returned for machine 2 machine auth (no user)`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        // M2M Auth will not provide an authzTenantId
        every { dfe.getAuthorizedTenantId() } returns null

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )
        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "Smith",
                givenName = "Josh"
            )
        } returns response

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(patient1) } returns "raw JSON for patient"
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient",
                        metadata = metadata
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        // Assert outcome
        assertNotNull(actualResponse)

        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )
        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "Smith",
                givenName = "Josh"
            )
        } returns response

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient",
                        metadata = metadata
                    )
                )
            )
        } throws (Exception("exception"))

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        // Assert outcome
        assertNotNull(actualResponse)

        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `ensure when ehr returns no patients no patients are returned`() {
        val response = listOf<R4Patient>()

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = LocalDate.of(1984, 8, 31),
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } returns response
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )

        // Assert outcome
        assertNotNull(actualResponse)

        // Patient
        assertEquals(0, actualResponse.data.size)
    }

    @Test
    fun `ensure when post search matching finds multiple patients only exact match is returned`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Potato"
                    every { given } returns listOf("Tomato").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-01-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1, patient2)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "Smith",
                givenName = "Josh"
            )
        } returns response

        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )
        // Assert outcome
        assertNotNull(actualResponse)
        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `ensure when post search matching finds multiple patients only exact match regardless of case is returned`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Potato"
                    every { given } returns listOf("Tomato").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-01-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }

        val response = listOf(patient1, patient2)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "smiTH",
                givenName = "jOsh"
            )
        } returns response

        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "jOsh",
            family = "smiTH",
            dfe = dfe
        )
        // Assert outcome
        assertNotNull(actualResponse)
        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `ensure when post search matching finds no exact match patients none are returned`() {
        // Not exact name match
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smyth"
                    every { given } returns listOf("Josh").asFHIR()
                },
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Joshua").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }

        // Not an exact match on dob
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-01-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }

        // No returned date of birth
        val patient3 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns null
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1, patient2, patient3)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        every { roninPatient.getRoninIdentifiers(patient2, tenant) } returns roninIdentifiers

        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "Smith",
                givenName = "Josh"
            )
        } returns response

        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )
        // Assert outcome
        assertNotNull(actualResponse)
        val patients = actualResponse.data
        assertEquals(0, patients.size)
    }

    @Test
    fun `ensure when post search matching finds multiple exact match patients all are returned`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh", "Potato").asFHIR()
                },
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Tomato"
                    every { given } returns listOf("Josh", "Potato").asFHIR()
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1, patient2)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        every { roninPatient.getRoninIdentifiers(patient2, tenant) } returns roninIdentifiers

        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1984, 8, 31),
                familyName = "Smith",
                givenName = "Josh"
            )
        } returns response

        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith",
            dfe = dfe
        )
        // Assert outcome
        assertNotNull(actualResponse)
        val patients = actualResponse.data
        assertEquals(2, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
        assertEquals(Patient(patient2, tenant, roninIdentifiers), patients[1])
    }

    @Test
    fun `ensure when post search matching supports supplied given names with multiple words`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Cyrus"
                    every { given } returns listOf("Billy", "Ray").asFHIR()
                }
            )
            every { birthDate } returns Date("1961-08-25")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1961, 8, 25),
                familyName = "Cyrus",
                givenName = "Billy Ray"
            )
        } returns response

        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1961-08-25",
            given = "Billy Ray",
            family = "Cyrus",
            dfe = dfe
        )
        // Assert outcome
        assertNotNull(actualResponse)
        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `ensure when post search matching supports supplied given names with multiple words that is represented as a single given name by the EHR`() {
        val patient1 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value?.value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Cyrus"
                    every { given } returns listOf("Billy Ray").asFHIR()
                }
            )
            every { birthDate } returns Date("1961-08-25")
            every { gender } returns AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value?.value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St").asFHIR()
                    every { city?.value } returns "Anywhere"
                    every { state?.value } returns "FL"
                    every { postalCode?.value } returns "37890"
                }
            )
        }
        val response = listOf(patient1)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.getAuthorizedTenantId() } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234".asFHIR()
            )
        )

        every { roninPatient.getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

        val patientService = mockk<PatientService>()
        every { ehrFactory.getVendorFactory(tenant).patientService } returns patientService
        every {
            patientService.findPatient(
                tenant = tenant,
                birthDate = LocalDate.of(1961, 8, 25),
                familyName = "Cyrus",
                givenName = "Billy Ray"
            )
        } returns response

        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1961-08-25",
            given = "Billy Ray",
            family = "Cyrus",
            dfe = dfe
        )
        // Assert outcome
        assertNotNull(actualResponse)
        val patients = actualResponse.data
        assertEquals(1, patients.size)
        assertEquals(Patient(patient1, tenant, roninIdentifiers), patients[0])
    }

    @Test
    fun `patientsByTenants - no matching tenants`() {
        every { tenantService.getTenantForMnemonic(any()) } returns null

        val response =
            patientHandler.patientsByTenants(listOf("tenant1", "tenant2"), "Smith", "Josh", "2001-02-03", dfe)
        assertEquals(0, response.data.size)
        assertEquals(2, response.errors.size)
        assertEquals("404 Invalid Tenant: tenant1", response.errors[0].message)
        assertEquals("404 Invalid Tenant: tenant2", response.errors[1].message)
    }

    @Test
    fun `patientsByTenants - no patients found`() {
        val tenant1 = mockk<Tenant> {
            every { mnemonic } returns "tenant1"
        }
        val tenant2 = mockk<Tenant> {
            every { mnemonic } returns "tenant2"
        }
        every { tenantService.getTenantForMnemonic("tenant1") } returns tenant1
        every { tenantService.getTenantForMnemonic("tenant2") } returns tenant2

        every { ehrFactory.getVendorFactory(tenant1) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant1, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns emptyList()
            }
        }
        every { ehrFactory.getVendorFactory(tenant2) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant2, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns emptyList()
            }
        }

        val response =
            patientHandler.patientsByTenants(listOf("tenant1", "tenant2"), "Smith", "Josh", "2001-02-03", dfe)
        assertEquals(2, response.data.size)

        val dataByTenant = response.data.associateBy { it.tenantId }
        assertEquals(0, dataByTenant["tenant1"]!!.patients.size)
        assertEquals(0, dataByTenant["tenant2"]!!.patients.size)

        assertEquals(0, response.errors.size)
    }

    @Test
    fun `patientsByTenants - patients found for all`() {
        val tenant1 = mockk<Tenant> {
            every { mnemonic } returns "tenant1"
        }
        val tenant2 = mockk<Tenant> {
            every { mnemonic } returns "tenant2"
        }
        every { tenantService.getTenantForMnemonic("tenant1") } returns tenant1
        every { tenantService.getTenantForMnemonic("tenant2") } returns tenant2

        val patient1 = mockk<R4Patient>(relaxed = true) {
            every { id } returns Id("Patient-UUID-1")
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("2001-02-03")
            every { gender } returns AdministrativeGender.MALE.asCode()
        }
        val patient2 = mockk<R4Patient>(relaxed = true) {
            every { id } returns Id("Patient-UUID-1")
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("2001-02-03")
            every { gender } returns AdministrativeGender.MALE.asCode()
        }

        every { ehrFactory.getVendorFactory(tenant1) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant1, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns listOf(patient1)
            }
        }
        every { ehrFactory.getVendorFactory(tenant2) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant2, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns listOf(patient2)
            }
        }

        every { roninPatient.getRoninIdentifiers(patient1, tenant1) } returns emptyList()
        every { roninPatient.getRoninIdentifiers(patient2, tenant2) } returns emptyList()

        val response =
            patientHandler.patientsByTenants(listOf("tenant1", "tenant2"), "Smith", "Josh", "2001-02-03", dfe)
        assertEquals(2, response.data.size)

        val dataByTenant = response.data.associateBy { it.tenantId }

        assertEquals(1, dataByTenant["tenant1"]!!.patients.size)
        val tenant1Patient1 = dataByTenant["tenant1"]!!.patients[0]
        assertEquals("tenant1-Patient-UUID-1", tenant1Patient1.id)
        assertEquals(0, tenant1Patient1.identifier.size)
        assertEquals(1, tenant1Patient1.name.size)
        assertEquals("usual", tenant1Patient1.name[0].use)
        assertEquals("Smith", tenant1Patient1.name[0].family)
        assertEquals(1, tenant1Patient1.name[0].given.size)
        assertEquals("Josh", tenant1Patient1.name[0].given[0])
        assertEquals("2001-02-03", tenant1Patient1.birthDate)
        assertEquals("male", tenant1Patient1.gender)
        assertEquals(0, tenant1Patient1.telecom.size)
        assertEquals(0, tenant1Patient1.address.size)

        assertEquals(1, dataByTenant["tenant2"]!!.patients.size)
        val tenant2Patient1 = dataByTenant["tenant2"]!!.patients[0]
        assertEquals("tenant2-Patient-UUID-1", tenant2Patient1.id)
        assertEquals(0, tenant2Patient1.identifier.size)
        assertEquals(1, tenant2Patient1.name.size)
        assertEquals("usual", tenant2Patient1.name[0].use)
        assertEquals("Smith", tenant2Patient1.name[0].family)
        assertEquals(1, tenant2Patient1.name[0].given.size)
        assertEquals("Josh", tenant2Patient1.name[0].given[0])
        assertEquals("2001-02-03", tenant2Patient1.birthDate)
        assertEquals("male", tenant2Patient1.gender)
        assertEquals(0, tenant2Patient1.telecom.size)
        assertEquals(0, tenant2Patient1.address.size)

        assertEquals(0, response.errors.size)
    }

    @Test
    fun `patientsByTenants - mixed results for each tenant`() {
        val tenant1 = mockk<Tenant> {
            every { mnemonic } returns "tenant1"
        }
        val tenant2 = mockk<Tenant> {
            every { mnemonic } returns "tenant2"
        }
        every { tenantService.getTenantForMnemonic("tenant1") } returns tenant1
        every { tenantService.getTenantForMnemonic("tenant2") } returns tenant2
        every { tenantService.getTenantForMnemonic("tenant3") } returns null

        val patient1 = mockk<R4Patient>(relaxed = true) {
            every { id } returns Id("Patient-UUID-1")
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Josh").asFHIR()
                }
            )
            every { birthDate } returns Date("2001-02-03")
            every { gender } returns AdministrativeGender.MALE.asCode()
        }

        every { ehrFactory.getVendorFactory(tenant1) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant1, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns listOf(patient1)
            }
        }
        every { ehrFactory.getVendorFactory(tenant2) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant2, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns emptyList()
            }
        }

        every { roninPatient.getRoninIdentifiers(patient1, tenant1) } returns emptyList()

        val response =
            patientHandler.patientsByTenants(
                listOf("tenant1", "tenant2", "tenant3"),
                "Smith",
                "Josh",
                "2001-02-03",
                dfe
            )
        assertEquals(2, response.data.size)

        val dataByTenant = response.data.associateBy { it.tenantId }

        assertEquals(1, dataByTenant["tenant1"]!!.patients.size)
        val tenant1Patient1 = dataByTenant["tenant1"]!!.patients[0]
        assertEquals("tenant1-Patient-UUID-1", tenant1Patient1.id)
        assertEquals(0, tenant1Patient1.identifier.size)
        assertEquals(1, tenant1Patient1.name.size)
        assertEquals("usual", tenant1Patient1.name[0].use)
        assertEquals("Smith", tenant1Patient1.name[0].family)
        assertEquals(1, tenant1Patient1.name[0].given.size)
        assertEquals("Josh", tenant1Patient1.name[0].given[0])
        assertEquals("2001-02-03", tenant1Patient1.birthDate)
        assertEquals("male", tenant1Patient1.gender)
        assertEquals(0, tenant1Patient1.telecom.size)
        assertEquals(0, tenant1Patient1.address.size)

        assertEquals(0, dataByTenant["tenant2"]!!.patients.size)

        assertEquals(1, response.errors.size)
        assertEquals("404 Invalid Tenant: tenant3", response.errors[0].message)
    }

    @Test
    fun `patientsByTenants - honors match filtering`() {
        val tenant1 = mockk<Tenant> {
            every { mnemonic } returns "tenant1"
        }
        every { tenantService.getTenantForMnemonic("tenant1") } returns tenant1

        val patient1 = mockk<R4Patient>(relaxed = true) {
            every { id } returns Id("Patient-UUID-1")
            every { name } returns listOf(
                mockk {
                    every { use } returns USUAL.asCode()
                    every { family?.value } returns "Smith"
                    every { given } returns listOf("Travis").asFHIR()
                }
            )
            every { birthDate } returns Date("2001-02-03")
            every { gender } returns AdministrativeGender.MALE.asCode()
        }

        every { ehrFactory.getVendorFactory(tenant1) } returns mockk {
            every { patientService } returns mockk {
                every { findPatient(tenant1, LocalDate.of(2001, 2, 3), "Josh", "Smith") } returns listOf(patient1)
            }
        }

        val response =
            patientHandler.patientsByTenants(
                listOf("tenant1"),
                "Smith",
                "Josh",
                "2001-02-03",
                dfe
            )
        assertEquals(1, response.data.size)

        val dataByTenant = response.data.associateBy { it.tenantId }
        assertEquals(0, dataByTenant["tenant1"]!!.patients.size)

        assertEquals(0, response.errors.size)
    }
}
