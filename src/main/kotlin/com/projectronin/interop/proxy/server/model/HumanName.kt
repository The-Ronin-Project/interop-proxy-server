package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.HumanName as R4HumanName

@GraphQLDescription("The name of a person")
data class HumanName(private val humanName: R4HumanName) {
    @GraphQLDescription("Defines the use of this name (e.g. official, nickname, maiden, etc)")
    val use: String? = humanName.use?.value

    @GraphQLDescription("Family name (often called 'Surname')")
    val family: String? = humanName.family?.value

    @GraphQLDescription("Given named (not always 'first'). Given names appear in the order they should be presented.")
    val given: List<String> = humanName.given.mapNotNull { it.value }
}
