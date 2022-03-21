package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.Patient
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.Message
import com.projectronin.interop.queue.model.MessageType
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import com.projectronin.interop.ehr.model.Patient as EHRPatient

class PatientHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var patientHandler: PatientHandler
    private lateinit var dfe: DataFetchingEnvironment

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
        val exception = assertThrows<IllegalArgumentException> {
            patientHandler.patientsByNameAndDOB(
                tenantId = "tenantId",
                birthdate = "1984-08-31",
                given = "Josh",
                family = "Smith",
                dfe = dfe
            )
        }

        assertEquals("Invalid Tenant: tenantId", exception.message)
    }

    @Test
    fun `unauthorized user returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val exception = assertThrows<IllegalArgumentException> {
            patientHandler.patientsByNameAndDOB(
                tenantId = "tenantId",
                birthdate = "1984-08-31",
                given = "Josh",
                family = "Smith",
                dfe = dfe
            )
        }

        assertEquals("No Tenants authorized for request.", exception.message)
    }

    @Test
    fun `unauthorized tenant returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "differentTenantId"

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val exception = assertThrows<IllegalArgumentException> {
            patientHandler.patientsByNameAndDOB(
                tenantId = "tenantId",
                birthdate = "1984-08-31",
                given = "Josh",
                family = "Smith",
                dfe = dfe
            )
        }

        assertEquals(
            "Requested Tenant 'tenantId' does not match authorized Tenant 'differentTenantId'",
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

        every {
            queueService.enqueueMessages(
                listOf(
                    Message(
                        id = null,
                        messageType = MessageType.API,
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
        assertEquals(Patient(patient1, tenant), patients[0])
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

        every {
            queueService.enqueueMessages(
                listOf(
                    Message(
                        id = null,
                        messageType = MessageType.API,
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
        assertEquals(Patient(patient1, tenant), patients[0])
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
