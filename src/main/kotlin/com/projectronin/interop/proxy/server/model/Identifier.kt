package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier

@GraphQLDescription("An identifier intended for computation")
data class Identifier(private val identifier: EHRIdentifier) {
    @GraphQLDescription("The namespace for the identifier")
    val system: String? = identifier.system

    @GraphQLDescription("The value.")
    val value: String = identifier.value
}
