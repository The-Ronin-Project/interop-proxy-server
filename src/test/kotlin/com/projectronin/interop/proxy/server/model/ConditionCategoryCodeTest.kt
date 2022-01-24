package com.projectronin.interop.proxy.server.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ConditionCategoryCodeTest {
    @Test
    fun `returns code`() {
        assertEquals("problem-list-item", ConditionCategoryCode.PROBLEM_LIST_ITEM.code)
    }
}
