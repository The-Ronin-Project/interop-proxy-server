package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.common.hl7.EventType
import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.ronin.code.RoninCodeSystem
import com.projectronin.interop.fhir.ronin.util.asEnum
import com.projectronin.interop.proxy.server.hl7.MDMService
import com.projectronin.interop.proxy.server.hl7.model.MDMPatientFields
import com.projectronin.interop.proxy.server.hl7.model.MDMPractitionerFields
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.proxy.server.input.PatientIdType
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.HL7Message
import com.projectronin.interop.tenant.config.TenantService
import graphql.schema.DataFetchingEnvironment
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class NoteHandler(
    private val patientService: PatientService,
    private val practitionerService: PractitionerService,
    private val queueService: QueueService,
    private val tenantService: TenantService
) : Mutation {
    private val logger = KotlinLogging.logger { }

    /**
     * Handler for Notes going to downstream EHRs. Sends notes to the queue to be sent to the tenant's EHR system based on tenant Id and noteInput
     */
    @GraphQLDescription("Takes in note from product and processes it for downstream services")
    fun sendNote(noteInput: NoteInput, tenantId: String, dfe: DataFetchingEnvironment): String {
        logger.info { "Receiving Note for patient ${noteInput.patientIdType}: ${noteInput.patientId} from Practitioner ${noteInput.practitionerFhirId}" }

        // Throw an exception if the tenant is bad
        findAndValidateTenant(dfe, tenantService, tenantId, false)

        val practitioner = practitionerService.getPractitioner(tenantId, noteInput.practitionerFhirId)
        val mdmPractitionerFields = MDMPractitionerFields(
            practitioner.name,
            practitioner.identifier
        )

        val patient = when (noteInput.patientIdType) {
            PatientIdType.FHIR -> patientService.getPatient(tenantId, noteInput.patientId)
            PatientIdType.MRN -> patientService.getPatient(
                tenantId,
                patientService.getPatientFHIRIds(
                    tenantId,
                    mapOf("key" to SystemValue(system = RoninCodeSystem.MRN.uri.value, value = noteInput.patientId))
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
        val hl7 = MDMService().generateMDM(
            tenantId,
            mdmPatientFields,
            mdmPractitionerFields,
            noteInput.noteText,
            noteInput.datetime
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
                        hl7Event = EventType.MDMT02
                    )
                )
            )
        } catch (e: Exception) {
            logger.warn(e.getLogMarker(), e) { "Exception sending note to queue: ${e.message}" }
        }
        return hl7.second
    }
}
