package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.ContactPoint as EHRContactPoint

@GraphQLDescription("Detail about an available form of contact with a patient")
data class ContactPoint(private val contactPoint: EHRContactPoint) {
    @GraphQLDescription("The system of contact (e.g. phone, email, fax, etc")
    val system: String? = contactPoint.system?.code

    @GraphQLDescription("The purpose of this contact (e.g. home, work, mobile, etc)")
    val use: String? = contactPoint.use?.code

    @GraphQLDescription("The actual contact value")
    val value: String? = contactPoint.value
}
