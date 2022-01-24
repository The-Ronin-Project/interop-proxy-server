package com.projectronin.interop.proxy.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.graphql.spring.boot.test.GraphQLTestTemplate
import com.ninjasquad.springmockk.MockkBean
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")

class InteropProxyServerTests {
    @Autowired
    private lateinit var graphQLTestTemplate: GraphQLTestTemplate

    @MockkBean
    private lateinit var tenantService: TenantService

    @MockkBean
    private lateinit var ehrFactory: EHRFactory

    @Test
    fun `Server handles patient query`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenant") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant,
                        "1980-12-20",
                        "First",
                        "Last"
                    )
                } returns mockk {
                    every { resources } returns listOf()
                }
            }
        }

        val response = graphQLTestTemplate.postForResource("graphql/allPatients.graphql")
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `Server handles appointment query`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenant") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { appointmentService } returns mockk {
                every {
                    findAppointments(
                        tenant,
                        "UUID-APPT-1",
                        "01-01-2020",
                        "01-01-2021"
                    )
                } returns mockk {
                    every { resources } returns listOf()
                }
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
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenant") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every {
                    sendMessage(
                        tenant,
                        EHRMessageInput("This is my text", "12345", listOf(EHRRecipient("1234", false)))
                    )
                } returns "MessageID#1"
            }
        }

        val objectMapper = ObjectMapper()

        val messageInput = objectMapper.createObjectNode()
        messageInput.put("text", "This is my text")

        val patientInput = objectMapper.createObjectNode()
        patientInput.put("mrn", "12345")
        messageInput.put("patient", patientInput)

        val recipientsArray = messageInput.putArray("recipients")
        val recipientInput = objectMapper.createObjectNode()
        recipientInput.put("id", "1234")
        recipientsArray.add(recipientInput)

        val objectNode = objectMapper.createObjectNode()
        objectNode.put("message", messageInput)

        val response = graphQLTestTemplate.perform("graphql/sendMessage.graphql", objectNode)
        assertEquals("""{"data":{"sendMessage":"sent"}}""", response.rawResponse.body)
    }
}
