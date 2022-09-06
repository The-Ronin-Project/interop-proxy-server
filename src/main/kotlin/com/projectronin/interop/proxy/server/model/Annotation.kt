package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.datatype.Annotation as R4Annotation
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

@GraphQLDescription("A text note which also contains information about who made the statement and when.")
data class Annotation(
    private val annotation: R4Annotation,
    private val tenant: Tenant
) {
    @GraphQLDescription("Individual responsible for the annotation")
    val author: Author? by lazy {
        annotation.author?.let {
            when (it.type) {
                DynamicValueType.STRING -> StringAuthor(it.value as String)
                DynamicValueType.REFERENCE -> ReferenceAuthor(it.value as R4Reference, tenant)
                else -> throw RuntimeException("Unknown annotation author type encountered")
            }
        }
    }

    @GraphQLDescription("When the annotation was made")
    val time: String? = annotation.time?.value

    @GraphQLDescription("The annotation - text content (as markdown)")
    val text: String = annotation.text?.value ?: ""
}
