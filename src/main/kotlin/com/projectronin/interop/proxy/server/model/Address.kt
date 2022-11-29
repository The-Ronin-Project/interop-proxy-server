package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.Address as R4Address

@GraphQLDescription("A physical address")
data class Address(private val address: R4Address) {
    @GraphQLDescription("Purpose of address - home | work | temp | old | billing")
    val use: String? = address.use?.value

    @GraphQLDescription("Street name, number, direction & P.O. Box etc.")
    val line: List<String> = address.line.mapNotNull { it.value }

    @GraphQLDescription("Name of city, town, etc.")
    val city: String? = address.city?.value

    @GraphQLDescription("Subunit of country")
    val state: String? = address.state?.value

    @GraphQLDescription("Postal code for area")
    val postalCode: String? = address.postalCode?.value
}
