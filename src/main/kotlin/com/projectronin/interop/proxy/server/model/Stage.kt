package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.resource.ConditionStage
import com.projectronin.interop.tenant.config.model.Tenant

@GraphQLDescription("Stage/grade, usually assessed formally")
data class Stage(private val stage: ConditionStage, private val tenant: Tenant) {
    @GraphQLDescription("Simple summary (disease specific)")
    val summary: CodeableConcept? by lazy {
        stage.summary?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("Formal record of assessment")
    val assessment: List<Reference> by lazy {
        stage.assessment.map { Reference.from(it, tenant) }
    }

    @GraphQLDescription("Kind of staging")
    val type: CodeableConcept? by lazy {
        stage.type?.let { CodeableConcept(it) }
    }
}
