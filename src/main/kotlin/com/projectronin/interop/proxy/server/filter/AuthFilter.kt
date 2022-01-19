package com.projectronin.interop.proxy.server.filter

import com.projectronin.interop.proxy.server.auth.AuthService
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
class AuthFilter(private val authService: AuthService) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {

        val bearer = try { // substring can throw an exception
            exchange.request.headers.getFirst(AUTH_HEADER)?.substring(7) // strip the 'Bearer ' prefix
        } catch (e: Exception) {
            KotlinLogging.logger {}.debug { e.message }
            null
        }

        val authResponse = bearer?.let { authService.validateToken(it) }

        return if (authResponse == null) {
            // return early with custom error response
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            exchange.response.writeWith(
                Flux.just(
                    exchange.response.bufferFactory().wrap("Invalid Bearer token '$bearer'".toByteArray())
                )
            )
        } else {
            // mutate the exchange to inject the returned tenant ID from the Auth service for comparison later
            val mutatedRequest =
                exchange.request.mutate().header(AUTHZ_TENANT_HEADER, authResponse.user.tenantId).build()
            val mutatedExchange = exchange.mutate().request(mutatedRequest).build()
            chain.filter(mutatedExchange) // This essentially just means 'nothing wrong, please continue'
        }
    }
}
