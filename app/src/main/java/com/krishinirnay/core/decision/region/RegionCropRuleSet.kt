package com.krishinirnay.core.decision.region

import com.krishinirnay.core.decision.DecisionRules

/**
 * Region/crop-specific overrides for [com.krishinirnay.core.decision.DecisionEngine] —
 * a modular rule layer so a new region+crop combination (e.g. "Maharashtra + Soybean",
 * "Punjab + Wheat") is a new implementation of this interface, never a change to the
 * engine itself or a hardcoded branch in a screen. [RegionCropRuleRegistry] is the
 * only place that decides which implementation applies.
 *
 * Deliberately narrow for Phase 2: only the two knobs actually used today
 * (moisture thresholds, pest-vulnerable crop stages). Extend this interface — don't
 * fork the engine — when a rule needs a new knob.
 */
interface RegionCropRuleSet {
    val regionLabel: String
    val cropLabel: String

    /** Below this, water stress is HIGH. Defaults match the generic thresholds every region used before Phase 2. */
    val soilMoistureHighRiskBelowPct: Float get() = DecisionRules.SOIL_MOISTURE_HIGH_RISK_BELOW_PCT

    /** Below this (and at/above [soilMoistureHighRiskBelowPct]), water stress is MEDIUM. */
    val soilMoistureMediumRiskBelowPct: Float get() = DecisionRules.SOIL_MOISTURE_MEDIUM_RISK_BELOW_PCT

    /**
     * Crop stages (matching [com.krishinirnay.core.data.model.FarmerProfile.seedlingStage])
     * where a pest detection is treated as more severe than its raw model confidence alone
     * would suggest — e.g. flowering/boll stages are widely known to be when pest damage
     * costs the most yield. Empty by default (no escalation) so an unmodeled region/crop
     * never silently overclaims agronomic specificity it doesn't have.
     */
    val pestVulnerableCropStages: Set<String> get() = emptySet()
}

/** Fallback for any region/crop combination without a dedicated rule set — the exact thresholds every region used before this rule layer existed. */
object DefaultRuleSet : RegionCropRuleSet {
    override val regionLabel: String = "General"
    override val cropLabel: String = "General"
}
