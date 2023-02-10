package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.ehr.inputs.IdentifierVendorIdentifier
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import datadog.trace.api.Trace
import graphql.GraphQLException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
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
    private val patientService: PatientService,
) : Mutation {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Sends a message and returns the current status.")
    @Trace
    fun sendMessage(tenantId: String, message: MessageInput, dfe: DataFetchingEnvironment): DataFetcherResult<String> {
        logger.info { "Sending message to $tenantId" }
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        val patientFHIRID = if (message.patient.patientFhirId != null) {
            message.patient.patientFhirId.unlocalize(tenant)
        } else if (message.patient.mrn != null) {
            logger.info { "sendMessage called with MRN" }
            // ensure patient exists in Aidbox
            patientService.getPatientFHIRIds(
                tenant.mnemonic,
                mapOf("MRN" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = message.patient.mrn))
            ).getOrElse("MRN") {
                val error =
                    "Attempted to send message for patient with MRN ${message.patient.mrn} who does not exist in Aidbox."
                logger.error { error }
                return DataFetcherResult.newResult<String>().errors(listOf(GraphQLException(error).toGraphQLError()))
                    .build()
            }
        } else {
            return DataFetcherResult.newResult<String>().errors(listOf(GraphQLException("Either MRN or Ronin ID must be specified").toGraphQLError()))
                .build()
        }
        val messageService = ehrFactory.getVendorFactory(tenant).messageService

        val messageId = messageService.sendMessage(tenant, mapEHRMessage(tenant, message, patientFHIRID))
        logger.info { "Message, id $messageId, sent to $tenantId" }
        return DataFetcherResult.newResult<String>().data("sent").build()
    }

    private fun mapEHRMessage(tenant: Tenant, message: MessageInput, patientFHIRID: String): EHRMessageInput {
        return EHRMessageInput(
            text = message.text,
            patientFHIRID = patientFHIRID,
            recipients = message.recipients.map { mapEHRRecipient(tenant, it) }.toList(),
        )
    }

    private fun mapEHRRecipient(tenant: Tenant, recipientInput: MessageRecipientInput): EHRRecipient {
        val practitionerIdentifiers =
            practitionerService.getPractitionerIdentifiers(tenant.mnemonic, recipientInput.fhirId.unlocalize(tenant))
        val vendorIdentifier = ehrFactory.getVendorFactory(tenant).identifierService.getPractitionerUserIdentifier(
            tenant,
            FHIRIdentifiers(
                id = Id(recipientInput.fhirId),
                identifiers = practitionerIdentifiers
            )
        )

        return EHRRecipient(id = recipientInput.fhirId, identifier = IdentifierVendorIdentifier(vendorIdentifier))
    }
}
