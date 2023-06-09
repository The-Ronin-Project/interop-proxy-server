package com.projectronin.interop.proxy.server.graphql

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.proxy.server.BaseProxyIT
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach

abstract class BaseGraphQLIT : BaseProxyIT() {
    val url = "$serverUrl/graphql"

    @BeforeEach
    fun `set`() {
        populateTenantData()
    }

    fun fhirIdentifier(fhirID: String): Identifier {
        return identifier {
            value of fhirID
            system of "http://projectronin.com/id/fhir"
        }
    }

    fun getM2MAuthentication(): String = runBlocking {
        val json: JsonNode = httpClient.submitForm(
            url = "http://localhost:8083/proxy/token",
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", "proxy-client")
                append("client_secret", "secret")
            }
        ).body()
        json.get("access_token").asText()
    }
}
