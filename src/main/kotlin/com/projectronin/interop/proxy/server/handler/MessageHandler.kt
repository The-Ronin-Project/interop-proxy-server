package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.models.Identifier
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.ehr.inputs.IdentifierVendorIdentifier
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.proxy.server.util.findFhirID
import com.projectronin.interop.proxy.server.util.findFhirIDFromEHRDA
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import datadog.trace.api.Trace
import graphql.GraphQLException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Handler for Message resources.
 */
@Component
class MessageHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val ehrDataAuthorityClient: EHRDataAuthorityClient
) : Mutation {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Sends a message and returns the current status. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun sendMessage(tenantId: String, message: MessageInput, dfe: DataFetchingEnvironment): DataFetcherResult<String> {
        logger.info { "Sending message to $tenantId" }
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        val patientFHIRID = if (message.patient.patientFhirId != null) {
            // check that the inputted UDP ID is correct
            val patient = runBlocking {
                ehrDataAuthorityClient.getResourceAs<Patient>(tenant.mnemonic, "Patient", message.patient.patientFhirId)
            } ?: return createError("No patient found for ${message.patient.patientFhirId}")
            patient.identifier.findFhirID()
        } else if (message.patient.mrn != null) {
            logger.info { "sendMessage called with MRN" }

            val searchIdentifier = Identifier(CodeSystem.RONIN_MRN.uri.value!!, message.patient.mrn)
            // ensure patient exists in EHR Data Authority
            val resources = runBlocking {
                ehrDataAuthorityClient.getResourceIdentifiers(
                    tenant.mnemonic,
                    IdentifierSearchableResourceTypes.Patient,
                    listOf(searchIdentifier)
                )
            }.find { it.searchedIdentifier == searchIdentifier }?.foundResources ?: emptyList()

            when (resources.size) {
                1 -> resources.first().identifiers.findFhirIDFromEHRDA()
                0 -> return createError("Attempted to send message for patient with MRN ${message.patient.mrn} who does not exist in EHR Data Authority.")
                else -> return createError("More than 1 patient found for MRN ${message.patient.mrn} with tenant ${tenant.mnemonic}")
            }
        } else {
            return DataFetcherResult.newResult<String>()
                .errors(listOf(GraphQLException("Either MRN or Ronin ID must be specified").toGraphQLError()))
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
            recipients = message.recipients.map { mapEHRRecipient(tenant, it) }.toList()
        )
    }

    private fun mapEHRRecipient(tenant: Tenant, recipientInput: MessageRecipientInput): EHRRecipient {
        val recipientUDPId = recipientInput.fhirId
        val practitioner = runBlocking {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(tenant.mnemonic, "Practitioner", recipientUDPId)
        } ?: throw IllegalArgumentException("No Practitioner found for $recipientUDPId")
        val practitionerIdentifiers = practitioner.identifier
        val practitionerFhirID = practitionerIdentifiers.findFhirID()

        val vendorIdentifier = ehrFactory.getVendorFactory(tenant).identifierService.getPractitionerUserIdentifier(
            tenant,
            FHIRIdentifiers(
                id = Id(practitionerFhirID),
                identifiers = practitionerIdentifiers
            )
        )

        return EHRRecipient(id = practitionerFhirID, identifier = IdentifierVendorIdentifier(vendorIdentifier))
    }

    private fun createError(errorMessage: String): DataFetcherResult<String> {
        logger.error { errorMessage }
        return DataFetcherResult.newResult<String>().errors(listOf(GraphQLException(errorMessage).toGraphQLError()))
            .build()
    }
}
