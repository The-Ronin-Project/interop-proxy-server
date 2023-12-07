package com.projectronin.interop.proxy.server.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.validation.ValidationError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryLoggerInstrumentationTest {
    @Test
    fun `returns the provided execution input after requesting the query`() {
        val input =
            mockk<ExecutionInput> {
                every { query } returns "Query"
            }

        val output = QueryLoggerInstrumentation().instrumentExecutionInput(input, mockk())
        assertEquals(input, output)
    }

    @Test
    fun `instrumentExecutionResult calls parent function`() {
        val instrumentation = spyk(QueryLoggerInstrumentation(), recordPrivateCalls = true)
        val executionResult = mockk<ExecutionResult>()
        val instrumentationExecutionParameters = mockk<InstrumentationExecutionParameters>()

        every { executionResult.errors } returns
            listOf(
                mockk<ValidationError> {
                    every { message } returns "error"
                },
            )

        mockkConstructor(SimpleInstrumentation::class)
        every {
            anyConstructed<SimpleInstrumentation>()
                .instrumentExecutionResult(executionResult, instrumentationExecutionParameters)
        } returns mockk()

        instrumentation.instrumentExecutionResult(executionResult, instrumentationExecutionParameters)
        verify(exactly = 1) {
            (instrumentation as SimpleInstrumentation).instrumentExecutionResult(executionResult, instrumentationExecutionParameters)
        }
    }
}
