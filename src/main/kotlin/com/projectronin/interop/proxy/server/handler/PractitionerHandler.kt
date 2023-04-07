package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.proxy.server.util.JacksonUtil
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
import com.projectronin.interop.proxy.server.model.Practitioner as ProxyServerPractitioner

/**
 * Handler for Practitioner resources.
 */
@Component
class PractitionerHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val queueService: QueueService
) : Query {
    private val logger = KotlinLogging.logger { }

    @GraphQLDescription("Searches the EHR for a FHIR Practitioner by an internal identifier, and adds it to the Aidbox queue. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun getPractitionerByProvider(
        tenantId: String,
        providerId: String,
        dfe: DataFetchingEnvironment
    ): DataFetcherResult<ProxyServerPractitioner> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        return retrievePractitioner(
            tenant,
            providerId,
            ehrFactory.getVendorFactory(tenant).practitionerService::getPractitionerByProvider
        )
    }

    @GraphQLDescription("Retrieves a FHIR Practitioner from the EHR, and adds it to the Aidbox queue. Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.")
    @Trace
    fun getPractitionerById(
        tenantId: String,
        practitionerFhirId: String,
        dfe: DataFetchingEnvironment
    ): DataFetcherResult<ProxyServerPractitioner> {
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)
        return retrievePractitioner(
            tenant,
            practitionerFhirId,
            ehrFactory.getVendorFactory(tenant).practitionerService::getPractitioner
        )
    }

    internal fun retrievePractitioner(
        tenant: Tenant,
        idToUse: String,
        practitionerLookup: (Tenant, String) -> Practitioner
    ): DataFetcherResult<ProxyServerPractitioner> {
        val errors = mutableListOf<GraphQLError>()
        val practitioner = try {
            practitionerLookup(tenant, idToUse)
        } catch (e: Exception) {
            errors.add(GraphQLException(e.message).toGraphQLError())
            logger.error(e.getLogMarker(), e) { "Practitioner query failed for tenant ${tenant.name}." }
            null
        }
        practitioner?.let {
            try {
                queueService.enqueueMessages(
                    listOf(
                        ApiMessage(
                            id = null,
                            resourceType = ResourceType.PRACTITIONER,
                            tenant = tenant.mnemonic,
                            text = JacksonUtil.writeJsonValue(practitioner)
                        )
                    )
                )
            } catch (e: Exception) {
                logger.warn { "Exception sending practitioners to queue: ${e.message}" }
            }
        }

        return DataFetcherResult.newResult<ProxyServerPractitioner>()
            .data(practitioner?.let { ProxyServerPractitioner(it, tenant) })
            .errors(errors).build()
    }
}
