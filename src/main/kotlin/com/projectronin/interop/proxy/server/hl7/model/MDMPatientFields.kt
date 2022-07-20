package com.projectronin.interop.proxy.server.hl7.model

import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.datatype.Address as R4Address
import com.projectronin.interop.fhir.r4.datatype.ContactPoint as Phone
import com.projectronin.interop.fhir.r4.datatype.HumanName as Name
import com.projectronin.interop.fhir.r4.datatype.Identifier as R4Identifier

data class MDMPatientFields(
    val identifier: List<R4Identifier>,
    val name: List<Name>,
    val dob: Date?,
    val gender: AdministrativeGender?,
    val address: List<R4Address> = listOf(),
    val phone: List<Phone> = listOf()
)
