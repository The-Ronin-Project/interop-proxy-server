package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.tenant.config.TenantService
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Handler for Message resources.
 */
@Component
class MessageHandler(private val ehrFactory: EHRFactory, private val tenantService: TenantService) : Mutation {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Sends a message and returns the current status")
    fun sendMessage(tenantId: String, message: MessageInput): String {
        logger.info { "Sending message to $tenantId" }

        val tenant =
            tenantService.getTenantForMnemonic(tenantId)
        if (tenant == null) {
            logger.error { "No tenant found for $tenantId" }
            throw IllegalArgumentException("Tenant not found: $tenantId")
        }

        val messageService = ehrFactory.getVendorFactory(tenant).messageService
        // For now there is only one possible vendor/service
        val messageId = messageService.sendMessage(tenant, mapEHRMessage(message))

        logger.info { "Message, id $messageId, sent to $tenantId" }
        return "sent"
    }

    private fun mapEHRMessage(message: MessageInput): EHRMessageInput {
        return EHRMessageInput(
            text = message.text,
            patientMRN = message.patient.mrn,
            recipients = message.recipients.map { mapEHRRecipient(it) }.toList()
        )
    }

    private fun mapEHRRecipient(recipientInput: MessageRecipientInput): EHRRecipient {
        return recipientInput.poolInd?.let { EHRRecipient(recipientInput.id, it) }
            ?: EHRRecipient(recipientInput.id, false)
    }
}
