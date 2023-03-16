package com.projectronin.interop.proxy.server.hl7

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.model.Varies
import ca.uhn.hl7v2.model.v251.datatype.TX
import ca.uhn.hl7v2.model.v251.message.MDM_T02
import ca.uhn.hl7v2.model.v251.segment.EVN
import ca.uhn.hl7v2.model.v251.segment.OBX
import ca.uhn.hl7v2.model.v251.segment.PID
import ca.uhn.hl7v2.model.v251.segment.PV1
import ca.uhn.hl7v2.model.v251.segment.TXA
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.proxy.server.hl7.model.MDMPatientFields
import com.projectronin.interop.proxy.server.hl7.model.MDMPractitionerFields
import com.projectronin.interop.proxy.server.util.asEnum
import io.ktor.util.toUpperCasePreservingASCIIRules
import org.springframework.stereotype.Component

@Component
class MDMService {
    /**
     * generateMDM create and return an HL7v2 MDM_T02 message based on the inputs:
     * @param tenantId: Identifier for tenant that will be receiving the HL7v2 message downstream
     * @param patient: MDMPatientFields, relevant patient information for the patient from aidbox
     * @param practitioner: MDMPractitionerFields, relevant practitioner information for the practitioner from aidbox
     * @param note: Text of the note to be attached in the OBX segment(s)
     * @param datetime: Date and Time the note was recorded, in yyyymmddhhmmss
     * @param parentDocumentId: document identifier for parent note, in case of addendum
     * @param documentStatus: Whether the document is documented "DO" or in progress "IP", goes into TXA-17
     */
    fun generateMDM(
        tenantId: String,
        patient: MDMPatientFields,
        practitioner: MDMPractitionerFields,
        note: String,
        datetime: String,
        parentDocumentId: String? = null,
        documentStatus: String = "DO"
    ): Pair<String, String> {
        val mdm = MDM_T02()
        val eventType = parentDocumentId?.let { "T06" } ?: "T02"
        // TODO MSH-11, MSH-5 should be set based on Tenant interface information, will need to be added to our tenant configurations and pulled based on tenantId
        mdm.initQuickstart("MDM", eventType, "T")
        mdm.msh.msh3_SendingApplication.namespaceID.value = "RONIN"
        mdm.msh.msh5_ReceivingApplication.namespaceID.value = "TenantApplication"

        // Populate the EVN Segment
        val evn: EVN = mdm.evn
        evn.eventTypeCode.value = eventType
        evn.recordedDateTime.time.value = datetime

        // Populate PID Segment
        setPID(mdm, patient)

        // Populate the PV1 Segment
        val pv1: PV1 = mdm.pV1
        pv1.patientClass.value = "U"

        // Populate the TXA Segment
        setTXA(mdm, practitioner, parentDocumentId, documentStatus)

        // Populate the OBX Segment
        setOBX(mdm, note)

        val encodedMessage = DefaultHapiContext().pipeParser.encode(mdm)
        return Pair(encodedMessage, mdm.txa.uniqueDocumentNumber.entityIdentifier.value)
    }

    // formatDate converts the dates from aidbox with dashes into yyyymmdd format for HL7 compatibility
    fun formatDate(date: Date): String {
        return date.value?.replace("-", "") ?: ""
    }

    // splitIntoChunks breaks down notes that are too long for the OBX-5 character limit into repeating OBX segments
    private fun splitIntoChunks(max: Int, string: String): List<String> =
        ArrayList<String>(string.length / max + 1).also {
            var firstWord = true
            val builder = StringBuilder()

            // split string by whitespace
            for (word in string.split(Regex("( |\n|\r)+"))) {
                // if the current string exceeds the max size
                if (builder.length + word.length > max) {
                    // then we add the string to the list and clear the builder
                    it.add(builder.toString())
                    builder.setLength(0)
                    firstWord = true
                }
                // append a space at the beginning of each word, except the first one
                if (firstWord) firstWord = false else builder.append(' ')
                builder.append(word)
            }

            // add the last collected part if there was any
            if (builder.isNotEmpty()) {
                it.add(builder.toString())
            }
        }

