package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A message that should be submitted to one or more recipients")
data class MessageInput(
    @GraphQLDescription("The text of the message that should be sent")
    val text: String,
    @GraphQLDescription("The patient for whom the message is being sent")
    val patient: MessagePatientInput,
    @GraphQLDescription("The List of recipients of the message")
    val recipients: List<MessageRecipientInput>,
)
