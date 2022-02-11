package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("The recipient of a message")
data class MessageRecipientInput(
    @GraphQLDescription("The FHIR ID of the message recipient")
    val fhirId: String
)
