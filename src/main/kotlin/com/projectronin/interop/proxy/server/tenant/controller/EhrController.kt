package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.Ehr
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
        val ehrDOList = ehrDAO.read()
        return ehrDOList.map { ehrDOtoEhr(it) }
    }

    @PostMapping
    fun insert(@RequestBody ehr: Ehr): Ehr? {
        val insertObj = EhrDO {
            vendorType = ehr.vendorType
            clientId = ehr.clientId
            publicKey = ehr.publicKey
            privateKey = ehr.privateKey
        }
        val insertedEhr = ehrDAO.insert(insertObj)
        return insertedEhr?.let { ehrDOtoEhr(it) }
    }

    @PutMapping
    fun update(@RequestBody ehr: Ehr): Ehr? {
        val updateObj = EhrDO {
            vendorType = ehr.vendorType
            clientId = ehr.clientId
            publicKey = ehr.publicKey
            privateKey = ehr.privateKey
        }
        val updatedEhr = ehrDAO.update(updateObj)
        return updatedEhr?.let { ehrDOtoEhr(it) }
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
