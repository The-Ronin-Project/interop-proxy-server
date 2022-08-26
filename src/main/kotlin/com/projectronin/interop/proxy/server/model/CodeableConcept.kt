package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept as R4CodeableConcept

@GraphQLDescription("A CodeableConcept represents a value that is usually supplied by providing a reference to one or more terminologies or ontologies but may also be defined by the provision of text.")
data class CodeableConcept(private val codeableConcept: R4CodeableConcept) {
    @GraphQLDescription("Code defined by a terminology system")
    val coding: List<Coding> by lazy {
        codeableConcept.coding.map(::Coding)
    }

    @GraphQLDescription("Plain text representation of the concept")
    val text: String? = codeableConcept.text
}
