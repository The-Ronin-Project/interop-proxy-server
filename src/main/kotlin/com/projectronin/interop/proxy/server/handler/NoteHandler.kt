package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Mutation
import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.util.localize
import com.projectronin.interop.proxy.server.input.NoteInput
import com.projectronin.interop.proxy.server.input.NoteSender
import com.projectronin.interop.proxy.server.input.PatientIdType
import com.projectronin.interop.proxy.server.util.DateUtil
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import datadog.trace.api.Trace
import graphql.GraphQLException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import com.projectronin.interop.ehr.inputs.NoteInput as EhrNoteInput
import com.projectronin.interop.ehr.inputs.NoteSender as EhrNoteSender

@Component
class NoteHandler(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val ehrDataAuthorityClient: EHRDataAuthorityClient,
    @Value("\${proxy.mrn.padding.enabled:yes}") private val padMRNs: String = "yes"
) : Mutation {
    private val logger = KotlinLogging.logger { }

    /**
     * Handler for Notes going to downstream EHRs.
     * Sends notes to the queue to be sent to the tenant's EHR system based on tenant Id and noteInput.
     * The return value is the HL7v2 MDM document ID, if the new HL7v2 MDM document could be successfully created.
     */
    @GraphQLDescription("Takes in note from product and processes it for downstream services. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun sendNote(noteInput: NoteInput, tenantId: String, dfe: DataFetchingEnvironment): DataFetcherResult<String> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        return try {
            val ehrNoteInput = setEhrNoteInput(tenant, noteInput)
            val response = ehrFactory.getVendorFactory(tenant).noteService.sendPatientNote(tenant, ehrNoteInput)
            DataFetcherResult.newResult<String>().data(response).build()
        } catch (e: Exception) {
            logger.error(e.getLogMarker(), e) { "Exception occurred while sending note: ${e.message}" }
            val error = GraphQLException(e.message).toGraphQLError()
            DataFetcherResult.newResult<String>().errors(listOf(error)).build()
        }
    }

    @GraphQLDescription("Takes in addendum note from product and processes it for downstream services. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun sendNoteAddendum(
        noteInput: NoteInput,
        tenantId: String,
        parentDocumentId: String,
        dfe: DataFetchingEnvironment
    ): DataFetcherResult<String> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        return try {
            val ehrNoteInput = setEhrNoteInput(tenant, noteInput)
            val response = ehrFactory.getVendorFactory(tenant).noteService.sendPatientNoteAddendum(tenant, ehrNoteInput, parentDocumentId)
            DataFetcherResult.newResult<String>().data(response).build()
        } catch (e: Exception) {
            logger.error(e.getLogMarker(), e) { "Exception occurred while sending note addendum: ${e.message}" }
            val error = GraphQLException(e.message).toGraphQLError()
            DataFetcherResult.newResult<String>().errors(listOf(error)).build()
        }
    }

    private fun getPractitioner(tenant: Tenant, noteInput: NoteInput): Practitioner {
        val practitionerFhirId = noteInput.practitionerFhirId
        val isUDPId = practitionerFhirId.startsWith("${tenant.mnemonic}-")

        return try {
            val id = if (isUDPId) practitionerFhirId else practitionerFhirId.localize(tenant)
            runBlocking { ehrDataAuthorityClient.getResourceAs<Practitioner>(tenant.mnemonic, "Practitioner", id) }
                ?: throw IllegalArgumentException("No Practitioner found for $id")
        } catch (exception: Exception) {
            logWarningMessage(noteInput, exception)

            val id = if (isUDPId) practitionerFhirId.removePrefix("${tenant.mnemonic}-") else practitionerFhirId
            ehrFactory.getVendorFactory(tenant).practitionerService.getPractitioner(tenant, id)
        }
    }

    private fun getPatient(tenant: Tenant, noteInput: NoteInput): Patient {
        return when (noteInput.patientIdType) {
            PatientIdType.FHIR -> {
                // get the Patient from Aidbox
                val patient = runBlocking {
                    ehrDataAuthorityClient.getResourceAs<Patient>(
                        tenant.mnemonic,
                        "Patient",
                        noteInput.patientId
                    )
                } ?: throw IllegalArgumentException("No Patient found for ${noteInput.patientId}")
                patient
            }

            PatientIdType.MRN -> {
                val ehrPatientService = ehrFactory.getVendorFactory(tenant).patientService
                if ((padMRNs == "yes") && (noteInput.patientId.length < 7)) {
                    // pivot from the MRN to get the Patient from the EHR
                    val paddedMrn = noteInput.patientId.padStart(7, '0')
                    val patient = ehrPatientService.getPatient(
                        tenant,
                        ehrPatientService.getPatientFHIRId(tenant, paddedMrn)
                    )
                    val paddedPatient = patient.copy(
                        identifier = patient.identifier +
                            Identifier(
                                system = CodeSystem.RONIN_MRN.uri,
                                value = paddedMrn.asFHIR(),
                                type = CodeableConcepts.RONIN_MRN
                            )
                    )
                    paddedPatient
                } else {
                    // pivot from the MRN to get the Patient from the EHR
                    val patient = ehrPatientService.getPatient(
                        tenant,
                        ehrPatientService.getPatientFHIRId(tenant, noteInput.patientId)
                    )
                    patient
                }
            }
        }
    }
    private fun setEhrNoteInput(tenant: Tenant, noteInput: NoteInput): EhrNoteInput {
        return EhrNoteInput(
            noteText = noteInput.noteText,
            dateTime = DateUtil().parseDateTimeString(noteInput.datetime),
            noteSender = when (noteInput.noteSender) {
                NoteSender.PATIENT -> EhrNoteSender.PATIENT
                NoteSender.PRACTITIONER -> EhrNoteSender.PRACTITIONER
            },
            isAlert = noteInput.isAlert,
            patient = getPatient(tenant, noteInput),
            practitioner = getPractitioner(tenant, noteInput)
        )
    }

    private fun logWarningMessage(noteInput: NoteInput, exception: Exception) {
        logger.warn(exception.getLogMarker(), exception) {
            "Exception sending Note for patient ${noteInput.patientIdType}:${noteInput.patientId}" +
                " from Practitioner ${noteInput.practitionerFhirId}: ${exception.message}"
        }
    }
}
