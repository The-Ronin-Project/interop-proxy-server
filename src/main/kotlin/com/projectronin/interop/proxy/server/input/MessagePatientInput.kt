package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("The patient for whom the message is being sent")
data class MessagePatientInput(
    @GraphQLDeprecated("Please use patientFhirId")
    @GraphQLDescription("The MRN of the patient")
    val mrn: String?,
    @GraphQLDescription("The Ronin ID of the patient")
    val patientFhirId: String?,
)
