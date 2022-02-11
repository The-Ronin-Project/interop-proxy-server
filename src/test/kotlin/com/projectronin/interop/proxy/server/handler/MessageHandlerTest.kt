package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Identifier
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
    fun `ensure message with no pool ind can be sent`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput = EHRMessageInput("Test Message", "MRN#1", listOf(EHRRecipient("doc#1", listOf())))
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }
        every { practitionerService.getPractitionerIdentifiers("doc#1") } returns listOf()
        val messageInput =
            MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf(MessageRecipientInput("doc#1")))
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
                        "doc#1",
                        listOf(Identifier(value = "IdentifierID", type = CodeableConcept(text = "EXTERNAL")))
                    ),
                    EHRRecipient("pool#1", listOf())
                )
            )
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }
        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1"),
                listOf(MessageRecipientInput("doc#1"), MessageRecipientInput("pool#1"))
            )

        every { practitionerService.getPractitionerIdentifiers("doc#1") } returns listOf(
            Identifier(
                value = "IdentifierID",
                type = CodeableConcept(text = "EXTERNAL")
            )
        )
        every { practitionerService.getPractitionerIdentifiers("pool#1") } returns listOf()
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput)

        assertEquals("sent", actualResponse)
    }
}
