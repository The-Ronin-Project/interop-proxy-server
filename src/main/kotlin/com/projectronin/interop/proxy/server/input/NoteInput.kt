package com.projectronin.interop.proxy.server.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@GraphQLDescription("A message that contains note information from product for HL7v2 downstream writebacks")
data class NoteInput(
    @GraphQLDescription("The identifier of the patient that the note is for")
    val patientId: String,
    @GraphQLDescription("The patient identifier type")
    val patientIdType: PatientIdType,
    @GraphQLDescription("The FHIR ID of the practitioner authoring the note")
    val practitionerFhirId: String,
    @GraphQLDescription("The text of the note that should be sent")
    val noteText: String,
    @GraphQLDescription("Timestamp of when note was recorded, in yyyyMMddHHmm[ss] format")
    val datetime: String,
    @GraphQLDescription("The originator of the note")
    val noteSender: NoteSender,
    @GraphQLDescription("If the note is an alert")
    val isAlert: Boolean,
) {
    // Using the pattern here results in some odd parsing logic that actually does not work for our case.
    private val dateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .toFormatter()

    /**
     * Validates the NoteInput and throws an [IllegalArgumentException] if any invalid data is encountered.
     */
    fun validate() {
        try {
            LocalDateTime.parse(datetime, dateTimeFormatter)
        } catch (e: Exception) {
            throw IllegalArgumentException("""datetime must be of form "yyyyMMddHHmm[ss]" but was "$datetime"""")
        }
    }
}
