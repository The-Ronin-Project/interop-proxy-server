package com.projectronin.interop.proxy.server.handler

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import com.projectronin.interop.common.logmarkers.LogMarkers
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.Appointment
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.proxy.server.util.generateMetadata
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.tenant.config.model.vendor.Epic
import graphql.schema.DataFetchingEnvironment
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
import com.projectronin.interop.fhir.r4.resource.Appointment as R4Appointment

@TestInstance(Lifecycle.PER_CLASS)
class AppointmentHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var appointmentHandler: AppointmentHandler
    private lateinit var dfe: DataFetchingEnvironment

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
        appointmentHandler = AppointmentHandler(ehrFactory, tenantService, queueService)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns null
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            appointmentHandler.appointmentsByPatientAndDate(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                startDate = "2025-01-20",
                endDate = "2025-01-22",
                dfe = dfe
            )
        }
        assertEquals("404 Invalid Tenant: tenantId", exception.message)
    }

    @Test
    fun `unauthorized user returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            appointmentHandler.appointmentsByPatientAndDate(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                startDate = "2025-01-20",
                endDate = "2025-01-22",
                dfe = dfe
            )
        }

        assertEquals("403 No Tenants authorized for request.", exception.message)
    }

    @Test
    fun `unauthorized tenant returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "differentTenantId"

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            appointmentHandler.appointmentsByPatientAndDate(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                startDate = "2025-01-20",
                endDate = "2025-01-22",
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

        // Run Test
        val result = appointmentHandler.appointmentsByPatientAndDate(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
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
                        patientFHIRId = "123456789",
                        startDate = LocalDate.of(2025, 1, 20),
                        endDate = LocalDate.of(2025, 1, 22),
                        useEHRFallback = false
                    )
                } throws (IllegalStateException("Error"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = appointmentHandler.appointmentsByPatientAndDate(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
        assertNull(logAppender.list.last().marker)
    }

    @Test
    fun `ensure findAppointment service unavailable sets log marker`() {
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientFHIRId = "123456789",
                        startDate = LocalDate.of(2025, 1, 20),
                        endDate = LocalDate.of(2025, 1, 22),
                        useEHRFallback = false
                    )
                } throws (ServiceUnavailableException(HttpStatusCode.ServiceUnavailable, "Proxy"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = appointmentHandler.appointmentsByPatientAndDate(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Received 503 Service Unavailable when calling Proxy", result.errors[0].message)
        assertEquals(logAppender.list.last().marker, LogMarkers.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `ensure full appointment is correctly returned`() {
        // Mock response
        val appointment1 = mockk<R4Appointment> {
            every { id } returns Id("APPT-ID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "test-system"
                    every { value?.value } returns "test-value"
                }
            )
            every { status?.value } returns AppointmentStatus.BOOKED.code
            every { appointmentType } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system?.value } returns "test-system"
                        every { version?.value } returns "test-version"
                        every { code?.value } returns "test-code"
                        every { display?.value } returns "test-appt-type"
                        every { userSelected?.value } returns true
                    }
                )
                every { text?.value } returns "appt-type-text"
            }
            every { serviceType } returns listOf(
                mockk {
                    every { coding } returns listOf(
                        mockk {
                            every { system?.value } returns "test-system"
                            every { version?.value } returns "test-version"
                            every { code?.value } returns "test-code"
                            every { display?.value } returns "test-service-type"
                            every { userSelected?.value } returns true
                        }
                    )
                    every { text?.value } returns "service-type-text"
                }
            )
            every { start?.value } returns "2025-01-21"
            every { participant } returns listOf(
                mockk {
                    every { actor } returns mockk {
                        every { reference?.value } returns "test-reference"
                        every { display?.value } returns "test-display"
                        every { type } returns Uri("Practitioner")
                        every { id?.value } returns "test-id"
                        every { identifier } returns mockk {
                            every { system?.value } returns "test-system"
                            every { value?.value } returns "test-value"
                        }
                    }
                }
            )
        }
        val response = listOf(appointment1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientFHIRId = "123456789",
                        startDate = LocalDate.of(2025, 1, 20),
                        endDate = LocalDate.of(2025, 1, 22),
                        useEHRFallback = false
                    )
                } returns response
            }
        }
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(appointment1) } returns "serializedAppt"
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.APPOINTMENT,
                        tenant = "tenantId",
                        text = "serializedAppt",
                        metadata = metadata
                    )
                )
            )
        } just Runs

        // Run test
        val actualResponse = appointmentHandler.appointmentsByPatientAndDate(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)

        val appointments = actualResponse.data
        assertEquals(1, appointments.size)
        assertEquals(Appointment(appointment1, tenant), appointments[0])
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        // Mock response
        val appointment1 = mockk<R4Appointment> {
            every { id } returns Id("APPT-ID-1")
            every { identifier } returns listOf(
                mockk {
                    every { system?.value } returns "test-system"
                    every { value?.value } returns "test-value"
                }
            )
            every { status?.value } returns AppointmentStatus.BOOKED.code
            every { appointmentType } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system?.value } returns "test-system"
                        every { version?.value } returns "test-version"
                        every { code?.value } returns "test-code"
                        every { display?.value } returns "test-appt-type"
                        every { userSelected?.value } returns true
                    }
                )
                every { text?.value } returns "appt-type-text"
            }
            every { serviceType } returns listOf(
                mockk {
                    every { coding } returns listOf(
                        mockk {
                            every { system?.value } returns "test-system"
                            every { version?.value } returns "test-version"
                            every { code?.value } returns "test-code"
                            every { display?.value } returns "test-service-type"
                            every { userSelected?.value } returns true
                        }
                    )
                    every { text?.value } returns "service-type-text"
                }
            )
            every { start?.value } returns "2025-01-21"
            every { participant } returns listOf()
        }
        val response = listOf(appointment1)

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientFHIRId = "123456789",
                        startDate = LocalDate.of(2025, 1, 20),
                        endDate = LocalDate.of(2025, 1, 22),
                        useEHRFallback = false
                    )
                } returns response
            }
        }
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(appointment1) } returns "serializedAppt"
        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.APPOINTMENT,
                        tenant = "tenantId",
                        text = "serializedAppt",
                        metadata = metadata
                    )
                )
            )
        } throws (Exception("exception"))

        // Run test
        val actualResponse = appointmentHandler.appointmentsByPatientAndDate(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
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
        val response = listOf<R4Appointment>()

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientFHIRId = "123456789",
                        startDate = LocalDate.of(2025, 1, 20),
                        endDate = LocalDate.of(2025, 1, 22),
                        useEHRFallback = false
                    )
                } returns response
            }
        }

        // Run test
        val actualResponse = appointmentHandler.appointmentsByPatientAndDate(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)
        assertEquals(0, actualResponse.data.size)
    }

    @Test
    fun `test MRN function`() {
        // Mock response
        val response = listOf<R4Appointment>()

        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { tenant.vendor } returns mockk<Epic> {
            every { patientMRNSystem } returns "MRNSYSTEM"
        }
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant = tenant,
                        patientFHIRId = "FHIRID",
                        startDate = LocalDate.of(2025, 1, 20),
                        endDate = LocalDate.of(2025, 1, 22),
                        patientMRN = "MRN",
                        useEHRFallback = false
                    )
                } returns response
            }
            every { patientService } returns mockk {
                every {
                    getPatientFHIRId(
                        tenant = tenant,
                        patientIDValue = "MRN"
                    )
                } returns "FHIRID"
            }
        }

        // Run test
        val actualResponse = appointmentHandler.appointmentsByMRNAndDate(
            tenantId = "tenantId",
            mrn = "MRN",
            startDate = "2025-01-20",
            endDate = "2025-01-22",
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)
        assertEquals(0, actualResponse.data.size)
    }
}
