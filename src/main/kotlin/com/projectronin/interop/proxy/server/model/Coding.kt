package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A Coding is a representation of a defined concept using a symbol from a defined \"code system\"")
data class Coding(
    @GraphQLDescription("Identity of the terminology system")
    val system: String? = null,
    @GraphQLDescription("Version of the system")
    val version: String? = null,
    @GraphQLDescription("Symbol in syntax defined by the system")
    val code: String? = null,
    @GraphQLDescription("Representation defined by the system")
    val display: String? = null,
    @GraphQLDescription("If this coding was chosen directly by the user")
    val userSelected: Boolean? = null
)
