package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.ehr.inputs.IdentifierVendorIdentifier
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.ronin.code.RoninCodeSystem
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessagePatientInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException

class MessageHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var messageHandler: MessageHandler
    private lateinit var practitionerService: PractitionerService
    private lateinit var patientService: PatientService
    private lateinit var dfe: DataFetchingEnvironment

    private var identifier = Identifier(system = Uri("system"), value = "1234".asFHIR())
    private var identifierVendorIdentifier = IdentifierVendorIdentifier(identifier)

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        practitionerService = mockk()
        patientService = mockk {
            every {
                getPatientFHIRIds(
                    "TEST_TENANT",
                    mapOf("MRN" to SystemValue(system = RoninCodeSystem.MRN.uri.value!!, value = "MRN#1"))
                )
            } returns mapOf("MRN" to "FHIRID")
        }
        messageHandler = MessageHandler(ehrFactory, tenantService, practitionerService, patientService)
        dfe = mockk()
    }

    @Test
    fun `patient not found`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant
        every {
            patientService.getPatientFHIRIds(
                "TEST_TENANT",
                mapOf("MRN" to SystemValue(system = RoninCodeSystem.MRN.uri.value!!, value = "MRN#1"))
            )
        } returns emptyMap()
        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        assertEquals(
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).errors.first().message,
            "Attempted to send message for patient with MRN MRN#1 who does not exist in Aidbox."
        )
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns null

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        val exception = assertThrows<HttpClientErrorException> {
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe)
        }

        assertEquals("404 Invalid Tenant: TEST_TENANT", exception.message)
    }

    @Test
    fun `unknown vendor returns an error`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } throws IllegalStateException("Error")

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        val exception = assertThrows<IllegalStateException> {
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe)
        }

        assertEquals("Error", exception.message)
    }

    @Test
    fun `ensure message can be sent`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant
        val expectedEHRMessageInput = EHRMessageInput("Test Message", "MRN#1", listOf())
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with one recipient can be sent`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "TEST_TENANT"
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput =
            EHRMessageInput(
                "Test Message",
                "MRN#1",
                listOf(
                    EHRRecipient("doc1", identifierVendorIdentifier)
                )
            )
        val fhirIdentifiers = FHIRIdentifiers(
            id = Id("doc1"),
            identifiers = listOf(identifier)
        )

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
            every { identifierService } returns mockk {
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiers) } returns (identifier)
            }
        }
        every { practitionerService.getPractitionerIdentifiers("TEST_TENANT", "doc1") } returns listOf(identifier)

        val messageInput =
            MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf(MessageRecipientInput("doc1")))
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with multiple recipients can be sent`() {
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "TEST_TENANT"
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput =
            EHRMessageInput(
                "Test Message",
                "MRN#1",
                listOf(
                    EHRRecipient(
                        "doc1",
                        identifierVendorIdentifier
                    ),
                    EHRRecipient(
                        "pool1",
                        identifierVendorIdentifier
                    )
                )
            )
        val fhirIdentifiersDoc = FHIRIdentifiers(
            id = Id("doc1"),
            identifiers = listOf(identifier)
        )
        val fhirIdentifiersPool = FHIRIdentifiers(
            id = Id("pool1"),
            identifiers = listOf(identifier)
        )

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
            every { identifierService } returns mockk {
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiersDoc) } returns (identifier)
                every {
                    getPractitionerUserIdentifier(
                        tenant,
                        fhirIdentifiersPool
                    )
                } returns (identifier)
            }
        }
        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1"),
                listOf(MessageRecipientInput("doc1"), MessageRecipientInput("pool1"))
            )

        every { practitionerService.getPractitionerIdentifiers("TEST_TENANT", "doc1") } returns listOf(identifier)
        every { practitionerService.getPractitionerIdentifiers("TEST_TENANT", "pool1") } returns listOf(identifier)
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }
}