    // creates and populates the PID segment
    private fun setPID(mdm: MDM_T02, patient: MDMPatientFields) {
        val pid: PID = mdm.pid

        // PID-3 Identifiers
        var pid3count = 0
        for (i in patient.identifier.indices) {
            val type = patient.identifier[i].system?.value.toString().substringAfterLast("/")

            if (type in listOf("mrn", "fhir")) {
                pid.getPatientIdentifierList(pid3count).idNumber.value = patient.identifier[i].value?.value
                pid.getPatientIdentifierList(pid3count).assigningAuthority.namespaceID.value =
                    type.toUpperCasePreservingASCIIRules()
                pid3count += 1
            }
        }

        // PID-5 Name
        for (i in patient.name.indices) {
            pid.getPatientName(i).familyName.surname.value = patient.name[i].family?.value
            pid.getPatientName(i).givenName.value = patient.name[i].given.getOrNull(0)?.value
        }
        // PID-7 DOB
        pid.dateTimeOfBirth.time.value = patient.dob?.let { formatDate(it) }

        // PID-8 Administrative Sex
        pid.administrativeSex.value = patient.gender.toString().take(1).uppercase()

        // PID-11 Address
        for (i in patient.address.indices) {
            pid.getPatientAddress(i).streetAddress.streetOrMailingAddress.value =
                patient.address[i].line.mapNotNull { it.value }.joinToString(", ")
            pid.getPatientAddress(i).city.value = patient.address[i].city?.value
            pid.getPatientAddress(i).stateOrProvince.value = patient.address[i].state?.value
            pid.getPatientAddress(i).zipOrPostalCode.value = patient.address[i].postalCode?.value
            pid.getPatientAddress(i).country.value = patient.address[i].country?.value.orEmpty()
        }

        // PID-13 Phone
        var phonecount = 0
        for (i in patient.phone.indices) {
            when (patient.phone[i].use.asEnum<ContactPointUse>()) {
                ContactPointUse.HOME -> {
                    pid.getPhoneNumberHome(phonecount).telephoneNumber.value = patient.phone[i].value?.value
                    pid.getPhoneNumberHome(phonecount).telecommunicationUseCode.value = "PRN"
                    phonecount += 1
                }
                ContactPointUse.WORK -> {
                    pid.getPhoneNumberHome(phonecount).telephoneNumber.value = patient.phone[i].value?.value
                    pid.getPhoneNumberHome(phonecount).telecommunicationUseCode.value = "WPN"
                    phonecount += 1
                }
                ContactPointUse.MOBILE -> {
                    pid.getPhoneNumberHome(phonecount).telephoneNumber.value = patient.phone[i].value?.value
                    pid.getPhoneNumberHome(phonecount).telecommunicationUseCode.value = "ORN"
                    phonecount += 1
                }
                else -> {}
            }
        }
    }

    // creates and populates the TXA segment
    private fun setTXA(
        mdm: MDM_T02,
        practitioner: MDMPractitionerFields,
        parentDocumentId: String?,
        documentStatus: String
    ) {
        val txa: TXA = mdm.txa
        txa.setIDTXA.value = "1"
        txa.documentType.value = "PR"
        txa.documentContentPresentation.value = "TX"

        // TXA-5 Primary Activity Provider from Practitioner Info
        txa.getPrimaryActivityProviderCodeName(0).familyName.surname.value =
            practitioner.name.getOrNull(0)?.family?.value
        txa.getPrimaryActivityProviderCodeName(0).givenName.value =
            practitioner.name.getOrNull(0)?.given?.getOrNull(0)?.value

        // TXA-9 Originator Code/Name (need to provide information from Project Ronin as originators of the transcription)
        txa.getOriginatorCodeName(0).familyName.surname.value = "Project"
        txa.getOriginatorCodeName(0).givenName.value = "Ronin"

        // TXA-12 Unique Document Number (needs to be generated and unique, needs to be cited for addendum messages)
        txa.uniqueDocumentNumber.entityIdentifier.value =
            "RoninNote" + mdm.msh.dateTimeOfMessage.time.value + "." + mdm.msh.messageControlID.value

        // TXA-13 Parent Document Number, used for addendum (T06) messages
        parentDocumentId?.let { txa.parentDocumentNumber.entityIdentifier.value = parentDocumentId }

        // TXA-17 Document Completion Status, default to documented.
        // This default comes from when a message is first requested at generation
        txa.documentCompletionStatus.value = documentStatus
    }

    // creates and populates the OBX segment
    private fun setOBX(mdm: MDM_T02, note: String) {
        var obxcount = 1
        for (line in note.lines()) {
            val lines = splitIntoChunks(65535, line)
            for (i in lines.indices) {
                val obx: OBX = mdm.getOBSERVATION(obxcount - 1).obx
                obx.setIDOBX.value = (obxcount).toString()
                obx.valueType.value = "TX"
                val tx = TX(mdm)
                tx.value = lines[i]
                val value: Varies = obx.getObservationValue(0)
                value.data = tx
                obxcount += 1
            }
        }
    }
}
