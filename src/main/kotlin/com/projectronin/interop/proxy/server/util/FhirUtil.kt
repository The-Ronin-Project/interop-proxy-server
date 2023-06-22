package com.projectronin.interop.proxy.server.util

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier

/**
 * Finds the FHIR ID from a list of identifiers based on the Ronin System information
 */
fun List<Identifier>.findFhirID(): String =
    this.first { it.system?.value == CodeSystem.RONIN_FHIR_ID.uri.value }.value?.value!!

/**
 * Finds the FHIR ID from a list of EHR Data Authority identifiers based on the Ronin System information
 */
fun List<com.projectronin.ehr.dataauthority.models.Identifier>.findFhirIDFromEHRDA(): String =
    this.first { it.system == CodeSystem.RONIN_FHIR_ID.uri.value }.value
