package com.projectronin.interop.proxy.server.tenant.controller

import com.projectronin.interop.proxy.server.tenant.model.Ehr
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.tenant.config.exception.NoEHRFoundException
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
    fun read(): ResponseEntity<List<Ehr>> {
        val ehrDOList = ehrDAO.read()
        return ResponseEntity(ehrDOList.map { it.toEhr() }, HttpStatus.OK)
    }

    @PostMapping
    fun insert(@RequestBody ehr: Ehr): ResponseEntity<Ehr> {
        val insertedEhr = ehrDAO.insert(ehr.toEhrDO())
        return ResponseEntity(insertedEhr.toEhr(), HttpStatus.CREATED)
    }

    @PutMapping("/{instanceName}")
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
        return Ehr(
            vendorType = this.vendorType,
            instanceName = this.instanceName,
            clientId = this.clientId,
            publicKey = this.publicKey,
            privateKey = this.privateKey
        )
    }

    /**
     * On inserts we don't care about the id, so let it be 0.  On updates we do, so set the new [EhrDO]s
     * id to [newId]
     */
    fun Ehr.toEhrDO(newId: Int = 0): EhrDO {
        return EhrDO {
            id = newId
            vendorType = this@toEhrDO.vendorType
            instanceName = this@toEhrDO.instanceName
            clientId = this@toEhrDO.clientId
            publicKey = this@toEhrDO.publicKey
            privateKey = this@toEhrDO.privateKey
        }
    }
}
