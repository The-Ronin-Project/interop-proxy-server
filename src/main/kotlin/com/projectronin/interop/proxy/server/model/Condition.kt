package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A patient condition")
data class Condition(
    @GraphQLDescription("The internal identifier for this condition")
    val id: String,
    @GraphQLDescription("List of external identifiers for this condition")
    val identifier: List<Identifier> = listOf(),
    @GraphQLDescription("The clinical status of this condition (e.g. active, relapse, recurrence, etc)")
    val clinicalStatus: CodeableConcept? = null,
    @GraphQLDescription("The category of this condition (e.g. probelm-list-item, encounter-diagnosis, etc)")
    val category: List<CodeableConcept> = listOf(),
    @GraphQLDescription("Identification of the condition, problem or diagnosis")
    val code: CodeableConcept
)
