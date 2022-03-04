package com.projectronin.interop.proxy.server.model
import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A participant on an appointment")
data class Participant(
    @GraphQLDescription("The reference to the participant object")
    val actor: Reference
)
