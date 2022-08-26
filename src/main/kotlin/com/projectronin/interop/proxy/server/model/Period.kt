package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
@GraphQLDescription("Time range defined by start and end date/time")
data class Period(private val period: R4Period) {
    @GraphQLDescription("Starting time with inclusive boundary")
    val start: String? = period.start?.value

    @GraphQLDescription("End time with inclusive boundary, if not ongoing")
    val end: String? = period.end?.value
}
