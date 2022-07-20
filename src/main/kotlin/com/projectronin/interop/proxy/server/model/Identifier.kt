package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier

@GraphQLDescription("An identifier intended for computation")
data class Identifier(
    @GraphQLDescription("The namespace for the identifier")
    val system: String?,
    @GraphQLDescription("The value.")
    val value: String
) {
    constructor(identifier: R4Identifier) : this(identifier.system?.value, identifier.value ?: "")

    constructor(identifier: EHRIdentifier) : this(identifier.system, identifier.value)
}
