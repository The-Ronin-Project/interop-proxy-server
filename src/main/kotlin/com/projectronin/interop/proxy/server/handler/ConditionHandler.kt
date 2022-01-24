package com.projectronin.interop.proxy.server.handler

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import com.projectronin.interop.proxy.server.model.CodeableConcept
import com.projectronin.interop.proxy.server.model.Coding
import com.projectronin.interop.proxy.server.model.Condition
import com.projectronin.interop.proxy.server.model.ConditionCategoryCode
import graphql.execution.DataFetcherResult
import org.springframework.stereotype.Component

/**
 * Handler for Condition resources.
 */
@Component
class ConditionHandler : Query {
    @GraphQLDescription("Finds active patient conditions for a given patient and category. Only conditions registered within the category will be returned.")
    fun conditionsByPatientAndCategory(
        tenantId: String,
        patientFhirId: String,
        conditionCategoryCode: ConditionCategoryCode
    ): DataFetcherResult<List<Condition>> {
        val active = CodeableConcept(
            coding = listOf(
                Coding(
                    system = "http://terminology.hl7.org/CodeSystem/condition-clinical",
                    code = "active"
                )
            )
        )
        val problemList = CodeableConcept(
            coding = listOf(
                Coding(
                    system = "http://terminology.hl7.org/CodeSystem/condition-category",
                    code = "problem-list-item",
                    display = "Problem List Item"
                )
            )
        )
        val condition1 = Condition(
            id = "f205",
            clinicalStatus = active,
            category = listOf(problemList),
            code = CodeableConcept(
                coding = listOf(
                    Coding(
                        system = "http://snomed.info/sct",
                        code = "87628006",
                        display = "Bacterial infectious disease"
                    )
                )
            )
        )
        val condition2 = Condition(
            id = "f001",
            clinicalStatus = active,
            category = listOf(problemList),
            code = CodeableConcept(
                coding = listOf(
                    Coding(
                        system = "http://snomed.info/sct",
                        code = "368009",
                        display = "Heart valve disorder"
                    )
                )
            )
        )
        return DataFetcherResult.newResult<List<Condition>>().data(listOf(condition1, condition2)).build()
    }
}
