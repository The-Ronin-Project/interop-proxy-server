package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Condition
import com.projectronin.interop.tenant.config.model.Tenant

@GraphQLDescription("Supporting evidence")
data class Evidence(private val evidence: Condition.Evidence, private val tenant: Tenant) {
    @GraphQLDescription("Manifestation/symptom")
    val code: List<CodeableConcept> by lazy {
        evidence.code.map(::CodeableConcept)
    }

    @GraphQLDescription("Supporting information found elsewhere")
    val detail: List<Reference> by lazy {
        evidence.detail.map { Reference.from(it, tenant) }
    }
}
