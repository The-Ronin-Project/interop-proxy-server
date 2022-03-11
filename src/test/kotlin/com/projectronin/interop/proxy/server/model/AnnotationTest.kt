package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.projectronin.interop.ehr.model.Annotation as EHRAnnotation

internal class AnnotationTest {
    private val testTenant = relaxedMockk<Tenant>()

    @Test
    fun `can get time`() {
        val ehrAnnotation = relaxedMockk<EHRAnnotation> {
            every { time } returns "time"
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertEquals("time", annotation.time)
    }

    @Test
    fun `can get text`() {
        val ehrAnnotation = relaxedMockk<EHRAnnotation> {
            every { text } returns "text"
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertEquals("text", annotation.text)
    }

    @Test
    fun `can get null author`() {
        val ehrAnnotation = relaxedMockk<EHRAnnotation> {
            every { author } returns null
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertNull(annotation.author)
    }

    @Test
    fun `can get string author`() {
        val ehrStringAuthor = mockk<EHRAnnotation.StringAuthor> {
            every { value } returns "author"
        }
        val ehrAnnotation = relaxedMockk<EHRAnnotation> {
            every { author } returns ehrStringAuthor
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertTrue(annotation.author is StringAuthor)
    }

    @Test
    fun `can get reference author`() {
        val ehrReferenceAuthor = mockk<EHRAnnotation.ReferenceAuthor> {
            every { value } returns relaxedMockk()
        }
        val ehrAnnotation = relaxedMockk<EHRAnnotation> {
            every { author } returns ehrReferenceAuthor
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        assertTrue(annotation.author is ReferenceAuthor)
    }

    @Test
    fun `throws exception on unknown author type`() {
        val ehrAnnotation = relaxedMockk<EHRAnnotation> {
            every { author } returns mockk()
        }
        val annotation = Annotation(ehrAnnotation, testTenant)
        val exception = assertThrows<RuntimeException> {
            annotation.author
        }
        assertEquals("Unknown annotation author type encountered", exception.message)
    }
}
