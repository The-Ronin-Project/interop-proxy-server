package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Age as EHRAge

@GraphQLDescription("An age quantity")
data class Age(private val age: EHRAge) {
    @GraphQLDescription("Numerical value (with implicit precision)")
    val value: Double? = age.value

    @GraphQLDescription("< | <= | >= | > - how to understand the value")
    val comparator: String? = age.comparator?.code

    @GraphQLDescription("Unit representation")
    val unit: String? = age.unit

    @GraphQLDescription("System that defines coded unit form")
    val system: String? = age.system

    @GraphQLDescription("Coded form of the unit")
    val code: String? = age.code
}
