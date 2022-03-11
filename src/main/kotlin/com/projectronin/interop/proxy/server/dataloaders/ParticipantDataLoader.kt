package com.projectronin.interop.proxy.server.dataloaders

import com.expediagroup.graphql.server.execution.KotlinDataLoader
import com.projectronin.interop.proxy.server.model.TenantParticipant
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import com.projectronin.interop.proxy.server.model.Participant as ProxyParticipant

@Component
class ParticipantDataLoader(private val service: ParticipantService) :
    KotlinDataLoader<TenantParticipant, ProxyParticipant> {
    companion object {
        const val name = "ParticipantDataLoader"
    }

    override val dataLoaderName = name

    override fun getDataLoader(): DataLoader<TenantParticipant, ProxyParticipant> {
        // return a data loader which will later supply the actual results
        return DataLoaderFactory.newMappedDataLoader { tenantProviders ->
            val tenant = tenantProviders.first().tenant
            val providers = tenantProviders.map { it.participant }.toSet()
            val serviceResults = service.getParticipants(providers, tenant)
            val results = tenantProviders.associateWith { serviceResults[it.participant] }
            CompletableFuture.completedStage(results)
        }
    }
}
