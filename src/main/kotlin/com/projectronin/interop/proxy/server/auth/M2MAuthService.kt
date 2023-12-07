package com.projectronin.interop.proxy.server.auth

import com.nimbusds.jwt.JWTParser
import mu.KotlinLogging
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component

@Component
class M2MAuthService(private val properties: Auth0MachineToMachineProperties, private val m2mJwtDecoder: JwtDecoder) {
    private val logger = KotlinLogging.logger { }

    /**
     * Returns true if the token is a Machine 2 Machine token, false otherwise.
     */
    fun isM2MToken(token: String): Boolean {
        // Compare the issuer with the known m2m issuer
        return runCatching { JWTParser.parse(token) }.map { it.jwtClaimsSet.issuer == properties.issuer }
            .getOrDefault(false)
    }

    /**
     * Validates a M2M token.
     */
    fun validateToken(token: String): ParsedM2MToken {
        // Decode the JWT to ensure there are no validation exceptions
        return try {
            ParsedM2MToken(
                m2mJwtDecoder.decode(token),
                true,
            )
        } catch (e: Exception) {
            logger.info { "M2M Auth Failed with exception: ${e.message}" }
            ParsedM2MToken(
                null,
                false,
            )
        }
    }
}

data class ParsedM2MToken(
    val token: Jwt?,
    val success: Boolean,
)
