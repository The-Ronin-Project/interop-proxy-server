package com.projectronin.interop.proxy.server.handler

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import com.projectronin.interop.common.logmarkers.LogMarkers
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.Patient
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
import com.projectronin.interop.ehr.model.Patient as EHRPatient

@TestInstance(Lifecycle.PER_CLASS)
class PatientHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var patientHandler: PatientHandler
    private lateinit var dfe: DataFetchingEnvironment

    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    private val logAppender = ListAppender<ILoggingEvent>()

    @BeforeAll
    fun initAllTests() {
        logger.addAppender(logAppender)
        logAppender.start()
    }

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        queueService = mockk()
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
        val patient1 = mockk<EHRPatient> {
            every { id } returns "Patient-UUID-1"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns "1984-08-31"
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
            every { raw } returns "raw JSON for patient"
        }
        val response = mockk<Bundle<EHRPatient>> {
            every { resources } returns listOf(patient1)
        }

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
            every { patientTransformer } returns mockk {
                every { getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
            }
        }

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
        val patient1 = mockk<EHRPatient> {
            every { id } returns "Patient-UUID-1"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns "1984-08-31"
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
            every { raw } returns "raw JSON for patient"
        }
        val response = mockk<Bundle<EHRPatient>> {
            every { resources } returns listOf(patient1)
        }

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
            every { patientTransformer } returns mockk {
                every { getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
            }
        }

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
        val patient1 = mockk<EHRPatient> {
            every { id } returns "Patient-UUID-1"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "http://hl7.org/fhir/sid/us-ssn"
                    every { value } returns "987-65-4321"
                }
            )
            every { name } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.NameUse.USUAL
                    every { family } returns "Smith"
                    every { given } returns listOf("Josh")
                }
            )
            every { birthDate } returns "1984-08-31"
            every { gender } returns com.projectronin.interop.fhir.r4.valueset.AdministrativeGender.MALE
            every { telecom } returns listOf(
                mockk {
                    every { system } returns com.projectronin.interop.fhir.r4.valueset.ContactPointSystem.PHONE
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.ContactPointUse.MOBILE
                    every { value } returns "123-456-7890"
                }
            )
            every { address } returns listOf(
                mockk {
                    every { use } returns com.projectronin.interop.fhir.r4.valueset.AddressUse.HOME
                    every { line } returns listOf("1234 Main St")
                    every { city } returns "Anywhere"
                    every { state } returns "FL"
                    every { postalCode } returns "37890"
                }
            )
            every { raw } returns "raw JSON for patient"
        }
        val response = mockk<Bundle<EHRPatient>> {
            every { resources } returns listOf(patient1)
        }

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
            every { patientTransformer } returns mockk {
                every { getRoninIdentifiers(patient1, tenant) } returns roninIdentifiers
            }
        }

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
        val response = mockk<Bundle<EHRPatient>> {
            every { resources } returns listOf()
        }

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
}
