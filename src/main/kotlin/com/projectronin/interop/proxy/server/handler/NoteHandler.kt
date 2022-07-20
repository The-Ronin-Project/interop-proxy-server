package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.common.hl7.EventType
import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.proxy.server.hl7.MDMService
import com.projectronin.interop.proxy.server.hl7.model.MDMPatientFields
import com.projectronin.interop.proxy.server.hl7.model.MDMPractitionerFields
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.HL7Message
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class NoteHandler(
    private val patientService: PatientService,
    private val practitionerService: PractitionerService,
    private val queueService: QueueService
) : Mutation {
    private val logger = KotlinLogging.logger { }
    /**
     * Handler for Notes going to downstream EHRs. Sends notes to the queue to be sent to the tenant's EHR system based on tenant Id and noteInput
     */
    @GraphQLDescription("Takes in note from product and processes it for downstream services")
    fun sendNote(noteInput: NoteInput, tenantId: String): String {
        logger.info { "Receiving Note for patient ${noteInput.patientFhirId} from Practitioner ${noteInput.practitionerFhirId}" }
        val oncologyPractitioner = practitionerService.getOncologyPractitioner(tenantId, noteInput.practitionerFhirId)
        val oncologyPatient = patientService.getOncologyPatient(tenantId, noteInput.patientFhirId)
        val mdmPatientFields = MDMPatientFields(
            oncologyPatient.identifier,
            oncologyPatient.name,
            oncologyPatient.birthDate,
            oncologyPatient.gender,
            oncologyPatient.address,
            oncologyPatient.telecom
        )
        val mdmPractitionerFields = MDMPractitionerFields(
            oncologyPractitioner.name,
            oncologyPractitioner.identifier
        )
        val hl7 = MDMService().generateMDM(tenantId, mdmPatientFields, mdmPractitionerFields, noteInput.noteText, noteInput.datetime)

        // Send generated MDM message to queue service
        try {
            queueService.enqueueMessages(
                listOf(
                    HL7Message(
                        id = null,
                        tenant = tenantId,
                        text = hl7.first,
                        hl7Type = MessageType.MDM,
                        hl7Event = EventType.MDMT02
                    )
                )
            )
        } catch (e: Exception) {
            logger.error { "Exception sending note to queue: ${e.message}" }
        }
        return hl7.second
    }
}
