package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.ReferenceTypes
import com.projectronin.interop.proxy.server.dataloaders.ParticipantDataLoader
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.CompletableFuture
import com.projectronin.interop.ehr.model.Appointment as EHRAppointment

@GraphQLDescription("An appointment in a clinical setting")
data class Appointment(
    private val appointment: EHRAppointment,
    private val tenant: Tenant
) {
    @GraphQLDescription("The internal identifier for this appointment")
    val id: String by lazy {
        appointment.id.localize(tenant)
    }

    @GraphQLDescription("List of appointment identifiers")
    val identifier: List<Identifier> by lazy {
        appointment.identifier.map(::Identifier)
    }

    @GraphQLDescription("When appointment is to take place. An instant in time in the format YYYY-MM-DDThh:mm:ss.sss+zz:zz (e.g. 2015-02-07T13:28:17.239+02:00 or 2017-01-01T00:00:00Z). The time SHALL specified at least to the second and SHALL include a time zone.")
    val start: String? = appointment.start

    @GraphQLDescription("Current status of the meeting")
    val status: String? = appointment.status?.code

    @GraphQLDescription("The specific service that is to be performed during this appointment")
    val serviceType: List<CodeableConcept> by lazy {
        appointment.serviceType.map(::CodeableConcept)
    }

    @GraphQLDescription("The style of appointment or patient that has been booked in the slot (not service type)")
    val appointmentType: CodeableConcept? by lazy {
        appointment.appointmentType?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("Participants on this appointment")
    fun participants(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<Participant>> {
        val providers = appointment.participants.filter { it.actor.type == ReferenceTypes.PRACTITIONER }
        val tenantParticipants = providers.map { TenantParticipant(tenant = this.tenant, participant = it) }

        val loader = dataFetchingEnvironment.getDataLoader<TenantParticipant, Participant>(ParticipantDataLoader.name)
        return loader.loadMany(tenantParticipants)
    }
}
