package com.projectronin.interop.proxy.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
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

    /**
     * Sends token to authentication service and returns the [AuthResponse].  Returns null if the token is not valid,
     * or there is an http problem.
     */
    fun validateToken(consumerToken: String): AuthResponse? {
        val authURL = authServiceEndPoint + authURLPart

        logger.debug { "Calling authentication for $authURL" }
        return runBlocking {
            try {
                val httpResponse: HttpResponse = client.get(authServiceEndPoint + authURLPart) {
                    headers {
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    parameter("token", consumerToken)
                }

                val authResponse = httpResponse.body<AuthResponse>()
                logger.info { "User ${authResponse.user} successfully validated" }
                authResponse
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.Unauthorized) {
                    // token is invalid, but we received a response back we could handle
                    logger.info(e) { "Token not valid: ${e.message}" }
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
