package com.projectronin.interop.proxy.server.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("ehrs")
class EhrController(private val ehrDAO: EhrDAO) {

    @GetMapping
    fun read(): List<Ehr> {
        // TODO
        val ehrDOList = ehrDAO.read()
        return ehrDOList.map { ehrDOtoEhr(it) }
    }

    @PostMapping
    fun insert(@RequestBody ehr: Ehr): Ehr {
        // TODO
        return Ehr(
            VendorType.EPIC,
            "clientID",
            "public",
            "private"
        )
    }

    @PutMapping
    fun update(@RequestBody ehr: Ehr): Ehr {
        // TODO
        return Ehr(
            VendorType.EPIC,
            "UpdatedClientID",
            "public",
            "private"
        )
    }

    private fun ehrDOtoEhr(ehrDO: EhrDO): Ehr {
        return Ehr(
            vendorType = ehrDO.vendorType,
            clientId = ehrDO.clientId,
            publicKey = ehrDO.publicKey,
            privateKey = ehrDO.privateKey
        )
    }
}
