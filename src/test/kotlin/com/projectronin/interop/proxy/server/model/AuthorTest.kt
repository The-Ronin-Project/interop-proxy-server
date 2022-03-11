package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.ehr.model.Annotation.ReferenceAuthor as EHRReferenceAuthor
import com.projectronin.interop.ehr.model.Annotation.StringAuthor as EHRStringAuthor

class AuthorTest {
    private val testTenant = relaxedMockk<Tenant>()

    @Test
    fun `creates string author`() {
        val ehrStringAuthor = mockk<EHRStringAuthor> {
            every { value } returns "author"
        }
        val stringAuthor = StringAuthor(ehrStringAuthor)
        assertEquals("author", stringAuthor.value)
    }

    @Test
    fun `creates reference author`() {
        val ehrReferenceAuthor = mockk<EHRReferenceAuthor> {
            every { value } returns relaxedMockk()
        }
        val referenceAuthor = ReferenceAuthor(ehrReferenceAuthor, testTenant)
        assertNotNull(referenceAuthor.value)
    }
}
