package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.common.logmarkers.getLogMarker
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.proxy.server.model.PatientsByTenant
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.Collections
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

    @GraphQLDescription(
        "Finds patient(s) that exactly match on family name, given name, and birthdate (YYYY-mm-dd format). " +
            "Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.",
    )
    @Trace
    fun patientsByNameAndDOB(
        tenantId: String,
        family: String,
        given: String,
        birthdate: String,
        dfe: DataFetchingEnvironment,
    ): DataFetcherResult<List<ProxyServerPatient>> {
        logger.info { "Processing patient query for tenant: $tenantId" }

        val findPatientErrors = mutableListOf<GraphQLError>()

        // make sure requested tenant is valid
        val tenant = findAndValidateTenant(dfe, tenantService, tenantId, false)

        val patients = searchPatients(tenant, family, given, birthdate, findPatientErrors)
        queuePatients(patients, tenant)

        // Translate for return
        return DataFetcherResult.newResult<List<ProxyServerPatient>>()
            .data(mapFHIRPatients(patients, tenant))
            .errors(findPatientErrors).build()
    }

    @GraphQLDescription(
        "Finds patient(s) across the supplied tenants that exactly match on family name, given name, and birthdate (YYYY-mm-dd format). " +
            "Requires M2M Authorization or User Auth matching to the requested tenant or will result in an error with no results.",
    )
    @Trace
    fun patientsByTenants(
        tenantIds: List<String>,
        family: String,
        given: String,
        birthdate: String,
        dfe: DataFetchingEnvironment,
    ): DataFetcherResult<List<PatientsByTenant>> {
        logger.info { "Processing patient query for tenants: $tenantIds" }

        val patientSearchErrors = Collections.synchronizedList(mutableListOf<GraphQLError>())

        val patientsByTenants =
            runBlocking {
                val requests =
                    tenantIds.map {
                        async {
                            val tenant =
                                runCatching { findTenant(tenantService, it) }.fold(
                                    onSuccess = { it },
                                    onFailure = {
                                        patientSearchErrors.add(GraphQLException(it.message).toGraphQLError())
                                        null
                                    },
                                )

                            val patients =
                                tenant?.let { searchPatients(tenant, family, given, birthdate, patientSearchErrors) }
                                    ?: emptyList()
                            tenant to patients
                        }
                    }
                awaitAll(*requests.toTypedArray())
            }

        val responseData =
            patientsByTenants.mapNotNull { (tenant, patients) ->
                tenant?.let {
                    queuePatients(patients, tenant)

                    PatientsByTenant(
                        tenant.mnemonic,
                        mapFHIRPatients(patients, tenant),
                    )
                }
            }

        return DataFetcherResult.newResult<List<PatientsByTenant>>()
            .data(responseData).errors(patientSearchErrors).build()
    }

    private fun searchPatients(
        tenant: Tenant,
        family: String,
        given: String,
        birthdate: String,
        errors: MutableList<GraphQLError>,
    ): List<R4Patient> {
        val cleanedFamily = family.replace(",", "")
        val patients =
            try {
                val patientService = ehrFactory.getVendorFactory(tenant).patientService

                patientService.findPatient(
                    tenant = tenant,
                    familyName = cleanedFamily,
                    givenName = given,
                    birthDate = dateFormatter.parseDateString(birthdate),
                )
            } catch (e: Exception) {
                errors.add(GraphQLException("Lookup error for ${tenant.mnemonic}: ${e.message}").toGraphQLError())
                logger.error(e.getLogMarker(), e) { "Patient query for tenant ${tenant.mnemonic} contains errors" }
                listOf()
            }
        logger.debug { "Patient query for tenant ${tenant.mnemonic} returned ${patients.size} patients." }
        // post search matching
        val postMatchPatients = postSearchPatientMatch(patients, family, given, birthdate)
        logger.debug { "Patient post filtering for tenant ${tenant.mnemonic} returned ${postMatchPatients.size} patients." }
        return postMatchPatients
    }

    private fun queuePatients(
        patients: List<R4Patient>,
        tenant: Tenant,
    ) {
        val metadata = generateMetadata()

        try {
            queueService.enqueueMessages(
                patients.map {
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.PATIENT,
                        tenant = tenant.mnemonic,
                        text = JacksonUtil.writeJsonValue(it),
                        metadata = metadata,
                    )
                },
            )
        } catch (e: Exception) {
            logger.warn { "Exception sending patients to queue: ${e.message}" }
        }

        logger.debug { "Patient results for ${tenant.mnemonic} sent to queue" }
    }

    /**
     * Translates a list of [R4Patient]s into the appropriate list of proxy server [ProxyServerPatient]s for return.
     */
    private fun mapFHIRPatients(
        fhirPatients: List<R4Patient>,
        tenant: Tenant,
    ): List<ProxyServerPatient> {
        if (fhirPatients.isEmpty()) return emptyList()
        return fhirPatients.map { ProxyServerPatient(it, tenant) }
    }

    private fun postSearchPatientMatch(
        patientList: List<R4Patient>,
        family: String,
        given: String,
        dob: String,
    ): List<R4Patient> {
        return patientList.filter { patient ->
            val requestDate = Date(dob)
            patient.birthDate?.equals(requestDate) ?: false &&
                patient.name.any { humanName ->
                    humanName.family?.value.equals(family, true) && containsName(given, humanName.given)
                }
        }
    }

    /**
     * Determines if the supplied name is considered covered by the given names. This includes 2 distinct scenarios:
     * * At least one given name matches the supplied name
     * * If the above does not apply, and the [name] contains spaces, each space-delimited word must be present in [givenNames].
     */
    private fun containsName(
        name: String,
        givenNames: List<FHIRString>,
    ): Boolean {
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
    private fun List<FHIRString>.containsIgnoreCase(search: String): Boolean =
        any {
            it.value.equals(search, true)
        }
}
