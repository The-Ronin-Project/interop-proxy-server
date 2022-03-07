package com.projectronin.interop.proxy.server.dataloaders

import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.model.SystemValue
import com.projectronin.interop.proxy.server.handler.toProxyServerParticipant
import mu.KotlinLogging
import org.springframework.stereotype.Component
import com.projectronin.interop.ehr.model.Participant as EHRParticipant
import com.projectronin.interop.proxy.server.model.Participant as ProxyParticipant

@Component("practitionersServiceBean")
class ParticipantService(
    private val practitionerService: PractitionerService
) {
    val logger = KotlinLogging.logger {}

    fun getParticipants(ehrParticipants: Set<EHRParticipant>, tenantId: String): Map<EHRParticipant, ProxyParticipant> {
        logger.info("Retrieving ${ehrParticipants.size} Participants from Aidbox for Tenant:$tenantId")

        // either the results we got from the EHR already had an ID or they didn't
        // we don't need to resolve the ones that already had an ID
        val noIDParticipants = ehrParticipants.filter { it.actor.id === null }

        val foundIDsMap = practitionerService.getPractitionerFHIRIds(
            tenantMnemonic = tenantId,
            identifiers = noIDParticipants.associateWith {
                SystemValue(
                    system = it.actor.identifier?.system ?: "",
                    value = it.actor.identifier?.value ?: ""
                )
            }
        )

        return ehrParticipants.associateWith { ehrParticipant ->
            ehrParticipant.toProxyServerParticipant(foundIDsMap)
        }
    }
}
