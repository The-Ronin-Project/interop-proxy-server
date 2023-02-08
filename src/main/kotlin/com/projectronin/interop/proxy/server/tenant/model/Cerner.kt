package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.annotation.JsonTypeName
import com.projectronin.interop.common.vendor.VendorType

@JsonTypeName("CERNER")
data class Cerner(
    val serviceEndpoint: String,
    val authEndpoint: String,
    val patientMRNSystem: String,
    override val instanceName: String,
    val messagePractitioner: String,
    val messageTopic: String?,
    val messageCategory: String?,
    val messagePriority: String?
) : Vendor {
    override val vendorType: VendorType = VendorType.CERNER
}
