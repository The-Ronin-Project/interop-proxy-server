package com.projectronin.interop.proxy.server.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DateUtil {

    fun parseDateString(dateString: String): LocalDate {
        val formats = listOf(
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM-d-yyyy"),
            DateTimeFormatter.ofPattern("M-dd-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM/d/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )

        formats.forEach {
            try {
                return LocalDate.parse(dateString, it)
            } catch (_: DateTimeParseException) {
            }
        }
        // if no formats work, throw exception
        throw DateTimeParseException("'$dateString' is not in a recognized date format.", dateString, 0)
    }
    fun parseDateTimeString(dateTime: String): LocalDateTime {
        val formats = listOf(
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        )
        formats.forEach {
            try {
                return LocalDateTime.parse(dateTime, it)
            } catch (_: DateTimeParseException) {
            }
        }
        // if no formats work, throw exception
        throw DateTimeParseException("""'$dateTime' is not in a recognized date format, datetime must be of form "yyyyMMddHHmm[ss]"""", dateTime, 0)
    }
}
