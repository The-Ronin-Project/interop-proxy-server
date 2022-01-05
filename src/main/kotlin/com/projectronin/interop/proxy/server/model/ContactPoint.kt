package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Detail about an available form of contact with a patient")
data class ContactPoint(
    @GraphQLDescription("The system of contact (e.g. phone, email, fax, etc")
    val system: String? = null,
    @GraphQLDescription("The purpose of this contact (e.g. home, work, mobile, etc)")
    val use: String? = null,
    @GraphQLDescription("The actual contact value")
    val value: String? = null
)
