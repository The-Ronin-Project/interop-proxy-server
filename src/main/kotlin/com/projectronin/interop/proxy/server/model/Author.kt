package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.ehr.model.Annotation
import com.projectronin.interop.tenant.config.model.Tenant

@GraphQLDescription("Defines the available forms of author for an Annotation")
sealed interface Author

@GraphQLDescription("String representation of the Author")
data class StringAuthor(private val author: Annotation.StringAuthor) : Author {
    @GraphQLDescription("The author")
    val value: String = author.value
}

@GraphQLDescription("Reference representation of the Author")
data class ReferenceAuthor(
    private val author: Annotation.ReferenceAuthor,
    private val tenant: Tenant
) : Author {
    @GraphQLDescription("Reference to the author")
    val value: Reference by lazy {
        Reference.from(author.value, tenant)
    }
}
