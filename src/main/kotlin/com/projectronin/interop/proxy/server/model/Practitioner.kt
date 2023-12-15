package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.util.localizeFhirId
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.resource.Practitioner as R4Practitioner

@GraphQLDescription("A practitioner")
data class Practitioner(
    private val practitioner: R4Practitioner,
    private val tenant: Tenant,
) {
    @GraphQLDescription("The internal identifier for this patient")
    val id: String? by lazy {
        practitioner.id!!.value!!.localizeFhirId(tenant.mnemonic)
    }

    @GraphQLDescription("List of practitioner known identifiers")
    val identifier: List<Identifier> by lazy {
        practitioner.identifier.map(::Identifier)
    }

    @GraphQLDescription("The name(s) of the practitioner")
    val name: List<HumanName> by lazy {
        practitioner.name.map(::HumanName)
    }
}
