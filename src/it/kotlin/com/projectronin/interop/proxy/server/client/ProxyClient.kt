package com.projectronin.interop.proxy.server.client

import com.projectronin.interop.common.jackson.JacksonManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.util.StringValues
import kotlinx.coroutines.runBlocking

object ProxyClient {
    val httpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
            }
            // Setup JSON
            install(ContentNegotiation) {
                jackson {
                    JacksonManager.setUpMapper(this)
                }
            }

            // Enable logging.
            install(Logging) {
                level = LogLevel.NONE
            }
        }
    val url = "http://localhost:8080/graphql"

    fun query(
        query: String,
        token: String,
        headers: StringValues = getBaseGraphQLHeaders(token),
    ) = runBlocking {
        httpClient.post(url) {
            headers { appendAll(headers) }
            setBody(query)
        }
    }

    private fun getBaseGraphQLHeaders(token: String = "epic"): StringValues {
        return StringValues.build {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.ContentType, "application/graphql")
        }
    }

    fun getBaseTenantHeaders(): StringValues {
        return StringValues.build {
            append(HttpHeaders.Authorization, "Bearer epic")
            append(HttpHeaders.ContentType, "application/json")
        }
    }

    fun get(url: String) =
        runBlocking {
            httpClient.get(url) {
                headers {
                    appendAll(getBaseTenantHeaders())
                }
            }
        }

    inline fun <reified T> post(
        url: String,
        body: T,
    ) = runBlocking {
        httpClient.post(url) {
            headers {
                appendAll(getBaseTenantHeaders())
            }
            setBody(body)
        }
    }

    inline fun <reified T> put(
        url: String,
        body: T,
    ) = runBlocking {
        httpClient.put(url) {
            headers {
                appendAll(getBaseTenantHeaders())
            }
            setBody(body)
        }
    }

    fun delete(url: String) =
        runBlocking {
            httpClient.delete(url) {
                headers {
                    appendAll(getBaseTenantHeaders())
                }
            }
        }
}
