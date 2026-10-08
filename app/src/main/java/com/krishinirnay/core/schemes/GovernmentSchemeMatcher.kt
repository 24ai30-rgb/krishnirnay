package com.krishinirnay.core.schemes

import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.GovtScheme

/** A scheme the farmer's profile satisfies, plus why — never an LLM judgment call. */
data class MatchedScheme(
    val scheme: GovtScheme,
    val reasons: List<String>,
)

/**
 * Pure Kotlin, no Android/network imports — deterministic rules only, exactly
 * like DecisionEngine/FertilizerAdvisor. A scheme's applicableStates/
 * applicableCrops/land-size bounds are checked directly against the farmer's
 * real FarmerProfile; an empty applicableStates/applicableCrops list means
 * "no restriction on that axis," never "matches nothing." Never invents a
 * scheme or an eligibility rule the scheme data itself doesn't carry.
 */
object GovernmentSchemeMatcher {

    fun match(profile: FarmerProfile, schemes: List<GovtScheme>): List<MatchedScheme> =
        schemes.mapNotNull { scheme -> matchOne(profile, scheme) }

    private fun matchOne(profile: FarmerProfile, scheme: GovtScheme): MatchedScheme? {
        val reasons = mutableListOf<String>()

        if (scheme.applicableStates.isNotEmpty()) {
            val state = profile.farmLocation.state
            if (state.isBlank() || scheme.applicableStates.none { it.equals(state, ignoreCase = true) }) return null
            reasons += "Available in ${state}."
        }

        if (scheme.applicableCrops.isNotEmpty()) {
            val crop = profile.primaryCrop
            if (crop.isNullOrBlank() || scheme.applicableCrops.none { it.equals(crop, ignoreCase = true) }) return null
            reasons += "Applies to $crop."
        }

        scheme.minLandAcres?.let { min ->
            if (profile.farmSizeAcres < min) return null
            reasons += "Requires at least $min acre(s) — your farm qualifies."
        }

        scheme.maxLandAcres?.let { max ->
            if (profile.farmSizeAcres > max) return null
            reasons += "Requires at most $max acre(s) — your farm qualifies."
        }

        if (reasons.isEmpty()) {
            reasons += "Open to all farmers — no state, crop, or land-size restriction."
        }

        return MatchedScheme(scheme, reasons)
    }
}
