package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.ehr.model.Reference
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import mu.KotlinLogging
import com.projectronin.interop.ehr.model.Address as EHRAddress
import com.projectronin.interop.ehr.model.Appointment as EHRAppointment
import com.projectronin.interop.ehr.model.CodeableConcept as EHRCodeableConcept
import com.projectronin.interop.ehr.model.Coding as EHRCoding
import com.projectronin.interop.ehr.model.Condition as EHRCondition
import com.projectronin.interop.ehr.model.ContactPoint as EHRContactPoint
import com.projectronin.interop.ehr.model.HumanName as EHRHumanName
import com.projectronin.interop.ehr.model.Identifier as EHRIdentifier
import com.projectronin.interop.ehr.model.Participant as EHRParticipant
import com.projectronin.interop.ehr.model.Patient as EHRPatient
import com.projectronin.interop.proxy.server.model.Address as ProxyServerAddress
import com.projectronin.interop.proxy.server.model.Appointment as ProxyServerAppointment
import com.projectronin.interop.proxy.server.model.CodeableConcept as ProxyServerCodeableConcept
import com.projectronin.interop.proxy.server.model.Coding as ProxyServerCoding
import com.projectronin.interop.proxy.server.model.Condition as ProxyServerCondition
import com.projectronin.interop.proxy.server.model.ContactPoint as ProxyServerContactPoint
import com.projectronin.interop.proxy.server.model.HumanName as ProxyServerHumanName
import com.projectronin.interop.proxy.server.model.Identifier as ProxyServerIdentifier
import com.projectronin.interop.proxy.server.model.Participant as ProxyParticipant
import com.projectronin.interop.proxy.server.model.Patient as ProxyServerPatient
import com.projectronin.interop.proxy.server.model.Reference as ProxyReference

/**
 * Translate [EHRAppointment] to [ProxyServerAppointment].
 */
fun EHRAppointment.toProxyServerAppointment(tenant: Tenant): ProxyServerAppointment {
    return ProxyServerAppointment(
        id = this.id.localize(tenant),
        identifier = this.identifier.map { it.toProxyServerIdentifier() },
        start = this.start ?: "",
        status = this.status?.code ?: "",
        serviceType = this.serviceType.map { it.toProxyServerCodeableConcept() },
        appointmentType = this.appointmentType?.toProxyServerCodeableConcept(),
        providers = this.participants.filter { it.actor.type == Reference.ReferenceType.Provider },
        tenant = tenant
    )
}

/**
 * Translate [EHRIdentifier] to [ProxyServerIdentifier].
 */
fun EHRIdentifier.toProxyServerIdentifier(): ProxyServerIdentifier {
    return ProxyServerIdentifier(
        system = this.system,
        value = this.value
    )
}

/**
 * Translate [EHRCodeableConcept] to [ProxyServerCodeableConcept].
 */
fun EHRCodeableConcept.toProxyServerCodeableConcept(): ProxyServerCodeableConcept {
    return ProxyServerCodeableConcept(
        coding = this.coding.map { it.toProxyServerCoding() },
        text = this.text
    )
}

/**
 * Translate [EHRCoding] to [ProxyServerCoding]
 */
fun EHRCoding.toProxyServerCoding(): ProxyServerCoding {
    return ProxyServerCoding(
        system = this.system,
        version = this.version,
        code = this.code,
        display = this.display,
        userSelected = this.userSelected,
    )
}

/**
 * Translate [EHRPatient] to [ProxyServerPatient]
 */
fun EHRPatient.toProxyServerPatient(tenant: Tenant): ProxyServerPatient {
    return ProxyServerPatient(
        id = this.id.localize(tenant),
        identifier = this.identifier.map { it.toProxyServerIdentifier() },
        name = this.name.map { it.toProxyServerHumanName() },
        birthDate = this.birthDate,
        gender = this.gender?.code,
        telecom = this.telecom.map { it.toProxyServerContactPoint() },
        address = this.address.map { it.toProxyServerAddress() }
    )
}

/**
 * Translate [EHRHumanName] to [ProxyServerHumanName]
 */
fun EHRHumanName.toProxyServerHumanName(): ProxyServerHumanName {
    return ProxyServerHumanName(
        use = this.use?.code,
        family = this.family,
        given = this.given
    )
}

/**
 * Translate [EHRContactPoint] to [ProxyServerContactPoint]
 */
fun EHRContactPoint.toProxyServerContactPoint(): ProxyServerContactPoint {
    return ProxyServerContactPoint(
        system = this.system?.code,
        use = this.use?.code,
        value = this.value
    )
}

/**
 * Translate [EHRAddress] to [ProxyServerAddress]
 */
fun EHRAddress.toProxyServerAddress(): ProxyServerAddress {
    return ProxyServerAddress(
        use = this.use?.code,
        line = this.line,
        city = this.city,
        state = this.state,
        postalCode = this.postalCode
    )
}

fun EHRParticipant.toProxyServerParticipant(fhirIDMap: Map<EHRParticipant, String>): ProxyParticipant {
    // use existing FHIR ID, otherwise one from map
    val fhirID = this.actor.id?.value ?: fhirIDMap[this]
    var reference: String? = "Provider/$fhirID"
    // when this happens we should just return relevant information to the caller and let them decide
    if (fhirID == null) {
        KotlinLogging.logger {}.warn("ID for Participant not found for Identifier: ${this.actor.identifier}")
        reference = null // this is just so we don't get "Provider/null"
    }

    return ProxyParticipant(
        actor = ProxyReference(
            display = this.actor.display,
            reference = reference,
            id = fhirID,
            identifier = null,
            type = Reference.ReferenceType.Provider.name
        )
    )
}

/**
 * Translate [EHRCondition] to [ProxyServerCondition]
 */
fun EHRCondition.toProxyServerCondition(tenant: Tenant): ProxyServerCondition {
    return ProxyServerCondition(
        id = this.id.localize(tenant),
        identifier = this.identifier.map { it.toProxyServerIdentifier() },
        clinicalStatus = this.clinicalStatus?.toProxyServerCodeableConcept(),
        category = this.category.map { it.toProxyServerCodeableConcept() },

        // Code is nullable in the EHR model, but required for the proxy server response
        code = this.code?.toProxyServerCodeableConcept() ?: ProxyServerCodeableConcept(coding = listOf(), text = "")
    )
}
