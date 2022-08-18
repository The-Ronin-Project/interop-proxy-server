package com.projectronin.interop.proxy.server.handler

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.projectronin.interop.common.http.exceptions.ServiceUnavailableException
import com.projectronin.interop.common.logmarkers.LogMarkers
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.Condition
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import com.projectronin.interop.ehr.model.Condition as EHRCondition

@TestInstance(Lifecycle.PER_CLASS)
class ConditionHandlerTest {
    private lateinit var tenant: Tenant
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var conditionHandler: ConditionHandler
    private lateinit var dfe: DataFetchingEnvironment

    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    private val logAppender = ListAppender<ILoggingEvent>()

    @BeforeAll
    fun initAllTests() {
        logger.addAppender(logAppender)
        logAppender.start()
    }

    @BeforeEach
    fun initTest() {
        tenant = mockk()
        ehrFactory = mockk()
        tenantService = mockk()
        queueService = mockk()
        dfe = mockk()
        conditionHandler = ConditionHandler(ehrFactory, tenantService, queueService)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns null
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            conditionHandler.conditionsByPatientAndCategory(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
                dfe = dfe
            )
        }
        assertEquals("404 Invalid Tenant: tenantId", exception.message)
    }

    @Test
    fun `unauthorized user returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null

        // Run Test
        val exception = assertThrows<HttpClientErrorException> {
            conditionHandler.conditionsByPatientAndCategory(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
                dfe = dfe
            )
        }

        assertEquals("403 No Tenants authorized for request.", exception.message)
    }

    @Test
    fun `ensure findConditions exception is returned as error`() {
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { conditionService } returns mockk {
                every {
                    findConditions(
                        tenant = tenant,
                        patientFhirId = "123456789",
                        conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM.code,
                        clinicalStatus = "active"
                    )
                } throws (IllegalStateException("Error"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = conditionHandler.conditionsByPatientAndCategory(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
        assertNull(logAppender.list.last().marker)
    }

    @Test
    fun `ensure findConditions service unavailable exception sets log marker`() {
        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { conditionService } returns mockk {
                every {
                    findConditions(
                        tenant = tenant,
                        patientFhirId = "123456789",
                        conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM.code,
                        clinicalStatus = "active"
                    )
                } throws (ServiceUnavailableException(HttpStatusCode.ServiceUnavailable, "Proxy"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = conditionHandler.conditionsByPatientAndCategory(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
            dfe = dfe
        )

        assertNotNull(result)
        assertEquals("Received 503 Service Unavailable when calling Proxy", result.errors[0].message)
        assertEquals(logAppender.list.last().marker, LogMarkers.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `ensure condition is correctly returned`() {
        // Mock response
        val condition1 = mockk<EHRCondition> {
            every { id } returns "12345"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "test-system"
                    every { value } returns "test-value"
                }
            )
            every { clinicalStatus } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system } returns "test-system"
                        every { version } returns "test-version"
                        every { code } returns "test-code"
                        every { display } returns "test-display"
                        every { userSelected } returns true
                    }
                )
                every { text } returns "clinical status text"
            }
            every { category } returns listOf(
                mockk {
                    every { coding } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { version } returns "test-version"
                            every { code } returns "test-code"
                            every { display } returns "test-display"
                            every { userSelected } returns true
                        }
                    )
                    every { text } returns "category text"
                }
            )
            every { code } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system } returns "test-system"
                        every { version } returns "test-version"
                        every { code } returns "test-code"
                        every { display } returns "test-display"
                        every { userSelected } returns true
                    }
                )
                every { text } returns "code text"
            }
            every { raw } returns "raw JSON for condition"
            every { recordedDate } returns "2021-03-08"
        }
        val response = mockk<Bundle<EHRCondition>> {
            every { resources } returns listOf(condition1)
        }

        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { conditionService } returns mockk {
                every {
                    findConditions(
                        tenant = tenant,
                        patientFhirId = "123456789",
                        conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM.code,
                        clinicalStatus = "active"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.CONDITION,
                        tenant = "tenantId",
                        text = "raw JSON for condition"
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = conditionHandler.conditionsByPatientAndCategory(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)

        val conditions = actualResponse.data
        assertEquals(1, conditions.size)
        assertEquals(Condition(condition1, tenant), conditions[0])
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        // Mock response
        val condition1 = mockk<EHRCondition> {
            every { id } returns "12345"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns "test-system"
                    every { value } returns "test-value"
                }
            )
            every { clinicalStatus } returns mockk {
                every { coding } returns listOf(
                    mockk {
                        every { system } returns "test-system"
                        every { version } returns "test-version"
                        every { code } returns "test-code"
                        every { display } returns "test-display"
                        every { userSelected } returns true
                    }
                )
                every { text } returns "clinical status text"
            }
            every { category } returns listOf(
                mockk {
                    every { coding } returns listOf(
                        mockk {
                            every { system } returns "test-system"
                            every { version } returns "test-version"
                            every { code } returns "test-code"
                            every { display } returns "test-display"
                            every { userSelected } returns true
                        }
                    )
                    every { text } returns "category text"
                }
            )
            every { code } returns null
            every { raw } returns "raw JSON for condition"
            every { recordedDate } returns "2021-03-08"
        }
        val response = mockk<Bundle<EHRCondition>> {
            every { resources } returns listOf(condition1)
        }

        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { conditionService } returns mockk {
                every {
                    findConditions(
                        tenant = tenant,
                        patientFhirId = "123456789",
                        conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM.code,
                        clinicalStatus = "active"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.CONDITION,
                        tenant = "tenantId",
                        text = "raw JSON for condition"
                    )
                )
            )
        } throws (Exception("exception"))

        // Run Test
        val actualResponse = conditionHandler.conditionsByPatientAndCategory(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)

        val conditions = actualResponse.data
        assertEquals(1, conditions.size)
        assertEquals(Condition(condition1, tenant), conditions[0])
    }

    @Test
    fun `ensure when ehr returns no conditions none are returned`() {
        // Mock response
        val response = mockk<Bundle<EHRCondition>> {
            every { resources } returns listOf()
        }

        every { tenant.mnemonic } returns "tenantId"
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns "tenantId"

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { conditionService } returns mockk {
                every {
                    findConditions(
                        tenant = tenant,
                        patientFhirId = "123456789",
                        conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM.code,
                        clinicalStatus = "active"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    ApiMessage(
                        id = null,
                        resourceType = ResourceType.CONDITION,
                        tenant = "tenantId",
                        text = "raw JSON for condition"
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = conditionHandler.conditionsByPatientAndCategory(
            tenantId = "tenantId",
            patientFhirId = "123456789",
            conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
            dfe = dfe
        )

        // Check results
        assertNotNull(actualResponse)
        assertEquals(0, actualResponse.data.size)
    }
}
