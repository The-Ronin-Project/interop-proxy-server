package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

@GraphQLDescription("Defines the available forms of author for an Annotation")
sealed interface Author

@GraphQLDescription("String representation of the Author")
data class StringAuthor(private val author: String) : Author {
    @GraphQLDescription("The author")
    val value: String = author
}

@GraphQLDescription("Reference representation of the Author")
data class ReferenceAuthor(
    private val author: R4Reference,
    private val tenant: Tenant
) : Author {
    @GraphQLDescription("Reference to the author")
    val value: Reference by lazy {
        Reference.from(author, tenant)
    }
}
