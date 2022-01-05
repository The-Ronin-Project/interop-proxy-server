package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("An identifier intended for computation")
data class Identifier(
    @GraphQLDescription("The namespace for the identifier")
    val system: String,
    @GraphQLDescription("The value.")
    val value: String
)
