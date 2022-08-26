package com.projectronin.interop.proxy.server.util

import com.projectronin.interop.common.jackson.JacksonManager

object JacksonUtil {
    private val mapper = JacksonManager.objectMapper

    /**
     * Wrapper around JacksonManager.objectMapper.writeValueAsString
     */
    fun writeJsonValue(value: Any): String {
        return mapper.writeValueAsString(value)
    }
}
