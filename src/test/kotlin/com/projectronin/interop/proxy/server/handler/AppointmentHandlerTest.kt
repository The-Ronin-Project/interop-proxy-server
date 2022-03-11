package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.Appointment
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
import com.projectronin.interop.ehr.model.Appointment as EHRAppointment

class AppointmentHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var appointmentHandler: AppointmentHandler
    private lateinit var dfe: DataFetchingEnvironment

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        queueService = mockk()
        dfe = mockk()
        appointmentHandler = AppointmentHandler(ehrFactory, tenantService, queueService)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns null
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        // Run Test
        val exception = assertThrows<IllegalArgumentException> {
            appointmentHandler.appointmentsByMRNAndDate(
                tenantId = "tenantId",
                mrn = "123456789",
                startDate = "2025-01-20T13:30:00+00:00",
                endDate = "2025-01-22T14:30:00+00:00",
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

        // Run Test
        val exception = assertThrows<IllegalArgumentException> {
            appointmentHandler.appointmentsByMRNAndDate(
                tenantId = "tenantId",
                mrn = "123456789",
                startDate = "2025-01-20T13:30:00+00:00",
                endDate = "2025-01-22T14:30:00+00:00",
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

        // Run Test
        val exception = assertThrows<IllegalArgumentException> {
            appointmentHandler.appointmentsByMRNAndDate(
                tenantId = "tenantId",
                mrn = "123456789",
                startDate = "2025-01-20T13:30:00+00:00",
                endDate = "2025-01-22T14:30:00+00:00",
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

        // Run Test
        val result = appointmentHandler.appointmentsByMRNAndDate(
            tenantId = "tenantId",
            mrn = "123456789",
            startDate = "2025-01-20T13:30:00+00:00",
            endDate = "2025-01-22T14:30:00+00:00",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
    }

    @Test
    fun `ensure findAppointment exception is returned as error`() {
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientMRN = "123456789",
                        startDate = "2025-01-20T13:30:00+00:00",
                        endDate = "2025-01-22T14:30:00+00:00"
                    )
                } throws (IllegalStateException("Error"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = appointmentHandler.appointmentsByMRNAndDate(
            tenantId = "tenantId",
            mrn = "123456789",
            startDate = "2025-01-20T13:30:00+00:00",
            endDate = "2025-01-22T14:30:00+00:00",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
    }

    @Test
    fun `ensure full appointment is correctly returned`() {
        // Mock response
        val appointment1 = mockk<EHRAppointment> {
            every { id } returns "APPT-ID-1"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "test-system"
                    every { value } returns "test-value"
                }
            )
            every { status } returns com.projectronin.interop.fhir.r4.valueset.AppointmentStatus.BOOKED
            every { appointmentType } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system } returns "test-system"
                        every { version } returns "test-version"
                        every { code } returns "test-code"
                        every { display } returns "test-appt-type"
                        every { userSelected } returns true
                    }
                )
                every { text } returns "appt-type-text"
            }
            every { serviceType } returns listOf(
                mockk {
                    every { coding } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { version } returns "test-version"
                            every { code } returns "test-code"
                            every { display } returns "test-service-type"
                            every { userSelected } returns true
                        }
                    )
                    every { text } returns "service-type-text"
                }
            )
            every { start } returns "2025-01-21T14:30:00+00:00"
            every { raw } returns "raw JSON for appointment"
            every { participants } returns listOf(
                mockk {
                    every { actor } returns mockk {
                        every { reference } returns "test-reference"
                        every { display } returns "test-display"
                        every { type } returns com.projectronin.interop.ehr.model.ReferenceTypes.PRACTITIONER
                        every { id } returns "test-id"
                        every { identifier } returns mockk {
                            every { system } returns "test-system"
                            every { value } returns "test-value"
                        }
                    }
                }
            )
        }
        val response = mockk<Bundle<EHRAppointment>> {
            every { resources } returns listOf(appointment1)
        }

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientMRN = "123456789",
                        startDate = "2025-01-20T13:30:00+00:00",
                        endDate = "2025-01-22T14:30:00+00:00"
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
                        resourceType = ResourceType.APPOINTMENT,
                        tenant = "tenantId",
                        text = "raw JSON for appointment"
                    )
                )
            )
        } just Runs

        // Run test
        val actualResponse = appointmentHandler.appointmentsByMRNAndDate(
            tenantId = "tenantId",
            mrn = "123456789",
            startDate = "2025-01-20T13:30:00+00:00",
            endDate = "2025-01-22T14:30:00+00:00",
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)

        val appointments = actualResponse.data
        assertEquals(1, appointments.size)
        assertEquals(Appointment(appointment1, tenant), appointments[0])
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        // Mock response
        val appointment1 = mockk<EHRAppointment> {
            every { id } returns "APPT-ID-1"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "test-system"
                    every { value } returns "test-value"
                }
            )
            every { status } returns com.projectronin.interop.fhir.r4.valueset.AppointmentStatus.BOOKED
            every { appointmentType } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system } returns "test-system"
                        every { version } returns "test-version"
                        every { code } returns "test-code"
                        every { display } returns "test-appt-type"
                        every { userSelected } returns true
                    }
                )
                every { text } returns "appt-type-text"
            }
            every { serviceType } returns listOf(
                mockk {
                    every { coding } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { version } returns "test-version"
                            every { code } returns "test-code"
                            every { display } returns "test-service-type"
                            every { userSelected } returns true
                        }
                    )
                    every { text } returns "service-type-text"
                }
            )
            every { start } returns "2025-01-21T14:30:00+00:00"
            every { raw } returns "raw JSON for appointment"
            every { participants } returns listOf()
        }
        val response = mockk<Bundle<EHRAppointment>> {
            every { resources } returns listOf(appointment1)
        }

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientMRN = "123456789",
                        startDate = "2025-01-20T13:30:00+00:00",
                        endDate = "2025-01-22T14:30:00+00:00"
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
                        resourceType = ResourceType.APPOINTMENT,
                        tenant = "tenantId",
                        text = "raw JSON for appointment"
                    )
                )
            )
        } throws (Exception("exception"))

        // Run test
        val actualResponse = appointmentHandler.appointmentsByMRNAndDate(
            tenantId = "tenantId",
            mrn = "123456789",
            startDate = "2025-01-20T13:30:00+00:00",
            endDate = "2025-01-22T14:30:00+00:00",
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)

        val appointments = actualResponse.data
        assertEquals(1, appointments.size)
        assertEquals(Appointment(appointment1, tenant), appointments[0])
    }

    @Test
    fun `ensure when ehr returns no appointments none are returned`() {
        // Mock response
        val response = mockk<Bundle<EHRAppointment>> {
            every { resources } returns listOf()
        }

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientMRN = "123456789",
                        startDate = "2025-01-20T13:30:00+00:00",
                        endDate = "2025-01-22T14:30:00+00:00"
                    )
                } returns response
            }
        }

        // Run test
        val actualResponse = appointmentHandler.appointmentsByMRNAndDate(
            tenantId = "tenantId",
            mrn = "123456789",
            startDate = "2025-01-20T13:30:00+00:00",
            endDate = "2025-01-22T14:30:00+00:00",
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)
        assertEquals(0, actualResponse.data.size)
    }
}
