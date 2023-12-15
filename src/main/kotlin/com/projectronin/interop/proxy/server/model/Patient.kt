package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.util.localizeFhirId
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.resource.Patient as R4Patient

@GraphQLDescription("A patient")
data class Patient(
    private val patient: R4Patient,
    private val tenant: Tenant,
) {
    @GraphQLDescription("The internal identifier for this patient")
    val id: String? by lazy {
        patient.id!!.value!!.localizeFhirId(tenant.mnemonic)
    }

    @GraphQLDescription("List of patient known identifiers (e.g. MRN, EPI, etc.)")
    val identifier: List<Identifier> by lazy {
        patient.identifier.map(::Identifier)
    }

    @GraphQLDescription("The name(s) of the patient")
    val name: List<HumanName> by lazy {
        patient.name.map(::HumanName)
    }

    @GraphQLDescription("Date of birth in ISO 8601 format (YYYY-MM-DD)")
    val birthDate: String? = patient.birthDate?.value

    @GraphQLDescription("Gender (for administrative uses)")
    val gender: String? = patient.gender?.value

    @GraphQLDescription("The available means of telecommunication")
    val telecom: List<ContactPoint> by lazy {
        patient.telecom.map(::ContactPoint)
    }

    @GraphQLDescription("Physical address(es)")
    val address: List<Address> by lazy {
        patient.address.map(::Address)
    }
}
