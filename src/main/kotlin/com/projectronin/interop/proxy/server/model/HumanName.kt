package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("The name of a person")
data class HumanName(
    @GraphQLDescription("Defines the use of this name (e.g. official, nickname, maiden, etc)")
    val use: String? = null,
    @GraphQLDescription("Family name (often called 'Surname')")
    val family: String? = null,
    @GraphQLDescription("Given named (not always 'first'). Given names appear in the order they should be presented.")
    val given: List<String> = listOf()
)
