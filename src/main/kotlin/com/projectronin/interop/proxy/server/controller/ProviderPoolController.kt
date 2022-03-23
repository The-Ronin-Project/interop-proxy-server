package com.projectronin.interop.proxy.server.controller

import com.projectronin.interop.proxy.server.model.ProviderPool
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tenants/{tenantId}/pools")
class ProviderPoolController(private val providerPoolDAO: ProviderPoolDAO) {

    @GetMapping
    fun get(@PathVariable tenantId: Int, @RequestParam(required = false) providerIds: List<String>): List<ProviderPool> {
        // TODO
        /*
        ProviderPoolDAO accepts a tenant Id as the path variable and a list of providerIds , will return a map of provider ids -> pool ids.

        val providerpoollist = providerPoolDAO.getPoolsForProviders(tenantId, providerIds)

        providerIds are an optional parameter, so if no providerIds are supplied, call providerPoolDAO.getAll(tenantId)
        and return all values for a tenantId

         */
        val a = ProviderPool(
            1234,
            "test",
            "test2"
        )
        val b = ProviderPool(
            5678,
            "test3",
            "test4"
        )
        return listOf(a, b)
    }

    @PostMapping
    fun insert(@PathVariable tenantId: Int, @RequestBody providerPool: ProviderPool): String {
        // TODO
        /*
        ProviderPoolDAO accepts ProviderPoolDO as input, returns ProviderPoolDO for insert
        val tenantDO = TenantDO {
            id = tenantId
        }
        val insertProviderPoolDO = ProviderPoolDO {
            id = 0
            tenantId = tenantDO
            providerId = providerPool.providerId
            poolId = providerPool.PoolId
        }
        val providerpoolreturn = providerPoolDAO.insert(insertProviderPoolDO)

        maybe convert returned value into string to display in return string, or change the return of this
        function to ProviderPoolDO to return values directly
        */
        return "success, id: ?"
    }

    @PutMapping("/{providerPoolId}")
    fun update(@PathVariable tenantId: Int, @PathVariable providerPoolId: Int, @RequestBody providerPool: ProviderPool): String {
        // TODO
        /*
        ProviderPoolDAO accepts ProviderPoolDO as input, returns Int of row updated
        val tenantDO = TenantDO {
            id = tenantId
        }
        val updateProviderPoolDO = ProviderPoolDO {
            id = providerPoolId
            tenantId = tenantDO
            providerId = providerPool.providerId
            poolId = providerPool.PoolId
        }
        val updated = providerPoolDAO.insert(updateProviderPoolDO)
        can try and retrieve updated row to confirm or return in response
         */
        return "success, row ? updated"
    }

    @DeleteMapping("/{providerPoolId}")
    fun delete(@PathVariable tenantId: Int, @PathVariable providerPoolId: Long): String {
        // TODO
        /*
        val deleted = providerPoolDAO.delete(providerPoolId)
        return success message if successful
        */
        return "success, row ? deleted"
    }
}
