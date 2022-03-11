package com.projectronin.interop.proxy.server.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.transform.fhir.r4.util.localize
import com.projectronin.interop.ehr.model.Condition as EHRCondition

@GraphQLDescription("A patient condition")
data class Condition(
    private val condition: EHRCondition,
    private val tenant: Tenant
) {
    @GraphQLDescription("The internal identifier for this condition")
    val id: String by lazy {
        condition.id.localize(tenant)
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
            when (it) {
                is EHRCondition.DateTimeOnset -> DateTimeOnset(it)
                is EHRCondition.AgeOnset -> AgeOnset(it)
                is EHRCondition.PeriodOnset -> PeriodOnset(it)
                is EHRCondition.RangeOnset -> RangeOnset(it)
                is EHRCondition.StringOnset -> StringOnset(it)
                else -> throw RuntimeException("Unknown condition onset type encountered")
            }
        }
    }

    @GraphQLDescription("When in resolution/remission")
    val abatement: Abatement? by lazy {
        condition.abatement?.let {
            when (it) {
                is EHRCondition.DateTimeAbatement -> DateTimeAbatement(it)
                is EHRCondition.AgeAbatement -> AgeAbatement(it)
                is EHRCondition.PeriodAbatement -> PeriodAbatement(it)
                is EHRCondition.RangeAbatement -> RangeAbatement(it)
                is EHRCondition.StringAbatement -> StringAbatement(it)
                else -> throw RuntimeException("Unknown condition abatement type encountered")
            }
        }
    }

    @GraphQLDescription("Date record was first recorded")
    val recordedDate: String? = condition.recordedDate

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
