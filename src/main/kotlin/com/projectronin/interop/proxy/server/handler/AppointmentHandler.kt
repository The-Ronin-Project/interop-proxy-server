package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.Message
import com.projectronin.interop.queue.model.MessageType
import com.projectronin.interop.tenant.config.TenantService
import graphql.GraphQLError
import graphql.GraphQLException
import graphql.execution.DataFetcherResult
import mu.KotlinLogging
import org.springframework.stereotype.Component
import com.projectronin.interop.ehr.model.Appointment as EHRAppointment
import com.projectronin.interop.proxy.server.model.Appointment as ProxyServerAppointment

/**
 * Handler for Appointment resources.
 */
@Component
class AppointmentHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val queueService: QueueService
) :
    Query {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Finds appointments for a given MRN and date range")
    fun appointmentsByMRNAndDate(
        tenantId: String,
        mrn: String,
        startDate: String,
        endDate: String
    ): DataFetcherResult<List<ProxyServerAppointment>> {
        logger.debug { "Processing appointment query for tenant: $tenantId" }

        // Call to appointment service
        val findAppointmentErrors = mutableListOf<GraphQLError>()

        val tenant =
            tenantService.getTenantForMnemonic(tenantId)
        if (tenant == null) {
            findAppointmentErrors.add(GraphQLException("Tenant not found: $tenantId").toGraphQLError())
            logger.error { "No tenant found for $tenantId" }
        }

        val appointments = tenant?.let {
            try {
                val appointmentService = ehrFactory.getVendorFactory(tenant).appointmentService

                appointmentService.findAppointments(
                    tenant = tenant,
                    patientMRN = mrn,
                    startDate = startDate,
                    endDate = endDate
                ).resources
            } catch (e: Exception) {
                findAppointmentErrors.add(GraphQLException(e.message).toGraphQLError())
                logger.error { "Appointment query for tenant $tenantId contains error: ${e.message}" }

                listOf()
            }
        } ?: listOf()

        logger.debug { "Appointment query for tenant $tenantId returned" }

        // Send appointments to queue service
        try {
            queueService.enqueueMessages(
                appointments.map {
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.APPOINTMENT,
                        tenant = tenantId,
                        text = it.raw
                    )
                }
            )
        } catch (e: Exception) {
            logger.error { "Exception sending appointments to queue: ${e.message}" }
        }

        logger.debug { "Appointments for $tenantId sent to queue" }

        // Translate for return
        return DataFetcherResult.newResult<List<ProxyServerAppointment>>().data(mapEHRAppointments(appointments))
            .errors(findAppointmentErrors).build()
    }

    /**
     * Translates a list of [EHRAppointment]s into the appropriate list of proxy server [ProxyServerAppointment]s for return.
     */
    private fun mapEHRAppointments(ehrAppointments: List<EHRAppointment>): List<ProxyServerAppointment> {
        return ehrAppointments.map { it.toProxyServerAppointment() }
    }
}
