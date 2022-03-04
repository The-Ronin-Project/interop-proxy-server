package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.projectronin.interop.proxy.server.dataloaders.ParticipantDataLoader
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.CompletableFuture
import com.projectronin.interop.ehr.model.Participant as EHRParticipant

@GraphQLDescription("An appointment in a clinical setting")
data class Appointment(
    @GraphQLDescription("The internal identifier for this appointment")
    val id: String,
    @GraphQLDescription("List of appointment identifiers")
    val identifier: List<Identifier> = listOf(),
    @GraphQLIgnore
    val tenant: Tenant,
    @GraphQLDescription("When appointment is to take place. An instant in time in the format YYYY-MM-DDThh:mm:ss.sss+zz:zz (e.g. 2015-02-07T13:28:17.239+02:00 or 2017-01-01T00:00:00Z). The time SHALL specified at least to the second and SHALL include a time zone.")
    val start: String,
    @GraphQLDescription("Current status of the meeting")
    val status: String,
    @GraphQLDescription("The specific service that is to be performed during this appointment")
    val serviceType: List<CodeableConcept> = listOf(),
    @GraphQLDescription("The style of appointment or patient that has been booked in the slot (not service type)")
    val appointmentType: CodeableConcept? = null,
    @GraphQLIgnore
    val providers: List<EHRParticipant> = listOf(),
) {
    @GraphQLDescription("Participants on this appointment")
    fun participants(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<Participant>> {
        val loader = dataFetchingEnvironment.getDataLoader<TenantParticipant, Participant>(ParticipantDataLoader.name)
        val tenantParticipants = providers.map { TenantParticipant(tenant = this.tenant, participant = it) }
        return loader.loadMany(tenantParticipants)
    }
}
