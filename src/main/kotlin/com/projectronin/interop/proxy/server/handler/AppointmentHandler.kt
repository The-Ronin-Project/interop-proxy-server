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
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.GraphQLError
import graphql.GraphQLException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
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
) : Query {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Finds appointments for a given MRN and date range")
    fun appointmentsByMRNAndDate(
        tenantId: String,
        mrn: String,
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
                tenant = tenant, patientMRN = mrn, startDate = startDate, endDate = endDate
            ).resources
        } catch (e: Exception) {
            findAppointmentErrors.add(GraphQLException(e.message).toGraphQLError())
            logger.error(e) { "Appointment query for tenant $tenantId contains error" }
            listOf()
        }

        logger.debug { "Appointment query for tenant $tenantId returned" }

        // send appointments to queue service
        if (appointments.isNotEmpty()) try {
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

        logger.info { "Appointments for $tenantId sent to queue" }

        // translate for return
        return DataFetcherResult.newResult<List<ProxyServerAppointment>>()
            .data(mapEHRAppointments(appointments, tenant))
            .errors(findAppointmentErrors).build()
    }

    /**
     * Translates a list of [EHRAppointment]s into the appropriate list of proxy server [ProxyServerAppointment]s for return.
     */
    private fun mapEHRAppointments(
        ehrAppointments: List<EHRAppointment>,
        tenant: Tenant
    ): List<ProxyServerAppointment> {
        return ehrAppointments.map { ProxyServerAppointment(it, tenant) }
    }
}
