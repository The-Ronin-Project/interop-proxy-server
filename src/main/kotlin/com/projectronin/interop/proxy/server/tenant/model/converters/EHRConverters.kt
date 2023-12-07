package com.projectronin.interop.proxy.server.tenant.model.converters

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.proxy.server.tenant.model.Ehr as ProxyEHR

fun EhrDO.toProxyEHR(): ProxyEHR {
    return when (vendorType) {
        VendorType.EPIC -> this.toEpicEHR()
        VendorType.CERNER -> this.toCernerEHR()
    }
}

private fun EhrDO.toEpicEHR(): ProxyEHR {
    return ProxyEHR(
        vendorType = this.vendorType,
        instanceName = this.instanceName,
        clientId = this.clientId,
        publicKey = this.publicKey,
        privateKey = this.privateKey,
    )
}

private fun EhrDO.toCernerEHR(): ProxyEHR {
    return ProxyEHR(
        vendorType = this.vendorType,
        instanceName = this.instanceName,
        clientId = this.clientId,
        accountId = this.accountId,
        secret = this.secret,
    )
}

/**
 * On inserts we don't care about the id, so let it be 0.  On updates we do, so set the new [EhrDO]s
 * id to [newId]
 */
fun ProxyEHR.toEhrDO(newId: Int = 0): EhrDO {
    return when (this.vendorType) {
        VendorType.EPIC -> this.toEpicEhrDO(newId)
        VendorType.CERNER -> this.toCernerEhrDO(newId)
    }
}

private fun ProxyEHR.toEpicEhrDO(newId: Int): EhrDO {
    if (this.publicKey == null || this.privateKey == null) {
        throw IllegalStateException("EPIC EHRs require publicKey and privateKey")
    }
    return EhrDO {
        id = newId
        vendorType = this@toEpicEhrDO.vendorType
        instanceName = this@toEpicEhrDO.instanceName
        clientId = this@toEpicEhrDO.clientId
        publicKey = this@toEpicEhrDO.publicKey
        privateKey = this@toEpicEhrDO.privateKey
    }
}

private fun ProxyEHR.toCernerEhrDO(newId: Int): EhrDO {
    if (this.accountId == null || this.secret == null) {
        throw IllegalStateException("CERNER EHRs require accountId and secret")
    }
    return EhrDO {
        id = newId
        vendorType = this@toCernerEhrDO.vendorType
        instanceName = this@toCernerEhrDO.instanceName
        clientId = this@toCernerEhrDO.clientId
        accountId = this@toCernerEhrDO.accountId
        secret = this@toCernerEhrDO.secret
    }
}
