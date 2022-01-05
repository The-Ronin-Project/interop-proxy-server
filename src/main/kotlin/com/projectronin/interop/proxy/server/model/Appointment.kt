package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("An appointment in a clinical setting")
data class Appointment(
    @GraphQLDescription("The internal identifier for this appointment")
    val id: String,
    @GraphQLDescription("List of appointment identifiers")
    val identifier: List<Identifier> = listOf(),
    @GraphQLDescription("When appointment is to take place. An instant in time in the format YYYY-MM-DDThh:mm:ss.sss+zz:zz (e.g. 2015-02-07T13:28:17.239+02:00 or 2017-01-01T00:00:00Z). The time SHALL specified at least to the second and SHALL include a time zone.")
    val start: String,
    @GraphQLDescription("Current status of the meeting")
    val status: String,
    @GraphQLDescription("The specific service that is to be performed during this appointment")
    val serviceType: List<CodeableConcept> = listOf(),
    @GraphQLDescription("The style of appointment or patient that has been booked in the slot (not service type)")
    val appointmentType: CodeableConcept? = null
)
