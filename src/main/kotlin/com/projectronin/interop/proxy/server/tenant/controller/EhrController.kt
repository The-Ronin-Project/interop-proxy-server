package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.tenant.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
import datadog.trace.api.Trace
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriUtils

@RestController
@RequestMapping("ehrs")
class EhrController(private val ehrDAO: EhrDAO) {
    private val logger = KotlinLogging.logger { }

    @GetMapping
    @Trace
    fun read(): ResponseEntity<List<Ehr>> {
        val ehrDOList = ehrDAO.read()
        return ResponseEntity(ehrDOList.map { it.toEhr() }, HttpStatus.OK)
    }

    @PostMapping
    @Trace
    fun insert(@RequestBody ehr: Ehr): ResponseEntity<Ehr> {
        val insertedEhr = ehrDAO.insert(ehr.toEhrDO())
        return ResponseEntity(insertedEhr.toEhr(), HttpStatus.CREATED)
    }

    @PutMapping("/{instanceName}")
    @Trace
    fun update(@PathVariable("instanceName") instanceName: String, @RequestBody ehr: Ehr): ResponseEntity<Ehr> {
        val decodedInstanceName = UriUtils.decode(instanceName, Charsets.UTF_8)
        val existingEhr = ehrDAO.getByInstance(decodedInstanceName)
            ?: throw NoEHRFoundException("EHR $decodedInstanceName not found")

        val updatedEhr = ehrDAO.update(ehr.toEhrDO(existingEhr.id))
        return ResponseEntity(updatedEhr.toEhr(), HttpStatus.OK)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.warn(e) { "Unspecified error occurred during EhrController" }
        return ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    // likely the callers fault getting a wrong vendorType on a put
    @ExceptionHandler(value = [(NoEHRFoundException::class)])
    fun handleEHRException(e: NoEHRFoundException): ResponseEntity<String> {
        logger.warn(e) { "Unable to find EHR" }
        return ResponseEntity("Unable to find EHR", HttpStatus.NOT_FOUND)
    }

    fun EhrDO.toEhr(): Ehr {
        return when (vendorType) {
            VendorType.EPIC -> this.toEpicEHR()
            VendorType.CERNER -> this.toCernerEHR()
        }
    }

    fun EhrDO.toEpicEHR(): Ehr {
        return Ehr(
            vendorType = this.vendorType,
            instanceName = this.instanceName,
            clientId = this.clientId,
            publicKey = this.publicKey,
            privateKey = this.privateKey,
        )
    }

    fun EhrDO.toCernerEHR(): Ehr {
        return Ehr(
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
    fun Ehr.toEhrDO(newId: Int = 0): EhrDO {
        return when (this.vendorType) {
            VendorType.EPIC -> this.toEpicEhrDO(newId)
            VendorType.CERNER -> this.toCernerEhrDO(newId)
        }
    }
    fun Ehr.toEpicEhrDO(newId: Int): EhrDO {
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
    fun Ehr.toCernerEhrDO(newId: Int): EhrDO {
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
}
