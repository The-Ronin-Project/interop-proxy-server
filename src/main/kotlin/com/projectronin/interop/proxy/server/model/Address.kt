package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A physical address")
data class Address(
    @GraphQLDescription("Purpose of address - home | work | temp | old | billing")
    val use: String?,
    @GraphQLDescription("Street name, number, direction & P.O. Box etc.")
    val line: List<String> = listOf(),
    @GraphQLDescription("Name of city, town, etc.")
    val city: String?,
    @GraphQLDescription("Subunit of country")
    val state: String?,
    @GraphQLDescription("Postal code for area")
    val postalCode: String?
)
