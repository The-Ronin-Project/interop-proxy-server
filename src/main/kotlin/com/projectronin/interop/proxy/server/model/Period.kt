package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Period as EHRPeriod

@GraphQLDescription("Time range defined by start and end date/time")
data class Period(private val period: EHRPeriod) {
    @GraphQLDescription("Starting time with inclusive boundary")
    val start: String? = period.start

    @GraphQLDescription("End time with inclusive boundary, if not ongoing")
    val end: String? = period.end
}
