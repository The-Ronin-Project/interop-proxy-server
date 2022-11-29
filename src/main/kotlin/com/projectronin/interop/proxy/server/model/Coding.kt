package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.Coding as R4Coding

@GraphQLDescription("A Coding is a representation of a defined concept using a symbol from a defined \"code system\"")
data class Coding(private val coding: R4Coding) {
    @GraphQLDescription("Identity of the terminology system")
    val system: String? = coding.system?.value

    @GraphQLDescription("Version of the system")
    val version: String? = coding.version?.value

    @GraphQLDescription("Symbol in syntax defined by the system")
    val code: String? = coding.code?.value

    @GraphQLDescription("Representation defined by the system")
    val display: String? = coding.display?.value

    @GraphQLDescription("If this coding was chosen directly by the user")
    val userSelected: Boolean? = coding.userSelected?.value
}
