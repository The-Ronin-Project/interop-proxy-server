package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.util.localize
import com.projectronin.interop.fhir.util.localizeFhirId
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

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
    val id: String?,
) {
    companion object {
        fun from(
            reference: R4Reference?,
            tenant: Tenant,
        ): Reference {
            val localizedReference = reference?.localize(tenant.mnemonic)
            localizedReference.apply {
                return Reference(
                    reference = this?.reference?.value,
                    type = reference?.decomposedType(),
                    display = this?.display?.value,
                    identifier = this?.identifier?.let { Identifier(it) },
                    id = reference?.decomposedId()?.localizeFhirId(tenant.mnemonic),
                )
            }
        }
    }
}
