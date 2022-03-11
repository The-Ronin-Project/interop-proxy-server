package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Range as EHRRange

@GraphQLDescription("Set of values bounded by low and high")
data class Range(private val period: EHRRange) {
    @GraphQLDescription("Low limit")
    val low: SimpleQuantity? by lazy {
        period.low?.let { SimpleQuantity(it) }
    }

    @GraphQLDescription("High limit")
    val high: SimpleQuantity? by lazy {
        period.high?.let { SimpleQuantity(it) }
    }
}
