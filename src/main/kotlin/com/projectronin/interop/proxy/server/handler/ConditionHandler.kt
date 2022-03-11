package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
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
import com.projectronin.interop.ehr.model.Condition as EHRCondition
import com.projectronin.interop.proxy.server.model.Condition as ProxyServerCondition

/**
 * Handler for Condition resources.
 */
@Component
class ConditionHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val queueService: QueueService
) : Query {
    private val logger = KotlinLogging.logger { }

    /**
     * Returns only active [ProxyServerCondition]s for the given [patientFhirId] and [conditionCategoryCode]
     * See [Jira](https://projectronin.atlassian.net/browse/INT-284?focusedCommentId=24692)
     */
    @GraphQLDescription("Finds active patient conditions for a given patient and category. Only conditions registered within the category will be returned.")
    fun conditionsByPatientAndCategory(
        tenantId: String,
        patientFhirId: String,
        conditionCategoryCode: ConditionCategoryCode,
        dfe: DataFetchingEnvironment // automatically added to request
    ): DataFetcherResult<List<ProxyServerCondition>> {
        logger.info { "Processing condition query for tenant: $tenantId" }

        // Make sure requested tenant is valid
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId)

        val findConditionErrors = mutableListOf<GraphQLError>()

        // Call condition service
        val conditions = try {
            val conditionService = ehrFactory.getVendorFactory(tenant).conditionService

            conditionService.findConditions(
                tenant = tenant,
                patientFhirId = patientFhirId,
                conditionCategoryCode = conditionCategoryCode.code,
                clinicalStatus = "active" // We're only interested in active Conditions
            ).resources
        } catch (e: Exception) {
            findConditionErrors.add(GraphQLException(e.message).toGraphQLError())
            logger.error(e) { "Condition query for tenant $tenantId contains errors" }

            listOf()
        }

        logger.debug { "Condition query for tenant $tenantId returned" }

        val countWithoutCode = conditions.count { it.code == null }
        if (countWithoutCode > 0) {
            logger.warn { "$countWithoutCode condition(s) returned without codes" }
        }

        // Send conditions to queue service
        try {
            queueService.enqueueMessages(
                conditions.map {
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.CONDITION,
                        tenant = tenantId,
                        text = it.raw
                    )
                }
            )
        } catch (e: Exception) {
            logger.error { "Exception sending conditions to queue: ${e.message}" }
        }

        logger.info { "Condition results for $tenantId sent to queue" }

        // Translate for return
        return DataFetcherResult.newResult<List<ProxyServerCondition>>().data(mapEHRConditions(conditions, tenant))
            .errors(findConditionErrors).build()
    }

    /**
     * Translates a list of [EHRCondition]s into the appropriate list of proxy server [ProxyServerCondition]s for return.
     */
    private fun mapEHRConditions(ehrConditions: List<EHRCondition>, tenant: Tenant): List<ProxyServerCondition> {
        return ehrConditions.map { ProxyServerCondition(it, tenant) }
    }
}
