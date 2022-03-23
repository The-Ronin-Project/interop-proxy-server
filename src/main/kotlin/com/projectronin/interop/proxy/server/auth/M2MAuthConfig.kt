package com.projectronin.interop.proxy.server.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@ConfigurationProperties(prefix = "ronin.server.auth.m2m")
class Auth0MachineToMachineProperties {
    var issuer: String? = null
    var audience: String? = null
}

@Configuration
class M2MAuthConfig(private val properties: Auth0MachineToMachineProperties) {
    @Bean
    fun m2mJwtDecoder(): JwtDecoder {
        // Set up all claims that need to be validated.
        val validator = DelegatingOAuth2TokenValidator(
            listOf(
                JwtValidators.createDefaultWithIssuer(properties.issuer),
                JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD) { aud -> properties.audience in aud }
            )
        )

        // Build the JWT Decoder
        val jwtDecoder = JwtDecoders.fromOidcIssuerLocation<NimbusJwtDecoder>(properties.issuer)
        jwtDecoder.setJwtValidator(validator)
        return jwtDecoder
    }
}
