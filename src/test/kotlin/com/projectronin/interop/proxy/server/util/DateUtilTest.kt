package com.projectronin.interop.proxy.server.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal class DateUtilTest {

    private val util = DateUtil()

    @Test
    fun `date format works`() {
        val expected = LocalDate.of(2020, 1, 1)
        assertEquals(expected, util.parseDateString("20200101"))
        assertEquals(expected, util.parseDateString("2020-01-01"))
        assertEquals(expected, util.parseDateString("01-01-2020"))
        assertEquals(expected, util.parseDateString("1-01-2020"))
        assertEquals(expected, util.parseDateString("01-1-2020"))
        assertEquals(expected, util.parseDateString("1-1-2020"))
        assertEquals(expected, util.parseDateString("01/01/2020"))
        assertEquals(expected, util.parseDateString("1/1/2020"))
        assertEquals(expected, util.parseDateString("1/01/2020"))
        assertEquals(expected, util.parseDateString("01/1/2020"))
        assertThrows(DateTimeParseException::class.java) {
            util.parseDateString("not/real/date")
        }
    }
}
