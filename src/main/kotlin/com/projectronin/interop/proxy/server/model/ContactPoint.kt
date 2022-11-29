package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.ContactPoint as R4ContactPoint

@GraphQLDescription("Detail about an available form of contact with a patient")
data class ContactPoint(private val contactPoint: R4ContactPoint) {
    @GraphQLDescription("The system of contact (e.g. phone, email, fax, etc")
    val system: String? = contactPoint.system?.value

    @GraphQLDescription("The purpose of this contact (e.g. home, work, mobile, etc)")
    val use: String? = contactPoint.use?.value

    @GraphQLDescription("The actual contact value")
    val value: String? = contactPoint.value?.value
}
