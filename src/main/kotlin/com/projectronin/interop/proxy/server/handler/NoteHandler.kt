package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.common.hl7.EventType
import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.proxy.server.hl7.MDMService
import com.projectronin.interop.proxy.server.hl7.model.MDMPatientFields
import com.projectronin.interop.proxy.server.hl7.model.MDMPractitionerFields
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.proxy.server.input.NoteSender
import com.projectronin.interop.proxy.server.input.PatientIdType
import com.projectronin.interop.proxy.server.util.asEnum
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.HL7Message
import com.projectronin.interop.tenant.config.TenantService
import datadog.trace.api.Trace
import graphql.schema.DataFetchingEnvironment
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class NoteHandler(
    private val patientService: PatientService,
    private val practitionerService: PractitionerService,
    private val queueService: QueueService,
    private val tenantService: TenantService,
    private val mdmService: MDMService,
) : Mutation {
    private val logger = KotlinLogging.logger { }

    /**
     * Handler for Notes going to downstream EHRs. Sends notes to the queue to be sent to the tenant's EHR system based on tenant Id and noteInput
     */
    @GraphQLDescription("Takes in note from product and processes it for downstream services")
    @Trace
    fun sendNote(noteInput: NoteInput, tenantId: String, dfe: DataFetchingEnvironment): String {
        findAndValidateTenant(dfe, tenantService, tenantId, false)
        return enqueueHL7(noteInput, tenantId)
    }

    @GraphQLDescription("Takes in addendum note from product and processes it for downstream services")
    @Trace
    fun sendNoteAddendum(
        noteInput: NoteInput,
        tenantId: String,
        parentDocumentId: String,
        dfe: DataFetchingEnvironment
    ): String {
        findAndValidateTenant(dfe, tenantService, tenantId, false)
        return enqueueHL7(noteInput, tenantId, parentDocumentId)
    }

    private fun enqueueHL7(noteInput: NoteInput, tenantId: String, parentDocumentId: String? = null): String {
        logger.info { "Receiving Note for patient ${noteInput.patientIdType}: ${noteInput.patientId} from Practitioner ${noteInput.practitionerFhirId}" }
        parentDocumentId?.let { logger.info { "Attempting to addend parent document $it" } }

        val practitioner = practitionerService.getPractitionerByUDPId(tenantId, noteInput.practitionerFhirId)
        val mdmPractitionerFields = MDMPractitionerFields(
            practitioner.name,
            practitioner.identifier
        )

        val patient = when (noteInput.patientIdType) {
            PatientIdType.FHIR -> patientService.getPatientByUDPId(tenantId, noteInput.patientId)
            PatientIdType.MRN -> patientService.getPatientByFHIRId(
                tenantId,
                patientService.getPatientFHIRIds(
                    tenantId,
                    mapOf("key" to SystemValue(system = CodeSystem.RONIN_MRN.uri.value!!, value = noteInput.patientId))
                ).getValue("key")
            )
        }

        val mdmPatientFields = MDMPatientFields(
            patient.identifier,
            patient.name,
            patient.birthDate,
            patient.gender.asEnum<AdministrativeGender>(),
            patient.address,
            patient.telecom
        )

        val documentStatus = if (noteInput.noteSender == NoteSender.PATIENT && noteInput.isAlert) {
            "IP"
        } else {
            "DO"
        }
        val hl7 = mdmService.generateMDM(
            tenantId,
            mdmPatientFields,
            mdmPractitionerFields,
            noteInput.noteText,
            noteInput.datetime,
            parentDocumentId,
            documentStatus
        )

        // Send generated MDM message to queue service
        try {
            queueService.enqueueMessages(
                listOf(
                    HL7Message(
                        id = null,
                        tenant = tenantId,
                        text = hl7.first,
                        hl7Type = MessageType.MDM,
                        hl7Event = parentDocumentId?.let { EventType.MDMT06 } ?: EventType.MDMT02
                    )
                )
            )
        } catch (e: Exception) {
            logger.warn(e.getLogMarker(), e) { "Exception sending note to queue: ${e.message}" }
        }
        return hl7.second
    }
}
