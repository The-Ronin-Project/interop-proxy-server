package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime as R4DateTime

@GraphQLDescription("Defines the available forms of onset for a Condition")
sealed interface Onset

@GraphQLDescription("Date-time representation of the Onset")
data class DateTimeOnset(private val onset: R4DateTime) : Onset {
    @GraphQLDescription("The string representation of the date/time. The format is YYYY, YYYY-MM, YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+zz:zz")
    val value: String = onset.value ?: ""
}

@GraphQLDescription("Age representation of the Onset")
data class AgeOnset(private val onset: R4Age) : Onset {
    @GraphQLDescription("Age of onset")
    val value: Age by lazy {
        Age(onset)
    }
}

@GraphQLDescription("Period representation of the Onset")
data class PeriodOnset(private val onset: R4Period) : Onset {
    @GraphQLDescription("Period of onset")
    val value: Period by lazy {
        Period(onset)
    }
}

@GraphQLDescription("Range representation of the Onset")
data class RangeOnset(private val onset: R4Range) : Onset {
    @GraphQLDescription("Range of onset")
    val value: Range by lazy {
        Range(onset)
    }
}

@GraphQLDescription("String representation of the Onset")
data class StringOnset(private val onset: String) : Onset {
    @GraphQLDescription("The onset")
    val value: String = onset
}
