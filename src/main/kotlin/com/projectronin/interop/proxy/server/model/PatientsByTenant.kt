package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A grouping of Patients by a tenant")
data class PatientsByTenant(
    @GraphQLDescription("The identifier for the tenant. This will always be a value that was supplied in the request")
    val tenantId: String,
    @GraphQLDescription("A List of patients matching the requested search for the tenant")
    val patients: List<Patient>
)
