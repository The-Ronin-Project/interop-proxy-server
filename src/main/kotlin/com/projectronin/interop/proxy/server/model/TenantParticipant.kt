package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.ehr.model.Participant as EHRParticipant

@GraphQLIgnore
data class TenantParticipant(
    val tenant: Tenant,
    val participant: EHRParticipant
)
