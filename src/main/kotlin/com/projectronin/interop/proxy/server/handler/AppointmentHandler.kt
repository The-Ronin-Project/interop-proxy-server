package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.proxy.server.util.DateUtil
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.proxy.server.util.generateMetadata
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import datadog.trace.api.Trace
import graphql.GraphQLError
import graphql.GraphQLException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import mu.KotlinLogging
import org.springframework.stereotype.Component
import com.projectronin.interop.proxy.server.model.Appointment as ProxyServerAppointment

/**
 * Handler for Appointment resources.
 */
@Component
class AppointmentHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val queueService: QueueService,
) : Query {
    private val logger = KotlinLogging.logger { }
    private val dateFormatter = DateUtil()

    @GraphQLDescription(
        "Finds appointments for a given MRN and date range. " +
            "Requires User Auth matching to the requested tenant or will result in an error with no results.",
    )
    @Deprecated("This query is deprecated.", ReplaceWith("appointmentsByPatientAndDate"))
    @Trace
    fun appointmentsByMRNAndDate(
        tenantId: String,
        mrn: String,
        startDate: String,
        endDate: String,
        dfe: DataFetchingEnvironment,
    ): DataFetcherResult<List<ProxyServerAppointment>> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId)
        val patientFHIRID = ehrFactory.getVendorFactory(tenant).patientService.getPatientFHIRId(tenant, mrn)
        return appointmentHandler(tenant, patientFHIRID, startDate, endDate, mrn)
    }

    @GraphQLDescription(
        "Finds appointments for a given patient UDP ID and date range. " +
            "Requires User Auth matching to the requested tenant or will result in an error with no results.",
    )
    @Trace
    fun appointmentsByPatientAndDate(
        tenantId: String,
        patientFhirId: String,
        startDate: String,
        endDate: String,
        dfe: DataFetchingEnvironment,
    ): DataFetcherResult<List<ProxyServerAppointment>> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId)
        // INT-2073: This should be corrected in Appointment and moved there.
        val unlocalizedId = patientFhirId.unlocalize(tenant)
        return appointmentHandler(tenant, unlocalizedId, startDate, endDate)
    }

    private fun appointmentHandler(
        tenant: Tenant,
        patientFhirId: String,
        startDate: String,
        endDate: String,
        patientMrn: String? = null,
    ): DataFetcherResult<List<ProxyServerAppointment>> {
        logger.info { "Processing appointment query for tenant: ${tenant.name}" }

        val findAppointmentErrors = mutableListOf<GraphQLError>()

        // request appointment list from EHR
        val appointments =
            try {
                val appointmentService = ehrFactory.getVendorFactory(tenant).appointmentService
                appointmentService.findPatientAppointments(
                    tenant = tenant,
                    patientFHIRId = patientFhirId,
                    startDate = dateFormatter.parseDateString(startDate),
                    endDate = dateFormatter.parseDateString(endDate),
                    patientMRN = patientMrn,
                    // prevent overloading Epic with too many API calls
                    useEHRFallback = false,
                )
            } catch (e: Exception) {
                findAppointmentErrors.add(GraphQLException(e.message).toGraphQLError())
                logger.error(e.getLogMarker(), e) { "Appointment query for tenant ${tenant.name} contains error" }
                listOf()
            }

        logger.debug { "Appointment query for tenant ${tenant.name} returned" }

        val metadata = generateMetadata()

        // send appointments to queue service
        if (appointments.isNotEmpty()) {
            try {
                queueService.enqueueMessages(
                    appointments.map {
                        ApiMessage(
                            id = null,
                            resourceType = ResourceType.APPOINTMENT,
                            tenant = tenant.mnemonic,
                            text = JacksonUtil.writeJsonValue(it),
                            metadata = metadata,
                        )
                    },
                )
            } catch (e: Exception) {
                logger.warn { "Exception sending appointments to queue: ${e.message}" }
            }
        }
        logger.info { "Appointments for ${tenant.name} sent to queue" }

        // translate for return
        return DataFetcherResult.newResult<List<ProxyServerAppointment>>()
            .data(mapFHIRAppointments(appointments, tenant))
            .errors(findAppointmentErrors).build()
    }

    /**
     * Translates a list of [Appointment]s into the appropriate list of proxy server [ProxyServerAppointment]s for return.
     */
    private fun mapFHIRAppointments(
        fhirAppointments: List<Appointment>,
        tenant: Tenant,
    ): List<ProxyServerAppointment> {
        return fhirAppointments.map { ProxyServerAppointment(it, tenant) }
    }
}
