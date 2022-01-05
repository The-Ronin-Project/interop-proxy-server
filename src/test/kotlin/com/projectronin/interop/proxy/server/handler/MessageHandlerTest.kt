package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
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

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        messageHandler = MessageHandler(ehrFactory, tenantService)
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

        val expectedEHRMessageInput = EHRMessageInput("Test Message", "MRN#1", listOf())
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

        val expectedEHRMessageInput = EHRMessageInput("Test Message", "MRN#1", listOf(EHRRecipient("doc#1", false)))
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }

        val messageInput =
            MessageInput("Test Message", MessagePatientInput("MRN#1"), listOf(MessageRecipientInput("doc#1", null)))
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput)

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with multiple recipients can be sent`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput =
            EHRMessageInput("Test Message", "MRN#1", listOf(EHRRecipient("doc#1", false), EHRRecipient("pool#1", true)))
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }
        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1"),
                listOf(MessageRecipientInput("doc#1", false), MessageRecipientInput("pool#1", true))
            )
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput)

        assertEquals("sent", actualResponse)
    }
}
