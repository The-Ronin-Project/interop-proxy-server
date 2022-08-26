package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.proxy.server.util.DateUtil
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.tenant.config.model.vendor.Epic
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
    private val queueService: QueueService
) : Query {
    private val logger = KotlinLogging.logger { }
    private val dateFormatter = DateUtil()

    @GraphQLDescription("Finds appointments for a given MRN and date range. Requires User Auth.")
    @Deprecated("This query is deprecated.", ReplaceWith("appointmentsByPatientAndDate"))
    fun appointmentsByMRNAndDate(
        tenantId: String,
        mrn: String,
        startDate: String,
        endDate: String,
        dfe: DataFetchingEnvironment
    ): DataFetcherResult<List<ProxyServerAppointment>> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId)
        val patientFHIRID = ehrFactory.getVendorFactory(tenant).patientService.getPatientFHIRId(
            tenant,
            mrn,
            tenant.vendorAs<Epic>().patientMRNSystem
        ).fhirID
        return appointmentsByPatientAndDate(tenantId, patientFHIRID, startDate, endDate, dfe)
    }

    @GraphQLDescription("Finds appointments for a given patient UDP ID and date range. Requires User Auth.")
    fun appointmentsByPatientAndDate(
        tenantId: String,
        patientFhirId: String,
        startDate: String,
        endDate: String,
        dfe: DataFetchingEnvironment // automatically added to requests
    ): DataFetcherResult<List<ProxyServerAppointment>> {
        logger.info { "Processing appointment query for tenant: $tenantId" }

        val findAppointmentErrors = mutableListOf<GraphQLError>()

        // make sure requested tenant is valid
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId)

        // request appointment list from EHR
        val appointments = try {
            val appointmentService = ehrFactory.getVendorFactory(tenant).appointmentService
            appointmentService.findPatientAppointments(
                tenant = tenant,
                patientFHIRId = patientFhirId,
                startDate = dateFormatter.parseDateString(startDate),
                endDate = dateFormatter.parseDateString(endDate)
            )
        } catch (e: Exception) {
            findAppointmentErrors.add(GraphQLException(e.message).toGraphQLError())
            logger.error(e.getLogMarker(), e) { "Appointment query for tenant $tenantId contains error" }
            listOf()
        }

        logger.debug { "Appointment query for tenant $tenantId returned" }

        // send appointments to queue service
        if (appointments.isNotEmpty()) try {
            queueService.enqueueMessages(
                appointments.map {
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.APPOINTMENT,
                        tenant = tenantId,
                        text = JacksonUtil.writeJsonValue(it)
                    )
                }
            )
        } catch (e: Exception) {
            logger.error { "Exception sending appointments to queue: ${e.message}" }
        }

        logger.info { "Appointments for $tenantId sent to queue" }

        // translate for return
        return DataFetcherResult.newResult<List<ProxyServerAppointment>>()
            .data(mapFHIRAppointments(appointments, tenant))
            .errors(findAppointmentErrors).build()
    }

    /**
     * Translates a list of [Appointment]s into the appropriate list of proxy server [ProxyServerAppointment]s for return.
     */
    private fun mapFHIRAppointments(fhirAppointments: List<Appointment>, tenant: Tenant): List<ProxyServerAppointment> {
        return fhirAppointments.map { ProxyServerAppointment(it, tenant) }
    }
}
