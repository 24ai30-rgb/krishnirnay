package com.krishinirnay.core.data.model

/**
 * Real, named government schemes with real benefit/eligibility descriptions —
 * still a static demo list (no live government API is wired up), but never
 * fabricated scheme names. [applicableStates]/[applicableCrops] empty means
 * "no restriction on this axis" — never a guessed exclusion. [minLandAcres]/
 * [maxLandAcres] null means no bound on that side. See
 * [com.krishinirnay.core.schemes.GovernmentSchemeMatcher] for how these are
 * checked against a farmer's real profile — deterministic rules only, never
 * an LLM judgment call on eligibility.
 */
data class GovtScheme(
    val id: String,
    val name: String,
    val benefit: String,
    val description: String,
    val eligibility: String,
    val applicableStates: List<String> = emptyList(),
    val applicableCrops: List<String> = emptyList(),
    val minLandAcres: Float? = null,
    val maxLandAcres: Float? = null,
    val requiredDocuments: List<String> = emptyList(),
    val applicationMethod: String = "",
    val officialSource: String = "",
    val lastUpdated: String = "",
)
