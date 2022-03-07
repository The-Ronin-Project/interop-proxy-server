package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Appointment
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.ehr.model.ReferenceTypes
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.Message
import com.projectronin.interop.queue.model.MessageType
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import graphql.schema.DataFetchingEnvironment
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        val response = mockk<Bundle<Appointment>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "APPT-ID-1"
                    every { identifier } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { value } returns "test-value"
                        }
                    )
                    every { status } returns AppointmentStatus.BOOKED
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
                                every { type } returns ReferenceTypes.PRACTITIONER
                                every { id } returns "test-id"
                                every { identifier } returns mockk {
                                    every { system } returns "test-system"
                                    every { value } returns "test-value"
                                }
                            }
                        }
                    )
                }
            )
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

        // Appointment
        assertEquals(1, actualResponse.data.size)
        val actualAppointment = actualResponse.data[0]
        assertEquals("APPT-ID-1".localize(tenant), actualAppointment.id)
        assertEquals("booked", actualAppointment.status)
        assertEquals("2025-01-21T14:30:00+00:00", actualAppointment.start)

        // Identifier
        assertEquals(1, actualAppointment.identifier.size)
        val actualIdentifier = actualAppointment.identifier[0]
        assertEquals("test-system", actualIdentifier.system)
        assertEquals("test-value", actualIdentifier.value)

        // Appointment Type
        val actualAppointmentType = actualAppointment.appointmentType!!
        assertEquals(1, actualAppointmentType.coding.size)
        val actualAppointmentTypeCoding = actualAppointmentType.coding[0]
        assertEquals("test-system", actualAppointmentTypeCoding.system)
        assertEquals("test-version", actualAppointmentTypeCoding.version)
        assertEquals("test-code", actualAppointmentTypeCoding.code)
        assertEquals("test-appt-type", actualAppointmentTypeCoding.display)
        assertEquals(true, actualAppointmentTypeCoding.userSelected)
        assertEquals("appt-type-text", actualAppointmentType.text)

        // Service Type
        val actualServiceType = actualAppointment.serviceType
        assertEquals(1, actualServiceType.size)
        val actualServiceTypeCC = actualServiceType[0]
        assertEquals(1, actualServiceTypeCC.coding.size)
        val actualServiceTypeCoding = actualServiceTypeCC.coding[0]
        assertEquals("test-system", actualServiceTypeCoding.system)
        assertEquals("test-version", actualServiceTypeCoding.version)
        assertEquals("test-code", actualServiceTypeCoding.code)
        assertEquals("test-service-type", actualServiceTypeCoding.display)
        assertEquals(true, actualServiceTypeCoding.userSelected)
        assertEquals("service-type-text", actualServiceTypeCC.text)
        assertEquals(1, actualAppointment.providers.size)
        assertEquals("test-id", actualAppointment.providers[0].actor.id)
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        // Mock response
        val response = mockk<Bundle<Appointment>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "APPT-ID-1"
                    every { identifier } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { value } returns "test-value"
                        }
                    )
                    every { status } returns AppointmentStatus.BOOKED
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
            )
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

        // Appointment
        assertEquals(1, actualResponse.data.size)
        val actualAppointment = actualResponse.data[0]
        assertEquals("APPT-ID-1".localize(tenant), actualAppointment.id)
        assertEquals("booked", actualAppointment.status)
        assertEquals("2025-01-21T14:30:00+00:00", actualAppointment.start)

        // Identifier
        assertEquals(1, actualAppointment.identifier.size)
        val actualIdentifier = actualAppointment.identifier[0]
        assertEquals("test-system", actualIdentifier.system)
        assertEquals("test-value", actualIdentifier.value)

        // Appointment Type
        val actualAppointmentType = actualAppointment.appointmentType!!
        assertEquals(1, actualAppointmentType.coding.size)
        val actualAppointmentTypeCoding = actualAppointmentType.coding[0]
        assertEquals("test-system", actualAppointmentTypeCoding.system)
        assertEquals("test-version", actualAppointmentTypeCoding.version)
        assertEquals("test-code", actualAppointmentTypeCoding.code)
        assertEquals("test-appt-type", actualAppointmentTypeCoding.display)
        assertEquals(true, actualAppointmentTypeCoding.userSelected)
        assertEquals("appt-type-text", actualAppointmentType.text)

        // Service Type
        val actualServiceType = actualAppointment.serviceType
        assertEquals(1, actualServiceType.size)
        val actualServiceTypeCC = actualServiceType[0]
        assertEquals(1, actualServiceTypeCC.coding.size)
        val actualServiceTypeCoding = actualServiceTypeCC.coding[0]
        assertEquals("test-system", actualServiceTypeCoding.system)
        assertEquals("test-version", actualServiceTypeCoding.version)
        assertEquals("test-code", actualServiceTypeCoding.code)
        assertEquals("test-service-type", actualServiceTypeCoding.display)
        assertEquals(true, actualServiceTypeCoding.userSelected)
        assertEquals("service-type-text", actualServiceTypeCC.text)
        assertEquals(0, actualAppointment.providers.size)
    }

    @Test
    fun `ensure appointment with empty lists is correctly returned`() {
        // Mock response
        val response = mockk<Bundle<Appointment>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "APPT-ID-1"
                    every { identifier } returns listOf()
                    every { status } returns AppointmentStatus.BOOKED
                    every { appointmentType } returns mockk {
                        every { coding } returns listOf()
                        every { text } returns "appt-type-text"
                    }
                    every { serviceType } returns listOf()
                    every { start } returns "2025-01-21T14:30:00+00:00"
                    every { raw } returns "raw JSON for appointment"
                    every { participants } returns listOf()
                }
            )
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

        // Appointment
        assertEquals(1, actualResponse.data.size)
        val actualAppointment = actualResponse.data[0]
        assertEquals("APPT-ID-1".localize(tenant), actualAppointment.id)
        assertEquals("booked", actualAppointment.status)
        assertEquals("2025-01-21T14:30:00+00:00", actualAppointment.start)

        // Identifier
        assertEquals(0, actualAppointment.identifier.size)

        // Appointment Type
        val actualAppointmentType = actualAppointment.appointmentType!!
        assertEquals(0, actualAppointmentType.coding.size)

        // Service Type
        val actualServiceType = actualAppointment.serviceType
        assertEquals(0, actualServiceType.size)
    }

    @Test
    fun `ensure appointment with null values is correctly returned`() {
        // Mock response - All nullable values are set to null
        val response = mockk<Bundle<Appointment>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "APPT-ID-1"
                    every { identifier } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { value } returns "test-value"
                        }
                    )
                    every { status } returns null
                    every { appointmentType } returns null
                    every { serviceType } returns listOf(
                        mockk {
                            every { coding } returns listOf(
                                mockk {
                                    every { system } returns null
                                    every { version } returns null
                                    every { code } returns null
                                    every { display } returns null
                                    every { userSelected } returns null
                                }
                            )
                            every { text } returns null
                        }
                    )
                    every { start } returns null
                    every { raw } returns "raw JSON for appointment"
                    every { participants } returns listOf()
                }
            )
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

        // Appointment
        assertEquals(1, actualResponse.data.size)
        val actualAppointment = actualResponse.data[0]
        assertEquals("APPT-ID-1".localize(tenant), actualAppointment.id)
        assertEquals("", actualAppointment.status)
        assertEquals("", actualAppointment.start)
        assertNull(actualAppointment.appointmentType)
        assertEquals(0, actualAppointment.providers.size)

        // Service Type
        val actualServiceType = actualAppointment.serviceType
        assertEquals(1, actualServiceType.size)
        val actualServiceTypeCC = actualServiceType[0]
        assertEquals(1, actualServiceTypeCC.coding.size)
        val actualServiceTypeCoding = actualServiceTypeCC.coding[0]
        assertNull(actualServiceTypeCoding.system)
        assertNull(actualServiceTypeCoding.version)
        assertNull(actualServiceTypeCoding.code)
        assertNull(actualServiceTypeCoding.display)
        assertNull(actualServiceTypeCoding.userSelected)
        assertNull(actualServiceTypeCC.text)
    }

    @Test
    fun `ensure when ehr returns no appointments none are returned`() {
        // Mock response
        val response = mockk<Bundle<Appointment>> {
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

    @Test
    fun `ensure appointment with partial references return`() {
        // Mock response
        val response = mockk<Bundle<Appointment>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "APPT-ID-1"
                    every { identifier } returns listOf()
                    every { status } returns AppointmentStatus.BOOKED
                    every { appointmentType } returns mockk {
                        every { coding } returns listOf()
                        every { text } returns "appt-type-text"
                    }
                    every { serviceType } returns listOf()
                    every { start } returns "2025-01-21T14:30:00+00:00"
                    every { raw } returns "raw JSON for appointment"
                    every { participants } returns listOf(
                        mockk {
                            every { actor } returns mockk {
                                every { reference } returns "test-reference"
                                every { display } returns "test-display"
                                every { type } returns ReferenceTypes.PRACTITIONER
                                every { id } returns null
                                every { identifier } returns null
                            }
                        },
                        mockk {
                            every { actor } returns mockk {
                                every { reference } returns "test-reference"
                                every { display } returns "test-display"
                                every { type } returns null
                                every { id } returns null
                                every { identifier } returns null
                            }
                        }
                    )
                }
            )
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

        // Appointment
        assertEquals(1, actualResponse.data.size)
        val actualAppointment = actualResponse.data[0]
        assertEquals("APPT-ID-1".localize(tenant), actualAppointment.id)
        assertEquals("booked", actualAppointment.status)
        assertEquals("2025-01-21T14:30:00+00:00", actualAppointment.start)
        assertEquals(1, actualAppointment.providers.size)
        assertEquals("test-display", actualAppointment.providers[0].actor.display)

        // Identifier
        assertEquals(0, actualAppointment.identifier.size)

        // Appointment Type
        val actualAppointmentType = actualAppointment.appointmentType!!
        assertEquals(0, actualAppointmentType.coding.size)

        // Service Type
        val actualServiceType = actualAppointment.serviceType
        assertEquals(0, actualServiceType.size)
    }
}
