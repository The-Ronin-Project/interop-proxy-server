package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A message that contains note information from product for HL7 downstream writebacks")
data class NoteInput(
    @GraphQLDescription("The FHIR ID of the patient")
    val patientFhirId: String,
    @GraphQLDescription("The FHIR ID of the practitioner authoring the note")
    val practitionerFhirId: String,
    @GraphQLDescription("The text of the note that should be sent")
    val noteText: String
)
