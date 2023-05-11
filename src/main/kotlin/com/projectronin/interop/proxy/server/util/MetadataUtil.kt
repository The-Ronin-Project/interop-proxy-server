package com.projectronin.interop.proxy.server.util

import com.projectronin.event.interop.internal.v1.Metadata
import io.opentracing.util.GlobalTracer
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Generates the [Metadata] based off the current tracing span's ID.
 */
fun generateMetadata(): Metadata {
    val span = GlobalTracer.get().activeSpan()
    return Metadata(runId = span.context().toTraceId(), runDateTime = OffsetDateTime.now(ZoneOffset.UTC))
}
