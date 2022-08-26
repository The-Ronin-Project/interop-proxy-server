package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.ronin.util.localize
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier
import com.projectronin.interop.fhir.r4.resource.Patient as R4Patient

@GraphQLDescription("A patient")
data class Patient(
    private val patient: R4Patient,
    private val tenant: Tenant,
    private val roninIdentifiers: List<R4Identifier>
) {
    @GraphQLDescription("The internal identifier for this patient")
    val id: String? by lazy {
        patient.id!!.value.localize(tenant)
    }

    @GraphQLDescription("List of patient known identifiers (e.g. MRN, EPI, etc.)")
    val identifier: List<Identifier> by lazy {
        patient.identifier.map(::Identifier) + roninIdentifiers.map(::Identifier)
    }

    @GraphQLDescription("The name(s) of the patient")
    val name: List<HumanName> by lazy {
        patient.name.map(::HumanName)
    }

    @GraphQLDescription("Date of birth in ISO 8601 format (YYYY-MM-DD)")
    val birthDate: String? = patient.birthDate?.value

    @GraphQLDescription("Gender (for administrative uses)")
    val gender: String? = patient.gender?.code

    @GraphQLDescription("The available means of telecommunication")
    val telecom: List<ContactPoint> by lazy {
        patient.telecom.map(::ContactPoint)
    }

    @GraphQLDescription("Physical address(es)")
    val address: List<Address> by lazy {
        patient.address.map(::Address)
    }
}
