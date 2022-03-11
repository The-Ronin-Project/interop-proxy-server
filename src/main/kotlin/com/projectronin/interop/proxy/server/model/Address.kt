package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Address as EHRAddress

@GraphQLDescription("A physical address")
data class Address(private val address: EHRAddress) {
    @GraphQLDescription("Purpose of address - home | work | temp | old | billing")
    val use: String? = address.use?.code

    @GraphQLDescription("Street name, number, direction & P.O. Box etc.")
    val line: List<String> = address.line

    @GraphQLDescription("Name of city, town, etc.")
    val city: String? = address.city

    @GraphQLDescription("Subunit of country")
    val state: String? = address.state

    @GraphQLDescription("Postal code for area")
    val postalCode: String? = address.postalCode
}
