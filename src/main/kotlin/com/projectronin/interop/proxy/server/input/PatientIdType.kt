package com.projectronin.interop.proxy.server.input

enum class PatientIdType {
    FHIR,

    @Deprecated("Deprecated ID Type", ReplaceWith(""), DeprecationLevel.WARNING)
    MRN
}
