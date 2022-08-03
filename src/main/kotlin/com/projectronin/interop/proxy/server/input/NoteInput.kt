package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A message that contains note information from product for HL7v2 downstream writebacks")
data class NoteInput(
    @GraphQLDescription("The identifier of the patient that the note is for")
    val patientId: String,
    @GraphQLDescription("The patient identifier type")
    val patientIdType: PatientIdType,
    @GraphQLDescription("The FHIR ID of the practitioner authoring the note")
    val practitionerFhirId: String,
    @GraphQLDescription("The text of the note that should be sent")
    val noteText: String,
    @GraphQLDescription("Timestamp of when note was recorded, in yyyymmddhhmmss format")
    val datetime: String,
)
