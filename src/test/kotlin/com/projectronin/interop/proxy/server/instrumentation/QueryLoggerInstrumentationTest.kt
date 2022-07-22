package com.projectronin.interop.proxy.server.instrumentation

import graphql.ExecutionInput
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryLoggerInstrumentationTest {
    @Test
    fun `returns the provided execution input after requesting the query`() {
        val input = mockk<ExecutionInput> {
            every { query } returns "Query"
        }

        val output = QueryLoggerInstrumentation().instrumentExecutionInput(input, mockk())
        assertEquals(input, output)
    }
}
