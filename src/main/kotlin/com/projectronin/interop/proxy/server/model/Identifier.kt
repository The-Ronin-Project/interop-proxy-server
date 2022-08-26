package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier

@GraphQLDescription("An identifier intended for computation")
data class Identifier(private val identifier: R4Identifier) {
    @GraphQLDescription("The namespace for the identifier")
    val system: String? = identifier.system?.value

    @GraphQLDescription("The value.")
    val value: String = identifier.value!!
}
