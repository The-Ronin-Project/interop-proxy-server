package com.projectronin.interop.proxy.server.model

import com.fasterxml.jackson.annotation.JsonTypeName
import com.projectronin.interop.common.vendor.VendorType

/**
 * Epic [Vendor] implementation.
 */
@JsonTypeName("EPIC")
data class Epic(
    val release: String,
    val serviceEndpoint: String,
    val ehrUserId: String,
    val messageType: String,
    val practitionerProviderSystem: String,
    val practitionerUserSystem: String,
    val hsi: String?,
) : Vendor {
    override val vendorType: VendorType = VendorType.EPIC
}
