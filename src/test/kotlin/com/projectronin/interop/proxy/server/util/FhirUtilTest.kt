package com.projectronin.interop.proxy.server.util

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FhirUtilTest {
    @Test
    fun `finds FHIR ID from FHIR Identifiers`() {
        val identifiers =
            listOf(
                Identifier(system = Uri("some-system"), value = FHIRString("some-value")),
                Identifier(value = FHIRString("some-value")),
                Identifier(system = CodeSystem.RONIN_FHIR_ID.uri, value = FHIRString("fhirID")),
            )
        assertEquals("fhirID", identifiers.findFhirID())
    }

    @Test
    fun `throws exception when no FHIR ID found from FHIR Identifiers`() {
        val identifiers =
            listOf(
                Identifier(system = Uri("some-system"), value = FHIRString("some-value")),
                Identifier(system = Uri("some-other-system"), value = FHIRString("some-other-value")),
            )
        assertThrows<NoSuchElementException> { identifiers.findFhirID() }
    }

    @Test
    fun `throws exception when FHIR ID with null value found from FHIR Identifiers`() {
        val identifiers =
            listOf(
                Identifier(system = Uri("some-system"), value = FHIRString("some-value")),
                Identifier(system = CodeSystem.RONIN_FHIR_ID.uri, value = null),
            )
        assertThrows<NullPointerException> { identifiers.findFhirID() }
    }

    @Test
    fun `throws exception when FHIR ID with value with null value found from FHIR Identifiers`() {
        val identifiers =
            listOf(
                Identifier(system = Uri("some-system"), value = FHIRString("some-value")),
                Identifier(system = CodeSystem.RONIN_FHIR_ID.uri, value = FHIRString(null)),
            )
        assertThrows<NullPointerException> { identifiers.findFhirID() }
    }

    @Test
    fun `finds FHIR ID from EHRDA Identifiers`() {
        val identifiers =
            listOf(
                com.projectronin.ehr.dataauthority.models.Identifier(system = "some-system", value = "some-value"),
                com.projectronin.ehr.dataauthority.models.Identifier(
                    system = CodeSystem.RONIN_FHIR_ID.uri.value!!,
                    value = "fhirID",
                ),
            )
        assertEquals("fhirID", identifiers.findFhirIDFromEHRDA())
    }

    @Test
    fun `throws exception when no FHIR ID found from EHRDA Identifiers`() {
        val identifiers =
            listOf(
                com.projectronin.ehr.dataauthority.models.Identifier(system = "some-system", value = "some-value"),
                com.projectronin.ehr.dataauthority.models.Identifier(
                    system = "some-other-system",
                    value = "some-other-value",
                ),
            )
        assertThrows<NoSuchElementException> { identifiers.findFhirIDFromEHRDA() }
    }
}
