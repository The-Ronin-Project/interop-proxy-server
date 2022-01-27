package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.ehr.model.Condition
import com.projectronin.interop.proxy.server.context.INTEROP_CONTEXT_KEY
import com.projectronin.interop.proxy.server.context.InteropGraphQLContext
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.Message
import com.projectronin.interop.queue.model.MessageType
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import graphql.schema.DataFetchingEnvironment
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConditionHandlerTest {
    private lateinit var tenant: Tenant
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var conditionHandler: ConditionHandler
    private lateinit var dfe: DataFetchingEnvironment

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
        val exception = assertThrows<IllegalArgumentException> {
            conditionHandler.conditionsByPatientAndCategory(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
                dfe = dfe
            )
        }
        assertEquals("Invalid Tenant: tenantId", exception.message)
    }

    @Test
    fun `unauthorized user returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant
        every { dfe.graphQlContext.get<InteropGraphQLContext>(INTEROP_CONTEXT_KEY).authzTenantId } returns null

        // Run Test
        val exception = assertThrows<IllegalArgumentException> {
            conditionHandler.conditionsByPatientAndCategory(
                tenantId = "tenantId",
                patientFhirId = "123456789",
                conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM,
                dfe = dfe
            )
        }

        assertEquals("No Tenants authorized for request.", exception.message)
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
    }

    @Test
    fun `ensure full condition is correctly returned`() {
        // Mock response
        val response = mockk<Bundle<Condition>> {
            every { resources } returns listOf(
                mockk {
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
                }
            )
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
                    Message(
                        id = null,
                        messageType = MessageType.API,
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

        // Condition
        assertEquals(1, actualResponse.data.size)
        val actualCondition = actualResponse.data[0]
        assertEquals("12345".localize(tenant), actualCondition.id)

        // Identifier
        assertEquals(1, actualCondition.identifier.size)
        val actualIdentifier = actualCondition.identifier[0]
        assertEquals("test-system", actualIdentifier.system)
        assertEquals("test-value", actualIdentifier.value)

        // Clinical status
        val actualClinicalStatus = actualCondition.clinicalStatus!!
        assertEquals(1, actualClinicalStatus.coding.size)
        val actualClinicalStatusCoding = actualClinicalStatus.coding[0]
        assertEquals("test-system", actualClinicalStatusCoding.system)
        assertEquals("test-version", actualClinicalStatusCoding.version)
        assertEquals("test-code", actualClinicalStatusCoding.code)
        assertEquals("test-display", actualClinicalStatusCoding.display)
        assertEquals(true, actualClinicalStatusCoding.userSelected)
        assertEquals("clinical status text", actualClinicalStatus.text)

        // Category
        val actualCategory = actualCondition.category
        assertEquals(1, actualCategory.size)
        val actualCategoryCodeableConcept = actualCategory[0]
        assertEquals(1, actualCategoryCodeableConcept.coding.size)
        val actualCategoryCoding = actualCategoryCodeableConcept.coding[0]
        assertEquals("test-system", actualCategoryCoding.system)
        assertEquals("test-version", actualCategoryCoding.version)
        assertEquals("test-code", actualCategoryCoding.code)
        assertEquals("test-display", actualCategoryCoding.display)
        assertEquals(true, actualCategoryCoding.userSelected)
        assertEquals("category text", actualCategoryCodeableConcept.text)

        // Code
        val actualCode = actualCondition.code
        assertEquals(1, actualCode.coding.size)
        val actualCodeCoding = actualCode.coding[0]
        assertEquals("test-system", actualCodeCoding.system)
        assertEquals("test-version", actualCodeCoding.version)
        assertEquals("test-code", actualCodeCoding.code)
        assertEquals("test-display", actualCodeCoding.display)
        assertEquals(true, actualCodeCoding.userSelected)
        assertEquals("code text", actualCode.text)
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        // Mock response
        val response = mockk<Bundle<Condition>> {
            every { resources } returns listOf(
                mockk {
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
                }
            )
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
                    Message(
                        id = null,
                        messageType = MessageType.API,
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

        // Condition
        assertEquals(1, actualResponse.data.size)
        val actualCondition = actualResponse.data[0]
        assertEquals("12345".localize(tenant), actualCondition.id)

        // Identifier
        assertEquals(1, actualCondition.identifier.size)
        val actualIdentifier = actualCondition.identifier[0]
        assertEquals("test-system", actualIdentifier.system)
        assertEquals("test-value", actualIdentifier.value)

        // Clinical status
        val actualClinicalStatus = actualCondition.clinicalStatus!!
        assertEquals(1, actualClinicalStatus.coding.size)
        val actualClinicalStatusCoding = actualClinicalStatus.coding[0]
        assertEquals("test-system", actualClinicalStatusCoding.system)
        assertEquals("test-version", actualClinicalStatusCoding.version)
        assertEquals("test-code", actualClinicalStatusCoding.code)
        assertEquals("test-display", actualClinicalStatusCoding.display)
        assertEquals(true, actualClinicalStatusCoding.userSelected)
        assertEquals("clinical status text", actualClinicalStatus.text)

        // Category
        val actualCategory = actualCondition.category
        assertEquals(1, actualCategory.size)
        val actualCategoryCodeableConcept = actualCategory[0]
        assertEquals(1, actualCategoryCodeableConcept.coding.size)
        val actualCategoryCoding = actualCategoryCodeableConcept.coding[0]
        assertEquals("test-system", actualCategoryCoding.system)
        assertEquals("test-version", actualCategoryCoding.version)
        assertEquals("test-code", actualCategoryCoding.code)
        assertEquals("test-display", actualCategoryCoding.display)
        assertEquals(true, actualCategoryCoding.userSelected)
        assertEquals("category text", actualCategoryCodeableConcept.text)

        // Code
        val actualCode = actualCondition.code
        assertEquals(1, actualCode.coding.size)
        val actualCodeCoding = actualCode.coding[0]
        assertEquals("test-system", actualCodeCoding.system)
        assertEquals("test-version", actualCodeCoding.version)
        assertEquals("test-code", actualCodeCoding.code)
        assertEquals("test-display", actualCodeCoding.display)
        assertEquals(true, actualCodeCoding.userSelected)
        assertEquals("code text", actualCode.text)
    }

    @Test
    fun `ensure condition with null values is correctly returned`() {
        // Mock response
        val response = mockk<Bundle<Condition>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "12345"
                    every { identifier } returns listOf()
                    every { clinicalStatus } returns null
                    every { category } returns listOf()
                    every { code } returns null
                    every { raw } returns "raw JSON for condition"
                }
            )
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
                    Message(
                        id = null,
                        messageType = MessageType.API,
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

        assertEquals(1, actualResponse.data.size)
        val actualCondition = actualResponse.data[0]
        assertEquals("12345".localize(tenant), actualCondition.id)
        assertEquals(0, actualCondition.identifier.size)
        assertNull(actualCondition.clinicalStatus)
        assertEquals(0, actualCondition.category.size)
        assertEquals(0, actualCondition.code.coding.size)
        assertEquals("", actualCondition.code.text)
    }

    @Test
    fun `ensure when ehr returns no conditions none are returned`() {
        // Mock response
        val response = mockk<Bundle<Condition>> {
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
                    Message(
                        id = null,
                        messageType = MessageType.API,
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
