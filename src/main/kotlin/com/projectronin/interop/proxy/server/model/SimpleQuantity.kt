package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.SimpleQuantity as R4SimpleQuantity

@GraphQLDescription("A simple quantity")
data class SimpleQuantity(private val simpleQuantity: R4SimpleQuantity) {
    @GraphQLDescription("Numerical value (with implicit precision)")
    val value: Double? = simpleQuantity.value

    @GraphQLDescription("Unit representation")
    val unit: String? = simpleQuantity.unit

    @GraphQLDescription("System that defines coded unit form")
    val system: String? = simpleQuantity.system?.value

    @GraphQLDescription("Coded form of the unit")
    val code: String? = simpleQuantity.code?.value
}
