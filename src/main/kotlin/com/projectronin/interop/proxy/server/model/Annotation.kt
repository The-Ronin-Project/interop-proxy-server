package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.ehr.model.Annotation as EHRAnnotation

@GraphQLDescription("A text note which also contains information about who made the statement and when.")
data class Annotation(
    private val annotation: EHRAnnotation,
    private val tenant: Tenant
) {
    @GraphQLDescription("Individual responsible for the annotation")
    val author: Author? by lazy {
        annotation.author?.let {
            when (it) {
                is EHRAnnotation.StringAuthor -> StringAuthor(it)
                is EHRAnnotation.ReferenceAuthor -> ReferenceAuthor(it, tenant)
                else -> throw RuntimeException("Unknown annotation author type encountered")
            }
        }
    }

    @GraphQLDescription("When the annotation was made")
    val time: String? = annotation.time

    @GraphQLDescription("The annotation - text content (as markdown)")
    val text: String = annotation.text
}
