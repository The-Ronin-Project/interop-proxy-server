package com.projectronin.interop.proxy.server.tenant.model

import com.projectronin.interop.common.vendor.VendorType

/**
 * Configuration associated to an EHR Vendor.
 * @property vendorType The type of vendor for this EHR (i.e. ALLSCRIPTS, CERNER, EPIC, etc.)
 * @property instanceName The name of this instance of the vendor (i.e. Epic Prod, Epic Non-Prod, etc.)
 * @property clientId The client ID associated with this EHR vendor.
 * @property publicKey The public key used for authentication.
 * @property privateKey The private key used for authentication.
 * @property accountId The Cerner account ID
 * @property secret The cerner secret
 */
data class Ehr(
    val vendorType: VendorType,
    val instanceName: String,
    val clientId: String? = null,
    val publicKey: String? = null,
    val privateKey: String? = null,
    val accountId: String? = null,
    val secret: String? = null,
)
