package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.ehr.model.ReferenceTypes
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import mu.KotlinLogging
import com.projectronin.interop.ehr.model.Participant as EHRParticipant
import com.projectronin.interop.proxy.server.model.Participant as ProxyParticipant
import com.projectronin.interop.proxy.server.model.Reference as ProxyReference

fun EHRParticipant.toProxyServerParticipant(fhirIDMap: Map<EHRParticipant, String>, tenant: Tenant): ProxyParticipant {
    // use existing FHIR ID, otherwise one from map
    val fhirID = actor.id?.localize(tenant) ?: fhirIDMap[this]
    val reference: String? =
        if (fhirID == null) {
            // when this happens we should just return relevant information to the caller and let them decide
            KotlinLogging.logger {}.warn("ID for Participant not found for Identifier: ${actor.identifier}")
            null // this is just so we don't get "Provider/null"
        } else {
            "Provider/$fhirID"
        }

    return ProxyParticipant(
        actor = ProxyReference(
            display = actor.display,
            reference = reference,
            id = fhirID,
            identifier = null,
            type = ReferenceTypes.PRACTITIONER
        )
    )
}
