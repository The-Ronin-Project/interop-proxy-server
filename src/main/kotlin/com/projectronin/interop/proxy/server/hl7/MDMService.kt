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
import org.springframework.stereotype.Component

@Component("proxyMdmService")
class MDMService {
    /**
     * generateMDM create and return an HL7v2 MDM_T02 message based on the inputs:
     * @param tenantId: Identifier for tenant that will be receiving the HL7v2 message downstream
     * @param patient: MDMPatientFields, relevant patient information for the patient from aidbox
     * @param practitioner: MDMPractitionerFields, relevant practitioner information for the practitioner from aidbox
     * @param note: Text of the note to be attached in the OBX segment(s)
     * @param dateTime: Date and Time the note was recorded, in yyyymmddhhmmss
     * @param parentDocumentId: document identifier for parent note, in case of addendum
     * @param documentStatus: Whether the document is documented "DO" or in progress "IP", goes into TXA-17
     */

    // TODO Need to break out tenant specific interface details, noted with "MDA" in comments below
    fun generateMDM(
        tenantId: String,
        patient: MDMPatientFields,
        practitioner: MDMPractitionerFields,
        note: String,
        dateTime: String,
        parentDocumentId: String? = null,
        documentStatus: String? = "IP"
    ): Pair<String, String> {
        val mdm = MDM_T02()
        val eventType = parentDocumentId?.let { "T08" } ?: "T02"
        // TODO MSH-11, MSH-5 should be set based on Tenant interface information, will need to be added to our tenant configurations and pulled based on tenantId
        mdm.initQuickstart("MDM", eventType, "T")
        mdm.msh.msh3_SendingApplication.namespaceID.value = "RONIN"
        mdm.msh.msh5_ReceivingApplication.namespaceID.value = "TenantApplication"

        // Populate the EVN Segment
        val evn: EVN = mdm.evn
        evn.eventTypeCode.value = eventType
        evn.recordedDateTime.time.value = dateTime

        // Populate PID Segment
        setPID(mdm, patient, patient.mrn)

        // Populate the PV1 Segment
        val pv1: PV1 = mdm.pV1
        pv1.patientClass.value = "U"

        // Populate the TXA Segment
        setTXA(mdm, practitioner, parentDocumentId, documentStatus!!, eventType, dateTime)

        // Populate the OBX Segment MDA: Replace all tabs with 4 spaces
        setOBX(mdm, note)

        val encodedMessage = DefaultHapiContext().pipeParser.encode(mdm)
        return Pair(encodedMessage, mdm.txa.uniqueDocumentNumber.universalID.value)
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
            for (word in string.split(Regex("( |\\n|\\r)+"))) {
                // if the current string exceeds the max size
                val newword = word.replace("\t", "    ")
                if (builder.length + newword.length > max) {
                    // then we add the string to the list and clear the builder
                    it.add(builder.toString())
                    builder.setLength(0)
                    firstWord = true
                }
                // append a space at the beginning of each word, except the first one
                if (firstWord) firstWord = false else builder.append(' ')
                builder.append(newword)
            }

            // add the last collected part if there was any
            if (builder.isNotEmpty()) {
                it.add(builder.toString())
            }
        }

    // creates and populates the PID segment
    private fun setPID(mdm: MDM_T02, patient: MDMPatientFields, mrn: String? = null) {
        val pid: PID = mdm.pid

        // PID-3 Identifiers MDA: Only want MRN
        val mrnValue =
            mrn ?: patient.identifier.firstOrNull { it.system?.value?.substringAfterLast("/") == "mrn" }?.value?.value
        mrnValue?.let {
            pid.getPatientIdentifierList(0).idNumber.value = mrnValue
            pid.getPatientIdentifierList(0).assigningAuthority.namespaceID.value = "MRN"
        }

        // PID-5 Name, MDA: only return official name
        val pid5 = patient.name.firstOrNull { it.use?.value == "official" }
        pid5?.let {
            pid.getPatientName(0).familyName.surname.value = pid5.family?.value
            pid.getPatientName(0).givenName.value = pid5.given.getOrNull(0)?.value
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
        documentStatus: String,
        eventType: String,
        dateTime: String
    ) {
        val txa: TXA = mdm.txa
        txa.setIDTXA.value = "1"

        // TXA-2 Document Type MDA: Hardcode TXA-2 to 3000326 for their numeric identifier
        txa.documentType.value = "3000326"
        txa.documentContentPresentation.value = "TX"

        // TXA-4 Activity Date/Time
        txa.activityDateTime.time.value = dateTime

        // TXA-5 Primary Activity Provider from Practitioner Info
        txa.getPrimaryActivityProviderCodeName(0).familyName.surname.value =
            practitioner.name.getOrNull(0)?.family?.value
        txa.getPrimaryActivityProviderCodeName(0).givenName.value =
            practitioner.name.getOrNull(0)?.given?.getOrNull(0)?.value

        // TXA-5 Identifier, MDA: pull ID with type "MDACC" and use "usual"
        val pracId =
            practitioner.identifier.firstOrNull { (it.use?.value == "usual" && it.type?.text?.value == "MDACC") }
        pracId?.let {
            txa.getPrimaryActivityProviderCodeName(0)?.idNumber!!.value = pracId.value?.value
        }

        // TXA-9 Originator Code/Name (need to provide information from Project Ronin as originators of the transcription)
        txa.getOriginatorCodeName(0).familyName.surname.value = "Project"
        txa.getOriginatorCodeName(0).givenName.value = "Ronin"

        // TXA-12 Unique Document Number (parentDocumentId for T08, or generated and unique for T02 or T06)
        // MDA: Set TXA-12.3 (Universal ID) instead of TXA-12.1 (Entity Identifier)
        txa.uniqueDocumentNumber.universalID.value = if (eventType == "T08") {
            parentDocumentId
        } else {
            val re = Regex(pattern = "[^A-Za-z0-9 ]")
            "RoninNote" + re.replace(mdm.msh.dateTimeOfMessage.time.value, "") + "-" + mdm.msh.messageControlID.value
        }

        // TXA-13 Parent Document Number, used for addendum (T06) messages

        // TXA-17 Document Completion Status, default to documented.
        // This default comes from when a message is first requested at generation
        // MDA: IP "in progress" for incomplete record, AU "authenticated" for final record
        txa.documentCompletionStatus.value = documentStatus

        // TXA-18 Document Confidentiality Status, MDA: defaults to "U", unrestricted
        txa.documentConfidentialityStatus.value = "U"

        // TXA-19 Document Availability Status, MDA: defaults to "AV", available
        txa.documentAvailabilityStatus.value = if (documentStatus == "IP") "UN" else "AV"
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
