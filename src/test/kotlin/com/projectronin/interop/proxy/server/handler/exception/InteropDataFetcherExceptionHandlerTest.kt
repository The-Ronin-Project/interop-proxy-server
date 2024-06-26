package com.projectronin.interop.proxy.server.handler.exception

import com.projectronin.interop.common.exceptions.InteropIllegalArgumentException
import com.projectronin.interop.common.logmarkers.LogMarkers
import graphql.execution.DataFetcherExceptionHandlerParameters
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging
import mu.Marker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InteropDataFetcherExceptionHandlerTest {
    @Test
    fun `logs exception as error`() {
        mockkObject(KotlinLogging)
        val logger = mockk<KLogger>(relaxed = true)
        every { KotlinLogging.logger(any<() -> Unit>()) } returns logger

        val illegalStateException = IllegalStateException("message")
        val parameters =
            mockk<DataFetcherExceptionHandlerParameters> {
                every { exception } returns illegalStateException
                every { sourceLocation } returns mockk(relaxed = true)
                every { path } returns mockk(relaxed = true)
            }

        val result = InteropDataFetcherExceptionHandler().onException(parameters)
        assertEquals(1, result.errors.size)

        verify(exactly = 1) { logger.error(null as Marker?, any<Throwable>(), any()) }
        confirmVerified(logger)

        unmockkObject(KotlinLogging)
    }

    @Test
    fun `logs exception as error with marker`() {
        mockkObject(KotlinLogging)
        val logger = mockk<KLogger>(relaxed = true)
        every { KotlinLogging.logger(any<() -> Unit>()) } returns logger

        val logMarkerException = InteropIllegalArgumentException("message")
        val parameters =
            mockk<DataFetcherExceptionHandlerParameters> {
                every { exception } returns logMarkerException
                every { sourceLocation } returns mockk(relaxed = true)
                every { path } returns mockk(relaxed = true)
            }

        val result = InteropDataFetcherExceptionHandler().onException(parameters)
        assertEquals(1, result.errors.size)

        verify(exactly = 1) { logger.error(LogMarkers.ILLEGAL_ARGUMENT, any<Throwable>(), any()) }
        confirmVerified(logger)

        unmockkObject(KotlinLogging)
    }
}
