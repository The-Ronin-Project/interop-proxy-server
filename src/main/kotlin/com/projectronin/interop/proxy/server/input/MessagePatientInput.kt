package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("The patient for whom the message is being sent")
data class MessagePatientInput(
    @GraphQLDescription("The MRN of the patient")
    val mrn: String
)
