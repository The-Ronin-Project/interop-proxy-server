package com.projectronin.interop.proxy.server

import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.validation.ValidationClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * Main Spring Boot application for the Interop proxy server.
 */
@ComponentScan(
    basePackages = ["com.projectronin.interop"],
    // Exclude Transform and Validation logic
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            value = [TransformManager::class, ValidationClient::class]
        )
    ]
)
@SpringBootApplication
class InteropProxyServer

fun main(args: Array<String>) {
    runApplication<InteropProxyServer>(*args)
}
