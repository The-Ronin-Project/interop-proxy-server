package com.projectronin.interop.proxy.server.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.opentracing.util.GlobalTracer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MetadataUtilTest {
    @Test
    fun `generateMetadata returns metadata`() {
        mockkStatic(GlobalTracer::class)
        every { GlobalTracer.get().activeSpan() } returns
            mockk {
                every { context().toTraceId() } returns "trace-id-1"
            }

        val dateTime = OffsetDateTime.now(ZoneOffset.UTC)
        mockkStatic(OffsetDateTime::class)
        every { OffsetDateTime.now(ZoneOffset.UTC) } returns dateTime

        val metadata = generateMetadata()
        assertEquals("trace-id-1", metadata.runId)
        assertEquals(dateTime, metadata.runDateTime)
        assertNull(metadata.upstreamReferences)

        unmockkAll()
    }
}
