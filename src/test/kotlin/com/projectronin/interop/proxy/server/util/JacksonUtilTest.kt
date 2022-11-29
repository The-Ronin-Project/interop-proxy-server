package com.projectronin.interop.proxy.server.util

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRBoolean
import com.projectronin.interop.fhir.r4.resource.Patient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JacksonUtilTest {

    @Test
    fun `string test`() {
        val realPatient = Patient(active = FHIRBoolean.TRUE, language = Code("Code"))
        val patJson = JacksonUtil.writeJsonValue(realPatient)
        assertEquals(JacksonManager.objectMapper.writeValueAsString(realPatient), patJson)
    }
}
