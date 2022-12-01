package com.projectronin.interop.proxy.server.handler.exception

import graphql.ExceptionWhileDataFetching
import graphql.execution.SimpleDataFetcherExceptionHandler
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class InteropDataFetcherExceptionHandler : SimpleDataFetcherExceptionHandler() {
    private val logger = KotlinLogging.logger { }

    override fun logException(error: ExceptionWhileDataFetching, exception: Throwable?) {
        logger.error(exception) { error.message }
    }
}
