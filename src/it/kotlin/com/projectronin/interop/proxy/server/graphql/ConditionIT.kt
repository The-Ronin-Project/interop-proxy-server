package com.projectronin.interop.proxy.server.graphql

import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.client.MockEHRClient
import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ConditionIT : BaseGraphQLIT() {

    @AfterEach
    fun `delete all`() {
        MockEHRClient.deleteAllResources("Patient")
        MockEHRClient.deleteAllResources("Condition")
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles condition query`(testTenant: String) {
        val patient = patient {}
        val patientId = MockEHRClient.addResource(patient)
        val condition = condition {
            subject of reference("Patient", patientId)
            clinicalStatus of codeableConcept {
                coding of listOf(
                    Coding(
                        system = CodeSystem.CONDITION_CLINICAL.uri,
                        code = Code("active")
                    )
                )
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        Coding(
                            system = CodeSystem.CONDITION_CATEGORY.uri,
                            code = Code(ConditionCategoryCode.PROBLEM_LIST_ITEM.code),
                            display = "Problem List Item".asFHIR()
                        ),
                        Coding(
                            system = CodeSystem.SNOMED_CT.uri,
                            code = Code("439401001"),
                            display = "Diagnosis".asFHIR()
                        )
                    )
                }
            )
            code of codeableConcept {
                coding of listOf(
                    Coding(
                        system = CodeSystem.SNOMED_CT.uri,
                        code = Code("39065001"),
                        display = "Burn of ear".asFHIR()
                    )
                )
                text of "Burnt Ear"
            }
        }
        val conditionId = MockEHRClient.addResource(condition)
        val query = this::class.java.getResource("/graphql/conditionsByPatient.graphql")!!
            .readText()
            .replace("__patFhir__", patientId)
            .replace("__tenant_mnemonic__", testTenant)
        val expectedJSON = this::class.java.getResource("/testConditionGraphQLResults.json")!!
            .readText()
            .replace("__return__id__", "$testTenant-$conditionId")

        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = objectMapper.readTree(body)
        val expectedJSONObject = objectMapper.readTree(expectedJSON)

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONNode.toString())
    }

    @Test
    fun `server handles missing field`() {
        val testTenant = "epic"
        val patientFhirId = MockEHRClient.addResource(patient {})

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$testTenant", 
                patientFhirId:"$patientFhirId", 
               {id}
            }
        """.trimIndent()

        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONNode.has("errors"))
    }

    @Test
    fun `server handles no conditions found`() {
        val testTenant = "epic"
        val patientFhirId = MockEHRClient.addResource(patient {}) // Test patient with no conditions
        val conditionCategoryCode = ConditionCategoryCode.PROBLEM_LIST_ITEM

        val query = """
            query {
               conditionsByPatientAndCategory(
                tenantId:"$testTenant", 
                patientFhirId:"$patientFhirId", 
                conditionCategoryCode:$conditionCategoryCode)
               {id}
            }
        """.trimIndent()

        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = objectMapper.readTree(body)
        val conditionSearchJSONArray = resultJSONNode["data"]["conditionsByPatientAndCategory"]
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(0, conditionSearchJSONArray.size())
    }
}
