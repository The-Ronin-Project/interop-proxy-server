package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Condition

@GraphQLDescription("Defines the available forms of abatement for a Condition")
sealed interface Abatement

@GraphQLDescription("Date-time representation of the Abatement")
data class DateTimeAbatement(private val abatement: Condition.DateTimeAbatement) : Abatement {
    @GraphQLDescription("The string representation of the date/time. The format is YYYY, YYYY-MM, YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+zz:zz")
    val value: String = abatement.value
}

@GraphQLDescription("Age representation of the Abatement")
data class AgeAbatement(private val abatement: Condition.AgeAbatement) : Abatement {
    @GraphQLDescription("Age of abatement")
    val value: Age by lazy {
        Age(abatement.value)
    }
}

@GraphQLDescription("Period representation of the Abatement")
data class PeriodAbatement(private val abatement: Condition.PeriodAbatement) : Abatement {
    @GraphQLDescription("Period of abatement")
    val value: Period by lazy {
        Period(abatement.value)
    }
}

@GraphQLDescription("Range representation of the Abatement")
data class RangeAbatement(private val abatement: Condition.RangeAbatement) : Abatement {
    @GraphQLDescription("Range of abatement")
    val value: Range by lazy {
        Range(abatement.value)
    }
}

@GraphQLDescription("String representation of the Abatement")
data class StringAbatement(private val abatement: Condition.StringAbatement) : Abatement {
    @GraphQLDescription("The abatement")
    val value: String = abatement.value
}
