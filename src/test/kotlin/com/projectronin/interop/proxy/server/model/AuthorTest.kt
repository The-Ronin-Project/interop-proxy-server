package com.projectronin.interop.proxy.server.model

import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.proxy.server.util.relaxedMockk
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.projectronin.interop.fhir.r4.datatype.Reference as R4Reference

class AuthorTest {
    private val testTenant = relaxedMockk<Tenant>()

    @Test
    fun `creates string author`() {
        val stringAuthor = StringAuthor("author")
        assertEquals("author", stringAuthor.value)
    }

    @Test
    fun `creates reference author`() {
        val ehrReferenceAuthor = mockk<R4Reference> {
            every { reference } returns "reference"
            every { type?.value } returns "type"
            every { display } returns "display"
            every { identifier } returns Identifier(value = "123")
            every { id } returns "123"
        }
        val referenceAuthor = ReferenceAuthor(ehrReferenceAuthor, testTenant)
        assertNotNull(referenceAuthor.value)
    }
}
