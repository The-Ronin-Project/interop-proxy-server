package com.projectronin.interop.proxy.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Main Spring Boot application for the Interop proxy server.
 */
@ComponentScan("com.projectronin.interop")
@SpringBootApplication
class InteropProxyServer

fun main(args: Array<String>) {
    runApplication<InteropProxyServer>(*args)
}
