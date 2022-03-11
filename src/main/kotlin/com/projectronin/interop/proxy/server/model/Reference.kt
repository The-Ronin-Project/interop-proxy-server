package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import com.projectronin.interop.transform.fhir.r4.util.localizeReference
import com.projectronin.interop.ehr.model.Reference as EHRReference

@GraphQLDescription("A reference to another FHIR object")
data class Reference(
    @GraphQLDescription("Literal reference, Relative, internal or absolute url")
    val reference: String?,
    @GraphQLDescription("Type of object the reference refers to")
    val type: String?,
    @GraphQLDescription("Text alternative for the resource")
    val display: String?,
    @GraphQLDescription("Logical Reference")
    val identifier: Identifier?,
    @GraphQLDescription("Unique Reference")
    val id: String?
) {
    companion object {
        fun from(reference: EHRReference, tenant: Tenant): Reference =
            Reference(
                reference = reference.reference?.localizeReference(tenant),
                type = reference.type,
                display = reference.display,
                identifier = reference.identifier?.let { Identifier(it) },
                id = reference.id?.localize(tenant)
            )
    }
}
