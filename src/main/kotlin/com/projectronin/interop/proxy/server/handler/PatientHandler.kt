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
import com.projectronin.interop.ehr.model.Patient as EHRPatient
import com.projectronin.interop.proxy.server.model.Patient as ProxyServerPatient

/**
 * Handler for Patient resources.
 */
@Component
class PatientHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val queueService: QueueService
) : Query {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Finds patient(s) by family name, given name, and birthdate (YYYY-mm-dd format)")
    fun patientsByNameAndDOB(
        tenantId: String,
        family: String,
        given: String,
        birthdate: String,
        dfe: DataFetchingEnvironment // automatically added to request calls
    ): DataFetcherResult<List<ProxyServerPatient>> {
        logger.debug { "Processing patient query for tenant: $tenantId" }

        val findPatientErrors = mutableListOf<GraphQLError>()

        // make sure requested tenant is valid
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId)

        // Call patient service
        val patients = try {
            val patientService = ehrFactory.getVendorFactory(tenant).patientService

            patientService.findPatient(
                tenant = tenant, familyName = family, givenName = given, birthDate = birthdate
            ).resources
        } catch (e: Exception) {
            findPatientErrors.add(GraphQLException(e.message).toGraphQLError())
            logger.error { "Patient query for tenant $tenantId contains errors" }

            listOf()
        }

        logger.debug { "Patient query for tenant $tenantId returned" }

        // Send patients to queue service
        try {
            queueService.enqueueMessages(
                patients.map {
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.PATIENT,
                        tenant = tenantId,
                        text = it.raw
                    )
                }
            )
        } catch (e: Exception) {
            logger.error { "Exception sending patients to queue: ${e.message}" }
        }

        logger.debug { "Patient results for $tenantId sent to queue" }

        // Translate for return
        return DataFetcherResult.newResult<List<ProxyServerPatient>>().data(mapEHRPatients(patients, tenant))
            .errors(findPatientErrors).build()
    }

    /**
     * Translates a list of [EHRPatient]s into the appropriate list of proxy server [ProxyServerPatient]s for return.
     */
    private fun mapEHRPatients(ehrPatients: List<EHRPatient>, tenant: Tenant): List<ProxyServerPatient> {
        return ehrPatients.map { it.toProxyServerPatient(tenant) }
    }
}
