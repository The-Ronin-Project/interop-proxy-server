package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ConditionTest {
    @Test
    fun `Ensure condition can be created`() {
        val identifier = Identifier("condition", "1234")
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
        val code = CodeableConcept(
            coding = listOf(
                Coding(
                    system = "http://snomed.info/sct",
                    code = "87628006",
                    display = "Bacterial infectious disease"
                )
            )
        )
        val condition = Condition(
            id = "f205",
            identifier = listOf(identifier),
            clinicalStatus = active,
            category = listOf(problemList),
            code = code
        )
        assertEquals("f205", condition.id)
        assertEquals(listOf(identifier), condition.identifier)
        assertEquals(active, condition.clinicalStatus)
        assertEquals(listOf(problemList), condition.category)
        assertEquals(code, condition.code)
    }

    @Test
    fun `condition can be created with default values`() {
        val code = CodeableConcept(
            coding = listOf(
                Coding(
                    system = "http://snomed.info/sct",
                    code = "87628006",
                    display = "Bacterial infectious disease"
                )
            )
        )
        val condition = Condition(
            id = "f205",
            code = code
        )
        assertEquals("f205", condition.id)
        assertEquals(listOf<Identifier>(), condition.identifier)
        assertNull(condition.clinicalStatus)
        assertEquals(listOf<CodeableConcept>(), condition.category)
        assertEquals(code, condition.code)
    }
}
