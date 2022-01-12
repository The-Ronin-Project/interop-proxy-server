package com.projectronin.interop.proxy.server.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.ClearEnvironmentVariable
import org.junitpioneer.jupiter.SetEnvironmentVariable

class EnvironmentVarUtilTest {
    @Test
    @SetEnvironmentVariable(key = "ENV_VAR", value = "abc")
    fun `finds existing env var`() {
        val value = getKeyFromEnv("ENV_VAR")
        assertEquals("abc", value)
    }

    @Test
    @ClearEnvironmentVariable(key = "ENV_VAR")
    fun `returns null when env var not set`() {
        val value = getKeyFromEnv("ENV_VAR")
        assertNull(value)
    }
}
