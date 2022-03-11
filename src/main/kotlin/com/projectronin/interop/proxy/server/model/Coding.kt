package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Coding as EHRCoding

@GraphQLDescription("A Coding is a representation of a defined concept using a symbol from a defined \"code system\"")
data class Coding(private val coding: EHRCoding) {
    @GraphQLDescription("Identity of the terminology system")
    val system: String? = coding.system

    @GraphQLDescription("Version of the system")
    val version: String? = coding.version

    @GraphQLDescription("Symbol in syntax defined by the system")
    val code: String? = coding.code

    @GraphQLDescription("Representation defined by the system")
    val display: String? = coding.display

    @GraphQLDescription("If this coding was chosen directly by the user")
    val userSelected: Boolean? = coding.userSelected
}
