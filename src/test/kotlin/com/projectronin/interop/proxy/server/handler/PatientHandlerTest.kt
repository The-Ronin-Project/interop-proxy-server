package com.projectronin.interop.proxy.server.handler

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import com.projectronin.interop.common.logmarkers.LogMarkers
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.IdentifierService
import com.projectronin.interop.ehr.PatientService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.Patient
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.proxy.server.util.asCode
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
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
    private lateinit var identifierService: IdentifierService
    private lateinit var dfe: DataFetchingEnvironment

    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    private val logAppender = ListAppender<ILoggingEvent>()

    @BeforeAll
    fun initAllTests() {
        logger.addAppender(logAppender)
        logAppender.start()
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        queueService = mockk()
        identifierService = mockk()
        dfe = mockk()
        patientHandler = PatientHandler(ehrFactory, tenantService, queueService)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns null
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

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
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "differentTenantId"

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
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

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
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

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
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
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
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(patient1) } returns "raw JSON for patient"
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        // M2M Auth will not provide an authzTenantId
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
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
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(patient1) } returns "raw JSON for patient"
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
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
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
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
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Potato"
                    every { given } returns listOf("Tomato")
                }
            )
            every { birthDate } returns Date("1984-01-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1, patient2)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
            )
        )
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Potato"
                    every { given } returns listOf("Tomato")
                }
            )
            every { birthDate } returns Date("1984-01-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }

        val response = listOf(patient1, patient2)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
            )
        )
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smyth"
                    every { given } returns listOf("Josh")
                },
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Joshua")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }

        // Not an exact match on dob
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-01-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }

        // No returned date of birth
        val patient3 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns null
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1, patient2, patient3)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
            )
        )
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient2, tenant) } returns roninIdentifiers

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val patient2 = mockk<R4Patient> {
            every { id } returns Id("Patient-UUID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh", "Potato")
                },
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Tomato"
                    every { given } returns listOf("Josh", "Potato")
                }
            )
            every { birthDate } returns Date("1984-08-31")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1, patient2)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
            )
        )
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient2, tenant) } returns roninIdentifiers

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Cyrus"
                    every { given } returns listOf("Billy", "Ray")
                }
            )
            every { birthDate } returns Date("1961-08-25")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
            )
        )
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

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
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL.asCode()
                    every { family } returns "Cyrus"
                    every { given } returns listOf("Billy Ray")
                }
            )
            every { birthDate } returns Date("1961-08-25")
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE.asCode()
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE.asCode()
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE.asCode()
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME.asCode()
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
        }
        val response = listOf(patient1)
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        val roninIdentifiers = listOf(
            Identifier(
                system = Uri("mrnSystem"),
                value = "1234"
            )
        )
        mockkConstructor(RoninPatient::class)
        every { ehrFactory.getVendorFactory(tenant).identifierService } returns identifierService
        every { anyConstructed<RoninPatient>().getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers

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
}
