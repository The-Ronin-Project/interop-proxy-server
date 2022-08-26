package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.ConditionEvidence
import com.projectronin.interop.tenant.config.model.Tenant

@GraphQLDescription("Supporting evidence")
data class Evidence(private val evidence: ConditionEvidence, private val tenant: Tenant) {
    @GraphQLDescription("Manifestation/symptom")
    val code: List<CodeableConcept> by lazy {
        evidence.code.map(::CodeableConcept)
    }

    @GraphQLDescription("Supporting information found elsewhere")
    val detail: List<Reference> by lazy {
        evidence.detail.map { Reference.from(it, tenant) }
    }
}
