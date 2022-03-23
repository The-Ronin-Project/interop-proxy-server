package com.projectronin.interop.proxy.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.projectronin.interop.proxy.server.util.getKeyFromEnv
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Validates a token with Seki authentication service.
 */
@Component
class UserAuthService(private val client: HttpClient, @Value("\${seki.endpoint}") val authServiceEndPoint: String) {
    private val logger = KotlinLogging.logger { }

    // We'll have to change these once Seki is live and the final config is done
    private val authURLPart = "/session/validate"
    private val issuer = "Banken"
    private val audience = "Seki"
    private val serviceCallJWTSecret = getKeyFromEnv("SERVICE_CALL_JWT_SECRET")

    /**
     * Sends token to authentication service and returns the [AuthResponse].  Returns null if the token is not valid,
     * or there is an http problem.
     */
    fun validateToken(consumerToken: String): AuthResponse? {
        val authURL = authServiceEndPoint + authURLPart

        logger.debug { "Setting up authentication for $authURL" }

        serviceCallJWTSecret ?: throw IllegalStateException("Could not load JWT secret")
        val jwtAuthString = JWT.create().withAudience(audience).withIssuer(issuer).sign(Algorithm.HMAC256(serviceCallJWTSecret))

        logger.debug { "Calling authentication for $authURL" }
        return runBlocking {
            try {
                val httpResponse: HttpResponse = client.get(authServiceEndPoint + authURLPart) {
                    headers {
                        append(HttpHeaders.ContentType, "application/json")
                        append("X-JWT", jwtAuthString)
                    }
                    parameter("token", consumerToken)
                }

                val authResponse = httpResponse.receive<AuthResponse>()
                logger.info { "User ${authResponse.user} successfully validated" }
                authResponse
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.Unauthorized) {
                    // token is invalid, but we received a response back we could handle
                    logger.warn(e) { "Token not valid: ${e.message}" }
                    null
                } else {
                    // Seiki is throwing an error we can't handle and we won't be able to
                    logger.error(e) { "Could not validate token: ${e.message}" }
                    throw e
                }
            }
        }
    }
}
