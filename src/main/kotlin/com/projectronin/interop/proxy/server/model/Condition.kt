package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.util.localizeFhirId
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.fhir.r4.datatype.Age as R4Age
import com.projectronin.interop.fhir.r4.datatype.Period as R4Period
import com.projectronin.interop.fhir.r4.datatype.Range as R4Range
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime as R4DateTime
import com.projectronin.interop.fhir.r4.resource.Condition as R4Condition

@GraphQLDescription("A patient condition")
data class Condition(
    private val condition: R4Condition,
    private val tenant: Tenant,
) {
    @GraphQLDescription("The internal identifier for this condition")
    val id: String? by lazy {
        condition.id!!.value!!.localizeFhirId(tenant.mnemonic)
    }

    @GraphQLDescription("List of external identifiers for this condition")
    val identifier: List<Identifier> by lazy {
        condition.identifier.map(::Identifier)
    }

    @GraphQLDescription("The clinical status of this condition (e.g. active, relapse, recurrence, etc)")
    val clinicalStatus: CodeableConcept? by lazy {
        condition.clinicalStatus?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("The verification status of this condition (e.g. confirmed, differential, unconfirmed, etc")
    val verificationStatus: CodeableConcept? by lazy {
        condition.verificationStatus?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("The category of this condition (e.g. probelm-list-item, encounter-diagnosis, etc)")
    val category: List<CodeableConcept> by lazy {
        condition.category.map(::CodeableConcept)
    }

    @GraphQLDescription("Subjective severity of condition")
    val severity: CodeableConcept? by lazy {
        condition.severity?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("Identification of the condition, problem or diagnosis")
    val code: CodeableConcept? by lazy {
        condition.code?.let { CodeableConcept(it) }
    }

    @GraphQLDescription("Anatomical location, if relevant")
    val bodySite: List<CodeableConcept> by lazy {
        condition.bodySite.map(::CodeableConcept)
    }

    @GraphQLDescription("Who has the condition?")
    val subject: Reference by lazy {
        Reference.from(condition.subject, tenant)
    }

    @GraphQLDescription("Encounter created as part of")
    val encounter: Reference? by lazy {
        condition.encounter?.let { Reference.from(it, tenant) }
    }

    @GraphQLDescription("Estimated or actual date, date-time, or age")
    val onset: Onset? by lazy {
        condition.onset?.let {
            when (it.type) {
                DynamicValueType.DATE_TIME -> DateTimeOnset(it.value as R4DateTime)
                DynamicValueType.AGE -> AgeOnset(it.value as R4Age)
                DynamicValueType.PERIOD -> PeriodOnset(it.value as R4Period)
                DynamicValueType.RANGE -> RangeOnset(it.value as R4Range)
                DynamicValueType.STRING -> StringOnset(it.value as String)
                else -> throw RuntimeException("Unknown condition onset type encountered")
            }
        }
    }

    @GraphQLDescription("When in resolution/remission")
    val abatement: Abatement? by lazy {
        condition.abatement?.let {
            when (it.type) {
                DynamicValueType.DATE_TIME -> DateTimeAbatement(it.value as R4DateTime)
                DynamicValueType.AGE -> AgeAbatement(it.value as R4Age)
                DynamicValueType.PERIOD -> PeriodAbatement(it.value as R4Period)
                DynamicValueType.RANGE -> RangeAbatement(it.value as R4Range)
                DynamicValueType.STRING -> StringAbatement(it.value as String)
                else -> throw RuntimeException("Unknown condition abatement type encountered")
            }
        }
    }

    @GraphQLDescription("Date record was first recorded")
    val recordedDate: String? = condition.recordedDate?.value

    @GraphQLDescription("Who recorded the condition")
    val recorder: Reference? by lazy {
        condition.recorder?.let { Reference.from(it, tenant) }
    }

    @GraphQLDescription("Person who asserts this condition")
    val asserter: Reference? by lazy {
        condition.asserter?.let { Reference.from(it, tenant) }
    }

    @GraphQLDescription("Stage/grade, usually assessed formally")
    val stage: List<Stage> by lazy {
        condition.stage.map { Stage(it, tenant) }
    }

    @GraphQLDescription("Supporting evidence")
    val evidence: List<Evidence> by lazy {
        condition.evidence.map { Evidence(it, tenant) }
    }

    @GraphQLDescription("Additional information about the Condition")
    val note: List<Annotation> by lazy {
        condition.note.map { Annotation(it, tenant) }
    }
}
