package com.projectronin.interop.proxy.server

import com.projectronin.ehr.dataauthority.client.spring.EHRDataAuthorityClientSpringConfig
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.ehr.cerner.spring.CernerSpringConfig
import com.projectronin.interop.ehr.epic.spring.EpicSpringConfig
import com.projectronin.interop.ehr.hl7.spring.HL7SpringConfig
import com.projectronin.interop.ehr.spring.EHRSpringConfig
import com.projectronin.interop.kafka.spring.KafkaSpringConfig
import com.projectronin.interop.tenant.config.spring.TenantSpringConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import

/**
 * Main Spring Boot application for the Interop proxy server.
 */
@ComponentScan(
    basePackages = ["com.projectronin.interop.proxy", "com.projectronin.interop.queue"],
)
@Import(
    EHRDataAuthorityClientSpringConfig::class,
    TenantSpringConfig::class,
    KafkaSpringConfig::class,
    HttpSpringConfig::class,
    EHRSpringConfig::class,
    EpicSpringConfig::class,
    CernerSpringConfig::class,
    HL7SpringConfig::class,
)
@SpringBootApplication
class InteropProxyServer

fun main(args: Array<String>) {
    runApplication<InteropProxyServer>(*args)
}
