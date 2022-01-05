package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A patient")
data class Patient(
    @GraphQLDescription("The internal identifier for this patient")
    val id: String,
    @GraphQLDescription("List of patient known identifiers (e.g. MRN, EPI, etc.)")
    val identifier: List<Identifier> = listOf(),
    @GraphQLDescription("The name(s) of the patient")
    val name: List<HumanName> = listOf(),
    @GraphQLDescription("Date of birth in ISO 8601 format (YYYY-MM-DD)")
    val birthDate: String? = null,
    @GraphQLDescription("Gender (for administrative uses)")
    val gender: String? = null,
    @GraphQLDescription("The available means of telecommunication")
    val telecom: List<ContactPoint> = listOf(),
    @GraphQLDescription("Physical address(es)")
    val address: List<Address> = listOf()
)
