package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.HumanName as EHRHumanName

@GraphQLDescription("The name of a person")
data class HumanName(private val humanName: EHRHumanName) {
    @GraphQLDescription("Defines the use of this name (e.g. official, nickname, maiden, etc)")
    val use: String? = humanName.use?.code

    @GraphQLDescription("Family name (often called 'Surname')")
    val family: String? = humanName.family

    @GraphQLDescription("Given named (not always 'first'). Given names appear in the order they should be presented.")
    val given: List<String> = humanName.given
}
