package com.projectronin.interop.proxy.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.graphql.spring.boot.test.GraphQLTestTemplate
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.common.exceptions.VendorIdentifierNotFoundException
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.ehr.inputs.IdentifierVendorIdentifier
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.ronin.code.RoninCodeSystem
import com.projectronin.interop.proxy.server.handler.findAndValidateTenant
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InteropProxyServerTests {
    @Autowired
    private lateinit var graphQLTestTemplate: GraphQLTestTemplate

    @MockkBean
    private lateinit var ehrFactory: EHRFactory

    @MockkBean
    private lateinit var practitionerService: PractitionerService

    @MockkBean
    private lateinit var patientService: PatientService

    @Test
    fun `Server handles patient query`() {
        val tenant = mockk<Tenant>()
        mockkStatic("com.projectronin.interop.proxy.server.handler.TenantUtilKt")
        every { findAndValidateTenant(any(), any(), any()) } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant,
                        LocalDate.of(1980, 12, 20),
                        "First",
                        "Last"
                    )
                } returns listOf()
            }
        }

        val response = graphQLTestTemplate.postForResource("graphql/allPatients.graphql")
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `Server handles appointment query`() {
        val tenant = mockk<Tenant>()
        mockkStatic("com.projectronin.interop.proxy.server.handler.TenantUtilKt")
        every { findAndValidateTenant(any(), any(), any()) } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findPatientAppointments(
                        tenant,
                        "UUID-APPT-1",
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2021, 1, 1)
                    )
                } returns listOf()
            }
        }

        val response = graphQLTestTemplate.postForResource("graphql/appointmentByMRN.graphql")
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `Server handles condition query`() {

        val response = graphQLTestTemplate.postForResource("graphql/conditionsByPatientAndCategory.graphql")
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `Handles message input`() {

        val identifier = Identifier(value = "IdentifierID", system = Uri("system"))

        every { practitionerService.getPractitionerIdentifiers("tenant", "1234") } returns listOf(identifier)
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        every { tenant.mnemonic } returns "tenant"

        mockkStatic("com.projectronin.interop.proxy.server.handler.TenantUtilKt")
        every { findAndValidateTenant(any(), any(), any(), false) } returns tenant
        every {
            patientService.getPatientFHIRIds(
                "tenant",
                mapOf("MRN" to SystemValue(system = RoninCodeSystem.MRN.uri.value, value = "12345"))
            )
        } returns mapOf("MRN" to "1234")
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every {
                    sendMessage(
                        tenant,
                        EHRMessageInput(
                            "This is my text",
                            "12345",
                            listOf(
                                EHRRecipient(
                                    "1234",
                                    IdentifierVendorIdentifier(identifier)
                                )
                            )
                        )
                    )
                } returns "MessageID#1"
            }
            every { identifierService } returns mockk {
                every {
                    getPractitionerUserIdentifier(
                        tenant,
                        FHIRIdentifiers(
                            id = Id("1234"),
                            identifiers = listOf(identifier)
                        )
                    )
                } returns identifier
                every {
                    getPractitionerUserIdentifier(
                        tenant,
                        FHIRIdentifiers(
                            id = Id("1234"),
                            identifiers = listOf()
                        )
                    )
                } throws (VendorIdentifierNotFoundException("Error"))
            }
        }

        val objectMapper = ObjectMapper()

        val messageInput = objectMapper.createObjectNode()
        messageInput.put("text", "This is my text")

        val patientInput = objectMapper.createObjectNode()
        patientInput.put("mrn", "12345")
        messageInput.replace("patient", patientInput)

        val recipientsArray = messageInput.putArray("recipients")
        val recipientInput = objectMapper.createObjectNode()
        recipientInput.put("fhirId", "1234")
        recipientsArray.add(recipientInput)

        val objectNode = objectMapper.createObjectNode()
        objectNode.replace("message", messageInput)

        val response = graphQLTestTemplate.withAdditionalHeader("AuthorizedTenant", "tenant")
            .perform("graphql/sendMessage.graphql", objectNode)
        assertEquals("""{"data":{"sendMessage":"sent"}}""", response.rawResponse.body)
    }

    @AfterEach
    fun after() {
        unmockkStatic("com.projectronin.interop.proxy.server.handler.TenantUtilKt")
    }
}
