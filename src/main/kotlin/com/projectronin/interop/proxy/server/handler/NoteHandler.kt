package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.common.hl7.EventType
import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.ronin.util.localize
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
import com.projectronin.interop.tenant.config.model.Tenant
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
    private val ehrFactory: EHRFactory
) : Mutation {
    private val logger = KotlinLogging.logger { }

    /**
     * Handler for Notes going to downstream EHRs.
     * Sends notes to the queue to be sent to the tenant's EHR system based on tenant Id and noteInput.
     * The return value is the HL7v2 MDM document ID, if the new HL7v2 MDM document could be successfully created.
     */
    @GraphQLDescription("Takes in note from product and processes it for downstream services. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun sendNote(noteInput: NoteInput, tenantId: String, dfe: DataFetchingEnvironment): String {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        return enqueueHL7(noteInput, tenant)
    }

    @GraphQLDescription("Takes in addendum note from product and processes it for downstream services. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun sendNoteAddendum(
        noteInput: NoteInput,
        tenantId: String,
        parentDocumentId: String,
        dfe: DataFetchingEnvironment
    ): String {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        return enqueueHL7(noteInput, tenant, parentDocumentId)
    }

    private fun enqueueHL7(noteInput: NoteInput, tenant: Tenant, parentDocumentId: String? = null): String {
        logger.info { "Sending Note for patient ${noteInput.patientIdType}:${noteInput.patientId} from Practitioner ${noteInput.practitionerFhirId}" }
        parentDocumentId?.let { logger.info { "Attempting to addend parent document $it" } }

        val practitioner = getPractitioner(tenant, noteInput)
        val mdmPractitionerFields = MDMPractitionerFields(
            practitioner.name,
            practitioner.identifier
        )

        val patient = getPatient(tenant, noteInput)
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
            tenant.mnemonic,
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
                        tenant = tenant.mnemonic,
                        text = hl7.first,
                        hl7Type = MessageType.MDM,
                        hl7Event = parentDocumentId?.let { EventType.MDMT08 } ?: EventType.MDMT02
                    )
                )
            )
        } catch (e: Exception) {
            logger.warn(e.getLogMarker(), e) { "Exception sending note to queue: ${e.message}" }
        }
        return hl7.second
    }

    private fun getPractitioner(tenant: Tenant, noteInput: NoteInput): Practitioner {
        return if (noteInput.practitionerFhirId.startsWith("${tenant.mnemonic}-")) {
            practitionerService.getPractitionerByUDPId(tenant.mnemonic, noteInput.practitionerFhirId)
        } else {
            try {
                practitionerService.getPractitionerByUDPId(
                    tenant.mnemonic,
                    noteInput.practitionerFhirId.localize(tenant)
                )
            } catch (exception: Exception) {
                logWarningMessage(noteInput, exception)
                ehrFactory.getVendorFactory(tenant).practitionerService.getPractitioner(
                    tenant,
                    noteInput.practitionerFhirId
                )
            }
        }
    }

    private fun getPatient(tenant: Tenant, noteInput: NoteInput): Patient {
        return when (noteInput.patientIdType) {
            PatientIdType.FHIR -> {
                // get the Patient from Aidbox
                patientService.getPatientByUDPId(tenant.mnemonic, noteInput.patientId)
            }
            PatientIdType.MRN -> {
                try {
                    // pivot from the MRN to get the Patient from Aidbox
                    val patientFhirId = patientService.getPatientFHIRIds(
                        tenant.mnemonic,
                        mapOf(
                            "patientFhirId" to SystemValue(
                                system = CodeSystem.RONIN_MRN.uri.value!!,
                                value = noteInput.patientId
                            )
                        )
                    ).getValue("patientFhirId")
                    patientService.getPatientByFHIRId(tenant.mnemonic, patientFhirId)
                } catch (exception: Exception) {
                    logWarningMessage(noteInput, exception)
                    // pivot from the MRN to get the Patient from the EHR
                    val ehrPatientService = ehrFactory.getVendorFactory(tenant).patientService
                    ehrPatientService.getPatient(
                        tenant,
                        ehrPatientService.getPatientFHIRId(tenant, noteInput.patientId)
                    )
                }
            }
        }
    }

    private fun logWarningMessage(noteInput: NoteInput, exception: Exception) {
        logger.warn(exception.getLogMarker(), exception) {
            "Exception sending Note for patient ${noteInput.patientIdType}:${noteInput.patientId}" +
                " from Practitioner ${noteInput.practitionerFhirId}: ${exception.message}"
        }
    }
}
