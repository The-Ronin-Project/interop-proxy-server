package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.ronin.util.localize
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.resource.Appointment as R4Appointment

@GraphQLDescription("An appointment in a clinical setting")
data class Appointment(
    private val appointment: R4Appointment,
    private val tenant: Tenant
) {
    @GraphQLDescription("The internal identifier for this appointment")
    val id: String by lazy {
        appointment.id!!.value.localize(tenant)
    }

    @GraphQLDescription("List of appointment identifiers")
    val identifier: List<Identifier> by lazy {
        appointment.identifier.map(::Identifier)
    }

    @GraphQLDescription("When appointment is to take place. An instant in time in the format YYYY-MM-DDThh:mm:ss.sss+zz:zz (e.g. 2015-02-07T13:28:17.239+02:00 or 2017-01-01T00:00:00Z). The time SHALL specified at least to the second and SHALL include a time zone.")
    val start: String? = appointment.start?.value

    @GraphQLDescription("Current status of the meeting")
    val status: String = appointment.status?.value ?: ""

    @GraphQLDescription("The specific service that is to be performed during this appointment")
    val serviceType: List<CodeableConcept> by lazy {
        appointment.serviceType.map(::CodeableConcept)
    }

    @GraphQLDescription("The style of appointment or patient that has been booked in the slot (not service type)")
    val appointmentType: CodeableConcept? by lazy {
        appointment.appointmentType?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("Participants on this appointment")
    val participants: List<Participant> by lazy {
        appointment.participant.filter { it.actor?.reference?.contains("Practitioner") ?: false }
            .map { Participant(Reference.from(it.actor!!, tenant)) }
    }
}
