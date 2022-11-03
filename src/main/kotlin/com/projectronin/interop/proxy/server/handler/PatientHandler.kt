package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.proxy.server.util.DateUtil
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
import com.projectronin.interop.fhir.r4.resource.Patient as R4Patient
import com.projectronin.interop.proxy.server.model.Patient as ProxyServerPatient

/**
 * Handler for Patient resources.
 */
@Component
class PatientHandler(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val queueService: QueueService,
) : Query {
    private val logger = KotlinLogging.logger { }
    private val dateFormatter = DateUtil()

    @GraphQLDescription("Finds patient(s) that exactly match on family name, given name, and birthdate (YYYY-mm-dd format).")
    @Trace
    fun patientsByNameAndDOB(
        tenantId: String,
        family: String,
        given: String,
        birthdate: String,
        dfe: DataFetchingEnvironment // automatically added to request calls
    ): DataFetcherResult<List<ProxyServerPatient>> {
        logger.info { "Processing patient query for tenant: $tenantId" }

        val findPatientErrors = mutableListOf<GraphQLError>()

        // make sure requested tenant is valid
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)

        // Call patient service
        val patients = try {
            val patientService = ehrFactory.getVendorFactory(tenant).patientService

            patientService.findPatient(
                tenant = tenant,
                familyName = family,
                givenName = given,
                birthDate = dateFormatter.parseDateString(birthdate)
            )
        } catch (e: Exception) {
            findPatientErrors.add(GraphQLException(e.message).toGraphQLError())
            logger.error(e.getLogMarker(), e) { "Patient query for tenant $tenantId contains errors" }
            listOf()
        }
        logger.debug { "Patient query for tenant $tenantId returned ${patients.size} patients." }
        // post search matching
        val postMatchPatients = postSearchPatientMatch(patients, family, given, birthdate)
        logger.debug { "Patient post filtering for tenant $tenantId returned ${postMatchPatients.size} patients." }

        // Send patients to queue service
        try {
            queueService.enqueueMessages(
                postMatchPatients.map {
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = tenantId,
                        text = JacksonUtil.writeJsonValue(it)
                    )
                }
            )
        } catch (e: Exception) {
            logger.warn { "Exception sending patients to queue: ${e.message}" }
        }

        logger.debug { "Patient results for $tenantId sent to queue" }

        // Translate for return
        return DataFetcherResult.newResult<List<ProxyServerPatient>>().data(mapFHIRPatients(postMatchPatients, tenant))
            .errors(findPatientErrors).build()
    }

    /**
     * Translates a list of [R4Patient]s into the appropriate list of proxy server [ProxyServerPatient]s for return.
     */
    private fun mapFHIRPatients(fhirPatients: List<R4Patient>, tenant: Tenant): List<ProxyServerPatient> {
        if (fhirPatients.isEmpty()) return emptyList()
        val oncologyPatient = RoninPatient.create(ehrFactory.getVendorFactory(tenant).identifierService)
        return fhirPatients.map { ProxyServerPatient(it, tenant, oncologyPatient.getRoninIdentifiers(it, tenant)) }
    }

    private fun postSearchPatientMatch(
        patientList: List<R4Patient>,
        family: String,
        given: String,
        dob: String
    ): List<R4Patient> {
        return patientList.filter { patient ->
            val requestDate = Date(dob)
            patient.birthDate?.equals(requestDate) ?: false &&
                patient.name.any { humanName ->
                    humanName.family.equals(family, true) && containsName(given, humanName.given)
                }
        }
    }

    /**
     * Determines if the supplied name is considered covered by the given names. This includes 2 distinct scenarios:
     * * At least one given name matches the supplied name
     * * If the above does not apply, and the [name] contains spaces, each space-delimited word must be present in [givenNames].
     */
    private fun containsName(name: String, givenNames: List<String>): Boolean {
        return if (givenNames.containsIgnoreCase(name)) {
            true
        } else {
            name.split(" ").all {
                givenNames.containsIgnoreCase(it)
            }
        }
    }

    /**
     * Extension function to encapsulate case-insensitive searches across a List of Strings.
     */
    private fun List<String>.containsIgnoreCase(search: String): Boolean =
        any {
            search.equals(it, true)
        }
}
