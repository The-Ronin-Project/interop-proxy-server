package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.proxy.server.util.DateUtil
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
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

    @GraphQLDescription("Finds patient(s) by family name, given name, and birthdate (YYYY-mm-dd format).")
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
        logger.debug { "Patient query for tenant $tenantId returned" }
        // post search matching
        val postMatchPatients = postSearchPatientMatch(patients, family, given, birthdate)

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
            logger.error { "Exception sending patients to queue: ${e.message}" }
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

    private fun postSearchPatientMatch(patientList: List<R4Patient>, family: String, given: String, dob: String): List<R4Patient> {

        var returnList = mutableListOf<R4Patient>()
        for (i in patientList) {
            i.name.forEach {
                if (it.family == family && it.given.contains(given) && i.birthDate.toString().contains(dob)) {
                    returnList.add(i)
                }
            }
        }
        return if (returnList.isNullOrEmpty()) {
            patientList
        } else {
            returnList
        }
    }
}
