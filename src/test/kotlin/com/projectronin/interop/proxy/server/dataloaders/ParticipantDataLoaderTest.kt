package com.projectronin.interop.proxy.server.dataloaders

import com.projectronin.interop.proxy.server.model.TenantParticipant
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Participant as EHRParticipant
import com.projectronin.interop.proxy.server.model.Participant as ProxyParticipant

class ParticipantDataLoaderTest {
    private lateinit var participantService: ParticipantService
    private lateinit var participantDataLoader: ParticipantDataLoader

    @BeforeEach
    fun initTest() {
        participantService = mockk()
    }
    @Test
    fun `get name works`() {
        participantDataLoader = ParticipantDataLoader(participantService)
        assertEquals("ParticipantDataLoader", participantDataLoader.dataLoaderName)
    }
    @Test
    fun `get data loader works`() {
        participantDataLoader = ParticipantDataLoader(participantService)
        assertNotNull(participantDataLoader.getDataLoader())
    }

    @Test
    fun `load data works`() {
        val testEHRParticipant = mockk<EHRParticipant>()
        val testTenantParticipant = mockk<TenantParticipant>() {
            every { tenant.mnemonic } returns "tenantId"
            every { participant } returns testEHRParticipant
        }

        val testProxyParticipant = mockk<ProxyParticipant>()
        val testParticipants = setOf(testEHRParticipant)
        val serviceResults = mockk<Map<EHRParticipant, ProxyParticipant>> {
            every { get(testEHRParticipant) } returns testProxyParticipant
        }

        val participantService = spyk(ParticipantService(mockk()))
        every { participantService.getParticipants(testParticipants, "tenantId") } returns serviceResults
        participantDataLoader = ParticipantDataLoader(participantService)
        val dl = participantDataLoader.getDataLoader()
        val loader = dl.loadMany(listOf(testTenantParticipant))
        dl.dispatch()
        val results = loader.get()
        assertNotNull(results)
        assertEquals(listOf(testProxyParticipant), results)
    }
}
