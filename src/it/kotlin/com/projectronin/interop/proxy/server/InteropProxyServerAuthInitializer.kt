package com.projectronin.interop.proxy.server

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/**
 * Initializes proxy server integration tests with a mocked web service used for consumer token authentication
 */
class InteropProxyServerAuthInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val mockAuthWebServer = MockWebServer()
        val mockWebServerUrl = mockAuthWebServer.url("/auth").toString()
        val queueDispatcher = QueueDispatcher()
        queueDispatcher.setFailFast(
            MockResponse().setBody(
                javaClass.getResource("/graphql/sekiAuthResponse.json")!!.readText()
            ).setHeader("Content-Type", "application/json").setResponseCode(200)
        )
        mockAuthWebServer.dispatcher = queueDispatcher
        try { // try/catch block necessary since the server is 'already started' after the first test
            mockAuthWebServer.start()
        } catch (_: IllegalStateException) {} // throw anything besides IllegalStateException

        TestPropertyValues.of(mapOf("seki.endpoint" to mockWebServerUrl))
            .applyTo(applicationContext) // overwrites the 'real' endpoint in application-it.properties
    }
}
