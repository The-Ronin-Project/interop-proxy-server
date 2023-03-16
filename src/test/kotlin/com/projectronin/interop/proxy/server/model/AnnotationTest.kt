package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.primitive.Markdown
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.projectronin.interop.fhir.r4.datatype.Annotation as R4Annotation
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

internal class AnnotationTest {
    private val testTenant = relaxedMockk<Tenant>()

    @Test
    fun `can get time`() {
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { time?.value } returns "time"
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertEquals("time", annotation.time)
    }

    @Test
    fun `null time`() {
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { time } returns null
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertNull(annotation.time)
    }

    @Test
    fun `can get text`() {
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { text } returns Markdown("text")
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertEquals("text", annotation.text)
    }

    @Test
    fun `can get null author`() {
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { author } returns null
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertNull(annotation.author)
    }

    @Test
    fun `can get string author`() {
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { author?.value } returns "author"
            every { author?.type } returns DynamicValueType.STRING
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertTrue(annotation.author is StringAuthor)
    }

    @Test
    fun `can get reference author`() {
        val referenceAuthor = relaxedMockk<R4Reference>()
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { author?.value } returns referenceAuthor
            every { author?.type } returns DynamicValueType.REFERENCE
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertTrue(annotation.author is ReferenceAuthor)
    }

    @Test
    fun `throws exception on unknown author type`() {
        val ehrAnnotation = relaxedMockk<R4Annotation> {
            every { author?.type } returns DynamicValueType.MONEY
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        val exception = assertThrows<RuntimeException> {
            annotation.author
        }
        assertEquals("Unknown annotation author type encountered", exception.message)
    }
}
