package com.projectronin.interop.proxy.server.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.validation.ValidationError
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

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

    /*
        Normally when there's a validation error on a GraphQL query, the error gets returned to the user and a warning
        is logged.  We want to log them as an error, so that when someone sends a bad query, or tries to call a query
        that doesn't exist, it'll get posted to #interops-errors and trigger a PagerDuty alert in Prod.
     */
    override fun instrumentExecutionResult(
        executionResult: ExecutionResult?,
        parameters: InstrumentationExecutionParameters?
    ): CompletableFuture<ExecutionResult> {
        executionResult?.let {
            it.errors.filterIsInstance<ValidationError>().map { error ->
                logger.error { "${error.errorType}: ${error.message}" }
            }
        }
        return super.instrumentExecutionResult(executionResult, parameters)
    }
}
