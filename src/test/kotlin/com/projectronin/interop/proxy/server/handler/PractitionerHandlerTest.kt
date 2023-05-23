package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.ehr.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.util.JacksonUtil
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class PractitionerHandlerTest {
    private val dfe = mockk<DataFetchingEnvironment>()
    private val factory = mockk<EHRFactory>()
    private val tenantService = mockk<TenantService>()
    private val queueService = mockk<QueueService>()
    private val practService = mockk<PractitionerService>()
    private val handler = PractitionerHandler(factory, tenantService, queueService)
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "tenant"
    }
    private val practitioner = mockk<Practitioner> {
        every { id!!.value } returns "practID1"
        every { identifier } returns listOf(
            mockk {
                every { system } returns Uri("system")
                every { value?.value } returns "value"
            }
        )
        every { name } returns listOf(
            mockk {
                every { use?.value } returns "use"
                every { family?.value } returns "family"
                every { given } returns listOf("given").asFHIR()
            }
        )
    }

    @Test
    fun `getById test`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"

        every { tenantService.getTenantForMnemonic("tenant") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenant"
        every { factory.getVendorFactory(tenant).practitionerService } returns practService
        every { practService.getPractitioner(tenant, "FHIRID1") } returns practitioner
        every { queueService.enqueueMessages(any()) } just runs
        val ret = handler.getPractitionerById("tenant", "FHIRID1", dfe)
        assertEquals(0, ret.errors.size)
        assertEquals("tenant-practID1", ret.data?.id)
        assertNotNull(ret.data?.identifier)
        assertNotNull(ret.data?.name)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `getById does not care about authorized tenant`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"

        every { tenantService.getTenantForMnemonic("tenant") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null
        every { factory.getVendorFactory(tenant).practitionerService } returns practService
        every { practService.getPractitioner(tenant, "FHIRID1") } returns practitioner
        every { queueService.enqueueMessages(any()) } just runs
        val ret = handler.getPractitionerById("tenant", "FHIRID1", dfe)
        assertEquals(0, ret.errors.size)
        assertEquals("tenant-practID1", ret.data?.id)
        assertNotNull(ret.data?.identifier)
        assertNotNull(ret.data?.name)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `getByProvider test`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"

        every { tenantService.getTenantForMnemonic("tenant") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenant"
        every { factory.getVendorFactory(tenant).practitionerService } returns practService
        every { practService.getPractitionerByProvider(tenant, "ProviderID1") } returns practitioner
        every { queueService.enqueueMessages(any()) } just runs
        val ret = handler.getPractitionerByProvider("tenant", "ProviderID1", dfe)
        assertEquals(0, ret.errors.size)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `getByProvider does not care about authorized tenant`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"

        every { tenantService.getTenantForMnemonic("tenant") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null
        every { factory.getVendorFactory(tenant).practitionerService } returns practService
        every { practService.getPractitionerByProvider(tenant, "ProviderID1") } returns practitioner
        every { queueService.enqueueMessages(any()) } just runs
        val ret = handler.getPractitionerByProvider("tenant", "ProviderID1", dfe)
        assertEquals(0, ret.errors.size)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `handles query errors`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"

        every { tenantService.getTenantForMnemonic("tenant") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenant"
        every { factory.getVendorFactory(tenant).practitionerService } returns practService
        every { practService.getPractitioner(tenant, "FHIRID1") } throws Exception("something wrong")
        every { queueService.enqueueMessages(any()) } throws Exception("something else wrong")
        val ret = handler.getPractitionerById("tenant", "FHIRID1", dfe)
        assertEquals(1, ret.errors.size)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `handles queue errors`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"

        every { tenantService.getTenantForMnemonic("tenant") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenant"
        every { factory.getVendorFactory(tenant).practitionerService } returns practService
        every { practService.getPractitioner(tenant, "FHIRID1") } returns practitioner
        every { queueService.enqueueMessages(any()) } throws Exception("something else wrong")
        val ret = handler.getPractitionerById("tenant", "FHIRID1", dfe)
        assertEquals(0, ret.errors.size)
        unmockkObject(JacksonUtil)
    }
}
