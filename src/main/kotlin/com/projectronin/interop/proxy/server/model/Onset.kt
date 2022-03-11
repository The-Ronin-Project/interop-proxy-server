package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Condition

@GraphQLDescription("Defines the available forms of onset for a Condition")
sealed interface Onset

@GraphQLDescription("Date-time representation of the Onset")
data class DateTimeOnset(private val onset: Condition.DateTimeOnset) : Onset {
    @GraphQLDescription("The string representation of the date/time. The format is YYYY, YYYY-MM, YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+zz:zz")
    val value: String = onset.value
}

@GraphQLDescription("Age representation of the Onset")
data class AgeOnset(private val onset: Condition.AgeOnset) : Onset {
    @GraphQLDescription("Age of onset")
    val value: Age by lazy {
        Age(onset.value)
    }
}

@GraphQLDescription("Period representation of the Onset")
data class PeriodOnset(private val onset: Condition.PeriodOnset) : Onset {
    @GraphQLDescription("Period of onset")
    val value: Period by lazy {
        Period(onset.value)
    }
}

@GraphQLDescription("Range representation of the Onset")
data class RangeOnset(private val onset: Condition.RangeOnset) : Onset {
    @GraphQLDescription("Range of onset")
    val value: Range by lazy {
        Range(onset.value)
    }
}

@GraphQLDescription("String representation of the Onset")
data class StringOnset(private val onset: Condition.StringOnset) : Onset {
    @GraphQLDescription("The onset")
    val value: String = onset.value
}
