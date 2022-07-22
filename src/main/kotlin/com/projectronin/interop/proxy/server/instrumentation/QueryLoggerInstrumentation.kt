package com.projectronin.interop.proxy.server.instrumentation

import graphql.ExecutionInput
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Provides instrumentation of the GraphQL request that results in logging out the query that was sent to the server.
 */
@Component
class QueryLoggerInstrumentation : SimpleInstrumentation() {
    private val logger = KotlinLogging.logger { }

    override fun instrumentExecutionInput(
        executionInput: ExecutionInput,
        parameters: InstrumentationExecutionParameters?
    ): ExecutionInput {
        logger.info { "Processing GraphQL query: ${executionInput.query}" }

        return executionInput
    }
}
