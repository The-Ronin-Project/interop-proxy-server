package com.projectronin.interop.proxy.server.model
import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A reference to another FHIR object")
data class Reference(
    @GraphQLDescription("Literal reference, Relative, internal or absolute url")
    val reference: String?,
    @GraphQLDescription("Type of object the reference refers to")
    val type: String?,
    @GraphQLDescription("Text alternative for the resource")
    val display: String?,
    @GraphQLDescription("Logical Reference")
    val identifier: Identifier?,
    @GraphQLDescription("Unique Reference")
    val id: String?
)
