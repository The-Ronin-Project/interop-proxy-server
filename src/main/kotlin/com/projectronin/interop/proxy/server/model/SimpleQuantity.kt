package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.SimpleQuantity as EHRSimpleQuantity

@GraphQLDescription("A simple quantity")
data class SimpleQuantity(private val simpleQuantity: EHRSimpleQuantity) {
    @GraphQLDescription("Numerical value (with implicit precision)")
    val value: Double? = simpleQuantity.value

    @GraphQLDescription("Unit representation")
    val unit: String? = simpleQuantity.unit

    @GraphQLDescription("System that defines coded unit form")
    val system: String? = simpleQuantity.system

    @GraphQLDescription("Coded form of the unit")
    val code: String? = simpleQuantity.code
}
