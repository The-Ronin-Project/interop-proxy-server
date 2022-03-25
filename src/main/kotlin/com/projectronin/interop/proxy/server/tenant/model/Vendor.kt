package com.projectronin.interop.proxy.server.tenant.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.projectronin.interop.common.vendor.VendorType

/**
 * Represents a vendor in the proxy server REST model.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Epic::class, name = "EPIC")
)
interface Vendor {
    val vendorType: VendorType
}
