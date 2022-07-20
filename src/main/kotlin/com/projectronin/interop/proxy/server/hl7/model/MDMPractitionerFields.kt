package com.projectronin.interop.proxy.server.hl7.model

import com.projectronin.interop.fhir.r4.datatype.HumanName as Name
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier

data class MDMPractitionerFields(
    val name: List<Name>,
    val identifier: List<R4Identifier>
)
