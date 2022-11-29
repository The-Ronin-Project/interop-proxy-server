package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime as R4DateTime

@GraphQLDescription("Defines the available forms of abatement for a Condition")
sealed interface Abatement

@GraphQLDescription("Date-time representation of the Abatement")
data class DateTimeAbatement(private val abatement: R4DateTime) : Abatement {
    @GraphQLDescription("The string representation of the date/time. The format is YYYY, YYYY-MM, YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+zz:zz")
    val value: String = abatement.value.orEmpty()
}

@GraphQLDescription("Age representation of the Abatement")
data class AgeAbatement(private val abatement: R4Age) : Abatement {
    @GraphQLDescription("Age of abatement")
    val value: Age by lazy {
        Age(abatement)
    }
}

@GraphQLDescription("Period representation of the Abatement")
data class PeriodAbatement(private val abatement: R4Period) : Abatement {
    @GraphQLDescription("Period of abatement")
    val value: Period by lazy {
        Period(abatement)
    }
}

@GraphQLDescription("Range representation of the Abatement")
data class RangeAbatement(private val abatement: R4Range) : Abatement {
    @GraphQLDescription("Range of abatement")
    val value: Range by lazy {
        Range(abatement)
    }
}

@GraphQLDescription("String representation of the Abatement")
data class StringAbatement(private val abatement: String) : Abatement {
    @GraphQLDescription("The abatement")
    val value: String = abatement
}
