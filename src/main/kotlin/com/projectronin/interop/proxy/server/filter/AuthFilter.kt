package com.projectronin.interop.proxy.server.filter

import com.projectronin.interop.proxy.server.auth.M2MAuthService
import com.projectronin.interop.proxy.server.auth.UserAuthService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

private const val AUTH_HEADER = "Authorization"
const val AUTHZ_TENANT_HEADER = "AuthorizedTenant"

/**
 *  Filters query requests if they do not authenticate correctly.
 *  Stores additional data to request for future use.
 */
@Component
class AuthFilter(private val userAuthService: UserAuthService, private val m2MAuthService: M2MAuthService) : WebFilter {
    private val logger = KotlinLogging.logger { }
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {

        val bearer =
            kotlin.runCatching { exchange.request.headers.getFirst(AUTH_HEADER)!!.substring(7) /* strip 'Bearer '*/ }
                .getOrElse {
                    logger.warn(it) { it.message }
                    return handleForbidden(exchange, "Invalid Bearer token")
                }

        // Check for M2M First
        if (m2MAuthService.isM2MToken(bearer)) {
            // Evaluate the m2m case
            return if (m2MAuthService.validateToken(bearer)) {
                // M2M Case does not set the AUTHZ_TENANT_HEADER, so can only apply to some endpoints.
                logger.debug { "Machine2Machine Authentication success" }
                chain.filter(exchange)
            } else {
                logger.warn { "Machine2Machine Authentication failed" }
                handleForbidden(exchange, "Invalid M2M Bearer token '$bearer'")
            }
        }

        //  Evaluate User (Seki) token
        val authResponse = kotlin.runCatching { userAuthService.validateToken(bearer) }.getOrNull()
        return if (authResponse != null) {
            // mutate the exchange to inject the returned tenant ID from the Auth service for comparison later
            val mutatedRequest =
                exchange.request.mutate().header(AUTHZ_TENANT_HEADER, authResponse.user.tenantId).build()
            val mutatedExchange = exchange.mutate().request(mutatedRequest).build()
            logger.debug { "User Authentication success" }
            chain.filter(mutatedExchange) // This essentially just means 'nothing wrong, please continue'
        } else {
            logger.warn { "User Authentication failed" }
            handleForbidden(exchange, "Invalid User Bearer token '$bearer'")
        }
    }

    private fun handleForbidden(exchange: ServerWebExchange, message: String): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        return exchange.response.writeWith(
            Flux.just(
                exchange.response.bufferFactory().wrap(message.toByteArray())
            )
        )
    }
}
