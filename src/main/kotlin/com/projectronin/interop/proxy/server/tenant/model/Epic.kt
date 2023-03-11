package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.annotation.JsonTypeName
import com.projectronin.interop.common.vendor.VendorType

/**
 * Epic [Vendor] implementation.
 */
@JsonTypeName("EPIC")
data class Epic(
    val release: String,
    val serviceEndpoint: String,
    val authEndpoint: String,
    val ehrUserId: String,
    val messageType: String,
    val practitionerProviderSystem: String,
    val practitionerUserSystem: String,
    val patientMRNSystem: String,
    val patientInternalSystem: String,
    val encounterCSNSystem: String,
    val patientMRNTypeText: String,
    val hsi: String?,
    override val instanceName: String,
    val departmentInternalSystem: String,
    val patientOnboardedFlagId: String?
) : Vendor {
    override val vendorType: VendorType = VendorType.EPIC
}
