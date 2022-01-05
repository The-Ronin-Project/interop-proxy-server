package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("The recipient of a message")
data class MessageRecipientInput(
    @GraphQLDescription("The ID of the message recipient")
    val id: String,
    @GraphQLDescription("True if this recipient represents a pool; otherwise, false.")
    val poolInd: Boolean? = false
)
