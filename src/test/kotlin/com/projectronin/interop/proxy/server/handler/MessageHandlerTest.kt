package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.ehr.inputs.IdentifierVendorIdentifier
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessagePatientInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessageHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var messageHandler: MessageHandler
    private lateinit var practitionerService: PractitionerService

    private var identifier = Identifier(system = Uri("system"), value = "1234")
    private var identifierVendorIdentifier = IdentifierVendorIdentifier(identifier)

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        practitionerService = mockk()
        messageHandler = MessageHandler(ehrFactory, tenantService, practitionerService)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns null

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        val exception = assertThrows<IllegalArgumentException> {
            messageHandler.sendMessage("TEST_TENANT", messageInput)
        }

        assertEquals("Tenant not found: TEST_TENANT", exception.message)
    }

    @Test
    fun `unknown vendor returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } throws IllegalStateException("Error")

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        val exception = assertThrows<IllegalStateException> {
            messageHandler.sendMessage("TEST_TENANT", messageInput)
        }

        assertEquals("Error", exception.message)
    }

    @Test
    fun `ensure message can be sent`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput = EHRMessageInput("Test Message", "MRN#1", listOf())
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf())
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput)

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with one recipient can be sent`() {
        val tenant = mockk<Tenant>()
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
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiers) } returns (identifierVendorIdentifier)
            }
        }
        every { practitionerService.getPractitionerIdentifiers("doc1") } returns listOf(identifier)

        val messageInput =
            MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf(MessageRecipientInput("doc1")))
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput)

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with multiple recipients can be sent`() {
        val tenant = mockk<Tenant>()
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
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiersDoc) } returns (identifierVendorIdentifier)
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiersPool) } returns (identifierVendorIdentifier)
            }
        }
        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1"),
                listOf(MessageRecipientInput("doc1"), MessageRecipientInput("pool1"))
            )

        every { practitionerService.getPractitionerIdentifiers("doc1") } returns listOf(identifier)
        every { practitionerService.getPractitionerIdentifiers("pool1") } returns listOf(identifier)
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput)

        assertEquals("sent", actualResponse)
    }
}
