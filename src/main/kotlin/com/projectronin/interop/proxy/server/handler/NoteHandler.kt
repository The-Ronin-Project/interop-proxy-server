package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.proxy.server.input.NoteInput
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Handler for Notes going to downstream EHRs
 */
@Component
class NoteHandler(
    /** private val patientService: PatientService,
     * private val practitionerService: PractitionerService
     * for querying aidbox for getOncologyPractitioner and getOncologyPatient
     * need to create new downstream HL7 service that takes in patient and practitioner information
     * and creates an HL7 message for sending to Mirth
     */
) : Mutation {
    private val logger = KotlinLogging.logger { }
    @GraphQLDescription("Takes in note from product and processes it for downstream services")
    fun sendNote(tenantId: String, noteInput: NoteInput): String {
        logger.info { "Receiving Note for patient ${noteInput.patientFhirId} from Practitioner ${noteInput.practitionerFhirId}" }
        // TODO
        return noteInput.patientFhirId
    }
}
