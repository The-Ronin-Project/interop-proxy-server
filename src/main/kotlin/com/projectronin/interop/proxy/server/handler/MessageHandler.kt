package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Handler for Message resources.
 */
@Component
class MessageHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val practitionerService: PractitionerService,
) : Mutation {
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
        val messageId = messageService.sendMessage(tenant, mapEHRMessage(tenant, message))

        logger.info { "Message, id $messageId, sent to $tenantId" }
        return "sent"
    }

    private fun mapEHRMessage(tenant: Tenant, message: MessageInput): EHRMessageInput {
        return EHRMessageInput(
            text = message.text,
            patientMRN = message.patient.mrn,
            recipients = message.recipients.map { mapEHRRecipient(tenant, it) }.toList()
        )
    }

    private fun mapEHRRecipient(tenant: Tenant, recipientInput: MessageRecipientInput): EHRRecipient {
        val practitionerIdentifiers = practitionerService.getPractitionerIdentifiers(recipientInput.fhirId)
        val vendorIdentifier = ehrFactory.getVendorFactory(tenant).identifierService.getPractitionerUserIdentifier(
            tenant,
            FHIRIdentifiers(
                id = Id(recipientInput.fhirId),
                identifiers = practitionerIdentifiers
            )
        )

        return EHRRecipient(id = recipientInput.fhirId, identifier = vendorIdentifier)
    }
}
